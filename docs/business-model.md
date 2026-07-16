# Business Model: Intercity/Chartered Coach Dispatch Logistics Coordination

## Classification
- Repository: `cloud-itonami-isic-4922`
- ISIC Rev.5: `4922` -- other passenger land transport (long-distance/
  intercity coach, chartered bus and similar passenger services;
  distinct from ISIC 4921's urban and suburban passenger land transport)
- Social impact: public safety, mobility access, transparency

## Customer
- independent intercity/chartered coach operators needing an auditable
  scheduling/dispatch-coordination platform
- multi-vehicle fleet operators needing consistent dispatch/maintenance/
  safety-concern governance across routes
- programs that cannot accept closed, unauditable dispatch platforms

## Offer
- trip/ridership/incident-report data logging
- vehicle/route/timetable dispatch scheduling coordination
- fleet maintenance procurement coordination with registered, verified
  maintenance providers
- vehicle-defect/driver-fitness/route-hazard safety-concern flagging for
  human triage
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per vehicle/fleet
- support retainer with SLA

## Trust Controls
- `:intercity-coach-governor` never lets a proposal for an unregistered/
  unverified vehicle/route/operator-license record, or a maintenance
  order naming an unregistered/unverified provider, commit or even
  escalate
- every proposal's `:effect` must be `:propose` -- a claim to directly
  actuate is a HARD, un-overridable block
- directly finalizing a dispatch-safety-clearance determination or a
  driver-fitness-to-drive determination is permanently out of scope, not
  a rollout milestone -- this actor coordinates SCHEDULING/DISPATCH
  LOGISTICS ONLY and may only flag a concern for a human
- a `:flag-safety-concern` proposal, and a high-cost `:coordinate-
  maintenance-order`, always require human sign-off
- sensitive passenger, driver and vehicle-incident data stays outside Git
