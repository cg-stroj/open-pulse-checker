# Security Policy

Open Pulse Checker follows a security-first development model and aligns with **OWASP ASVS** principles as a baseline.

## Baseline controls (Phase 0)
- Secure defaults and least privilege
- Static analysis via CodeQL workflow
- Dependency hygiene via Maven dependency management and CI build gates
- Containerized runtime with non-root execution

## Secrets policy
- No secrets in source control
- Local secrets must be supplied via secure environment management outside git
- `.env*`, keys, and certificate artifacts are ignored via `.gitignore`
- If a secret is committed, rotate immediately and purge history where possible

## Dependency policy
- Keep dependencies minimal and actively maintained
- Review transitive dependencies regularly
- Add SCA/dependency scanning enhancements in upcoming phases

## Secret/dependency scanning notes
- CodeQL is enabled in `.github/workflows/codeql.yml`
- CI build ensures dependency resolution and test execution on every PR/push
- Future phases will add dedicated dependency vulnerability and secret scanning automation

## Vulnerability reporting
Please open a private security advisory or contact maintainers through GitHub security channels.
Avoid public disclosure until a fix or mitigation is available.
