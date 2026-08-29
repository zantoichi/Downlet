# Live Clause Reconciliation

Date: 2026-08-29

## Changed clauses

- `PRODUCT.md`: progressive disclosure now names Compact/Expanded stages; context stability preserves one window, column, URL anchor, and user sizing precedence.
- `DESIGN.md`: fixed `720 × 420` / `620 × 350` guidance is replaced by width plus Compact/Expanded profiles and coordinated height-only motion.
- `design-primary-download-window/proposal.md`: the primary window is stage-sized, and `add-state-driven-window-sizing` is the P0 owner for bounds, motion, ownership, and work-area behavior.
- `design-primary-download-window/design.md`: fixed launch/minimum geometry, fixed-window language, minimum-height risk, and migration order now use the two-tier contract.
- `primary-download-flow/spec.md`: launch, tier minimums, and state-motion scenarios use Compact/Expanded sizing without changing the single-window flow.
- `design-primary-download-window/tasks.md`: remaining unchecked G3 geometry clauses use the new profiles and are blocked behind the P0 change.

## Preserved history

- No checked G0-G2 task text was edited.
- No historical screenshots, evidence indexes, review decisions, or accepted observations were rewritten.
- Product copy, state behavior, visual language, fake transitions, and backend exclusions remain unchanged.

## Validation

- `openspec validate add-state-driven-window-sizing --type change --strict --json --no-interactive`: PASS, 1/1 items, 0 issues.
- `openspec validate design-primary-download-window --type change --strict --json --no-interactive`: PASS, 1/1 items, 0 issues.
- IntelliJ inspections: 0 problems across all eight edited planning files.
