# Downlet project rules

## Workflow

- Work only in the current Codex task and the repository's Local checkout.
- Never spawn subagents, create or fork separate tasks, hand work off, or use worktrees.
- If the working directory is under `.codex/worktrees`, stop before editing and request a handoff to Local.
- Do not use OpenSpec change workflows. `openspec/specs/downlet/spec.md` is the durable behavior contract; update it only when observable behavior changes.
- Use one cycle: inspect the affected flow, implement it end to end, self-review the diff, run required checks, and report the result.
- Do not create G0-G3 gates, dispatch packets, approval phrases, evidence packages, screenshot commits, or independent review tasks.
- Do not commit unless the user explicitly requests it.
- Keep changes minimal. Reuse existing Kotlin, Compose, and Jewel patterns before adding dependencies or abstractions.

## Verification

- Documentation or configuration only: run the relevant validator and `git diff --check`.
- Kotlin or build changes: run `gradlew.bat --no-daemon --console=plain check`.
- UI, state, or window changes: also run `gradlew.bat --no-daemon --console=plain smokeTest` and one focused launch covering affected states.
- The Design Review app and Compose Hot Reload are optional preview tools, never delivery gates.
- Do not rerun a green check unless code, inputs, or concrete risk changed.
