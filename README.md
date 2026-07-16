# cloud-itonami-isic-4922

Open Business Blueprint for **ISIC Rev.5 4922**: other passenger land
transport -- long-distance/intercity coach, chartered bus, and similar
passenger services (distinct from sibling ISIC 4921's urban and
suburban passenger land transport).

This repository publishes an intercity/chartered-coach
SCHEDULING/DISPATCH LOGISTICS COORDINATION actor -- trip/ridership/
incident-report data logging, vehicle/route/timetable dispatch
scheduling, fleet maintenance procurement coordination with registered
providers, and vehicle-defect/driver-fitness/route-hazard safety-concern
flagging -- as an OSS business that any qualified operator can fork,
deploy, run, improve and sell, so an independent intercity/chartered
coach operator never surrenders its dispatch/operations data to a
closed back-office SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **IntercityCoachAdvisor
⊣ IntercityCoachGovernor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:intercity-coach-governor`, is a
distinct, independent build (no naming-collision precedent question --
distinct from sibling ISIC 4921's own governor, which is that actor's
own separate build for urban/suburban transit).

> **Why an actor layer at all?** An LLM is great at drafting a
> trip/ridership summary, a dispatch-scheduling proposal, or a
> maintenance-order request -- but it has no license to actually finalize
> a dispatch-safety-clearance determination or a driver-fitness-to-drive
> determination, no way to independently confirm a vehicle/route/
> operator-license or a maintenance provider is actually a registered/
> verified counterparty, and no notion of when a "flag this concern" op
> quietly turns into a claim to have already acted on it. Letting it act
> directly invites an unverified vehicle's data entering the ledger, an
> unverified provider receiving a maintenance order, or -- worst of all --
> a fabricated claim to have cleared a vehicle to dispatch despite a
> known defect or certified a driver fit to drive despite a fatigue
> concern, exposing passengers, drivers and the operator to real harm and
> liability. This project seals the IntercityCoachAdvisor into a single
> node and wraps it with an independent **IntercityCoachGovernor**, a
> human **approval workflow**, and an immutable **audit ledger**.

## Scope: coordination only, not vehicle operation or safety authority

This actor is **SCHEDULING/DISPATCH LOGISTICS COORDINATION ONLY**. It
never directly operates a vehicle and never overrides a safety judgment.
It never performs or authorizes:

- directly operating a vehicle
- directly finalizing a dispatch-safety-clearance determination
  (clearing a vehicle to depart despite a known defect or open safety
  hold)
- directly finalizing a driver-fitness-to-drive determination
  (certifying a driver medically/legally fit to drive, overriding a
  fatigue/fitness concern)

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. Flagging a
vehicle-defect/driver-fitness/route-hazard safety concern for a human to
triage is exactly this actor's job -- `:flag-safety-concern` is never
excluded by this check, only FINALIZING/clearing/determining that
concern is.

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`intercitycoachops.governor`'s `effect-not-propose-violations` HARD
check and `intercitycoachops.phase`'s phase table, which never puts
`:flag-safety-concern` in any phase's `:auto` set). A human dispatch/
fleet coordinator is always the one who actually acts on a flagged
concern or confirms a high-cost maintenance order.

## The core contract

```
vehicle/route/operator-license + provider registration + dispatch-coordination request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ IntercityCoach-       │ ─────────────▶ │ IntercityCoachGovernor       │  (independent system)
   │ Advisor (sealed)      │  + citations    │ vehicle-unverified ·        │
   └───────────────────────┘                 │ provider-unverified ·       │
          │                 commit ◀┼ effect-not-propose ·               │
          │                         │ scope-excluded (dispatch-safety-    │
    record + ledger        escalate ┼ clearance / driver-fitness-to-      │
          │              (ALWAYS for│ drive determination) ·              │
          │       :flag-safety-     │ op-not-allowed                      │
          │       concern/high-cost └────────────────────────────┘
          │       maintenance-order)
          ▼
      human approval / safety authority
