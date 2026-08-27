# G0 Jewel Codability Map

Checked: 2026-08-28  
Direction: The Quiet Transfer Desk  
Confidence result: no low-confidence visual or interaction effect remains

## Verified baseline

- Kotlin `2.3.20`
- Compose Multiplatform `1.11.0`
- Jewel standalone `0.39.1-262.9437.29`
- Compose Hot Reload `1.2.0`
- JetBrains Runtime/toolchain 25
- One future Kotlin/JVM Compose Desktop module

Jewel `0.39.1` is the current published standalone line. The official Jewel page presents the standalone dependency,
`IntUiTheme`, and `DecoratedWindow`; its 0.39.1 source JARs expose every Jewel API named below. Jewel 0.40 has newer
IntelliJ Platform sources but no published standalone coordinate in the current release table.

## Map

| Element or behavior                   | Intended API or primitive                                                                                   | Custom Compose work                                                               | Confidence          | Risk and G1 proof                                                                                                          |
|---------------------------------------|-------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|---------------------|----------------------------------------------------------------------------------------------------------------------------|
| Complete Windows frame                | Jewel `DecoratedWindow`                                                                                     | None                                                                              | High                | Requires JBR; launch on Windows and verify drag, resize, maximize/restore, minimize, and close.                            |
| Plain themed title bar                | Jewel `TitleBar` inside `DecoratedWindow`                                                                   | One centered/left `Text("Downlet")` only                                          | High                | Do not add project stripe, menu, toolbar, breadcrumbs, or custom hit regions. Capture full-frame light/dark screenshots.   |
| Decorated-window styling              | `IntUiTheme(theme, styling)` with `ComponentStyling.default().decoratedWindow()`                            | None                                                                              | High                | Minimal scaffold must prove the theme definition and decorated styling compile together.                                   |
| Window size and minimum               | Compose `WindowState` / `rememberWindowState`; underlying `ComposeWindow.minimumSize`                       | Tiny AWT `Dimension` edge assignment                                              | High                | Verify exact 720 × 420, 620 × 350, and 880 × 560 bounds.                                                                   |
| Stable one-column layout              | Compose `Column`, `BoxWithConstraints`, `Spacer`, `Row`                                                     | One compact-height metric branch; optional `verticalScroll` only on true overflow | High                | Prove no clipping at minimum size and no scrollbar at default size.                                                        |
| Persistent visible label              | Jewel `Text("YouTube link")`                                                                                | None                                                                              | High                | Fixed label width must still fit ordinary Windows scaling.                                                                 |
| Editable link field                   | Stable Jewel `TextField(state: TextFieldState, ...)`                                                        | None                                                                              | High                | Use the state-based stable overload; inspect focus and semantics.                                                          |
| Placeholder                           | Text-field placeholder slot with Jewel `Text("Paste a YouTube link…")`                                      | None                                                                              | High                | Confirm truncation and contrast in both themes.                                                                            |
| Paste button                          | Jewel `OutlinedButton`                                                                                      | None                                                                              | High                | Verify pointer, Enter, and Space activation.                                                                               |
| Clipboard access                      | `java.awt.Toolkit.getDefaultToolkit().systemClipboard` at app edge                                          | Tiny text callback with caught clipboard failures                                 | High                | No AWT type crosses into the product composable; test empty, non-text, busy, valid, and invalid clipboard cases.           |
| YouTube URL predicate                 | `java.net.URI` plus a fixed host allow-list                                                                 | One small pure function                                                           | High                | Test `youtube.com`, `www.youtube.com`, `m.youtube.com`, `youtu.be`, malformed text, and unsupported hosts.                 |
| Paste-immediate/type-debounced submit | Compose key preview, `snapshotFlow` over `TextFieldState.text`, `LaunchedEffect`, coroutine delay           | One short-lived paste-intent flag                                                 | High                | Test Ctrl+V immediate resolution, 350 ms typed debounce, and intent reset after one edit.                                  |
| Inline validation                     | Jewel `Text` below the field plus targeted Compose status semantics                                         | No custom container                                                               | High                | Invalid text remains editable; validation must not cause disruptive body reflow.                                           |
| Region separation                     | Jewel `Divider`                                                                                             | None                                                                              | High                | Jewel already hides decorative divider semantics.                                                                          |
| Empty body                            | Jewel `Text`                                                                                                | None                                                                              | High                | One restrained sentence at most; no inactive downstream controls.                                                          |
| Resolving body                        | Jewel `CircularProgressIndicator` and `Text`                                                                | Targeted live-region semantics                                                    | High                | Verify concise announcement and stable geometry during the fixed 550 ms transition.                                        |
| Thumbnail                             | Compose `Image` using a bundled deterministic resource                                                      | Fixed 16:9 `Box` geometry                                                         | High                | No network loader. Prove default/minimum sizes.                                                                            |
| Missing-thumbnail fallback            | Compose `Box` with Jewel `Icon` or `Text`                                                                   | One quiet fallback primitive with explicit semantics                              | High                | Must preserve 16:9 geometry and avoid broken-image styling.                                                                |
| Media title and metadata              | Jewel `Text`                                                                                                | Compose max-lines/overflow configuration and grouped semantics                    | High                | Title uses at most two lines; metadata stays one concise line.                                                             |
| Video/Audio choice                    | Jewel `RadioButtonRow`                                                                                      | None                                                                              | High                | Whole labeled rows remain clickable, keyboard reachable, selected, and disableable.                                        |
| Quality selector                      | Jewel `ListComboBox`                                                                                        | Fixed/fill width only                                                             | High                | Selected value names actual best quality; verify popup at minimum width.                                                   |
| Destination row                       | Jewel `Text` plus Jewel `Link("Change…")`                                                                   | Deterministic fixture cycling                                                     | High                | Keep full path in semantics and Change reachable beside truncated text.                                                    |
| Ready primary action                  | Jewel `DefaultButton("Download")`                                                                           | None                                                                              | High                | Only primary action in Ready; inspect disabled fixture.                                                                    |
| Download progress                     | Jewel `HorizontalProgressBar(progress)` plus Jewel `Text`                                                   | Deterministic values and targeted progress semantics                              | High                | Prove range/value semantics and locked choices.                                                                            |
| Cancel                                | Jewel `Link("Cancel")`                                                                                      | State event only                                                                  | High                | Returns to Ready with choices preserved.                                                                                   |
| Completed status                      | Jewel `Icon`, `Text`, `DefaultButton("Open Folder")`, `Link("Download Another")`                            | One-line simulated-folder acknowledgement                                         | High                | Outcome cannot rely on color; verify reset focus.                                                                          |
| Recoverable error                     | Non-deprecated Jewel `InlineErrorBanner` with a Retry link action                                           | State event only                                                                  | High                | Use ordinary copy, full width, preserved media context, and no raw backend details.                                        |
| Semantic hardening                    | Compose `semantics`, `liveRegion`, `progressBarRangeInfo`, descriptions/values                              | Only where Jewel native semantics are insufficient                                | High                | Capture semantic trees for Empty, Ready, Downloading, Completed, and Error.                                                |
| Fake state engine                     | Kotlin sealed state/data classes plus one Compose-aware holder and structured coroutines                    | Minimal deterministic transition logic                                            | High                | No repository, service interface, DI, or fake backend layer. Focused tests cover branches and cancellation.                |
| Design Review Controller              | A second small Jewel `DecoratedWindow` with ordinary radio/combo/button controls                            | Separate dev entry point sharing only state                                       | High                | `Design Review` run configuration must open two separately addressable windows; controller excluded from product captures. |
| Rendered evidence                     | Compose Hot Reload MCP `status`, `list_windows`, screenshots, semantics, interactions, resize, errors, logs | Project `hotMcpServer` configuration                                              | High after scaffold | G1 cannot pass until the MCP connects to the exact running commit.                                                         |

