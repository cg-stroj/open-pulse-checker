## Open Pulse Checker Release {{version}}

### Highlights
- 

### Deployment checklist
- [ ] CI workflow (`.github/workflows/ci.yml`) green on release commit
- [ ] CodeQL workflow (`.github/workflows/codeql.yml`) enabled and latest scan reviewed
- [ ] `mvn clean test` passed locally or in CI
- [ ] Flyway migration plan reviewed (forward + rollback documented)
- [ ] Backup completed before production migration/deploy
- [ ] Security-impacting changes reviewed against `SECURITY.md`

### Database and migration notes
- 

### Rollback notes
- 

### Verification
- [ ] `/actuator/health` is UP
- [ ] `/actuator/info` reports expected version/build metadata
