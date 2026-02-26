# Contributing

Thanks for your interest in Pulseguard.

## Development setup
1. Install Java 21 and Maven
2. Run `mvn clean test`
3. Start app locally (`mvn spring-boot:run`) or with Docker

## Pull request expectations
- Keep changes scoped and documented
- Add/adjust tests for behavioral changes
- Ensure CI passes (build/test + security checks)
- Avoid introducing secrets or sensitive data

## Code style
- Prefer clarity and explicitness
- Follow Spring Boot conventions
- Keep security impact in mind for all changes
