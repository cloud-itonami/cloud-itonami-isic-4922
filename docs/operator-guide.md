# Operator Guide

## First Deployment
1. Register operator, vehicles/routes and maintenance providers;
   independently confirm each vehicle's operator-license/route
   registration and each provider's registration before seeding
   `intercitycoachops.store`.
2. Import existing trip/ridership/incident-report, dispatch and
   maintenance-order history.
3. Run read-only service-record-logging and dispatch-scheduling dry-runs
   (Phase 0-1).
4. Configure the rollout phase and the `coordinate-maintenance-order`
   cost-escalation threshold for human sign-off paths.
5. Publish a dry-run safety-concern flag and audit export.

## Minimum Production Controls
- vehicle/route/operator-license registration/verification check before
  ANY proposal for that vehicle
- provider-registration/verification check before ANY `:coordinate-
  maintenance-order` proposal
- governor gate on every proposal before commit
- human sign-off for `:flag-safety-concern` (always) and high-cost
  `:coordinate-maintenance-order` proposals
- audit export for every commit, hold and approval
- backup manual dispatch process
- this actor never directly operates a vehicle and never finalizes a
  dispatch-safety-clearance or driver-fitness-to-drive determination --
  those decisions always route to the operator's actual safety authority

## Certification
Certified operators must prove vehicle/provider-verification discipline,
governor-bypass resistance, evidence-backed safety-concern reporting and
human review for every escalation-gated action.
