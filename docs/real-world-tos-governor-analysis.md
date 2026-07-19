# Real-world Conditions of Carriage vs. the Intercity Coach Governor

**As-of**: 2026-07-19
**Compares**: the archived `:tos/full-text` in
[`cloud-itonami-lei-529900fho4dyrmcnsv67`](https://github.com/cloud-itonami/cloud-itonami-lei-529900fho4dyrmcnsv67)
(Flix SE's real, currently-published General Terms and Conditions of
Carriage, retrieved 2026-07-19) against this repo's own
`src/intercitycoachops/governor.cljc`.

## Methodology and scope (read this before the findings)

This is a comparison against **one** real document from **one** real
operator, not a fleet-wide survey. Flix SE's document is the single
correct match in the `cloud-itonami-lei` catalog for this repo's
vertical — it is an actual **Conditions of Carriage** (the passenger-
transport contract itself), not a generic website terms-of-use page.
The catalog's other 7 entries (Transdev, ComfortDelGro, FirstGroup,
Go-Ahead, RATP Dev, Keolis, SBS Transit) are almost all generic
**website** terms of use, which is why the deeper analysis for that
class of document lives in the sibling repo
[`cloud-itonami-isic-4921`](https://github.com/cloud-itonami/cloud-itonami-isic-4921)'s
own `docs/real-world-tos-governor-analysis.md` instead of being
duplicated here. Every quoted clause below is copied verbatim from the
archived journal entry (`80-data/public/tos.journal.edn` in the LEI
repo linked above) — nothing here is paraphrased from memory or
invented.

## Finding 1: real carriage law never lets liability for death/injury be excluded — and the governor never lets safety-clearance be finalized by the actor

FlixBus §18.1–18.2 (real text):

> "In the event of slight negligence, liability shall only be
> assumed — except in the case of injury to life, limb or health — if
> essential contractual obligations are violated. **Unlimited liability
> for intent and gross negligence shall continue to apply.**"
>
> "Liability for collateral damage shall be excluded in cases of
> ordinary negligence. **This shall not apply in cases of intentional
> or negligent injury to body, life and health.**"

§18.3 sets a real statutory floor: minimum compensation for death "per
national law...shall not be less than EUR 220,000 per passenger" —
explicitly *not* something the carrier can contract around, unlike the
€1,200 luggage-damage cap in §18.4.1 which *is* freely limited.

This is a genuine, structural parallel to
`intercitycoachops.governor`'s own design, not a coincidence: this
actor permanently, un-overridably excludes "directly finalizing a
dispatch-safety-clearance determination" and "determining driver
fitness-to-drive" (`scope-exclusion-violations`, HARD block, evaluated
unconditionally on every proposal — see the governor's own docstring).
Real carriage law and this actor's architecture converge on the same
underlying principle from different directions: **commercial/
logistics matters can be limited, automated, or contracted around;
matters touching life/bodily safety cannot be** — not by a carrier's
own ToS, and not by an AI actor's own proposal.

## Finding 2: real carriage terms explicitly assign safety judgment to human personnel on-site — the governor routes every safety concern to a human, never itself

FlixBus §9.2 and §9.6 (real text):

> "**Driving and dispatch personnel** shall be authorized to exclude
> persons from travel if they are obviously intoxicated or under the
> influence of drugs. The same shall apply to passengers who
> compromise the safety of their fellow passengers for other reasons
> or significantly impair the well-being of the latter."
>
> "Carriers may cancel the contract of carriage without notice in the
> event that... a passenger behaves in a manner so disruptive[.] This
> shall also apply if the passenger fails to observe objectively
> justified guidelines (e.g. **safety guidelines**)."

Note precisely who the real document assigns this authority to:
**driving and dispatch personnel** — human beings physically present
or in direct operational contact, never an automated system, and
certainly never a website or a booking algorithm. This directly
matches `intercitycoachops.governor/always-escalate-ops` (`:flag-
safety-concern` ALWAYS escalates to a human dispatch coordinator,
`intercitycoachops.phase` independently agrees this op is never in any
phase's `:auto` set) and the actor's own charter statement (README):
"vehicle-defect/driver-fitness/route-hazard concern flagging" is the
actor's job, while "directly finalizing a dispatch-safety-clearance
determination" is permanently out of scope. The real operator's own
contract text and this actor's governed architecture assign the exact
same category of decision to the exact same category of decision-
maker — a human with direct operational authority, not the logistics-
coordination layer.

## Finding 3: real jurisdiction diversity validates the generic, forkable blueprint design (not a hard-coded compliance framework)

FlixBus's document (Part II) carries **15 country-specific
amendments** (Denmark, France, Italy, Poland, Czech Republic, Sweden,
Estonia, Latvia, Lithuania, Ukraine, United Kingdom, Spain, Greece,
Albania/Bosnia/Macedonia/Montenegro/Serbia, Finland) layered on top of
a German-law base (§20: "place of jurisdiction... is Munich"). A
single real intercity-coach operator's own carriage contract already
has to vary clause-by-clause across 15+ national carriage-liability
regimes to operate its actual network. This is concrete evidence for
why `cloud-itonami-isic-4922` is deliberately a **generic** blueprint
(no hard-coded national compliance logic) rather than a single
one-size-fits-all rule set: any real intercity-coach operator forking
this actor would need to layer its own jurisdiction-specific carriage
rules on top, the same way Flix SE itself does, rather than inherit a
single baked-in national framework from the blueprint.

## What this analysis does NOT claim

- Not a legal opinion, and not a compliance certification for any
  fork of this actor. It is a documented, evidence-cited parallel
  between one real company's published contract and this actor's own
  architecture, nothing more.
- Only one real company's document was analyzed for this repo
  (Flix SE) — this is not a survey across the intercity-coach segment.
  The broader passenger-road-transport survey (7 more companies) lives
  in the sibling `cloud-itonami-isic-4921` repo, whose primary vertical
  (urban/suburban transit) most of those archived documents actually
  belong to.
- FlixBus's operational safety-exclusion practice (§9.2/§9.6) does not
  prove this actor's governor design was *modeled on* FlixBus's terms
  — the direction of the finding is the reverse: an independently
  designed governor architecture happens to land on the same
  human-authority-for-safety principle that a real operator's own
  contract also encodes, which is evidence the design choice is sound,
  not evidence of copying.

## Related

- `cloud-itonami-lei-529900fho4dyrmcnsv67` (source document)
- `cloud-itonami-isic-4921/docs/real-world-tos-governor-analysis.md` (the 7-company urban-transit analysis)
- `src/intercitycoachops/governor.cljc` (the compared implementation)
