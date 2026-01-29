# CLAUDE.md

## Project Overview

Sonatype Nexus Repository Manager 3 plugin that adds hosted repository support for Vagrant boxes. Built as an OSGi bundle deployed into Nexus via the `deploy/` directory.

## Build & Test Commands

```bash
# Build + unit tests
mvn clean verify -s .mvn/maven-settings.xml

# Build only (skip tests)
mvn clean package -s .mvn/maven-settings.xml -DskipTests

# Integration tests (requires Docker)
src/test/integration/run-it.sh
```

Always include `-s .mvn/maven-settings.xml` — the custom settings file configures the Groovy plugin repository required by the parent POM.

## Tech Stack

- **Java 17** (Temurin)
- **Nexus 3.75.0** parent POM (`org.sonatype.nexus.plugins:nexus-plugins:3.75.0-06`)
- **OSGi bundle** packaging via `karaf-maven-plugin`
- **Jackson** for JSON serialization
- **javax.inject** for dependency injection
- **JUnit 4 + Mockito** for unit tests

## Architecture

The plugin follows the standard Nexus 3 format plugin pattern:

```
src/main/java/org/sonatype/nexus/plugins/vagrant/
  model/         # VagrantBoxMetadata, VagrantBoxVersion, VagrantBoxProvider
  internal/      # VagrantFormat, VagrantMetadataBuilder, security classes
  rest/          # REST API resources for repository CRUD
  datastore/
    internal/
      VagrantContentFacetImpl.java   # Content storage (get/put/delete assets)
      VagrantHostedHandler.java      # HTTP request handler (routes → actions)
      VagrantHostedRecipe.java       # Repository recipe (wires facets + routes)
      browse/                        # Browse node generation
      store/                         # DAO interfaces (component, asset, blob)
```

### Key patterns

- **Recipe** (`VagrantHostedRecipe`) defines URL routes and attaches facets to the repository
- **Handler** (`VagrantHostedHandler`) implements request logic for GET/PUT/DELETE/HEAD
- **ContentFacet** (`VagrantContentFacetImpl`) manages blob storage, components, and assets via the Nexus fluent content API
- **MetadataBuilder** (`VagrantMetadataBuilder`) dynamically generates Vagrant catalog JSON by scanning stored assets
- All classes use `@Named`/`@Singleton`/`@Inject` for Nexus DI discovery

### Asset path convention

```
/{org}/{name}/{version}/{provider}/{filename}.box
```

Metadata is served dynamically at `GET /{org}/{name}` — there is no stored metadata file.

### URL routing

| Method | Path | Handler method |
|--------|------|---------------|
| `GET/HEAD` | `/{org}/{name}` | Returns generated catalog JSON |
| `GET/HEAD` | `/{org}/{name}/{version}/{provider}/{file}.box` | Downloads box file |
| `PUT` | `/{org}/{name}/{version}/{provider}/{file}.box` | Uploads box file |
| `DELETE` | `/{org}/{name}/{version}/{provider}/{file}.box` | Deletes box file |

## Testing

### Unit tests

Located at `src/test/java/`. Key test classes:

- `VagrantHostedHandlerTest` — tests all HTTP methods, status codes, and edge cases
- `VagrantMetadataBuilderTest` — tests metadata generation from asset lists
- `VagrantBoxMetadataTest` — tests JSON serialization of the model
- `VagrantHostedRecipeTest` — tests recipe configuration and facet wiring

Uses `nexus-testsupport` base classes and Mockito for mocking Nexus internals.

### Integration tests

`src/test/integration/run-it.sh` starts a real Nexus container with the plugin installed and runs 13 HTTP-level tests. When `vagrant` is installed locally, it also runs CLI interop tests. The script uses `section_open`/`section_close` helpers for collapsible output in GitHub Actions CI.

## CI/CD

- **build.yml** — build + unit tests on every push/PR
- **integration.yml** — integration tests with Docker (Nexus image cached)
- **release.yml** — triggered by `v*` tags; sets POM version from tag, builds, creates GitHub Release with JAR
- **codeql.yml** — static security analysis on push/PR to main + weekly

## Conventions

