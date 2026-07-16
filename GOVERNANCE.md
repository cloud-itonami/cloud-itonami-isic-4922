# Governance

`cloud-itonami-isic-4922` is an OSS open-business blueprint for
intercity/chartered coach SCHEDULING/DISPATCH LOGISTICS operations
coordination (ISIC Rev.5 4922 -- other passenger land transport --
long-distance/intercity coach, chartered bus, and similar passenger
services distinct from ISIC 4921's urban and suburban passenger land
transport).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unverified/unregistered vehicle/route/operator-
  license record, or a maintenance order naming an unverified/
  unregistered provider, can never commit.
- the IntercityCoachGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, dispatch-safety-
  clearance or driver-fitness-to-drive finalization content, an op
  outside the closed allowlist) cannot be overridden by human approval.
- this actor never directly operates a vehicle and never overrides a
  safety judgment made by the actual safety-clearance or driver-fitness
  authority.
- every trip/ridership/incident-report log, dispatch-operation schedule,
  maintenance-order coordination and safety-concern flag is auditable.
- passenger, driver and vehicle-incident data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing service-record, dispatch-scheduling, maintenance-order or
  safety-concern policy checks
- mishandling passenger, driver or vehicle-incident data
- misrepresenting certification status
- failing to respond to security or safety incidents
