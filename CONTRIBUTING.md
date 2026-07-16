# Contributing

`cloud-itonami-isic-4922` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
clojure -M:test
clojure -M:lint
```

## Rules
- Do not commit real passenger, driver, vehicle-incident or
  safety-concern data.
- Keep trip/ridership/incident-report logging, dispatch-operation
  scheduling, maintenance-order coordination and safety-concern flagging
  behind the IntercityCoachGovernor.
- Treat intercity/chartered-coach-dispatch workflows as high-risk: add
  tests for vehicle/provider verification, effect discipline, scope
  exclusion, escalation and audit logging.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "safety", "fitness") -- phrase it as the finalization/execution ACTION
  (e.g. "cleared the vehicle to dispatch despite the defect", "certified
  the driver as fit to drive"), and add/extend the
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-safety-concern` happy path -- see
  `intercitycoachops.governor/scope-excluded-terms`'s docstring.
- Never add an op that directly finalizes a dispatch-safety-clearance or
  driver-fitness-to-drive determination to the closed op-allowlist. This
  actor coordinates SCHEDULING/DISPATCH LOGISTICS ONLY -- it never
  directly operates a vehicle or overrides a safety judgment.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
