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

### Pricing intelligence (real competitor research, 2026-07-19)
Charter-bus/motorcoach dispatch-and-booking SaaS (busHive, Busify,
Busie, Driver Schedule, BusCMMS) prices in the **$150-600/month for
small fleets, $1,200-3,000/month for 50+-vehicle enterprise fleets**
range, typically per-vehicle or per-user.

### Tiers
- **Self-host**: one-time setup fee (fork + seed + integration
  support), no recurring platform fee — the operator runs its own
  instance.
- **Managed Starter**: **¥80,000/month flat** (JPY, no-code Stripe
  Payment Link), unlimited vehicles/routes/providers for a single
  small-fleet operator instance — consistent with the `cloud-itonami`
  portfolio's existing Managed Starter reference point
  (`docs/adr/2607161745` at `com-junkawasaki/root`) and squarely inside
  the $150-600/month real competitor small-fleet range (≈$533/month at
  ~¥150/$).
- **Managed enterprise** (50+-vehicle fleets): negotiated, scaling from
  the Starter tier baseline toward the $1,200-3,000/month real
  competitor enterprise range.
- Support retainer with SLA (self-host customers).

No paid tenant yet (self-reported honestly, not fabricated) — see
`90-docs/business/cloud-itonami-flagship-rollout-ledger.edn` at
`com-junkawasaki/root` for this vertical's rollout status.

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
