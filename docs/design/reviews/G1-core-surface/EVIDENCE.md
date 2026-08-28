# G1 — Evidence

Gate: G1  
Status: SUPERSEDED_BY_APPROVED_G1_REVISION
Date: 2026-08-28  
Reviewed product commit: `4444ee7949e29b8c8432f3274e186addb9267164`  
Evidence source commit: `c80f48c03b64db26221d5a9c18a3f026a65b30a7`  
Final evidence correction commit: `28a140cd7b01655266deb927076a0776a4d58107`  
Review orchestration base: `69b01ade0ecb80c9a7110871b2f127e436c1b7af`

The evidence source and later documentation commits contain no application, resource, build, or run-configuration difference from the reviewed product commit. This remains an accurate historical record, but it cannot be used as final G1 evidence after the approved visual/interaction revision.

## Relevant product files

- `.run/Downlet.run.xml`
- `.run/Design Review.run.xml`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `src/main/kotlin/downlet/Main.kt`
- `src/main/kotlin/downlet/DesignReview.kt`
- `src/main/kotlin/downlet/DownloadState.kt`
- `src/main/composeResources/drawable/thumbnail_normal.svg`
- `src/main/resources/chevron-down.svg`
- `src/test/kotlin/downlet/DownloadStateTest.kt`

## IntelliJ, build, tests, and launch

- IntelliJ inspections: zero problems in production Kotlin, focused tests, and `build.gradle.kts` at the final reviewed product commit.
- Formatting: clean.
- IntelliJ `build_project`: PASS with zero problems.
- `DownloadStateTest`: 17/17 PASS, exit `0`.
- `Downlet` run configuration: PASS, exactly one product window.
- `Design Review` run configuration: PASS, product window plus controller.
- Strict OpenSpec validation: PASS.

## Compose MCP

- Connection: `connected=true`.
- Reload state: `ok`; `lastError=null`; zero failed reloads.
- Product/controller `get_ui_error`: `hasError=false`.
- Logs: expected state/theme, resize, capture, and semantic activity only; no unexplained application failure.
- Final product window ID: `78134800-5984-45f8-af50-0707afbcf2ce`.
- Final controller window ID: `c408aa0a-c27f-423a-9b25-faeac2d8a222`.

## Screenshots

Primary client-area evidence:

- `docs/design/evidence/G1/g1-empty-light-720x420.png`
- `docs/design/evidence/G1/g1-empty-dark-720x420.png`
- `docs/design/evidence/G1/g1-ready-light-720x420.png`
- `docs/design/evidence/G1/g1-ready-dark-720x420.png`
- `docs/design/evidence/G1/g1-ready-light-620x350.png`

Native Windows frame evidence:

- `docs/design/evidence/G1/g1-native-empty-light-full-window.png`
- `docs/design/evidence/G1/g1-native-ready-dark-full-window.png`

Additional integrated edge evidence:

- `docs/design/evidence/G1/g1-integration-ready-light-720x420.png`
- `docs/design/evidence/G1/g1-integration-missing-preview-light-620x350.png`

No generated image is used as G1 evidence.

## Semantic evidence

- `docs/design/evidence/G1/g1-empty-semantics.json`
- `docs/design/evidence/G1/g1-ready-semantics.json`

The product `get_semantic_tree` call timed out once in Empty and once in Ready after 120 seconds because of the known Jewel text-editor stall. No retry was made. The JSON files preserve that exact result and record controller semantics, native frame accessibility, and visually verified product labels/actions from the same observed states. Windows UI Automation exposed the native frame but not Jewel-rendered content. This is a documented tooling limitation, not a successful product-tree serialization claim.

## Tested sizes

- Product outer bounds: 720×420 in Empty/Ready and Light/Dark.
- Product outer bounds: 620×350 in Ready and edge fixtures.
- Compose client-area rasters: 704×412 and 604×342 respectively.
- Native restored frame: 706×413.
- Native maximized frame: 2560×1392.

## Tested interactions

- Paste button with deterministic valid and invalid clipboard inputs.
- Native `Ctrl+V` starts Resolving immediately and requires no Enter.
- Manual typing resolves after the 350 ms idle debounce and requires no Enter.
- Automatic Resolving completes after 550 ms; stale and superseded work cancels.
- Video/Audio selection and matching quality choices.
- Destination Change cycles deterministic fixtures and shows acknowledgement.
- Download shows the G1 design-preview acknowledgement without leaving Ready.
- Long title, long path, missing preview, and disabled Download at 620×350.
- Native drag, maximize, restore, minimize/maximize/close visibility, and post-restore input.

## Review records

- `docs/design/evidence/G1/foundation-window-proof.md`
- `docs/design/evidence/G1/integrated-review.md`
- `docs/design/evidence/G1/product-evidence.md`
- `docs/design/evidence/G1/impeccable-review.md`

The Impeccable review returned `READY WITH DOCUMENTED P2/P3`, score 32/40. No P0/P1 finding exists; no correction pass was dispatched. Follow-ups are assigned to tasks `5.3`, `5.4`, and `5.7`.
