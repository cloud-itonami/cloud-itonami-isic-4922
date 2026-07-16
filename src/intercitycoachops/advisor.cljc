(ns intercitycoachops.advisor
  "IntercityCoachAdvisor -- the *contained intelligence node* for the
  ISIC-4922 'Other passenger land transport' (long-distance/intercity
  coach, chartered bus) operations-coordination actor.

  It drafts exactly four kinds of SCHEDULING/DISPATCH LOGISTICS proposal
  from a closed allowlist: trip/ridership/incident-report data logging,
  vehicle/route/timetable dispatch scheduling, fleet maintenance
  procurement coordination, and vehicle-defect/driver-fitness/route-
  hazard safety-concern flagging. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record and NEVER a direct actuation -- every
  proposal's `:effect` is always `:propose`. Every output is censored
  downstream by `intercitycoachops.governor` before anything touches the
  SSoT.

  This advisor NEVER drafts a dispatch-safety-clearance decision, a
  driver-fitness-to-drive determination, or any other action that would
  directly operate a vehicle or override a safety judgment -- those are
  permanently out of scope for this actor, not merely un-implemented.
  `intercitycoachops.governor`'s `scope-exclusion-violations`
  independently re-scans every proposal for exactly this failure mode (a
  compromised or confused advisor drifting into scope it must never
  touch) and HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :vehicle-id str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor db request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-service-record
  "Draft a trip/ridership/incident-report data log entry. Pure logging of
  observed operations (trip completion, ridership counts, minor incident
  reports) -- never a safety-clearance decision."
  [_db {:keys [vehicle-id patch]}]
  {:op         :log-service-record
   :vehicle-id vehicle-id
   :summary    (str vehicle-id " の運行/乗車人数/インシデント報告を記録: " (pr-str (keys patch)))
   :rationale  "運行実績・乗車人数・軽微なインシデント報告の観察記録のみ。安全判定の変更は含まない。"
   :cites      [vehicle-id]
   :effect     :propose
   :value      (merge {:vehicle-id vehicle-id} patch)
   :confidence 0.93})

(defn- propose-dispatch-operation
  "Draft a vehicle/route/timetable dispatch scheduling proposal (a
  roster/timetable entry, never a direct dispatch-safety-clearance
  action)."
  [_db {:keys [vehicle-id patch]}]
  {:op         :schedule-dispatch-operation
   :vehicle-id vehicle-id
   :summary    (str vehicle-id " の配車/経路/時刻表スケジュールを提案: " (pr-str (keys patch)))
   :rationale  "車両・経路・時刻表の配車スケジュール調整提案のみ。出発可否の最終判定は人間が行う。"
   :cites      [vehicle-id]
   :effect     :propose
   :value      (merge {:vehicle-id vehicle-id} patch)
   :confidence 0.88})

(defn- propose-maintenance-order
  "Draft a fleet maintenance procurement coordination request naming a
  registered maintenance provider -- never a finalized purchase order; a
  human always confirms procurement."
  [_db {:keys [vehicle-id patch]}]
  {:op         :coordinate-maintenance-order
   :vehicle-id vehicle-id
   :summary    (str vehicle-id " 向け車両整備の発注調整を提案: " (pr-str (keys patch)))
   :rationale  "車両整備・部品交換等の発注調整提案のみ。確定発注は人間が行う。"
   :cites      [vehicle-id]
   :effect     :propose
   :value      (merge {:vehicle-id vehicle-id} patch)
   :confidence 0.90})

(defn- propose-safety-concern
  "Surface an observed vehicle-defect/driver-fitness/route-hazard concern
  for HUMAN triage. This op ALWAYS escalates in `intercitycoachops.
  governor` -- never auto-committed at any phase -- regardless of how
  confident the advisor is that the concern is real. Deliberately reports
  the OBSERVATION only, never a finalization/clearance/determination
  action, so the default rationale never trips the governor's
  `scope-excluded-terms` (see that var's docstring)."
  [_db {:keys [vehicle-id patch]}]
  {:op         :flag-safety-concern
   :vehicle-id vehicle-id
   :summary    (str vehicle-id " の安全懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "車両不具合・運転者の体調/適性・経路上の危険に関する懸念の観察事実の報告。常に人間の確認・対応が必要。"
   :cites      [vehicle-id]
   :effect     :propose
   :value      (merge {:vehicle-id vehicle-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-service-record (propose-service-record _db request)
                   :schedule-dispatch-operation (propose-dispatch-operation _db request)
                   :coordinate-maintenance-order (propose-maintenance-order _db request)
                   :flag-safety-concern (propose-safety-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually cleared the vehicle to dispatch despite the defect and certified the driver as fit to drive")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t          :advisor-proposal
   :op         (:op proposal)
   :vehicle-id (:vehicle-id proposal)
   :summary    (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _db request]
      (infer nil request))))
