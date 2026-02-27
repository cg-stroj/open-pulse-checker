## Open Pulse Checker Release {{version}}

### Highlights
- 

### Deployment checklist
- [ ] CI workflow (`.github/workflows/ci.yml`) green on release commit
- [ ] CodeQL workflow (`.github/workflows/codeql.yml`) enabled and latest scan reviewed
- [ ] `mvn test` passed locally or in CI
- [ ] `cd frontend && npm run lint && npm run build && npm run test:e2e:smoke` passed
- [ ] Flyway migration plan reviewed (forward + rollback documented)
- [ ] Backup completed before production migration/deploy
- [ ] Security-impacting changes reviewed against `SECURITY.md`
- [ ] `docs/v2.1-release-readiness-checklist.md` reviewed and status reflects current release commit

### Database and migration notes
- 

### Release cut commands used
```bash
# paste exact commands used for this release cut
```

### Rollback notes
- Last known good tag:
- Backup artifact name/location:
- DB restore command validated:

### Verification
- [ ] `/actuator/health` is UP
- [ ] `/actuator/info` reports expected version/build metadata
- [ ] Frontend smoke route checks passed after deploy
