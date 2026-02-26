# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]
### Added
- Phase 1.4 production-readiness hardening (distributed lock telemetry/idempotency checks, prod profile, release/runbook docs)

### Changed
- Scheduler lock renew semantics now prevent post-expiry renewals by stale owners
