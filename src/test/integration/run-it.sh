#!/usr/bin/env bash
#
# Integration test for the Vagrant hosted repository plugin.
#
# Builds the plugin, installs it into a Nexus 3 Docker container,
# and runs HTTP-level tests against real Vagrant operations:
#   - Upload a .box file
#   - Retrieve metadata JSON
#   - Download the .box file
#   - Delete the .box file
#
# Usage: ./src/test/integration/run-it.sh
#
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
NEXUS_VERSION="3.75.0"
CONTAINER_NAME="nexus-vagrant-it-$$"
NEXUS_PORT=8081
MAX_WAIT=120
PASSED=0
FAILED=0

cleanup() {
  echo ""
  echo "=== Cleanup ==="
  docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
}
trap cleanup EXIT

fail() {
  echo "  FAIL: $1"
  FAILED=$((FAILED + 1))
}

pass() {
  echo "  PASS: $1"
  PASSED=$((PASSED + 1))
}

assert_status() {
  local expected="$1" actual="$2" desc="$3"
  if [ "$actual" -eq "$expected" ]; then
    pass "$desc (HTTP $actual)"
  else
    fail "$desc — expected HTTP $expected, got HTTP $actual"
  fi
}

# -------------------------------------------------------
# Step 1: Build the plugin
# -------------------------------------------------------
echo "=== Building plugin ==="
docker run --rm \
  -v "$PROJECT_DIR":/build -w /build \
  maven:3.9-eclipse-temurin-17 \
  mvn clean package -s /build/.mvn/maven-settings.xml -DskipTests -q

JAR="$PROJECT_DIR/target/nexus-repository-vagrant-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
  echo "ERROR: Plugin JAR not found at $JAR"
  exit 1
fi
echo "Plugin JAR built: $(basename "$JAR")"

# -------------------------------------------------------
# Step 2: Start Nexus with plugin in the deploy directory
# -------------------------------------------------------
echo "=== Starting Nexus container ==="
docker run -d \
  --name "$CONTAINER_NAME" \
  -p "$NEXUS_PORT:8081" \
  -v "$JAR:/opt/sonatype/nexus/deploy/nexus-repository-vagrant-1.0.0-SNAPSHOT.jar:ro" \
  "sonatype/nexus3:$NEXUS_VERSION"

# -------------------------------------------------------
# Step 3: Wait for Nexus to become ready
# -------------------------------------------------------
echo "Waiting for Nexus to start (up to ${MAX_WAIT}s)..."
ELAPSED=0
until curl -sf "http://localhost:$NEXUS_PORT/service/rest/v1/status" >/dev/null 2>&1; do
  sleep 5
  ELAPSED=$((ELAPSED + 5))
  if [ "$ELAPSED" -ge "$MAX_WAIT" ]; then
    echo "ERROR: Nexus did not start within ${MAX_WAIT}s"
    docker logs "$CONTAINER_NAME" 2>&1 | tail -30
    exit 1
  fi
  printf "  %ds...\n" "$ELAPSED"
done
echo "Nexus is ready."

# Get admin password
ADMIN_PASS=$(docker exec "$CONTAINER_NAME" cat /nexus-data/admin.password 2>/dev/null || echo "admin123")
AUTH="admin:$ADMIN_PASS"

# -------------------------------------------------------
# Step 5: Create a vagrant-hosted repository
# -------------------------------------------------------
echo ""
echo "=== Creating vagrant-hosted repository ==="
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -u "$AUTH" \
  -X POST "http://localhost:$NEXUS_PORT/service/rest/v1/repositories/vagrant/hosted" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "vagrant-test",
    "online": true,
    "storage": {
      "blobStoreName": "default",
      "strictContentTypeValidation": false,
      "writePolicy": "ALLOW"
    }
  }')

if [ "$HTTP_CODE" -eq 201 ] || [ "$HTTP_CODE" -eq 200 ]; then
  pass "Create repository (HTTP $HTTP_CODE)"