## Exact source-signature checks

The 0.39.1 source artifacts were inspected for:

- `DecoratedWindow`
- `TitleBar`
- `ComponentStyling.decoratedWindow`
- `IntUiTheme`
- state-based `TextField`
- `Text`
- `DefaultButton`
- `OutlinedButton`
- `RadioButtonRow`
- `ListComboBox`
- `Link`
- `Divider`
- `CircularProgressIndicator`
- `HorizontalProgressBar`
- `InlineErrorBanner`
- `Icon`

The older value-based text-field overload is experimental and is not selected. `ErrorInlineBanner` is deprecated and is
not selected.

## Evidence sources

- Official Jewel standalone page: https://jewel-ui.dev/
- Official Jewel release
  notes: https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/RELEASE%20NOTES.md
- Jewel 0.39.1 source artifacts from Maven Central, including `jewel-int-ui-standalone`, `jewel-ui`,
  `jewel-decorated-window`, and `jewel-int-ui-decorated-window`
- Official Compose Hot Reload repository and MCP documentation: https://github.com/JetBrains/compose-hot-reload
- IntelliJ IDEA MCP project inspection: one empty `JAVA_MODULE` named `Downlet`, no project dependencies, and no run
  configurations before G1

## Remaining risks are verification risks

No effect is deferred as “figure it out later.” Remaining uncertainty is limited to real Windows rendering and
interaction:

- decorated title-bar behavior under JBR 25;
- exact client-area fit at 620 × 350;
- 125 and 150 percent Windows scaling;
- final semantics emitted by the composed control tree;
- Compose Hot Reload MCP connection after scaffold.

Each is assigned to a coded gate with an explicit build, launch, interaction, resize, screenshot, semantic-tree, error,
and log check.
