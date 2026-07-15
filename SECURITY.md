# Security Policy

## Threat Model

This actor handles administrative coordination for event catering logistics. The Governor enforces three HARD checks to prevent scope expansion into food-safety or food-preparation domains:

1. **Scope Boundary**: All proposals mentioning food-safety, health-inspection, recipes, or food-handling are rejected. This is NOT a security review — it is a scope boundary.
2. **Escalation**: `:flag-safety-concern` operations always escalate to human review. No auto-commit occurs.
3. **Verification**: Event/delivery targets must be independently verified before operations proceed.

## No Liability

This actor is **administrative coordination only**. Deployment requires:
- Local food-service regulatory compliance review (health codes, licensing vary by jurisdiction)
- Human-review infrastructure for `:flag-safety-concern` escalations
- Independent data validation (this actor is not a substitute for regulatory oversight)

## Responsible Disclosure

If you discover a security issue, please email security@junkawasaki.com with:
- Description of the issue
- Steps to reproduce
- Impact assessment

## No Warranty

This software is provided AS-IS. See LICENSE (AGPL-3.0).