```

**The IntercityCoachAdvisor never commits a proposal the
IntercityCoachGovernor would reject, and a safety-concern flag or a
high-cost maintenance order never commits without a human sign-off.**
Hard violations (an unregistered/unverified vehicle; an unregistered/
unverified maintenance provider; a non-`:propose` effect; content
touching dispatch-safety-clearance or driver-fitness-to-drive
finalization; an op outside the closed allowlist) force **hold** and
*cannot* be approved past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: automated pre-trip inspection
sensors, telematics-driven condition monitoring) under human/robot
operations gated by operator policy. This actor itself does not dispatch
robot/hardware actions or drive any vehicle -- it is strictly the
scheduling/dispatch-logistics-coordination layer (service-record
logging, dispatch scheduling, maintenance-order coordination,
safety-concern flagging) any physical-dispatch layer could eventually
feed proposals into, always gated the same way by the independent
IntercityCoachGovernor.

## Features

- **Closed proposal-op allowlist**: `log-service-record`,
  `schedule-dispatch-operation`, `coordinate-maintenance-order`,
  `flag-safety-concern` (all `:effect :propose`).
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Vehicle unverified** -- the target vehicle/route/operator-license
     record must exist AND be independently registered/verified in the
     store.
  2. **Provider unverified** -- for `:coordinate-maintenance-order`
     only, the named maintenance provider must exist AND be
     independently registered/verified -- a maintenance-supply-chain
     counterparty-verification gate.
  3. **Effect is :propose** -- any other `:effect` value is rejected.
  4. **Scope exclusion** -- directly finalizing a dispatch-safety-
     clearance determination or a driver-fitness-to-drive determination,
     and an op outside the closed allowlist, are both permanently
     blocked.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-safety-concern` -- ALWAYS escalates, regardless of confidence
    or phase. A "flag a concern" op is never auto-commit eligible and
    never finalizes a dispatch-safety-clearance/driver-fitness
    determination itself -- it only surfaces the concern for a human.
  - `:coordinate-maintenance-order` above a cost threshold -- a
    large-value fleet-maintenance procurement proposal always needs a
    human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: service-record logging only (approval-gated)
  - Phase 2: + dispatch-operation scheduling, maintenance-order
    proposals (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (safety concerns and high-cost maintenance orders always escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

### Test suite

- `test/intercitycoachops/governor_test.clj` -- unit tests of governor
  hard checks, scope exclusion, and the self-trip regression test
- `test/intercitycoachops/advisor_test.clj` -- advisor proposal shape and
  consistency
- `test/intercitycoachops/phase_test.clj` -- rollout phase logic
- `test/intercitycoachops/governor_contract_test.clj` -- full graph
  integration, audit trail
- `test/intercitycoachops/store_contract_test.clj` -- Store protocol and
  MemStore implementation

### Modules

- `intercitycoachops.store` -- SSoT (MemStore, String-keyed vehicle/
  provider directories, append-only ledger)
- `intercitycoachops.advisor` -- contained intelligence node (mock +
  real-LLM seam)
- `intercitycoachops.governor` -- independent compliance layer
- `intercitycoachops.phase` -- staged rollout (0→3)
- `intercitycoachops.operation` -- langgraph-clj StateGraph
- `intercitycoachops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4922`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Trip/ridership/incident-report data logging (`:log-service-record`) | Real telematics/onboard-system integration |
| Vehicle/route/timetable dispatch scheduling coordination (`:schedule-dispatch-operation`) | Directly operating any vehicle |
| Fleet maintenance procurement coordination with a registered, verified provider, HARD-gated on provider verification and a double-actuation-free single-proposal shape (`:coordinate-maintenance-order`) | Real maintenance-ordering-system integration |
| Vehicle-defect/driver-fitness/route-hazard safety-concern flagging, ALWAYS human-gated (`:flag-safety-concern`) | Directly finalizing any dispatch-safety-clearance or driver-fitness-to-drive determination -- permanently out of scope, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Fare/ticketing settlement -- a follow-up slice, not in this R0 |

Extending coverage is additive: add the next op (e.g. a route-diversion
proposal or a fare-reconciliation-escalation check) as its own governed
op with its own HARD checks and tests, following the SAME "an
independent governor re-verifies against the actor's own records before
any real-world act" pattern this repo's flagship checks already
establish.

## Maturity

`:implemented` -- `IntercityCoachAdvisor` + `IntercityCoachGovernor` run
as real, tested code (see `Development` above), following the SAME
governed-actor architecture as every prior actor across this fleet, with
its own distinct, independently-named governor and its own maintenance-
supply-chain provider-verification check.

## License

Code and implementation templates are AGPL-3.0-or-later.