- Format name is `"vagrant"`, recipe name is `"vagrant-hosted"`
- Only hosted repositories are supported (no proxy or group)
- All Nexus-provided dependencies use `<scope>provided</scope>` since Nexus supplies them at runtime
- Checksums are SHA-256, stored as asset attributes
- The plugin uses the Nexus datastore (SQL) content API, not the legacy Orient storage

## Gotchas & Lessons Learned

### Build

- **Maven settings file is mandatory.** The parent POM (`nexus-plugins:3.75.0-06`) requires a Groovy plugin repository hosted on JFrog Artifactory. Builds without `-s .mvn/maven-settings.xml` will fail with unresolvable dependency errors.

### Integration tests

- **The script builds the plugin itself when run locally** using a Docker Maven container. However, in CI the workflow pre-builds the JAR with `mvn clean package`, and the script detects the existing JAR and skips the redundant Docker build. This saves ~99 seconds in CI. If you change the script, preserve this skip-if-exists logic.
- **Nexus JVM startup takes ~45 seconds.** This is the floor for integration test duration and cannot be cached or parallelized.
- **The Nexus Docker image (`sonatype/nexus3:3.75.0`) is ~700MB.** The integration workflow caches it with `docker save`/`docker load` to avoid pulling on every run. If the Nexus version changes, update the cache key in `integration.yml`.
- **The 13 HTTP tests are stateful and sequential** — uploads must happen before downloads, metadata checks, and deletes. They run in ~1 second total, so parallelizing them would add complexity for no gain.
- **Vagrant CLI tests are skipped in CI** because `vagrant` is not installed on GitHub runners. The script gracefully detects this and skips. These tests only run locally when `vagrant` is on `$PATH`.
- **CI log output uses `::group::`/`::endgroup::` workflow commands** (emitted when `CI=true`) to create collapsible sections in the GitHub Actions UI. Locally, the script prints `=== Section ===` headers instead. Failed assertions emit `::error::` annotations that surface in the workflow summary.

### CI/CD

- **Branch protection is enabled on `main`** — the `build` and `integration` checks must pass before merging. Direct pushes by the repo owner are allowed but flagged with a bypass warning.
- **Release versioning:** the POM has `1.0.0-SNAPSHOT` as its version. The release workflow runs `mvn versions:set` to strip `-SNAPSHOT` and set the version from the git tag (e.g., tag `v1.2.0` → POM version `1.2.0`) before building. This ensures the JAR filename matches the release. The POM in the repo should stay at `-SNAPSHOT`.
- **Release assets include a sources JAR** because the Maven build produces both `*.jar` and `*-sources.jar` in `target/`, and the release step uploads `target/nexus-repository-vagrant-*.jar` (glob match).

### Plugin deployment

- The plugin JAR is deployed to Nexus by copying it into `$NEXUS_HOME/deploy/`. Nexus picks it up automatically via OSGi hot-deploy on startup. No restart manager config or Karaf console commands are needed.

## Reference Documentation

### Nexus plugin development

- [Format Plugin Development Guide](https://github.com/sonatype-nexus-community/nexus-development-guides/blob/master/docs/format-plugin.md) — step-by-step walkthrough of building a format plugin (recipes, handlers, facets, routes)
- [Format Plugin Archetype](https://github.com/sonatype-nexus-community/nexus-format-archetype) — Maven archetype to scaffold a new format plugin project
- [Installing a Custom Plugin](https://sonatype-nexus-community.github.io/nexus-development-guides/plugin-install.html) — deploy directory, Karaf console, and permanent install methods
- [Nexus Public Source Code](https://github.com/sonatype/nexus-public) — Nexus Repository Manager open-source codebase (the APIs this plugin builds against)

### Example format plugins (community)

- [nexus-repository-composer](https://github.com/sonatype-nexus-community/nexus-repository-composer) — Composer format plugin, good reference for hosted + proxy recipes
- [nexus-repository-puppet](https://github.com/sonatype-nexus-community/nexus-repository-puppet) — Puppet format plugin

### Nexus REST API

- [Nexus Repository REST API](https://help.sonatype.com/en/rest-and-integration-api.html) — official API docs for repository management, assets, components, and search
