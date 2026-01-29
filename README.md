# Nexus Repository Vagrant Plugin

A Sonatype Nexus Repository Manager 3 plugin that adds **hosted repository** support for [Vagrant](https://www.vagrantup.com/) boxes. Upload, version, and serve `.box` files with dynamic metadata generation compatible with the Vagrant CLI.

[![Build & Unit Tests](https://github.com/ed-vazquez/sonatype-nexus-vagrant-hosted-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/ed-vazquez/sonatype-nexus-vagrant-hosted-plugin/actions/workflows/build.yml)
[![Integration Tests](https://github.com/ed-vazquez/sonatype-nexus-vagrant-hosted-plugin/actions/workflows/integration.yml/badge.svg)](https://github.com/ed-vazquez/sonatype-nexus-vagrant-hosted-plugin/actions/workflows/integration.yml)

## Features

- **Hosted Vagrant repositories** &mdash; store and serve `.box` files from Nexus
- **Dynamic metadata** &mdash; catalog JSON is generated on the fly from stored assets, fully compatible with `vagrant box add`, `vagrant box outdated`, and `vagrant box update`
- **Multi-provider support** &mdash; virtualbox, libvirt, hyper-v, VMware, and any other Vagrant provider
- **Semantic versioning** &mdash; multiple versions per box, multiple providers per version
- **SHA-256 checksums** &mdash; integrity verification on every upload and download
- **Nexus-native integration** &mdash; browse, search, cleanup policies, security, and REST API management all work out of the box

## Requirements

- Nexus Repository Manager **3.75.0** or later
- Java **17**

## Installation

1. Download the JAR from the [latest release](https://github.com/ed-vazquez/sonatype-nexus-vagrant-hosted-plugin/releases/latest), or build it from source:

   ```bash
   mvn clean package -s .mvn/maven-settings.xml -DskipTests
   ```

2. Copy the JAR into the Nexus deploy directory:

   ```bash
   cp target/nexus-repository-vagrant-1.0.0-SNAPSHOT.jar \
     $NEXUS_HOME/deploy/
   ```

3. Restart Nexus. The plugin is loaded automatically via the OSGi hot-deploy mechanism.

## Usage

### Create a hosted repository

```bash
curl -u admin:admin123 \
  -X POST http://localhost:8081/service/rest/v1/repositories/vagrant/hosted \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "vagrant-hosted",
    "online": true,
    "storage": {
      "blobStoreName": "default",
      "strictContentTypeValidation": false,
      "writePolicy": "ALLOW"
    }
  }'
```

### Upload a box

```bash
curl -u admin:admin123 \
  -X PUT \
  --upload-file mybox.box \
  http://localhost:8081/repository/vagrant-hosted/myorg/mybox/1.0.0/virtualbox/mybox.box
```

### Use with the Vagrant CLI

```bash
# Add a box from the repository
vagrant box add http://localhost:8081/repository/vagrant-hosted/myorg/mybox

# Check for updates
vagrant box outdated

# Update to the latest version
vagrant box update
```

### Download a box directly

```bash
curl -O http://localhost:8081/repository/vagrant-hosted/myorg/mybox/1.0.0/virtualbox/mybox.box
```

### Delete a box

```bash
curl -u admin:admin123 \
  -X DELETE \
  http://localhost:8081/repository/vagrant-hosted/myorg/mybox/1.0.0/virtualbox/mybox.box
```

## Asset path structure

All box files follow this path convention:

```
/{org}/{name}/{version}/{provider}/{filename}.box
```

Requesting `GET /{org}/{name}` returns a Vagrant-compatible catalog:

```json
{
  "name": "myorg/mybox",
  "description": "Vagrant box myorg/mybox",
  "versions": [
    {
      "version": "1.0.0",
      "status": "active",
      "providers": [
        {
          "name": "virtualbox",
          "url": "http://localhost:8081/repository/vagrant-hosted/myorg/mybox/1.0.0/virtualbox/mybox.box",
          "checksum_type": "sha256",
          "checksum": "e3b0c44298fc1c149afbf4c8..."
        }
      ]
    }
  ]
}
```

## API reference

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/{org}/{name}` | Retrieve box metadata (catalog JSON) |
| `GET` | `/{org}/{name}/{version}/{provider}/{file}.box` | Download a box file |
| `PUT` | `/{org}/{name}/{version}/{provider}/{file}.box` | Upload a box file |
| `DELETE` | `/{org}/{name}/{version}/{provider}/{file}.box` | Delete a box file |
| `HEAD` | `/{org}/{name}` | Check metadata existence |
| `HEAD` | `/{org}/{name}/{version}/{provider}/{file}.box` | Check box existence |
| `POST` | `/service/rest/v1/repositories/vagrant/hosted` | Create a hosted repository |
| `PUT` | `/service/rest/v1/repositories/vagrant/hosted/{name}` | Update a hosted repository |

## Development

### Build

```bash
mvn clean package -s .mvn/maven-settings.xml
```

### Unit tests

```bash
mvn clean verify -s .mvn/maven-settings.xml
```

### Integration tests

The integration test suite starts a real Nexus instance in Docker, installs the plugin, and runs HTTP-level tests against it:

```bash
src/test/integration/run-it.sh
```

This requires Docker. The script will:

1. Build the plugin JAR (skipped if already built)
2. Start a Nexus 3.75.0 container with the plugin deployed
3. Create a vagrant-hosted repository
4. Run 13 HTTP tests covering upload, metadata, download, 404s, and delete
5. If `vagrant` is installed locally, run Vagrant CLI interop tests (`box add`, `box list`, `box outdated`, `box update`, `box remove`)

## Project structure

```
src/main/java/org/sonatype/nexus/plugins/vagrant/
  model/                    # VagrantBoxMetadata, VagrantBoxVersion, VagrantBoxProvider
  internal/                 # VagrantFormat, security, VagrantMetadataBuilder
  rest/                     # REST API resources and request/response models
  datastore/
    internal/
      VagrantContentFacetImpl.java   # Content storage operations
      VagrantHostedHandler.java      # HTTP request routing
      VagrantHostedRecipe.java       # Repository recipe configuration
      browse/               # Browse node generation
      store/                # DAO interfaces
```