else
  fail "Create repository — HTTP $HTTP_CODE (plugin may not have loaded)"
  echo ""
  echo "=== Checking Nexus logs for plugin errors ==="
  docker logs "$CONTAINER_NAME" 2>&1 | grep -i -E '(vagrant|ERROR|WARN.*bundle)' | tail -20
  echo ""
  echo "Results: $PASSED passed, $FAILED failed"
  exit 1
fi

REPO_URL="http://localhost:$NEXUS_PORT/repository/vagrant-test"

# -------------------------------------------------------
# Step 6: Create a dummy .box file
# -------------------------------------------------------
DUMMY_BOX=$(mktemp)
dd if=/dev/urandom bs=1024 count=10 of="$DUMMY_BOX" 2>/dev/null
BOX_SHA256=$(shasum -a 256 "$DUMMY_BOX" 2>/dev/null || sha256sum "$DUMMY_BOX")
BOX_SHA256=$(echo "$BOX_SHA256" | awk '{print $1}')

# -------------------------------------------------------
# Step 7: Upload a box
# -------------------------------------------------------
echo ""
echo "=== Test: Upload box ==="
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -u "$AUTH" \
  -X PUT \
  --upload-file "$DUMMY_BOX" \
  "$REPO_URL/testorg/testbox/1.0.0/virtualbox/testbox.box")
assert_status 201 "$HTTP_CODE" "PUT testorg/testbox/1.0.0/virtualbox/testbox.box"

# Upload a second provider
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -u "$AUTH" \
  -X PUT \
  --upload-file "$DUMMY_BOX" \
  "$REPO_URL/testorg/testbox/1.0.0/libvirt/testbox.box")
assert_status 201 "$HTTP_CODE" "PUT testorg/testbox/1.0.0/libvirt/testbox.box"

# Upload a second version
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -u "$AUTH" \
  -X PUT \
  --upload-file "$DUMMY_BOX" \
  "$REPO_URL/testorg/testbox/2.0.0/virtualbox/testbox.box")
assert_status 201 "$HTTP_CODE" "PUT testorg/testbox/2.0.0/virtualbox/testbox.box"

# -------------------------------------------------------
# Step 8: Retrieve metadata
# -------------------------------------------------------
echo ""
echo "=== Test: Get metadata ==="
METADATA_FILE=$(mktemp)
HTTP_CODE=$(curl -s -o "$METADATA_FILE" -w '%{http_code}' \
  "$REPO_URL/testorg/testbox")
assert_status 200 "$HTTP_CODE" "GET testorg/testbox (metadata)"

