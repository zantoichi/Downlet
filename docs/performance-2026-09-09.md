# Downlet performance implementation — 9 September 2026

Implemented startup and yt-dlp flow improvements in the Local checkout, retaining JBR 25 and Jewel window support. The historical investigation below records the original behavior; this section describes the delivered implementation.

### Selected changes

- **JBR 25 AOT cache:** train against final application jars and the linked runtime, then feed the same trained image to portable and MSI packaging. The launcher uses an APPDIR-relative cache. The training-only Java executable is removed. Cache training reruns when image, Java launcher, or training script inputs change. A strict packaged check exposed changed jar timestamps during staging/CAB extraction; training now rounds timestamps to two-second precision and staging preserves them. JBR's automatic mode permits ordinary startup when a cache cannot be used. [Java 25 launcher documentation](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html)
- **Small runtime:** ten required modules, `jlink --compress=zip-6`, 58.00 MiB versus 87.07 MiB before compression. No JCEF payload despite the full build SDK containing it. Keep desktop, crypto, networking, instrumentation, and unsupported APIs needed by Compose/Jewel/JNA. OpenJ9 was not selected because JBR 25 is required; its different runtime and window integration would require a separate compatibility evaluation.
- **Startup compiler and native work:** C1 compiler cap, SHA-256 pseudo handles, four bounded payload-verification workers, stale-payload pruning after application exit. Preserve complete manifest validation and rejection of unexpected files/reparse points. [Windows CNG pseudo handles](https://learn.microsoft.com/en-us/windows/win32/seccng/cng-algorithm-pseudo-handles)
- **Readiness:** signal after the first content draw and a subsequent AWT event; warm read-only tool validation afterward. This measures draw submission and event service, not GPU presentation or reboot-cold startup.
- **Progressive resolution:** optional preview and authoritative resolution overlap. Preview has one 3-second budget; Ready appears as soon as formats arrive. Late thumbnails preserve selected quality, mode, consent, and destination. Resolving has a 30-second deadline with process-tree cancellation; transfers and conversion do not inherit that deadline.
- **Reuse and preparation:** bounded in-memory sanitized information JSON (16 entries, five-minute expiry, 64 MiB information budget), isolated by source/browser/tool identity. Download supplies it through stdin with yt-dlp's fresh-extraction fallback. One selected download process may prepare after resolution, debounced 250 ms, expiring after 60 seconds. It receives no information document or media request before explicit Download. No history/cache file is written.
- **Long operations:** finite socket/retry budgets; four fragment workers; bounded process diagnostics; cancellation interrupts silent children and waits for owned process cleanup. Missing-tool setup runs at most two installations concurrently and reports per-tool download/verification progress, preserving consent and integrity checks. [yt-dlp options](https://github.com/yt-dlp/yt-dlp)

### Measured alternatives

| Experiment | Result | Decision |
| --- | --- | --- |
| Conventional AppCDS versus JBR AOT, three alternating extracted-app launches each | AppCDS median 1.902 s, AOT 1.676 s; approximately 12% lower startup time with AOT | Ship AOT. AppCDS required about 70.9 MiB of archives versus about 66.6 MiB for AOT. |
| Runtime ZIP compression, five launches per setting/compiler combination | ZIP 6 runtime 58.00 MiB; ZIP 9 57.95 MiB; ZIP 1 59.23 MiB | ZIP 6 retains nearly all the size benefit. Small launch differences were noisy. |
| Native verification, five samples each | One worker 0.677 s; two 0.527 s; four 0.410 s medians | Four workers, with all integrity checks retained. |
| CAB compression, three fresh extractions each | LZX 81.99 MiB / 2.611 s; MSZIP 95.21 MiB / 2.874 s; uncompressed 198.51 MiB / 1.762 s | Keep LZX. Uncompressed saves only about 0.85 s on first extraction at roughly 117 MiB extra download size. |
| Local HLS, 12 segments with simulated 80 ms HTTP delay, three runs each | One fragment worker 3.007 s; four 2.352 s; eight 2.537 s medians | Four workers. All outputs passed duration checks. This is a controlled fixture, not YouTube throughput. |
| ProGuard shrinking | Jars reduced from 73.05 to 47.21 MiB, but focused launches failed on reflected JBR/Jewel classes, including sealed-class metadata | Discard experimental shrinker rules; production packages retain working jars. |

C1 remains a deliberate startup tradeoff: the full test and UI smoke suites use the packaged setting, but long-session UI throughput has not been benchmarked. An additional 14-run AOT/compiler series overlapped Gradle activity and was discarded. Reported timing series were otherwise sequential and separated from builds; ordinary desktop activity remains a source of variance.

### Final package validation

The final `0.1.0` artifacts use JBR 25.0.4.1+1-b583.48. The portable is **82.67 MiB** (86,681,088 bytes), MSI **97.13 MiB** (101,845,893 bytes), extracted image **198.44 MiB**, including a **66.56 MiB** AOT cache. These are local unsigned artifacts; nothing was committed or published.

| Final measurement | Result |
| --- | --- |
| Warm portable, AOT strictly enabled, 20 launches | **2.349 s median**, 2.496 s p95, range 2.264–2.627 s |
| Same portable, AOT disabled, 20 launches | **3.526 s median**, 3.827 s p95, range 3.365–3.876 s |
| AOT improvement in the same final package | **33.4% lower median startup time**; mean working set 267.65 versus 281.05 MiB |
| Fresh current-version extraction plus launch, three runs | **4.750 s median**; 4.750, 4.607, 4.993 s |
| Administratively extracted MSI, strict AOT loading | Ready in 1.866 s; all jar timestamps match training |
| Relocation into a directory containing spaces, strict AOT | Ready in 1.971 s |
| Missing / deliberately invalid cache, automatic fallback | Both reached readiness, 2.891 / 2.845 s |

The warm-start target below three seconds is met on this machine. First extraction remains above it. The 40 warm launches alternated enabled/disabled order, with no concurrent Gradle builds or other benchmark processes. Both variants retain the identical AOT file and portable integrity work; disabling AOT isolates JVM cache benefit rather than comparing differently sized packages. P95 uses nearest rank. Fresh runs removed only this generated version's inactive payload directory after checking its path and lease. They are **not reboot-cold runs**. Historical measurements used a visibility endpoint and are not directly comparable to this draw-submission/event-service endpoint.

Validation passed: `gradlew.bat --no-daemon --console=plain check smokeTest packageWindowsSingleExe packageMsi`, with the pinned real CLI supplied through `DOWNLET_YT_DLP_TEST`. There were **112 tests, zero failures/errors/skips**, including progressive Ready/late-preview behavior, the resolution deadline, cache privacy/expiry/isolation, bounded output, actual silent-process cancellation, and actual prewarm consent plus stdin reuse. Native packaging checks passed for timing/status/dismissal, fresh and cached payloads, corruption repair, and concurrent extraction. PowerShell parser checks and `git diff --check` passed. The MSI was extracted for verification; it was not installed.

Reproduce strict package validation with `scripts/release/Measure-Startup.ps1 -Executable <portable.exe> -Portable -JvmOptions '-XX:AOTMode=on'`. Omit `-Portable` for an extracted or installed image. Use `-XX:AOTMode=off` for the paired baseline. Raw final records are `build/performance-investigation/final-warm.jsonl`, `final-fresh.jsonl`, `final-msi-strict.jsonl`, and `final-fallback.jsonl`.

Remaining measurement limits: authenticated YouTube extraction/throughput and browser-cookie performance, long-session UI throughput under C1, and reboot-cold launch remain unmeasured. Cross-time-zone CAB/MSI timestamp behavior also needs a clean-machine release check; automatic fallback remains available if archive prerequisites differ. The earlier timestamp-broken package's initial 5.785-second launch and failed strict launch were excluded from final results. Keep the strict-cache release probe: a fast-looking ordinary launch alone does not prove AOT was loaded.

## Historical baseline — before implementation

Startup is the largest confirmed local delay: **4.15 seconds to the main-window visibility signal on a warm portable launch**. The existing unpacked yt-dlp change already saves roughly 0.8–1.0 seconds per local metadata/download operation. The largest avoidable wait in the link flow is the serial preview stage, which can consume two separate 20-second HTTP deadlines before yt-dlp starts.

The baseline used the local 0.0.2 candidate and existing uncommitted changes above `0a92344`. The following baseline observations and recommendations describe the application before the implementation documented above.

**Startup measurements**

| Measurement | Result | Scope |
| --- | ---: | --- |
| Warm portable launch | **4.15 s median**; 4.15, 4.13, 4.65 s | Existing startup event, three launches, each closed before the next |
| Extracted application, default JVM | **3.50 s median**; 3.50, 3.87, 3.32 s | Same application payload, without the portable wrapper |
| Portable cached-payload verification | **0.74 s median** | Five calls to the actual native `ValidatePayload` function |
| Fresh extraction and verification | **2.70 s** | Actual `EnsurePayload`, empty private cache; excludes JVM/UI startup |
| Runtime construction | **0.43–0.49 s** | Actual HTTP client, folder discovery, runtime construction; already deferred |

An initial window-polling run observed 11.38 seconds, followed by 6.59 and 5.32 seconds. Those observations were not classified as OS-cold launches and are excluded from the cleaner event-based warm baseline above. A reboot-cold full launch and first fully painted frame remain unmeasured: the existing startup event means `window.isShowing`, not that rendering and input are ready.

The startup path is portable extraction/verification → JVM → Compose/Jewel/native graphics → visible window → background runtime initialization. yt-dlp and YouTube requests are absent from this path. The deferred runtime is an existing improvement, not a change made by this investigation. See [Main.kt](../src/main/kotlin/downlet/Main.kt), [WindowsStartup.kt](../src/main/kotlin/downlet/WindowsStartup.kt), [DeferredDownloadRuntime.kt](../src/main/kotlin/downlet/DeferredDownloadRuntime.kt), and [launcher.cpp](../src/launcher/windows/launcher.cpp).

A Java Flight Recorder run using the same packaged application jars under the matching full JBR showed startup work in class loading, JAR/native file access, font initialization, and Direct3D adapter/device initialization on the AWT thread. Parked coroutine workers and the HTTP selector were idle, not CPU bottlenecks. The trimmed packaged runtime lacks JFR, so that profiling run is diagnostic rather than a comparable launch-time sample.

| Startup experiment | Paired median result | Tradeoff and decision |
| --- | --- | --- |
| `-XX:TieredStopAtLevel=1` | 3.50 → **2.90 s**, about 17% faster | JVM CPU at readiness fell from about 5.47 to 3.38 CPU-seconds; working set from 303 to 273 MiB. One candidate run still took 3.88 s. Promising, but it limits optimization for the entire session; keep experimental until longer UI sessions are measured. |
| Base class-data-sharing archive | 3.63 → **3.41 s**, about 6% faster | Added 29.4 MiB uncompressed and increased working set. This small observed benefit does not justify adding it to the package. This was a base archive, not an application-trained archive. |

**Every stage involving yt-dlp**

The real application flow is serial: initialization → preview metadata → thumbnail → tool validation → resolution. Pressing Download then validates tools again and launches another yt-dlp process against the original URL. Successful display metadata is cached in memory per URL/browser, but the download does not reuse a full yt-dlp information document. See [DownloadStateHolder.kt](../src/main/kotlin/downlet/DownloadStateHolder.kt) and [DownloadRuntime.kt](../src/main/kotlin/downlet/DownloadRuntime.kt).

| State or action | Observation | Performance implication |
| --- | --- | --- |
| Previewing | A real preview succeeded in **0.86 s**. Metadata and thumbnail each use a 20-second request timeout. | Optional preview work can approach 40 s across two slow requests before extraction even begins; there is no shorter total preview budget. |
| Tool validation | First observed call **1.85 s**; fresh JVM with warm files **0.27–0.29 s**; subsequent calls **54–65 ms**. | Checks retain file integrity, but their first-use cost is currently paid after preview. Initialization warm-up does not prevalidate tools. |
| Setup / automatic repair | Local pinned ZIP hashing **0.12 s**, extraction/installation/verification **1.20 s**, subsequent tree validation **0.17 s**. | Network transfer of the approximately 18 MB yt-dlp archive, and FFmpeg when missing, dominates real setup. Setup download time was not measured. Consent remains required before missing-tool downloads. |
| Resolving | Real YouTube resolution returned a bot challenge in **2.28 s**. | This is a failure-path measurement, not successful resolution latency. Extraction, network access, QuickJS, and browser-cookie work can all precede Ready. |
| Ready / choosing quality | Mode and quality selection do not launch yt-dlp. Successful resolution cache hits bypass extraction. | No network optimization is needed for these controls. A different browser choice has a separate metadata cache entry. |
| Downloading / Preparing | The application launches yt-dlp again with the URL and selected format. | Preparation repeats extraction and, when selected, browser-cookie reading. Ready does not mean a transfer can begin immediately. |
| Downloading / Transferring | Local original-audio download **1.27 s** total; first transfer generally arrived around 1.15–1.22 s. | Even with negligible transfer time, process/extractor startup remains visible. Progress is already limited to 0.25-second intervals and duplicate updates are filtered. |
| Downloading / Processing | Local MP3 **1.46 s** total; tiny separate-stream merge **0.80 s** total with preloaded local metadata. | Actual conversion depends on media duration and CPU; merging depends on stream size/storage. These tiny fixtures cannot predict long-video processing time. |
| Cancel | Actual runtime cancellation returned in **39 ms**, then the resolving call drained in **4 ms**. | The tested process-startup cancellation is fast. Cancellation during FFmpeg work or large directory cleanup was not timed. |
| Retry / browser verification | Retries follow the same resolution or download paths; browser selection passes `--cookies-from-browser` to the next process. | Browser-assisted performance was not measured. No browser cookies were accessed for this investigation. |
| Completed | The staged file is moved within the chosen destination volume. | There is no second yt-dlp call or deliberate whole-file copy at publication. Cleanup is filesystem work. See [DownloadAttempt.kt](../src/main/kotlin/downlet/DownloadAttempt.kt). |

The process runner has no application-level resolution deadline, and common arguments do not set `--socket-timeout` or explicit retry budgets. This does not mean yt-dlp has no internal timeouts; it means Downlet has no independent upper bound for that state. Process-output reads and termination waits are blocking IO; cancellation relies on terminating the active process tree. A metadata deadline must terminate that tree, not merely cancel a coroutine waiting for output. yt-dlp exposes socket and retry controls in its [official options](https://github.com/yt-dlp/yt-dlp#network-options).

**Controlled yt-dlp comparison**

Both distributions reported `2026.08.19`. The same local HTTP fixtures, QuickJS executable, and PATH FFmpeg 8.0.1 were used. This FFmpeg is the existing PATH fallback, not managed FFmpeg 9.0.1. Three runs were made per operation and distribution.

| Operation | Single EXE median | Unpacked median | Reduction |
| --- | ---: | ---: | ---: |
| Metadata / format printing | 2.305 s | **1.471 s** | 36% |
| Original audio | 2.171 s | **1.272 s** | 41% |
| MP3 conversion | 2.434 s | **1.459 s** | 40% |
| Separate video/audio merge | 1.836 s | **0.798 s** | 57% |

Metadata, original audio, and MP3 used a tiny local WAV URL. Merge used generated one-second video/audio files and `--load-info-json`, so it intentionally excludes website extraction. Download commands retained the application's mode/quality arguments and progress behavior, with simplified test filenames and shortened progress payloads. These are subprocess baselines, not complete YouTube download timings.

All 24 commands succeeded. All six original-audio files matched the fixture hash. FFprobe verified the expected MP3 or H.264/AAC streams in all 12 converted/merged outputs. A real preview was available for `aqz-KE-bpKQ`, but anonymous extraction was challenged by YouTube. The older `BaW_jenozKc` test video was unavailable. Successful authenticated YouTube resolution and full transfer throughput remain unmeasured.

**Recommended order**

1. **Startup first.** Carry the C1 compiler-cap experiment into a focused candidate and measure first painted/input-ready content, repeated theme changes, and a longer active session. Adopt it only if those measurements retain the startup gain without degrading interaction. Keep payload verification intact. The base CDS experiment is not compelling enough to package.
2. **Remove the long preview tail.** Give optional preview work a short total deadline, initially testing about three seconds, then continue to authoritative yt-dlp resolution. Overlap read-only tool validation with preview or warm its integrity cache after the window is ready. Preserve setup consent, error recovery, and cancellation of obsolete requests.
3. **Bound metadata waits.** Add a resolution-specific timeout and deliberate socket/retry limits, with process-tree cleanup. Do not apply the metadata deadline to healthy media transfers or FFmpeg conversion.
4. **Then investigate repeated extraction.** The current cache contains display metadata, so another extraction occurs at Download. yt-dlp supports [downloading from saved information](https://github.com/yt-dlp/yt-dlp#embedding-examples). Reusing complete metadata in memory could remove that round trip, but requires successful real-video measurements, browser-context isolation, and a fresh-resolution fallback for expired media URLs. It is a larger change than the first three items.

Diagnostic scripts and raw measurements are in the ignored `build/performance-investigation` directory: `Measure-AppStartup.ps1`, `LauncherProbe.cpp`, `RuntimeProbe.java`, `yt_dlp_probe.py`, the startup/JVM/CDS JSONL files, `yt-dlp-results.json`, and `startup.jfr`. Timed application launches were sequential; ordinary desktop activity and occasional probe setup remained present. These are local estimates, not laboratory measurements. The extracted application passed the original payload manifest again after removal of the JVM experiments. These initial measurements preceded the implementation and its check/smoke verification.
