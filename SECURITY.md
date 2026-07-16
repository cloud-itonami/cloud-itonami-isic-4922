# Security Policy

This project handles intercity/chartered-coach dispatch operations and
safety-concern workflows. Treat vulnerabilities as potentially high
impact even when the demo data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real passenger, driver or vehicle-incident data exposure
- authorization bypass
- IntercityCoachGovernor bypass
- audit-ledger tampering
- over-disclosure in safety-concern reports or exports
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on passenger/driver data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real passenger, driver and vehicle-incident data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