# Validate metadata structure
if command -v python3 >/dev/null 2>&1; then
  VALIDATION=$(python3 -c "
import json, sys
with open('$METADATA_FILE') as f:
    m = json.load(f)
errors = []
if m.get('name') != 'testorg/testbox':
    errors.append(f\"name: expected 'testorg/testbox', got '{m.get('name')}'\")
versions = {v['version']: v for v in m.get('versions', [])}
if '1.0.0' not in versions:
    errors.append('missing version 1.0.0')
if '2.0.0' not in versions:
    errors.append('missing version 2.0.0')
if '1.0.0' in versions:
    providers = {p['name'] for p in versions['1.0.0'].get('providers', [])}
    if 'virtualbox' not in providers:
        errors.append('1.0.0 missing virtualbox provider')
    if 'libvirt' not in providers:
        errors.append('1.0.0 missing libvirt provider')
if errors:
    print('FAIL: ' + '; '.join(errors))
    sys.exit(1)
else:
    print('OK')
" 2>&1)
  if [ "$?" -eq 0 ]; then
    pass "Metadata JSON structure is valid"
  else
    fail "Metadata validation: $VALIDATION"
  fi
  echo "  Metadata content:"
  python3 -m json.tool "$METADATA_FILE" 2>/dev/null | sed 's/^/    /'
fi

# -------------------------------------------------------
# Step 9: Download a box file
# -------------------------------------------------------
echo ""
echo "=== Test: Download box ==="
DOWNLOAD_FILE=$(mktemp)
HTTP_CODE=$(curl -s -o "$DOWNLOAD_FILE" -w '%{http_code}' \
  "$REPO_URL/testorg/testbox/1.0.0/virtualbox/testbox.box")
assert_status 200 "$HTTP_CODE" "GET testorg/testbox/1.0.0/virtualbox/testbox.box"

DOWNLOAD_SHA256=$(shasum -a 256 "$DOWNLOAD_FILE" 2>/dev/null || sha256sum "$DOWNLOAD_FILE")
DOWNLOAD_SHA256=$(echo "$DOWNLOAD_SHA256" | awk '{print $1}')
if [ "$BOX_SHA256" = "$DOWNLOAD_SHA256" ]; then
  pass "Downloaded file checksum matches upload"
else
  fail "Checksum mismatch: uploaded=$BOX_SHA256 downloaded=$DOWNLOAD_SHA256"
fi

# -------------------------------------------------------
# Step 10: 404 for nonexistent box
# -------------------------------------------------------
echo ""
echo "=== Test: Not found ==="
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  "$REPO_URL/testorg/testbox/9.9.9/virtualbox/testbox.box")
assert_status 404 "$HTTP_CODE" "GET nonexistent version"

HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  "$REPO_URL/noorg/nobox")
assert_status 404 "$HTTP_CODE" "GET nonexistent box metadata"

# -------------------------------------------------------
# Step 11: Delete a box
# -------------------------------------------------------
echo ""
echo "=== Test: Delete box ==="
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -u "$AUTH" \
  -X DELETE \
  "$REPO_URL/testorg/testbox/2.0.0/virtualbox/testbox.box")
assert_status 204 "$HTTP_CODE" "DELETE testorg/testbox/2.0.0/virtualbox/testbox.box"

# Verify deleted box returns 404
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  "$REPO_URL/testorg/testbox/2.0.0/virtualbox/testbox.box")
assert_status 404 "$HTTP_CODE" "GET deleted box returns 404"

