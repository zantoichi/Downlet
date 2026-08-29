## Purpose

Defines a deterministic developer-only harness that makes every product state and edge-case fixture directly reviewable from real running code.

## ADDED Requirements

### Requirement: Dedicated design-review entry point

The project SHALL provide an obvious development run configuration named `Design Review` that opens the normal product window and a separate small Design Review Controller window.

#### Scenario: Reviewer launches Design Review

- **WHEN** the reviewer runs the `Design Review` configuration
- **THEN** the product window and controller window open as separately addressable desktop windows

#### Scenario: Product runs normally

- **WHEN** the normal application entry point is launched
- **THEN** the controller window and its controls are absent

### Requirement: Every state can be forced

The controller SHALL let a reviewer force Empty, Resolving, Ready, Downloading, Completed, and Error without editing source code or relying on timing.

#### Scenario: Reviewer selects a state

- **WHEN** a state control is activated in the controller
- **THEN** the product window immediately renders that exact deterministic state

### Requirement: Edge-case fixtures are deterministic

The controller SHALL provide fixtures for normal content, a long two-line title, an extremely long title, a long destination path, a missing thumbnail, disabled actions, invalid input, and recoverable error.

#### Scenario: Reviewer selects an edge-case fixture

- **WHEN** a fixture control is activated
- **THEN** the product window renders fixed, repeatable data for that condition

### Requirement: Theme and reset are controllable

The controller SHALL explicitly switch the product between light and dark Jewel themes regardless of the normal Windows startup preference and SHALL reset all fake state to the same known Empty baseline.

#### Scenario: Reviewer switches theme

- **WHEN** Light or Dark is selected
- **THEN** the product window changes theme without changing its current content fixture or product state

#### Scenario: Reviewer resets

- **WHEN** Reset is activated
- **THEN** the product returns to Empty with the default light theme and normal fixtures

### Requirement: Normal fake flow remains traversable

The product window SHALL support the ordinary fake transitions independently of the controller, using fixed delays and progress values that produce repeatable outcomes.

#### Scenario: Reviewer follows the happy path

- **WHEN** the reviewer submits a valid URL and activates Download
- **THEN** the product deterministically traverses Resolving, Ready, Downloading, and Completed

#### Scenario: Reviewer follows the failure path

- **WHEN** the failure fixture is active and the reviewer activates Download
- **THEN** the product deterministically enters Error and Retry restarts the defined fake download path

### Requirement: Review evidence comes from the product window

Gate screenshots and semantic captures SHALL come from the running product window at the exact reviewed commit. Compose Hot Reload MCP screenshots SHALL be treated as client-area captures. When title-bar behavior is reviewed, the evidence SHALL also include a Codex Computer Use `Windows.Graphics.Capture` screenshot of the complete real Downlet window and a manual Windows interaction record at that commit. The controller SHALL NOT appear in product screenshots.

#### Scenario: Gate evidence is captured

- **WHEN** G1, G2, or G3 evidence is prepared
- **THEN** the recorded commit, IntelliJ build result, tests, run configuration, Compose MCP state, client-area screenshots, semantic trees, resize cases, interactions, UI-error result, log result, and any required native full-window proof describe the same running code

### Requirement: Fast local product smoke is available

The project SHALL expose one local `smokeTest` command that renders the real product composition in-process, drives the normal Empty-to-Resolving-to-Ready path through semantics, checks deterministic state transitions with virtual time, performs no network or subprocess work, and reports elapsed wall time.

#### Scenario: Developer runs the focused smoke path

- **WHEN** `gradlew.bat smokeTest` runs on a warmed reference Windows development environment
- **THEN** the product flow assertions pass, the actual elapsed time is reported, and a run above the ten-second target is clearly flagged for investigation without becoming a cross-machine correctness failure

#### Scenario: Smoke path exercises fake behavior

- **WHEN** the smoke test submits a valid link and advances virtual time
- **THEN** the same product composition reaches Resolving and Ready with no real clipboard read, network request, process launch, file write, or controller dependency

### Requirement: Human gates stop implementation progress

The workflow MUST stop for explicit user approval at G0, G1, G2, and G3.

#### Scenario: G0 is awaiting review

- **WHEN** G0 artifacts are complete but the user has not said `APPROVE G0`
- **THEN** no G1 application code is implemented

#### Scenario: A coded gate is awaiting review

- **WHEN** G1, G2, or G3 evidence is complete but its explicit approval has not been given
- **THEN** work for the next gate does not begin
