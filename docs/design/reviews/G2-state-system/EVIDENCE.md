# G2 State System Evidence

Date: 2026-08-29
Status: PASS WITH DOCUMENTED LIMITATIONS
Product commit: `cd0d342ed92b76bb2ec00f067287b62779a46d62`
OpenSpec tasks: `4.12`, `4.13`

## Verification

- Clean `main` and exact product commit confirmed before capture.
- IntelliJ `build_project`: PASS, zero problems, `0.956 s`.
- `gradlew.bat smokeTest`: PASS, exit `0`, command `5.049 s`.
- Happy flow: Completed in `2.396241700 s` reported wall time.
- Recoverable flow: Error then Retry to Downloading in `206.331800 ms` reported wall time.
- Product and controller UI errors: none.
- Compose status after interactions: connected, reload `ok`, zero failed reloads.
- Recent live-app logs: expected interaction/capture entries only.
- Smoke logs: `PASS_WITH_KNOWN_JEWEL_RESOURCE_ERROR` because Jewel logs missing `expui/general/chevronDown.svg`; result
  is not described as clean.

## Windows and semantics

- Product: `6bace43d-807e-40cb-b371-e813a2b46352` (`Downlet`).
- Controller: `08362fd9-24e8-4e90-b5f6-09d27b3a4add` (`Design Review Controller`).
- `docs/design/evidence/G2/g2-controller-semantics.json`: all state, fixture, and theme controls exposed.
- `docs/design/evidence/G2/g2-empty-semantics.json`: single Empty product attempt passed; focused named field and helper
  status exposed.
- Later-state product semantics were not attempted. G3 task `5.8` owns full later-state semantic evidence.

## Product evidence matrix

| State / fixture | Light                                  | Dark                                  |    Size |
|-----------------|----------------------------------------|---------------------------------------|--------:|
| Empty           | `g2-empty-light-720x420.png`           | `g2-empty-dark-720x420.png`           | 720x420 |
| Resolving       | `g2-resolving-light-720x420.png`       | `g2-resolving-dark-720x420.png`       | 720x420 |
| Ready           | `g2-ready-light-720x420.png`           | `g2-ready-dark-720x420.png`           | 720x420 |
| Downloading 43% | `g2-downloading-light-720x420.png`     | `g2-downloading-dark-720x420.png`     | 720x420 |
| Completed       | `g2-completed-light-720x420.png`       | `g2-completed-dark-720x420.png`       | 720x420 |
| Error           | `g2-error-light-720x420.png`           | `g2-error-dark-720x420.png`           | 720x420 |
| Ready           | `g2-ready-light-620x350.png`           | Not required                          | 620x350 |
| Missing preview | `g2-missing-preview-light-620x350.png` | `g2-missing-preview-dark-620x350.png` | 620x350 |

All 15 images are product-only Compose captures and were opened at original detail. No controller screenshot or
generated mockup counts as evidence.

## Observed acceptance points

- Empty and Resolving have no work plane or premature media/options/actions.
- Ready introduces one restrained work plane.
- Ready, Downloading, Completed, and Error retain stable URL, window, work-plane, media, and destination geometry while
  inner status/actions change.
- Downloading has determinate `43%` progress, speed/time copy, Cancel, and visibly locked choices.
- Completed makes Open Folder primary and Download Another secondary; success is textual.
- Error uses the Jewel banner's native Retry link action and explicit recovery copy.
- Missing preview reads exactly `Preview unavailable`, uses the frame-and-signal mark, and does not clip at 620x350.

Full observations, audit scoring, log classification, and file hashes are in
`docs/design/evidence/G2/product-evidence.md` and `manifest.json`.