# Verify metadata no longer includes deleted version
METADATA_FILE2=$(mktemp)
curl -s -o "$METADATA_FILE2" "$REPO_URL/testorg/testbox"
if command -v python3 >/dev/null 2>&1; then
  HAS_200=$(python3 -c "
import json
with open('$METADATA_FILE2') as f:
    m = json.load(f)
versions = [v['version'] for v in m.get('versions', [])]
print('2.0.0' not in versions)
" 2>&1)
  if [ "$HAS_200" = "True" ]; then
    pass "Metadata no longer includes deleted version"
  else
    fail "Metadata still includes deleted version 2.0.0"
  fi
fi

# -------------------------------------------------------
# Vagrant CLI interop tests (only if vagrant is installed)
# -------------------------------------------------------
if ! command -v vagrant >/dev/null 2>&1; then
  echo ""
  echo "=== Vagrant CLI not found — skipping interop tests ==="
else
  echo ""
  echo "######################################################"
  echo "  Vagrant CLI Interop Tests"
  echo "######################################################"

  # Use a temporary VAGRANT_HOME so we don't pollute the user's boxes
  VAGRANT_TEST_HOME=$(mktemp -d)
  export VAGRANT_HOME="$VAGRANT_TEST_HOME"

  # Create a valid minimal .box file (tar.gz with metadata.json)
  VALID_BOX_DIR=$(mktemp -d)
  echo '{"provider": "virtualbox"}' > "$VALID_BOX_DIR/metadata.json"
  # Include a minimal Vagrantfile inside the box
  cat > "$VALID_BOX_DIR/Vagrantfile" <<'VFEOF'
Vagrant.configure("2") do |config|
end
VFEOF
  VALID_BOX=$(mktemp -u).box
  tar -czf "$VALID_BOX" -C "$VALID_BOX_DIR" metadata.json Vagrantfile

  # Upload the valid .box as v1.0.0/virtualbox
  echo ""
  echo "=== Setup: Upload valid .box v1.0.0 ==="
  HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
    -u "$AUTH" -X PUT --upload-file "$VALID_BOX" \
    "$REPO_URL/cliorg/clibox/1.0.0/virtualbox/clibox.box")
  assert_status 201 "$HTTP_CODE" "Upload cliorg/clibox v1.0.0 for CLI tests"

  # -------------------------------------------------------
  # Test: vagrant box add from catalog URL
  # -------------------------------------------------------
  echo ""
  echo "=== Test: vagrant box add ==="
  VAGRANT_ADD_OUTPUT=$(vagrant box add "$REPO_URL/cliorg/clibox" \
    --provider virtualbox \
    --force 2>&1) || true
  echo "  vagrant box add output:"
  echo "$VAGRANT_ADD_OUTPUT" | sed 's/^/    /'

  if echo "$VAGRANT_ADD_OUTPUT" | grep -qi "successfully added"; then
    pass "vagrant box add succeeded"
  else
    fail "vagrant box add did not report success"
  fi

  # -------------------------------------------------------
  # Test: vagrant box list shows the box
  # -------------------------------------------------------
  echo ""
  echo "=== Test: vagrant box list ==="
  BOX_LIST_OUTPUT=$(vagrant box list 2>&1)
  echo "  vagrant box list output:"
  echo "$BOX_LIST_OUTPUT" | sed 's/^/    /'

  if echo "$BOX_LIST_OUTPUT" | grep -q "cliorg/clibox"; then
    pass "vagrant box list shows cliorg/clibox"
  else
    fail "vagrant box list does not show cliorg/clibox"
  fi

  if echo "$BOX_LIST_OUTPUT" | grep -q "1.0.0"; then
    pass "vagrant box list shows version 1.0.0"
  else
    fail "vagrant box list does not show version 1.0.0"
  fi

  # -------------------------------------------------------
  # Test: vagrant box outdated (should be up to date)
  # -------------------------------------------------------
  echo ""
  echo "=== Test: vagrant box outdated (no update available) ==="
  VAGRANTFILE_DIR=$(mktemp -d)
  cat > "$VAGRANTFILE_DIR/Vagrantfile" <<VFEOF
Vagrant.configure("2") do |config|
  config.vm.box = "cliorg/clibox"
  config.vm.box_url = "$REPO_URL/cliorg/clibox"
