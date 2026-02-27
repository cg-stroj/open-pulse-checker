# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]
### Added
- v2.1 release-readiness gate checklist document (`docs/v2.1-release-readiness-checklist.md`) covering test/migration/security/release-cut/rollback criteria and evidence.
- Release runbook cut process section with exact gate/tag/deploy verification commands.
- Audit API v2 endpoints (`/api/v2/admin/audit-events` + `/export`) with filter parity, cursor pagination option, and bounded export limits.
- Audit API v2 integration tests for filter combinations, cursor pagination behavior, and export format/guardrail correctness.
- DB migration `V8__audit_events_query_indexes.sql` to add query-supporting indexes for audit workloads.

### Changed
- README now links canonical v2.1 readiness checklist and FE v1 (EPIC #48) closure evidence path.
- Release template updated to include frontend smoke gate and explicit release cut/rollback command capture.
