(ns intercitycoachops.governor
  "IntercityCoachGovernor -- the independent compliance layer that earns
  the IntercityCoachAdvisor the right to commit. The advisor has no
  notion of whether a vehicle/route/operator-license record is actually
  registered and verified, whether a named maintenance provider is
  itself a registered/verified counterparty, whether its own proposed
  `:effect` secretly claims a direct actuation instead of a mere
  proposal, or whether it has silently drifted into a permanently
  out-of-scope decision area, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- SCHEDULING/DISPATCH
  LOGISTICS COORDINATION ONLY (trip/ridership/incident-report data
  logging, vehicle/route/timetable dispatch scheduling, fleet
  maintenance procurement coordination, vehicle-defect/driver-fitness/
  route-hazard safety-concern flagging). It NEVER performs or authorizes:
    - directly operating a vehicle
    - directly finalizing a dispatch-safety-clearance determination
      (clearing a vehicle to depart despite a known defect or open
      safety hold)
    - directly finalizing a driver-fitness-to-drive determination
      (certifying a driver medically/legally fit to drive, overriding a
      fatigue/fitness concern)
    - overriding any safety judgment made by the actual safety-clearance
      or driver-fitness authority

  Four HARD checks, ALL permanent, un-overridable by any human approval:

    1. Vehicle unverified          -- the target vehicle/route/operator-
                                       license record must exist AND be
                                       independently confirmed
                                       `:registered?`/`:verified?` in the
                                       store before ANY proposal for it
                                       may commit or even escalate. Never
                                       trusts a proposal's own claim
                                       about the vehicle -- re-derived
                                       from the vehicle's own record, the
                                       same 'ground truth, not
                                       self-report' discipline every
                                       sibling actor's governor uses.
    2. Provider unverified         -- for `:coordinate-maintenance-order`
                                       ONLY, the proposal's own drafted
                                       `:value` must name a `:provider-id`
                                       that resolves to an independently
                                       `:registered?`/`:verified?`
                                       maintenance-provider record. A
                                       missing provider-id, or one that
                                       resolves to an unregistered or
                                       unverified provider, is a HARD
                                       block.
    3. Effect not :propose         -- every proposal's `:effect` MUST be
                                       `:propose`. Any other effect value
                                       is, by construction, a claim to
                                       directly actuate/commit outside
                                       governance -- HARD block, not
                                       merely low-confidence.
    4. Scope exclusion             -- ANY proposal (regardless of op)
                                       whose op, summary, rationale,
                                       cites or draft value touches
                                       directly finalizing a dispatch-
                                       safety-clearance determination or
                                       a driver-fitness-to-drive
                                       determination is a HARD,
                                       PERMANENT block -- this actor's
                                       charter excludes that territory
                                       structurally, not as a rollout
                                       milestone. Evaluated
                                       UNCONDITIONALLY on every proposal.
                                       An op outside the closed four-op
                                       allowlist is the SAME failure mode
                                       (an advisor proposing something it
                                       was never authorized to propose)
                                       and is folded into this same
                                       check. `:flag-safety-concern`
                                       itself is never excluded by this
                                       check -- surfacing a
                                       vehicle-defect/driver-fitness/
                                       route-hazard concern for a human is
                                       exactly this actor's job; only
                                       FINALIZING/clearing/determining
                                       that concern is excluded (see
                                       `scope-excluded-terms` below --
                                       phrased as the finalization/
                                       execution ACTION, never a bare
                                       noun like 'safety' or 'fitness',
                                       so the default mock advisor's own
                                       `:flag-safety-concern` rationale
                                       never self-trips this check).

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-safety-concern` -- ALWAYS escalates to a human,
      regardless of confidence, regardless of how clean the proposal
      otherwise is. `intercitycoachops.phase` independently agrees:
      `:flag-safety-concern` is never a member of any phase's `:auto`
      set either -- two layers, not one.
    - A `:coordinate-maintenance-order` whose drafted `:value` names an
      `:estimated-cost` above `maintenance-cost-threshold` -- a
      large-value fleet-maintenance procurement proposal always needs a
      human sign-off, even when the governor and phase would otherwise
      allow auto-commit."
  (:require [clojure.string :as str]
            [intercitycoachops.store :as store]))

(def confidence-floor 0.6)

(def maintenance-cost-threshold
  "Example single-vehicle fleet-maintenance procurement threshold
  (USD-equivalent units, domain-illustrative -- not a universal
  cross-domain constant). A `:coordinate-maintenance-order` proposal
  citing an `:estimated-cost` above this value ALWAYS escalates to human
  sign-off, regardless of confidence or rollout phase."
  1500.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`). This
  actor coordinates SCHEDULING/DISPATCH LOGISTICS ONLY -- it never
  directly operates a vehicle or overrides a safety judgment, so no op
  in this allowlist may ever finalize a dispatch-safety-clearance or
  driver-fitness-to-drive determination."
  #{:log-service-record :schedule-dispatch-operation
    :coordinate-maintenance-order :flag-safety-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not. A 'flag a
  concern' op must always escalate and never auto-commit."
  #{:flag-safety-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- directly finalizing a
  dispatch-safety-clearance determination or a driver-fitness-to-drive
  determination, or otherwise directly operating a vehicle / overriding
  a safety judgment rather than merely flagging a concern for a human.
  Scanned across the proposal's op/summary/rationale/cites/value, never
  trusting the advisor's own framing of its intent.

  CRITICAL: every term here is phrased as the finalization/execution
  ACTION (e.g. 'cleared the vehicle to dispatch despite the defect',
  'certified the driver as fit to drive'), never a bare noun like
  'safety', 'fitness', 'dispatch' or 'clearance' -- a bare noun would
  accidentally match inside this actor's own legitimate
  `:flag-safety-concern` default proposal text (whose whole job is to
  talk about vehicle-defect/driver-fitness/route-hazard SAFETY concerns,
  and whose own printed `:op` keyword literally contains the substring
  'safety') and self-block the happy path. See
  `intercitycoachops.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the regression test."
  ["finalize the dispatch safety clearance" "finalized the dispatch safety clearance"
   "finalizes the dispatch safety clearance" "finalizing the dispatch safety clearance"
   "finalize the driver fitness-to-drive determination"
   "finalized the driver fitness-to-drive determination"
   "finalizes the driver fitness-to-drive determination"
   "finalizing the driver fitness-to-drive determination"
   "certify the driver as fit to drive" "certified the driver as fit to drive"
   "certifying the driver as fit to drive" "certifies the driver as fit to drive"
   "clear the vehicle to dispatch despite the defect"
   "cleared the vehicle to dispatch despite the defect"
   "clearing the vehicle to dispatch despite the defect"
   "authorize the vehicle to depart despite the defect"
   "authorized the vehicle to depart despite the defect"
   "authorizing the vehicle to depart despite the defect"
   "override the safety hold" "overrode the safety hold" "overriding the safety hold"
   "override the dispatch safety clearance" "overrode the dispatch safety clearance"
   "waive the pre-trip inspection" "waived the pre-trip inspection"
   "waiving the pre-trip inspection"
   "approve the driver to drive despite the concern"
   "approved the driver to drive despite the concern"
   "approving the driver to drive despite the concern"
   "dispatch the vehicle despite the open safety hold"
   "dispatched the vehicle despite the open safety hold"
   "運行安全確認を確定" "運行安全確認を確定した" "運行安全確認を確定します"
   "運転可否判定を確定" "運転可否判定を確定した" "運転可否判定を確定します"
   "整備不良のまま出発を承認" "整備不良のまま出発を承認した"
   "点呼を省略" "点呼を省略した" "点呼を省略します"
   "乗務可否を確定" "乗務可否を確定した" "安全確認を省略して出発を許可"])

;; ----------------------------- checks -----------------------------

(defn- vehicle-unverified-violations
  "The target vehicle/route/operator-license record must exist AND be
  independently `:registered?`/`:verified?` in the store -- never trust
  the proposal's own `:vehicle-id` claim without a store lookup."
  [{:keys [vehicle-id]} st]
  (let [v (store/vehicle-record st vehicle-id)]
    (when-not (and v (:registered? v) (:verified? v))
      [{:rule :vehicle-unverified
        :detail (str vehicle-id " は未登録または未検証の車両/経路/事業許可 -- いかなる提案も進められない")}])))

(defn- provider-unverified-violations
  "For `:coordinate-maintenance-order` ONLY, the proposal's own drafted
  `:value` must name a `:provider-id` that resolves to an independently
  `:registered?`/`:verified?` maintenance-provider record. A missing
  provider-id, or one that resolves to an unregistered/unverified
  provider, is a HARD block -- never trust the proposal's own provider
  claim without a store lookup, the SAME 'ground truth, not self-report'
  discipline as `vehicle-unverified-violations`, reapplied to the
  maintenance-supply-chain counterparty."
  [proposal st]
  (when (= :coordinate-maintenance-order (:op proposal))
    (let [provider-id (get-in proposal [:value :provider-id])
          p (and provider-id (store/provider-record st provider-id))]
      (when-not (and p (:registered? p) (:verified? p))
        [{:rule :provider-unverified
          :detail (str (or provider-id "(provider-id missing)")
                        " は未登録または未検証の整備事業者 -- 整備発注調整提案を進められない")}]))))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches directly finalizing a dispatch-safety-
  clearance determination or a driver-fitness-to-drive determination,
  regardless of confidence or how clean every other check is. Evaluated
  UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "出発可否確定・運転可否判定・安全確認省略など運行安全確定行為(dispatch-safety-clearance / driver-fitness-to-drive finalization)に触れる提案は永久に禁止"}])))

(defn- high-cost-maintenance-order?
  "A `:coordinate-maintenance-order` proposal citing an `:estimated-cost`
  above `maintenance-cost-threshold` -- always needs human sign-off (SOFT
  escalate, not a hard block: the order itself is in scope, only its
  size requires a human)."
  [proposal]
  (and (= :coordinate-maintenance-order (:op proposal))
       (some-> proposal :value :estimated-cost (> maintenance-cost-threshold))))

(defn check
  "Censors an IntercityCoachAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [vehicle-id (or (:vehicle-id proposal) (:vehicle-id request))
        hard (into []
                   (concat (vehicle-unverified-violations {:vehicle-id vehicle-id} store)
                           (provider-unverified-violations proposal store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-maintenance-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :vehicle-id (:vehicle-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
