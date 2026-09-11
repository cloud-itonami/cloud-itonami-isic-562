# cloud-itonami-isic-562: Event Catering Operations Coordination Actor

Event catering and other food service activities (ISIC-562) back-office administrative coordination.

**Maturity: `:implemented`.** `src/cateringops/` implements the
`CateringOpsAdvisor` (`cateringops.advisor`, a real `Advisor` protocol
+ `MockAdvisor`) and the independent Governor (`cateringops.governor`),
composed by `cateringops.operation` following the itonami actor
pattern: `intake -> advise -> govern -> decide -> commit |
request-approval -> commit | hold`, compiled to a real `langgraph-clj`
`StateGraph` (`langgraph.graph/state-graph` + `compile-graph`,
mirroring `cerealops.operation`, cloud-itonami-isic-0111) with
`interrupt-before #{:request-approval}` and checkpoint-based
human-in-the-loop resume for escalated operations. Every commit/hold/
approval-rejected decision fact is appended to `cateringops.store`'s
append-only audit ledger (`ledger`/`append-ledger!`), implemented on
both `MemStore` and a `DatomicStore` (backed by `langchain.db` via
`kotoba-lang/langchain-store`) that pass the same store-contract test
(`test/cateringops/store_contract_test.cljk`). 16 tests / 90 assertions
green (`clojure -M:dev:test`); the demo runner (`clojure -M:dev:run`)
drives the compiled graph end-to-end through a commit path, an
escalate→approve→commit path, an auto-commit path, an
escalate→reject→hold path, and a hard-hold path, printing the
resulting audit ledger.

## Domain Scope

**Coordination-only actor** — handles administrative logistics and scheduling, never food-safety or food-preparation decisions:

- Event booking/scheduling logistics
- Delivery/logistics status tracking (non-food supply chain)
- Non-food supply coordination (equipment, linens, utensils)
- Staff shift proposals (administrative only, not certification)
- Safety escalation (facility/sanitation concerns → human review)

## Modules

- `cateringops.store` — `Store` protocol: event/delivery/supply/shift directories +
  append-only audit ledger, implemented by `MemStore` (in-memory, default) and
  `DatomicStore` (`langchain.db`-backed, via `kotoba-lang/langchain-store`)
- `cateringops.advisor` — `Advisor` protocol + `MockAdvisor` (the sealed
  decision node; a real-LLM `Advisor` implementation is the documented next
  seam, same as every sibling cloud-itonami actor's advisor). The
  confidence-scoring functions are unchanged from this actor's pre-StateGraph
  implementation — only now genuinely invoked by the `:advise` node
- `cateringops.governor` — Three HARD, permanent, un-overridable checks,
  re-derived from the Store's protocol (never a backend's private atoms)
- `cateringops.phase` — Rollout phases 0–3 (auto-commit gate control);
  `phase-auto-commit?`'s matrix is preserved verbatim
- `cateringops.operation` — compiles the `langgraph-clj` `StateGraph`:
  advise → govern → decide → commit | request-approval → commit | hold, with
  `interrupt-before` + checkpoint-based resume for escalated operations
- `cateringops.sim` — demo runner (`clojure -M:dev:run`)
- Tests (`test/cateringops/`) — real `clojure.test` `deftest`/`is` coverage
  (store, governor, advisor, phase, store-contract Mem≡Datomic parity, and
  end-to-end compiled-StateGraph paths)

## Quick Start

```bash
# Run tests (langgraph/langchain-store resolved via local sibling checkouts)
clojure -M:dev:test

# Run the linter (clj-kondo, 0 errors / 0 warnings)
clojure -M:lint

# Run the demo -- drives the compiled StateGraph end-to-end
clojure -M:dev:run
```

`:dev` pins the transitive `langchain` dependency to the in-monorepo local
checkout (`../../kotoba-lang/langchain`) for offline workspace development;
a standalone fork should override `deps.edn`'s `:local/root` coordinates
with git coordinates (see `deps.edn`'s own comment).

## Governor: Three HARD Checks

1. **Target Not Verified** — `:schedule-catering-event` / `:coordinate-delivery-status-update` targets must be `:registered?` AND `:verified?`
2. **Effect Not `:propose`** — rejected outright
3. **Scope Exclusion** — food-safety, health-inspection, recipe/menu-content, food-handling-technique — all blocked (EN+JA substring scan); `:flag-safety-concern` is exempt from its own "safety" mention

## Always-escalate operations (human sign-off, regardless of phase)

- `:flag-safety-concern` — `phase-auto-commit?` is `false` for this op at
  every phase (0–3), so it always routes through `:request-approval`

## Operational requests (closed set, all `:effect :propose`)

```text
:schedule-catering-event            — event booking/scheduling logistics
:coordinate-delivery-status-update  — administrative delivery/logistics tracking
:coordinate-supply-request          — non-food consumable coordination
:schedule-staff-shift-proposal      — administrative shift proposal
:flag-safety-concern                — facility/sanitation/safety escalation (ALWAYS escalates)
```

## References

- ADR-2607155100 (ISIC-561 restaurant coordination, module-shape mirror)
- ADR-2607121000 (Wave 4 definition, food-service cluster)
- ADR-2607152500 (Wave 4 amendment, verified-redo authorization)
- Skill `build-actor` (actor pattern, langgraph-clj StateGraph, Governor)

## License

AGPL-3.0-or-later.
