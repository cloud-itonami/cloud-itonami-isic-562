# Governance: cloud-itonami-isic-562 Governor

The actor enforces three HARD, permanent, un-overridable checks via `cateringops.governor`.

## Hard Check 1: Event/Delivery Record Unverified

- **Scope**: Event/delivery-specific operations
- **Rule**: Target must exist AND be `:registered?` AND `:verified?`
- **Re-validation**: Derived every proposal from store's own fields
- **Bypass**: None

## Hard Check 2: Effect Not `:propose`

- **Scope**: All operations
- **Rule**: Effect must be `:propose`
- **Other values**: Rejected outright
- **Bypass**: None

## Hard Check 3: Scope Exclusion

- **Blocked content**:
  - Food-safety/health-inspection determinations
  - Recipe/menu-content decisions
  - Food-handling-technique decisions
  - Safety-authority overrides
- **Pattern**: EN+JA substring/regex matching (e.g., "food-safety", "health-code", "recipe", "調理法")
- **Allowed operations** (closed allowlist):
  1. `:schedule-catering-event` — Event booking/scheduling logistics
  2. `:coordinate-delivery-status-update` — Administrative delivery/logistics tracking
  3. `:coordinate-supply-request` — Non-food consumables (equipment, linens, cleaning)
  4. `:schedule-staff-shift-proposal` — Administrative shift proposal (never binding)
  5. `:flag-safety-concern` — Facility/sanitation escalation (always escalates)
- **Legitimate escalation**: `:flag-safety-concern` can mention safety without self-blocking
- **Bypass**: None

## Operation Phases

- **Phase 0**: Read-only (all proposals held)
- **Phase 1**: Event scheduling + delivery status auto-commit
- **Phase 2**: + supply coordination + staff shift auto-commit
- **Phase 3**: All non-safety auto-commit, safety always escalates

## Enforcement

The Governor is applied before any proposal reaches the langgraph-clj StateGraph. All three checks must pass; any failure immediately rejects the proposal.

**No override mechanism exists.** This is intentional and by design.
