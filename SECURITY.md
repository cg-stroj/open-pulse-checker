# Security Policy

Open Pulse Checker follows a security-first development model and aligns with **OWASP ASVS** principles as a baseline.

## Baseline controls (Phase 1.1)
- Secure defaults and least privilege
- Static analysis via CodeQL workflow
- Dependency hygiene via Maven dependency management and CI build gates
- Containerized runtime with non-root execution
- Spring Security HTTP Basic auth baseline with role separation (`ADMIN`, `VIEWER`)
- Write API endpoints restricted to `ADMIN`
- Read monitor endpoints restricted to authenticated users (`ADMIN`/`VIEWER`)
- Bounded scheduler worker pool to avoid unbounded thread growth

## Secrets policy
- No secrets in source control
- Local secrets must be supplied via secure environment management outside git
- `.env*`, keys, and certificate artifacts are ignored via `.gitignore`
- If a secret is committed, rotate immediately and purge history where possible

## Dependency policy
- Keep dependencies minimal and actively maintained
- Review transitive dependencies regularly
- Add SCA/dependency scanning enhancements in upcoming phases

## Auth baseline and hardening notes
- Local credentials are configurable via `OPENPULSE_SECURITY_*` environment variables
- Do not use default placeholder credentials outside local development
- Use hashed passwords via Spring Security format prefix (e.g. `{bcrypt}...`) in non-dev setups
- Prefer HTTPS/TLS termination in front of the API before exposing externally
- Rotate credentials regularly and after any suspected disclosure

## Secret/dependency scanning notes
- CodeQL is enabled in `.github/workflows/codeql.yml`
- CI build ensures dependency resolution and test execution on every PR/push
- Future phases will add dedicated dependency vulnerability and secret scanning automation

## Vulnerability reporting
Please open a private security advisory or contact maintainers through GitHub security channels.
Avoid public disclosure until a fix or mitigation is available.
