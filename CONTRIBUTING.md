# Contributing

## Development

This project uses ClojureScript (`.cljc`) and nbb for testing and demo execution.

### Prerequisites

- nbb (Node-based ClojureScript)
- ClojureScript dependencies via `deps.edn`

### Development Workflow

1. Edit files in `src/cateringops/`
2. Run tests: `nbb scripts/run-tests.cljs`
3. Run demo: `nbb scripts/run-demo.cljs`
4. Commit and push to a feature branch
5. Submit a pull request

### Code Style

- Use `.cljc` for portable (ClojureScript-native) code
- Keep Governor checks immutable and deterministic
- Document all operations in the allowlist
- Write tests before implementing features

## Testing

All changes must pass:
- Unit tests in `test/cateringops/test.cljc`
- Demo scenarios in `scripts/run-demo.cljs`

## Governance

This actor operates under three HARD, un-overridable Governor checks. See GOVERNANCE.md.
