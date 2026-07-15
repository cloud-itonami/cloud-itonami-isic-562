# cloud-itonami-isic-562: Event Catering Operations Coordination Actor

Event catering and other food service activities (ISIC-562) back-office administrative coordination.

## Domain Scope

**Coordination-only actor** — handles administrative logistics and scheduling, never food-safety or food-preparation decisions:

- Event booking/scheduling logistics
- Delivery/logistics status tracking (non-food supply chain)
- Non-food supply coordination (equipment, linens, utensils)
- Staff shift proposals (administrative only, not certification)
- Safety escalation (facility/sanitation concerns → human review)

## Modules

- `cateringops.store` — SSoT with event/delivery directories, append-only ledgers
- `cateringops.advisor` — Proposal confidence scoring
- `cateringops.governor` — Three HARD, permanent, un-overridable checks
- `cateringops.operation` — langgraph-clj StateGraph orchestration
- `cateringops.phase` — Rollout phases 0–3 (auto-commit gate control)
- `cateringops.sim` — Deterministic demo runner
- Tests (`test/cateringops/`) — Full coverage via nbb

## Quick Start

```bash
# Run tests
nbb scripts/run-tests.cljs

# Run demo (5 scenarios)
nbb scripts/run-demo.cljs
```

## Governor: Three HARD Checks

1. **Event/Delivery Unverified** — target must be `:registered?` and `:verified?`
2. **Effect Not `:propose`** — rejected outright
3. **Scope Exclusion** — food-safety, health-inspection, recipe/menu-content, food-handling-technique — all blocked

## References

- ADR-2607155100 (ISIC-561 restaurant coordination, module-shape mirror)
- ADR-2607121000 (Wave 4 definition, food-service cluster)
- ADR-2607152500 (Wave 4 amendment, verified-redo authorization)
- Skill `build-actor` (actor pattern, langgraph-clj StateGraph, Governor)

## License

AGPL-3.0
