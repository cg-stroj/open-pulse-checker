# Architecture (Phase 0)

## Current components
- **API Service (Spring Boot, Java 21):** foundational backend service exposing a health endpoint
- **CI/CD Baseline:** GitHub Actions for build/test and CodeQL static analysis
- **Container Runtime:** Dockerfile + docker-compose for local self-hosted execution
- **Install Framework:** cross-platform preflight and installer stubs

## Security-first principles
- Least-privilege runtime (non-root container user)
- Minimal initial attack surface
- Dependency and static code analysis in CI
- No embedded credentials or secrets in source

## Future split (target)
- `pulseguard-api` (control plane and API)
- `pulseguard-agent` (node/host metrics collection)
- `pulseguard-ui` (web dashboard)
- `pulseguard-rules` (detection rules and policy packs)