end
VFEOF

  OUTDATED_OUTPUT=$(cd "$VAGRANTFILE_DIR" && vagrant box outdated 2>&1) || true
  echo "  vagrant box outdated output:"
  echo "$OUTDATED_OUTPUT" | sed 's/^/    /'

  if echo "$OUTDATED_OUTPUT" | grep -qi "up to date\|is running the latest\|Checking if box"; then
    # If it reports "Checking" without "newer version", the box is current
    if echo "$OUTDATED_OUTPUT" | grep -qi "newer version"; then
      fail "vagrant box outdated found unexpected newer version"
    else
      pass "vagrant box outdated reports up to date"
    fi
  else
    fail "vagrant box outdated did not report up to date"
  fi

  # -------------------------------------------------------
  # Upload v2.0.0 and test outdated detection
  # -------------------------------------------------------
  echo ""
  echo "=== Setup: Upload valid .box v2.0.0 ==="
  HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
    -u "$AUTH" -X PUT --upload-file "$VALID_BOX" \
    "$REPO_URL/cliorg/clibox/2.0.0/virtualbox/clibox.box")
  assert_status 201 "$HTTP_CODE" "Upload cliorg/clibox v2.0.0 for CLI tests"

  # -------------------------------------------------------
  # Test: vagrant box outdated (update available)
  # -------------------------------------------------------
  echo ""
  echo "=== Test: vagrant box outdated (update available) ==="
  OUTDATED_OUTPUT=$(cd "$VAGRANTFILE_DIR" && vagrant box outdated --force 2>&1) || true
  echo "  vagrant box outdated output:"
  echo "$OUTDATED_OUTPUT" | sed 's/^/    /'

  if echo "$OUTDATED_OUTPUT" | grep -qi "newer version\|2.0.0"; then
    pass "vagrant box outdated detects v2.0.0 is available"
  else
    fail "vagrant box outdated did not detect v2.0.0 update"
  fi

  # -------------------------------------------------------
  # Test: vagrant box update
  # -------------------------------------------------------
  echo ""
  echo "=== Test: vagrant box update ==="
  UPDATE_OUTPUT=$(cd "$VAGRANTFILE_DIR" && vagrant box update --force 2>&1) || true
  echo "  vagrant box update output:"
  echo "$UPDATE_OUTPUT" | sed 's/^/    /'

  if echo "$UPDATE_OUTPUT" | grep -qi "successfully added.*2.0.0\|2.0.0.*successfully"; then
    pass "vagrant box update to v2.0.0 succeeded"
  elif echo "$UPDATE_OUTPUT" | grep -qi "successfully added" && echo "$UPDATE_OUTPUT" | grep -q "2.0.0"; then
    pass "vagrant box update to v2.0.0 succeeded"
  else
    fail "vagrant box update did not succeed"
  fi

  # Verify v2.0.0 is now installed
  BOX_LIST_OUTPUT=$(vagrant box list 2>&1)
  if echo "$BOX_LIST_OUTPUT" | grep -q "2.0.0"; then
    pass "vagrant box list shows version 2.0.0 after update"
  else
    fail "vagrant box list does not show version 2.0.0 after update"
  fi

  # -------------------------------------------------------
  # Test: vagrant box add with --box-version constraint
  # -------------------------------------------------------
  echo ""
  echo "=== Test: vagrant box add with version constraint ==="
  VAGRANT_VER_OUTPUT=$(vagrant box add "$REPO_URL/cliorg/clibox" \
    --provider virtualbox \
    --box-version "1.0.0" \
    --force 2>&1) || true
  echo "  vagrant box add --box-version output:"
  echo "$VAGRANT_VER_OUTPUT" | sed 's/^/    /'

  if echo "$VAGRANT_VER_OUTPUT" | grep -qi "successfully added"; then
    pass "vagrant box add with version constraint succeeded"
  else
    fail "vagrant box add with version constraint did not succeed"
  fi

  # -------------------------------------------------------
  # Test: vagrant box remove
  # -------------------------------------------------------
  echo ""
  echo "=== Test: vagrant box remove ==="
  REMOVE_OUTPUT=$(vagrant box remove "cliorg/clibox" --all --force 2>&1) || true
  echo "  vagrant box remove output:"
  echo "$REMOVE_OUTPUT" | sed 's/^/    /'

  if echo "$REMOVE_OUTPUT" | grep -q "Removing box\|Successfully removed\|Box.*removed"; then
    pass "vagrant box remove succeeded"
  else
    fail "vagrant box remove did not succeed"
  fi

  # Verify box is gone
  BOX_LIST_OUTPUT=$(vagrant box list 2>&1)
  if echo "$BOX_LIST_OUTPUT" | grep -q "cliorg/clibox"; then
    fail "vagrant box list still shows cliorg/clibox after remove"
  else
    pass "vagrant box list confirms box is removed"
  fi

  # Cleanup vagrant test artifacts
  rm -rf "$VAGRANT_TEST_HOME" "$VAGRANTFILE_DIR" "$VALID_BOX_DIR" "$VALID_BOX"
fi

# -------------------------------------------------------
# Results
# -------------------------------------------------------
echo ""
echo "========================================"
echo "  Results: $PASSED passed, $FAILED failed"
echo "========================================"

if [ "$FAILED" -gt 0 ]; then
  exit 1
fi
