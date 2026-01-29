# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-01-29

### Added

- Hosted repository support for Vagrant boxes in Nexus Repository Manager 3
- Dynamic Vagrant catalog metadata generation compatible with the Vagrant CLI (`vagrant box add`, `vagrant box outdated`, `vagrant box update`)
- Multi-provider support (virtualbox, libvirt, hyper-v, VMware, etc.)
- SHA-256 checksum verification on upload and download
- REST API for repository management (`POST`/`PUT` hosted repository configuration)
- HTTP endpoints for box operations (`GET`, `PUT`, `DELETE`, `HEAD`)
- Nexus-native browse, search, cleanup policies, and security integration
- GitHub Actions CI: build & unit tests, integration tests, release, and CodeQL scanning
- Integration test suite with 13 HTTP-level tests against a real Nexus instance in Docker
- Vagrant CLI interop tests (box add, list, outdated, update, remove)
- Dependabot for automated dependency updates
- Branch protection requiring CI to pass before merging to main

[1.0.0]: https://github.com/ed-vazquez/sonatype-nexus-vagrant-hosted-plugin/releases/tag/v1.0.0
