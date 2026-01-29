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
