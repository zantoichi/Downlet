# G1 Product Evidence

Status: PASS  
Date: 2026-08-28  
Task: 3.16  
Codex task ID: 01a048a3-3531-7173-884d-81a5775878a8  
Source commit: c80f48c03b64db26221d5a9c18a3f026a65b30a7  
Reviewed product commit: 4444ee7949e29b8c8432f3274e186addb9267164

## Source and build

The source commit differs from the reviewed product commit only by committed orchestration/evidence documents. No app source, resource, build, or run-configuration file differs. IntelliJ MCP ran a full rebuild before capture with isSuccess=true and no reported problems. Design Review was launched through the IntelliJ run configuration. Run log: C:\Users\SVall\AppData\Local\JetBrains\IntelliJIdea2026.2\tmp\ij_run__Design_Review_6670247620090025740.log.

## Compose MCP record

Initial product window: 5995f162-1175-422f-bbf4-9fe3188fd1a0. Initial controller window: 401bde98-22bd-4501-9a03-a61a8e291560. After a no-source-change Compose restart used to remove a screen-space Computer Use hover strip from the final native capture, product window: 78134800-5984-45f8-af50-0707afbcf2ce; controller window: c408aa0a-c27f-423a-9b25-faeac2d8a222. Final Compose status was connected=true, buildContinuous=false, reloadState=ok, lastError=null, successfulReloads=0, failedReloads=0.

Captured and visually inspected sequence:

- Empty Light at 720x420.
- Empty Dark at 720x420.
- Ready normal Light at 720x420.
- Ready normal Dark at 720x420.
- Ready normal Light at 620x350; all essential controls remained visible without clipping.

Compose list_windows reported the requested outer product bounds exactly. The client-area PNG rasters exclude the native frame: each 720x420 window produced a 704x412 PNG, and the 620x350 window produced a 604x342 PNG.

Every client-area capture was preceded by an exact resize and list_windows confirmation. Product and controller get_ui_error returned hasError=false after captures. Recent Compose logs contained state/theme clicks, resize/capture operations, and semantic-tree activity only; no runtime exception or unexplained application failure was present.

Interaction transcript:

1. Manual typing: Reset, Light, Compose text input node 20, value https://youtu.be/g1-manual-typing. Controller semantics showed Resolving immediately and Ready normal after the deterministic delay. No Enter key was sent.
2. Native paste: clipboard seeded without reading existing contents, value https://youtu.be/g1-native-paste. A fresh WGC observation focused the product field; Control_L+v was sent once. The immediate refreshed WGC frame showed Checking this YouTube link... and the controller then reported Ready normal. No Enter key was sent.

Product get_semantic_tree was attempted exactly once in Empty and exactly once in Ready. Each attempt timed out after 120 seconds with: tool call failed for compose_hot_reload/get_semantic_tree; timed out awaiting tools/call after 120s. No retry was made. The JSON evidence records the exact timeout, controller semantic state, native accessibility tree, and visible labels/actions from the same fresh Computer Use observation. Windows UI Automation exposed the native frame controls but not the Jewel-rendered content; this known tooling limitation is covered by controller semantics plus visually inspected WGC content. Bounded node discovery before manual typing produced expected unsupported/not-found SetText results for nodes 1-19; node 20 succeeded. No application error resulted.

## Native Windows record

Computer Use selected exactly one returned window titled Downlet before each state-changing native action. Initial native target: java.exe window 34868352. Clean recapture target after Compose restart: java.exe window 31918586.

Frame and interaction record:

- Empty Light WGC: 706x413, origin (15,48), native title Downlet, matching light title bar/content, minimize/maximize/close visible, no foreign pixels.
- Required drag: from window-relative (350,20) to (550,180). Bounds changed from origin (15,48), 706x413 to origin (215,208), 706x413.
- Maximize: successful fresh-screenshot click at (634,20). Bounds became origin (0,0), 2560x1392.
- Restore: successful fresh-screenshot click at (2490,20). Bounds returned to origin (215,208), 706x413, with Empty Light state unchanged.
- Ready Dark WGC: 706x413, origin (15,48), native title Downlet, matching dark title bar/content, minimize/maximize/close visible, no foreign pixels.
- After restore the window remained a normal non-topmost frame and usable: it accepted field focus and Control_L+v, showed Resolving, reached Ready, and Compose controller interaction remained available.

Two bounded no-effect attempts are retained for exactness: accessibility element 7 reported no cached bounds before coordinate maximize; an initial restore coordinate (2297,44) did not change maximized state. Fresh observations were taken before the successful coordinate actions. The first Ready Dark WGC candidate was rejected because a Computer Use screen-space hover strip overlapped the dragged frame. Compose restarted the unchanged process, and the final clean WGC data URL was saved directly after selecting the single restarted Downlet window.

## Evidence files

- docs/design/evidence/G1/g1-empty-light-720x420.png
- docs/design/evidence/G1/g1-empty-dark-720x420.png
- docs/design/evidence/G1/g1-ready-light-720x420.png
- docs/design/evidence/G1/g1-ready-dark-720x420.png
- docs/design/evidence/G1/g1-ready-light-620x350.png
- docs/design/evidence/G1/g1-empty-semantics.json
- docs/design/evidence/G1/g1-ready-semantics.json
- docs/design/evidence/G1/g1-native-empty-light-full-window.png
- docs/design/evidence/G1/g1-native-ready-dark-full-window.png
- docs/design/evidence/G1/product-evidence.md

Only these final task-3.16 evidence files and the task-3.16 checkbox are changed.
