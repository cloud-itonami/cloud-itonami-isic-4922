# Operator Quickstart: Intercity/chartered coach dispatch coordination

## Prerequisites

1. **Clojure CLI** (`clojure` ≥ 1.11.0). [Install here](https://clojure.org/guides/install_clojure).
2. **If running inside the monorepo**: sibling paths in `deps.edn`'s `:dev` alias resolve `langgraph`/`langchain` via `:local/root`. A standalone fork should drop the `:dev` alias override and pin `io.github.kotoba-lang/langgraph` (already the root `:deps` entry) via its own Git coordinates.
3. **A text editor** for reading `src/intercitycoachops/*.cljc` as you explore.

## Run the demo

Walk through the full disposition set — clean auto-commits, an always-escalating safety-concern flag, a high-cost maintenance order requiring sign-off, and four distinct HARD-hold reasons (unregistered vehicle, registered-but-unverified vehicle, unverified maintenance provider, plus the effect/scope-exclusion checks):

```bash
clojure -M:run
```

The demo driver (`src/intercitycoachops/sim.cljc`) shows the OperationActor, the Intercity Coach Governor, and how a safety-concern flag or a high-cost maintenance order never auto-commits.

## Regenerate the live operator console

The same actor stack, driven through a trimmed scenario, renders
`docs/samples/operator-console.html` (published via GitHub Pages,
regenerated nightly by `.github/workflows/regenerate.yml`):

```bash
clojure -M:dev:render-html
```

## Run tests

```bash
clojure -M:test
```

Key test modules:
- `test/intercitycoachops/governor_test.clj` — the four HARD checks (vehicle-unverified, provider-unverified, effect-not-propose, scope-excluded) and the scope-exclusion self-trip regression test
- `test/intercitycoachops/phase_test.clj` — Phase 0→3 invariants; `:flag-safety-concern` never auto-eligible at any phase
- `test/intercitycoachops/advisor_test.clj` — advisor proposal shape and consistency
- `test/intercitycoachops/governor_contract_test.clj` — full graph integration, audit trail
- `test/intercitycoachops/store_contract_test.clj` — Store protocol and MemStore implementation

## Lint

```bash
clojure -M:lint
```

## Fork and seed your own operator data

1. Independently confirm each vehicle's registration, its assigned
   route's certification, and its operator-license record with your
   own transport authority/DMV BEFORE seeding — this actor never
   determines any of these itself.
2. Independently confirm each maintenance provider's registration/
   certification before seeding — a `:coordinate-maintenance-order`
   naming an unverified provider is a permanent HARD block.
3. Replace `intercitycoachops.store/demo-data` (or call
   `store/mem-store vehicles providers` directly) with your own
   vehicle/provider directory, keyed by your own real ids.
4. Run read-only service-record-logging dry-runs at Phase 0-1 before
   advancing to Phase 3 (auto-commit).
5. Configure the `coordinate-maintenance-order` cost-escalation
   threshold (`intercitycoachops.governor/maintenance-cost-threshold`)
   for your own fleet's procurement sign-off policy.
6. Publish a dry-run safety-concern flag and audit export before going
   live, per `docs/operator-guide.md`'s certification checklist.

## Governor location

The **Intercity Coach Governor** sits at:

```
src/intercitycoachops/governor.cljc
```

Four HARD checks (all permanent, non-overrideable): vehicle-unverified,
provider-unverified (maintenance-order only), effect-not-propose,
scope-excluded (dispatch-safety-clearance / driver-fitness-to-drive
finalization / any op outside the closed four-op allowlist). Two SOFT
escalation gates force human sign-off: `:flag-safety-concern` (always)
and a `:coordinate-maintenance-order` above the cost threshold.

## Architecture overview

| File | Role |
|---|---|
| `src/intercitycoachops/store.cljc` | Store protocol (MemStore); string-keyed vehicle/provider directories; append-only audit ledger |
| `src/intercitycoachops/advisor.cljc` | IntercityCoachAdvisor (contained intelligence node; mock or real-LLM seam) |
| `src/intercitycoachops/governor.cljc` | Intercity Coach Governor — independent compliance layer, four HARD checks |
| `src/intercitycoachops/phase.cljc` | Phase table (0→3): read-only → assisted logging → assisted scheduling → supervised-auto |
| `src/intercitycoachops/operation.cljc` | OperationActor (langgraph StateGraph) |
| `src/intercitycoachops/sim.cljc` | Demo driver |
| `src/intercitycoachops/render_html.clj` | Build-time HTML renderer for the live operator console |

## Business model & operations

See `docs/business-model.md` for the revenue model and pricing, and
`docs/operator-guide.md` for first-deployment steps and minimum
production controls.

## Certification

Operators must prove:
- Independent vehicle/route/operator-license and maintenance-provider
  verification discipline (never trusting a proposal's own claim)
- Governor-bypass resistance
- Evidence-backed safety-concern reporting, always routed to a human
- Human review for every escalation-gated action

## Next steps

1. **Read the README** (`../README.md`) for full architecture and context.
2. **Run the demo**: `clojure -M:run`
3. **Explore the Governor**: `src/intercitycoachops/governor.cljc` and its tests
4. **Fork and seed**: replace the demo vehicle/provider directory with your own, following the steps above

---

Built on [langgraph](https://github.com/kotoba-lang/langgraph) StateGraph runtime. Sibling to [cloud-itonami-isic-4911](https://github.com/cloud-itonami/cloud-itonami-isic-4911) (interurban passenger rail) and [cloud-itonami-isic-4921](https://github.com/cloud-itonami/cloud-itonami-isic-4921) (urban/suburban transit dispatch) in the passenger-land-transport fleet.

License: AGPL-3.0-or-later
