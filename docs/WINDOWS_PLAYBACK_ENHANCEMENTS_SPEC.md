# NuvioDV Windows adaptive playback specification

Date: 2026-11-08. Status: specification/configuration artifacts only; no runtime
integration, SVP update or release is performed by creating this document.

## Scope

Add automatic AnimeJaNai for anime/cartoons that benefit from upscaling and optional
adaptive SVP for films/series classified as non-animation. Preserve the integrated
player, existing fork features and pinned Dolby Vision/libplacebo protections.
Windows only; no new macOS/Linux packages. The backend deployment is independently
deferred in `NUVIO_SELF_HOST_DEPLOYMENT_SPEC.md`.

The user selected AnimeJaNai on 2026-11-08, replacing the original shader-only
upscaler requirement. This changes the upscaling engine and its packaging,
preparation and verification requirements; content rules, final user overrides,
the JSONL decision ledger and the separate SVP scope remain unchanged.

The user also assigned ownership of upstream mpv synchronization and carrying
AnimeJaNai support in an owned native source fork on 2026-11-08. P7 defines that
ongoing responsibility. The initial specification update created no repositories
or native builds. The subsequent source-sync authorization below supersedes its
documentation-only boundary for the narrowly stated maintenance setup.

Revision 2026-11-12 (pre-implementation plan review, documentation only):
- The user directed that the Animation toggle exist everywhere it makes sense.
  P2 now defines one per-title choice (movies and series) exposed on every
  title-scoped surface, which also resolves the earlier contradiction between a
  series-only control and the movie-only Disney exclusion.
- The user is not interested in NVIDIA RTX Video Super Resolution (found jarring
  in testing). It is out of scope; P4 defines the only interaction required.
- P7's native strategy is corrected after measuring the AnimeJaNai mpv delta.
- Android AnimeJaNai is recorded as tracked deferral D1 instead of an untracked
  verbal answer.

## P1. Content indicators and initial suggestion

`playback-content-indicators.json` is the versioned, editable rule artifact.
It is not yet loaded by the application. Future integration must package this
same artifact and permit a validated user-local copy to tune false positives and
false negatives without editing compiled code. Invalid replacement configuration
must report its precise error and preserve the last validated configuration.

Evaluate structured title metadata, not screenshot text or arbitrary substring
matches in magnet names. Map provider fields into the documented normalized fields.
Normalize case/whitespace and configured aliases; use provider IDs where available.
Every Nuvio Anime-category title qualifies without a second confidence classifier.
Preserve that title classification when launching through search, library, Continue
Watching or next episode; do not require entering through the Anime navigation row.

The Animation genre alone is a positive indicator. Title-level Crunchyroll
references are positive. TOKYO MX plus Japanese origin is a configurable contextual
rule. JP origin alone is insufficient because it includes live-action productions.
An add-on/service that returns mixed content is not by itself a title-level
Crunchyroll reference. Missing metadata means unknown, not proven live-action.

Disney-produced animated movies are excluded from automatic AnimeJaNai suggestion,
including the configured Pixar studio aliases. Use title production-company data,
not Disney+ availability. Keep their underlying animation evidence, so an untouched
Disney animation is not automatically sent to SVP. Exclusions affect suggestions
only and MUST NOT override the explicit user choice defined by P2.

Rules produce separate facts: animation evidence, suggested Animation toggle,
matched rule IDs, and any automatic AnimeJaNai exclusion. This distinction avoids
mistaking an excluded cartoon for confidently identified live action.

## P2. Final independent per-title toggle

The `Animation` choice is one two-state value per profile and title, where a
title is a movie or a series. Episodes inherit their series' value; there is no
per-episode or per-season value. Do not use a three-state Auto/Yes/No switch.

Expose that same value on every title-scoped surface where the user acts on a
specific movie or series:
- Title details action row for movies and series, beside the playback/watched/
  library actions shown in the user's reference (`DetailActions`).
- The shared poster long-press action sheet (`PosterActionTarget`) used by Home,
  catalog, collection, library and search posters.
- The Continue Watching action sheet; for an episode it edits the series value.
- The player side panel during playback of a movie or episode, next to the
  existing enhancement status/skip reason.
Do not add it to non-title surfaces (browse rows themselves, global settings,
person pages, folder/collection headers, episode or season watched sheets).
Every surface reads and writes the same stored choice; a change on one surface is
reflected immediately on the others. Fit into each existing layout with an
accessible label, keyboard focus and a clear On/Off state, including normal and
maximized Windows sizes.

A change made in the player is saved immediately. It applies live only when the
affected enhancement can switch without reloading the stream and any required
AnimeJaNai engine is already prepared; otherwise it applies at the next stream
start and the panel shows it as pending. Never reload or replace the current
stream automatically to apply the toggle.

Before a user choice exists, show the rule-suggested initial value. Metadata can
refine this suggestion until the user interacts; do not persist a suggestion as
though it were an explicit choice. Once the user sets On or Off, that boolean is
final for this profile/title and, for a series, all its episodes, regardless of
subsequent metadata, rule changes or Disney exclusions. On classifies it as
animation and permits AnimeJaNai; Off classifies it as non-animation and permits
SVP where otherwise eligible.
No rule may silently undo, periodically reset or re-prompt an explicit choice.

Resolve the title by canonical provider namespace/ID and preserve known aliases;
never key by display title or current episode ID. Scope choices to the active user
profile. Unknown/ambiguous ID aliases must not merge unrelated titles. A global
AnimeJaNai/SVP Off switch and technical feasibility constraints still apply: the
Animation toggle overrides classification rules, not the user's other settings.

For unmodified titles: animation evidence excludes SVP, even if an AnimeJaNai
exclusion makes the suggested toggle Off; positively non-animated films/series
can use SVP; unknown classification starts neither enhancement automatically.
Explicit user On/Off overrides this automatic decision, including that exclusion.

## P3. Durable decision ledger and rule review

Use a profile-scoped local append-only JSONL ledger under the app's existing
persistent user-data directory, not Temp, the installation directory or a cloud
credential payload. The ledger is the authoritative explicit-choice history;
replay its latest valid record for a profile/title to recover the final boolean.
Avoid a second independent preferences store that can disagree with the ledger.

Each event records schema version, unique event ID, UTC timestamp, profile ID,
canonical title ID/type, readable title, chosen boolean, previous explicit value,
original and current rule suggestion, rule-set version/hash, matched positive and
exclusion rule IDs, the originating surface (`details`, `posterSheet`,
`continueWatchingSheet` or `player`), and the minimal normalized metadata evidence used. Store no
tokens, private keys, stream URLs or unrelated personal data.

Append an event when the user first explicitly selects a value, including selecting
the already-suggested value, or changes it afterward. Do not append on every play
or merely because metadata refreshed. Serialize writers, commit a complete durable
record before acknowledging the toggle, and retain the prior value on write failure.
Handle an interrupted trailing record without discarding prior valid history;
surface malformed earlier records rather than silently skipping corruption.
Verify that the existing data directory satisfies the required write semantics.

Rule-review export must be owner-initiated and credential-free. Compare the
suggestion AT THE TIME against the choice and retain the rule version. These are
candidate FP/FN labels, not proof of objective content classification or permission
to automatically train/rewrite rules. Repeated user changes stay in the history.
Rule tuning never changes an existing explicit choice. Do not implement background
uploads, automatic pruning, or cloud sync as part of this ledger.

Example event structure (illustrative IDs, not user data):

```json
{"schemaVersion":1,"eventId":"example-unique-event","timestampUtc":"2026-11-08T16:00:00Z","profileId":"example-profile","titleId":"tmdb:tv:123","titleType":"series","title":"Example series","animation":true,"previousExplicitValue":null,"initialSuggestedValue":false,"currentSuggestedValue":false,"ruleSetVersion":2,"ruleSetSha256":"<actual-file-sha256>","matchedRuleIds":[],"matchedExclusionIds":[],"surface":"details","evidence":{"genres":[],"networks":[],"originCountries":["JP"]}}
```

## P4. AnimeJaNai eligibility, runtime and adaptive profiles

Expose global AnimeJaNai Off/Automatic plus an optional explicit quality selection.
Automatic processing requires effective animation eligibility under P1/P2, a
decoded source below 4K, and a destination larger than the source. Read actual
dimensions, frame rate, color properties and output size from libmpv; do not use
magnet labels. Initial boundary: after orientation normalization, skip if the
long dimension is at least 3840 OR the short dimension is at least 2160. This also
skips cropped 3840-wide content. Treat this boundary as an initial documented
policy that can be revised from playback evidence.

Integrate AnimeJaNai's native mpv upscaling filter directly through Nuvio's Windows
player bridge and the public libmpv API. Use the owned AnimeJaNai-capable libmpv
build maintained under P7, its inference library, pinned ONNX models and the selected inference runtime
(TensorRT on the current NVIDIA machine). Do not introduce a configuration-call
interception DLL or replace the integrated player with an external player.

Pin and record source revisions, artifact hashes, dependency versions and licenses
for the complete runtime/model set. Establish that its FFmpeg/libplacebo revisions
preserve the existing Dolby Vision behavior and the known sync-object leak fix.
Build the required revisions together through the owned, reproducible P7 release
path; a third-party prebuilt may provide comparison evidence but does not replace
ownership of the source integration and build recipe.
The selected libmpv must also retain the VapourSynth support required by P5. A
working upscaler alone is not acceptance of a replacement player runtime.

Package the required inference dependencies and models through the established
Windows release path, with verified hashes and explicit runtime/model versions.
Use existing application-owned persistent data/cache locations for model engines.
AnimeJaNai must not independently update libmpv, dependencies, scripts or models
outside the fork's verified release process. Record installed runtime/model
versions and the active inference backend for diagnosis.

Offer a small reviewed set of upstream quality/balanced/performance model profiles.
Retain normal scaling when no upscaling is needed. Do not stack a second neural
upscaler, import Kai's color/audio/denoise configuration, or enable AnimeJaNai's
optional frame interpolation. SVP remains the separate non-animation motion path.

NVIDIA RTX Video Super Resolution (upstream's opt-in `d3d11vpp` scaling setting,
off by default) is out of scope: the user found it jarring and will not use it.
Do not extend, recommend, auto-enable or build on it, and run all qualification
with it off. The only required interaction is no stacking: while that upstream
setting is on, automatic AnimeJaNai is skipped with a reported reason. Leave the
upstream setting and its code path otherwise unchanged.

Prepare any required GPU-specific inference engines before enabling their profile.
Cache identity must include model hash, GPU compatibility, driver/runtime versions,
precision, input dimensions and other actual engine-build constraints. Do not
reuse an incompatible engine merely because the selected profile name matches.
Use a supervised preparation operation with visible progress and explicit
completion/failure; no unexpected command window or apparent frozen player.
Keep ordinary playback available while an engine is unprepared. Do not start a
competing inference-engine build during playback or automatically replace the
current stream to conceal preparation. Enable the profile at a safe stream-start
boundary once preparation and the applicable resource checks pass.

Choose profiles by measured capability, not a GPU-name lookup or free-memory
number alone. Cache benchmark results locally by selected GPU/adapter, driver,
libmpv, inference-runtime and model versions, engine build settings,
source-size/FPS/color class and output class. Invalidate
only affected results when those inputs change. Keep machine-specific budgets out
of profile cloud synchronization.

At video start, use a valid cached result and prepared compatible engine. On a cold
start use a conservative already-prepared, qualified model if available; otherwise
use ordinary scaling and report preparation required. Measure actual decode,
inference and render cost. An isolated representative inference benchmark may
establish missing budgets, but must not compete with playback or start a second
heavy workload. Define its sample by processed frames and cover representative
detail/motion; do not block every start on engine compilation or a full benchmark.
Select the highest preset with measured frame-time and memory headroom. Initial
acceptance budget: measured 95th-percentile end-to-end processing cost below 80%
of the relevant output-frame interval, with no enhancement-induced steady-state
drops in the verification fixture. Confirm which mpv metrics measure GPU completion
rather than submission before treating them as benchmark evidence. Performance
selection applies only to profiles already accepted for image quality: compare
fine line art, textures, grain, halos and temporal shimmer using moving fixtures,
not only static screenshots or nominal FPS. A larger model is not automatically
the best visual choice for every source.

During playback, a sustained measured budget breach may downgrade the owned preset
or disable it with a recorded reason. Downgrade only to an already-prepared,
compatible model; do not trigger engine compilation mid-stream. Define the
observation window by completed frames and separate seeks/buffering from
steady-state rendering. Avoid oscillation
or repeated live quality upgrades. Re-evaluate on actual stream/output changes.

## P5. SVP setup and adaptive operation

Use the existing licensed SVP installation and its supported Manager/mpv IPC/
VapourSynth integration. Inspect and update Manager and required runtime/plugin
components, preserve the license and unrelated settings, then qualify them against
the qualified AnimeJaNai-capable libmpv selected by P4. The observed Manager version
was 4.7.0.302; available versions must be checked again when implementation begins.
Do not substitute SVP's DLL for
our proven runtime without equivalent Dolby Vision/leak-fix provenance.

Expose an independent SVP enabled toggle. When enabled, run automatically only for
film/series playback effectively classified as non-animation under P1/P2. Exclude
unknowns, trailers and other unsupported content types. Explicit Animation On
always excludes SVP; explicit Off permits it without a contradictory rule veto.

At playback start, configure the available SVP profile using actual source FPS,
resolution/color format, display refresh and measured machine capability. Choose
a sustainable target cadence and interpolation quality; do not hardcode every
display to 60 FPS or perform wasteful interpolation on an already adequate cadence.
Re-evaluate on source/display changes and measured processing pressure. Bound the
VapourSynth cache/concurrency from measured requirements. Use copy-back decoding
only where necessary and respect the user's explicit decoding policy; disclose
and skip incompatible processing rather than silently changing unrelated settings.

Use an application-owned IPC endpoint and deterministic filter ownership/teardown.
Do not clear unrelated audio/video filters. Qualify seek, pause, resume, next
episode, stream switch and app exit; avoid concurrent Manager/app control of the
same filter chain. Report active/inactive state, source/output FPS and skip reason
in the existing player details/settings surface.

## P6. Color correctness, integration and verification

Preserve HDR/Dolby Vision metadata and rendering. Kai's forced 8-bit anime
interpolation script is not acceptable as a drop-in implementation. Enable
enhancements on an HDR/DV path only after that exact path is verified; otherwise
show why processing was skipped and preserve ordinary HDR/DV playback. SDR
enhancements may be delivered with this explicit supported-scope boundary; do not
claim verified HDR/DV interpolation when only SDR was tested.

Classification/override behavior, persistence and stream-derived eligibility need
focused regression checks. Include: Anime category without Animation genre;
Animation outside Anime; Crunchyroll; TOKYO MX + JP; JP live action; Disney
animation; unrelated anime on Disney+; unknown metadata; explicit On overriding
Disney on a movie; explicit Off overriding Anime; rule update preserving both choices;
all series episodes/launch routes; the same value read and written from every P2
surface for both a movie and a series; in-player change applied live or shown
pending without a stream reload; AnimeJaNai skipped while the upstream RTX VSR
setting is on; profile isolation; interrupted/concurrent ledger
writes; sub-4K/native-4K and no-upscale cases; driver/output changes; sustained
performance; model preparation success/failure and incompatible cache rejection;
runtime/model pins and disabled independent updates; ordinary playback while
preparation is pending; supported inference backend and simultaneous workload
admission; repeated seek/episode transitions and long playback without resource
growth. Verify the referenced action layout at normal and maximized Windows sizes.

Use the host-wide Gradle gate and required low-memory profile for any later build.
Complete the whole authorized playback scope before its final Windows-only normal
release, verify the exact public package, and install/verify through the elevation
broker when delivery is authorized. No release is required for these documents.

## P7. Owned mpv source fork and ongoing upstream maintenance

### Repository boundaries and patch ownership

Maintain a personal mpv SOURCE fork under the owner's GitHub account, preserving
the history of `mpv-player/mpv`. Record its actual repository URL, maintained branch,
upstream tracking branch and baseline commits when the implementation starts;
do not document a proposed repository as already created. The existing
`Darkaxt/mpv-android-libdovi:libdovi` is an Android application/library build fork
of `FortunasXP/mpv-android-libdovi`, not the current Windows source fork. Desktop
currently pins the external build documented in `WINDOWS_LIBMPV_RUNTIME.md`.

Source-maintenance baseline established on 2026-11-08: owned source repository
`https://github.com/Darkaxt/mpv`, maintained branch `master`, upstream
`https://github.com/mpv-player/mpv:master`, persistent checkout `D:\Projects\mpv`.
Both remote branches and the clean checkout resolve to
`8bc468896ec711cc5a3ac33dae9cbc0e266bcfa4` at qualification (zero ahead/behind).
No AnimeJaNai patch or new binary is present in that baseline. The existing
Android library fork remains at `d7c00b242551d28a814928606a19a7e36684d892`,
seven commits ahead and zero behind its `upstream/libdovi` tip
`fee434bfcfdd7f4226ba21a4f4cb8ce260621c53`; its last AAR build run `34656988140`
is successful. This source check does not revalidate or replace its old artifact.

Carry AnimeJaNai's required native filter/GPU integration in the owned mpv fork.
Trace it to exact commits from `the-database/mpv`, retain attribution and licenses,
record the matching `animejanai-inference` ABI, runtime/model pins and build
recipe, and prefer reviewable history/merges over opaque binary replacement or
repeated wholesale copying of another distribution.

Measured on 2026-11-12: `the-database/mpv:master` is 340 commits ahead of and 0
behind `mpv-player/mpv:master`, and keeps itself synchronized with upstream. Its
delta is not a small filter patch: besides `vf_animejanai.c` it changes the filter
chain, hardware transfer, `vo_gpu_next`, D3D11/CUDA helpers, `vf_vapoursynth.c`,
the MKV demuxer and subtitles/OSD, and adds an addon host, RIFE scene detection and
a GPL MVTools helper. The owned `Darkaxt/mpv` delta is build/verification tooling
and documentation only (`TOOLS/nuviodv`, `ci/build-*.sh`, workflow, docs; no player
source). Therefore the AnimeJaNai integration branch is based on
`the-database/mpv` history with the owned NuvioDV build/verification tooling
re-applied on top, instead of merging the AnimeJaNai delta into the
upstream-tracking `master` every maintenance cycle. `master` remains the plain
Dolby Vision baseline until an AnimeJaNai-capable candidate is promoted. Record
which AnimeJaNai features are compiled in or left disabled (addon host, RIFE,
MVTools) and keep unused ones off; GPL components require a GPL build and license
record. The first native stage must confirm early, before feature work:
the CUDA/TensorRT build of the AnimeJaNai filter fits the owned reproducible
Windows recipe (or record the toolchain change it needs), and the modified
`vf_vapoursynth.c` still supports the SVP path required by P5.

Track the Windows build recipe in version control with exact toolchain, mpv,
FFmpeg, libplacebo, VapourSynth, inference-library and dependency revisions. Preserve
the demonstrated Dolby Vision pipeline and libplacebo sync-object leak correction.
Changes to model/inference dependencies are part of native compatibility review,
not independent floating downloads. Build Windows x64 libmpv and required runtime
artifacts only for this Desktop scope; this requirement does not add macOS/Linux
releases or automatically replace the Android AAR.

### Recurring synchronization and controlled promotion

As part of implementation, extend the existing three-day fork-maintenance check
to inspect the owned mpv fork against `mpv-player/mpv`, plus relevant AnimeJaNai
source/inference changes and native build results. Record actual upstream tips,
divergence, carried patch revisions, last verified runtime and unresolved conflicts.
An inactive Nuvio application upstream must not cause native maintenance to be
skipped. Preserve quiet healthy/no-change behavior and notify on actionable failures.

Merge upstream history into the owned integration branch, resolve conflicts while
preserving required fork behavior, and verify before publishing a candidate native
artifact. Never force-push, discard user changes or interpret an upstream merge as
proof of playback compatibility. Dependency updates need an evidenced reason and
their affected checks; do not advance every dependency blindly with each merge.

Keep source synchronization, native artifact qualification and application runtime
promotion distinct. NuvioDV continues to use its last verified exact artifact pin
until a candidate passes P4/P5/P6 and its application integration checks. A paused
app fork must not consume a newer DLL through a floating URL or an independent
AnimeJaNai updater. If the candidate is incompatible, retain the working pin,
record the exact regression/conflict and repair it without claiming maintenance
complete. Do not silently leave a known failed promotion marked healthy.

Publish qualified native artifacts through the owned project's normal release
channel with exact source/dependency manifest, build evidence, hashes and licenses.
Verify independently retrieved artifacts. Promote an exact digest through the
Desktop fork's normal verified Windows release process; native publication alone
does not update the installed app. Retain the previous verified source/artifact
pin and an explicit rollback procedure. Update runtime provenance and artifact
verification scripts together, replacing the present external-only hash contract
only when the owned artifact is qualified.

### Acceptance

Demonstrate one genuine upstream merge retaining AnimeJaNai support, the required
libplacebo fix and a compatible public libmpv API. Produce the Windows artifact from
the recorded recipe and verify actual Nuvio playback for ordinary SDR, Dolby Vision,
AnimeJaNai upscaling and the licensed SVP path, including seek/episode teardown and
long playback. Verify that the three-day monitor covers the native fork and that
a failed candidate leaves the installed application's runtime unchanged. Document
and inspect the exact native release and its later application promotion separately.
These are future acceptance checks; no such merge/build/release is claimed here.

## P8. Native Windows player controls (WebView2 migration)

Added 2026-11-13 at the user's direction as the last phase to be considered, after
Integrated Windows delivery. AnimeJaNai and SVP are already GPU-intensive, so playback
should not also keep a browser runtime alive. mpv renders video natively into the
player container window (`wid`). The player controls are a WebView2 page in a
transparent child window of that same container (`player_bridge.cpp`
`CreateCoreWebView2Controller(containerHwnd)`). For the whole playback session this
keeps WebView2 browser/GPU/renderer processes alive, and DWM composites the
transparent layer over every video frame. The rest of the app (Compose/Skia on the
JVM) is not browser-rendered and is out of scope.

Requirements:
- Evidence before replacement. During P5 (and with AnimeJaNai active), measure
  the WebView2 process tree's CPU, GPU engine, dedicated VRAM and private memory, with
  the controls hidden and visible, against a control-free baseline. If the measured
  cost is negligible, record the measurement and close P8 without a migration.
- Replace the HTML controls (`player-ui/controls.*`), including the Enhancements
  panel added in the Animation stage, with a native Windows overlay that has the same
  functions and accessibility, so playback runs with no WebView2 runtime.
- Keep the Kotlin `PlayerControlsState` contract, the JSON bridge semantics and
  upstream parity of the Kotlin side, so upstream player-UI changes remain portable.
- Preserve DV/SDR/AnimeJaNai/SVP rendering, fullscreen/DPI, keyboard/mouse
  (including back/forward) and teardown behaviour.

Acceptance: the same in-app checks as the HTML layer (debug control bridge plus
screenshots) pass. Measured playback overhead is no worse than the WebView2
baseline. No WebView2 process exists during playback.

Out of scope: a full native rewrite (for example Rust + WPF). That is considered
only once upstream Nuvio has a stable release, together with the self-hosted Nuvio
backend side project (`NUVIO_SELF_HOST_DEPLOYMENT_SPEC.md`).

## Current bounded execution: source baseline and recurring maintenance

On 2026-11-08 the user authorized starting with upstream mpv source synchronization
and extending the existing schedule alongside Nuvio Android/Windows maintenance.
This step establishes the owned source fork and its maintained checkout, checks
the existing Android library fork, and activates recurring source maintenance.
It does not implement AnimeJaNai/SVP, update native dependency pins, build/publish
new binaries, or replace installed/packaged AAR/DLL artifacts merely to mirror
unchanged upstream source. Native artifact delivery remains subject to P4-P7.

| Stage | Status | Acceptance criteria | Evidence / remaining work |
| --- | --- | --- | --- |
| mpv source baseline and recurring maintenance | COMPLETE | Owned source fork preserves mpv history and is zero behind upstream; existing Android library divergence inspected; existing three-day automation includes both native repositories and retains Android/Windows delivery, resource gates and quiet healthy behavior; remote state and saved schedule verified | Created `Darkaxt/mpv:master` from `mpv-player/mpv`, fetched and verified exact shared tip `8bc468896e` and clean persistent checkout. Android library is zero behind. Updated existing automation `maintain-nuviodv-upstream-fork`, now named `Maintain NuvioDV apps and native mpv`; read back the saved prompt, ACTIVE status, unchanged three-day recurrence and original target chat. Both native repository paths, existing app release workflows, managed execution gates, quiet healthy behavior and unchanged AAR/DLL promotion safeguards are present. No native/application code or binary pin changed; no build/release was required for this source-only step. |

Blockers: none for this bounded step. Tracked deferrals: none for this bounded
step. Closure review found no new execution infrastructure or duplicate schedule;
the existing heartbeat owns the added source checks. The future feature stages below remain separate and
must not be reported complete from a source-only synchronization.

## Future stages and reconciliation

### 2026-11-11 native maintenance evidence

`Darkaxt/mpv:master` normally merged upstream through `bcde6b93cc` (27 commits).
Native source `6b4a48dfff89306a091f6d16e23c5aa888e2727f` passed pinned build/contracts
and real Windows GPU-next SDR/Profile 5/Profile 8.1 qualification in run
`43101201701`. Normal public release `nuviodv-windows-dv.2` was independently
downloaded: ZIP SHA-256
`c7d406eacf434bfd07f81c9e5c73710aa12099548c4db0f4e03f5f00553d9148`,
DLL SHA-256 `df30ec9b4c458c778640e0aee93e46a8f0636abb981fb6d442e5aeef8149562d`,
loaded API 2.6, matching immutable source/dependency manifest, required licenses
and build/runtime evidence. Profile 5/8.1 frames were inspected. No image or
dependency commit floated. This completes this native maintenance delivery,
not P7 feature/runtime integration or any AnimeJaNai/SVP stage below.
Desktop's packaged DLL remains
`04e4d27dc385dc18ddd93fbe69c75e635b864f3c6ec53a9122df54c2f7842f4a`.
Application promotion still requires the stated real application gates.

Before these feature stages, the user's subsequent 2026-11-08 instruction
authorizes the owned Windows Dolby Vision baseline and README update in
`Darkaxt/mpv`. Its authoritative bounded specification is
`D:\Projects\mpv\DOCS\NUVIODV_WINDOWS_RUNTIME_SPEC.md`. It builds and verifies
the existing DV pipeline with pinned dependencies and publishes a native artifact;
it does not implement AnimeJaNai/SVP or replace this application's runtime pin.
The required later feature/application checks below remain in force.

The native-runtime stage is COMPLETE (owned build `nuviodv-windows-ajn.1` qualified below). The Animation decisions and adaptive AnimeJaNai stage is ACTIVE; see its progress section below. The other stages are NOT STARTED.

| Stage | Requirements | Required end-to-end evidence |
| --- | --- | --- |
| Owned native runtime integration and qualification | P7, runtime portions of P4-P6 | Owned mpv history/build recipe -> upstream merge with AnimeJaNai -> real Nuvio SDR/DV/upscaling/SVP compatibility -> verified native artifact and maintenance coverage |
| Animation decisions and adaptive AnimeJaNai | P1-P4, relevant P6 | Qualified owned runtime -> prepared compatible engine -> real metadata -> suggested toggle -> durable final override -> measured model/profile/status across episodes |
| Licensed adaptive SVP | P5, relevant P1-P3/P6 | Updated licensed runtime -> eligible live-action stream -> measured interpolation -> clean transitions |
| Integrated Windows delivery | P6-P7, all prior requirements | Exact owned runtime pin, reconciled behavior, normal Windows release, package/install/runtime verification |
| Native player controls (last, considered after delivery) | P8 | WebView2 overhead measured during AnimeJaNai/SVP playback -> decision recorded -> if migrating, native overlay with parity, in-app verified, no WebView2 during playback |

Resolve metadata field mappings, canonical aliases, ledger filesystem semantics,
benchmark metric availability, AnimeJaNai/SVP runtime compatibility, model quality,
inference-engine preparation and replacement libmpv provenance in their owning stages.
The native-runtime stage qualifies the actual player paths with controlled
configuration; it does not implement or claim completion of the later automatic
classification, settings, ledger or adaptive-profile features. It must establish
both AnimeJaNai and SVP compatibility before either dependent feature stage starts.
These are unverified implementation requirements, not completed feasibility proofs.

Open qualification questions recorded by the 2026-11-12 review, owned by the
native-runtime stage: SVP Manager normally attaches to a standalone mpv over IPC,
while Nuvio embeds libmpv in its own process, so the app may need to drive SVP's
VapourSynth plugins directly under P5's application-owned control; and whether the
TensorRT runtime and models (roughly 1-1.6 GB) ship inside the Windows release or
as a pinned, hash-verified application-owned download consistent with P4/P7.

#### Native-runtime stage: feasibility evidence (2026-11-12, comparison build)

The native-runtime stage is ACTIVE. These checks used the third-party
`the-database/mpv-winbuild` libmpv (SHA-256 `77fec11d…`) only as comparison evidence
through a ctypes harness, not as the owned P7 artifact; the stage stays open until
the owned build repeats them.

- Dolby Vision on D3D11: profiles 5 and 8.1 map to `dolbyvision` on gpu-next/d3d11.
  The renders match the owned `nuviodv-windows-dv.2` renders (WARP about 77 dB PSNR,
  RTX 4090 about 88 dB, AE@2% = 0; SDR identical).
- Dolby Vision on Vulkan: profiles 5 and 8.1 map to `dolbyvision`/PQ on
  gpu-next/vulkan. The renders match the qualified D3D11 hardware renders
  (87.5/87.2 dB PSNR, AE@2% = 0).
- SVP (open question 1 resolved): Nuvio's embedded libmpv drives the licensed SVP 4
  VapourSynth runtime and `svpflow1/2_vs` plugins directly through
  `vf=vapoursynth`, with no SVP Manager or IPC. On gpu-next/d3d11 with
  `hwdec=auto-copy` (`d3d11va-copy`), a 23.976 fps 1080p source ran at about
  47.5 fps (2x) with 0 decoder drops and 3 VO drops. libmpv resolves
  `VSScript.dll` at runtime, so the integration must point it at SVP's `mpv64`
  directory. Defect to fix in P5: the host process did not exit after
  `mpv_terminate_destroy` returned with the VapourSynth filter loaded, so teardown
  and stream transitions must be qualified explicitly.
- AnimeJaNai TensorRT requires CUDA frames (`hwdec=nvdec`) and `gpu-api=vulkan`;
  DirectML uses d3d11va/d3d11. Nuvio currently defaults to gpu-next with automatic
  gpu-api, so AnimeJaNai on TensorRT requires selecting Vulkan at stream start (DV
  on Vulkan qualified above).
- AnimeJaNai TensorRT (passed): on gpu-next/vulkan with `hwdec=nvdec`, CUDA frames
  reach `vf=animejanai` (slot 1002 Balanced, model
  `2x_AnimeJaNai_HD_V3.1_Balanced_SPANF3_b8f64_unshuffle_fp16`). A 1280x720
  23.976 fps illustrated excerpt rendered at 2560x1440 at 23.976 fps with 0
  decoder/VO drops and 0 delayed frames on the RTX 4090 Laptop GPU. The cached
  engine (2 MiB) deserialized in about 32 ms. The result was compared against a
  Lanczos upscale of the unfiltered control frame (39.8 dB PSNR, visibly crisper
  line art). The bundled AnimeJaNai benchmark clips are blank 8 kbps throughput
  sources, unusable for visual checks.
- Engine preparation (P4 integration requirement): the first play at a new
  resolution builds a TensorRT engine (about 2 minutes for 720p at
  `--builderOptimizationLevel=5`). In `stream-mode=normal` frames pass through
  unprocessed until the build finishes, and a build in progress is killed if
  playback ends (observed: trtexec exit 1). `stream-mode=prepare` builds without
  emitting frames and reports `AJN_STREAM_V1 <state>` log lines, but it re-runs
  the filter (and reports `active`) only when the host wakes it again after the
  build. A host that just waits never sees completion. Nuvio must drive
  preparation explicitly and show the user a preparing state.
- Package size (open question 2 input): the TensorRT runtime is about 167 MB plus
  about 152 MB per GPU-architecture builder resource (sm89 here), well below the
  1-1.6 GB estimate.
- Provenance gap: mpv-winbuild floats libplacebo (master plus 4 Vulkan patches) and
  FFmpeg without recording exact revisions. The leak fix `c93aa134` is present. The
  owned P7 build must pin both explicitly.
- mpv filter options need `[...]` quoting for Windows paths (drive colons).
- Tablet testing (user request 2026-11-12): the qualification checks are to be
  repeated on the user's Android tablet at a later point, excluding SVP (not
  expected to run there). Dolby Vision can be checked against the Android library
  directly; AnimeJaNai on the tablet depends on tracked deferral D1 (an Android
  inference backend) and is not claimed until D1 resolves. Requires the
  established Android device workflow and ownership checks.
- Upstream lag (user decision 2026-11-12): no dedicated lag monitoring for now.
  Recurring maintenance stays with the Codex automation `Maintain NuvioDV apps and
  native mpv`; Claude sessions do not run it. If `the-database/mpv` is observed
  lagging `mpv-player/mpv` by several weeks, the owned AnimeJaNai branch takes over
  tracking mpv master directly (the user owns upstream merges).

#### Native-runtime stage: owned build qualification (2026-11-12) - COMPLETE

Owned artifact: `Darkaxt/mpv` branch `nuviodv-animejanai` at `a77256324b`, a merge of
`the-database/mpv` master `042613482d` (0 behind it) with our `master` `222a45316c`.
Built by `nuviodv-windows.yml` run 43248843240 (GCC, `--werror`, unit tests, WARP DV
verify) with our DV-qualified pins (libplacebo `9fabf6d5` plus four vendored
`mpv-winbuild` presentation patches, FFmpeg `9a861e96`, OpenSSL 3.6.5 `c8bd5a57`).
Published without rebuilding by run 43278638733 as release `nuviodv-windows-ajn.1`
(not marked Latest; `nuviodv-windows-dv.2` stays the application pin until the
Integrated Windows delivery stage). Zip SHA-256 `0fcba959…ab44b` (identical to the
qualified CI artifact), `libmpv-2.dll` SHA-256 `79ca36e4…6779`.

Real-GPU qualification on the RTX 4090 Laptop GPU with the owned DLL (same ctypes
harness and fixtures as the comparison build):
- DV D3D11 hardware (`verify-windows.py hardware`): profiles 5/8.1 and SDR
  pixel-identical to the qualified `nuviodv-windows-dv.2` renders; WARP renders
  identical to the dv.2 CI renders.
- DV Vulkan: profiles 5/8.1 map to `dolbyvision`/PQ; 98.7/98.5 dB PSNR against the
  D3D11 hardware references, AE@2% = 0 (closer than the comparison build's 87 dB).
- SVP: 1080p 23.976 -> 47.48 fps via `vf=vapoursynth` on `d3d11va-copy`, 0 decoder
  drops, 1 VO drop. The teardown hang reproduces; it remains a P5 requirement.
- AnimeJaNai TensorRT (gated local-AI workflow, completed, caches cleaned): the
  illustrated 720p excerpt renders 2560x1440 at 23.976 fps with 0 drops; the frame
  is pixel-identical to the comparison build's output.
- Engine lifecycle for P4 (from `vf_animejanai.c` and measured):
  `stream-mode=check` reports `engineMissing` in about 2 s without building.
  `stream-mode=prepare` builds and reports `building` -> `active` when the host
  sends `vf-command <label> poll ""` periodically (768x576 engine: 47 s); without
  the poll it never reports completion. `stream-mode=required` plays only with a
  cached engine (`active` in 1.6 s, 0 drops) and builds nothing; with a missing
  engine the filter fails fatally and playback ENDS (end-file), so Nuvio must check
  or prepare before inserting the filter and must never insert it for an
  unprepared resolution. `vf-command <label> slot 0` bypasses the filter without
  rebuilding the chain, which allows a live in-player Off (and On for an already
  inserted, prepared filter) without a stream reload.

Maintenance coverage: recurring upstream maintenance remains with the existing
Codex automation, and dedicated `the-database/mpv` lag monitoring is deferred by
the user's 2026-11-12 decision (above). The stage's acceptance criteria are met;
application integration of these paths belongs to the next stages.

#### Animation decisions and adaptive AnimeJaNai: progress (2026-11-12) - ACTIVE

Implemented and pushed on `Dev` (gated Gradle receipts, desktop focused tests):

- P1-P3 classification rules, append-only decision ledger and per-title toggle store;
  P4 eligibility, filter insertion only for a prepared engine, real-stream supervised
  preparation (commit `eb44a43b1`; 18 classification + 26 resolver tests).
- P2 Animation toggle on details, poster sheet, both Continue Watching sheets and the
  player enhancement panel; P4 settings rows for runtime install and pending-engine
  preparation (commit `c2d455195`).
- P4 pinned runtime installer: user-initiated download of the pinned AnimeJaNai
  3.11.0 overlay, `animejanai-inference` runtime-2 TensorRT packs (GPU-matched builder
  resource) and pinned `7zr.exe` into `%LOCALAPPDATA%\NuvioDV\AnimeJaNai`, every file
  hash-verified. Live end-to-end install into scratch passed (receipt
  `c85569497a01c2a5796314206bc05b61` attempt 2: trt-runtime 11.4.0.106 + trt-sm89).
- P4 adaptive budgets: after a successful preparation the same supervised operation
  runs an isolated windowless benchmark (480 frames `untimed`, starting 10% in,
  `stream-mode=required`), recording mean decode+inference time per frame against
  80% of the source frame interval. Results are cached locally (never synced) per
  engine key and an environment fingerprint of GPU name/driver, loaded libmpv, the
  installed runtime manifest and the engine files. Start-time selection requires a
  prepared engine AND a qualified measurement; otherwise ordinary scaling with a
  reported reason (unmeasured = preparation required). During playback a frame-counted
  monitor (240-frame windows after a 48-frame warm-up; pauses, seeks and buffering
  reset it) bypasses AnimeJaNai for the rest of the stream after two consecutive
  windows with more than 2% drops, records the breach for that output-size class and
  never re-enables it live. Unit-tested (budget/monitor tests, receipt attempt 1).

Decisions and limits recorded for this stage:

- Only the upstream default slot 1002 (HD V3.1 Balanced, SD Compact for SD sources) is
  documented in the pinned runtime, so it is the only offered profile; "downgrade"
  therefore means off. Deferral D2.
- The benchmark measures mean per-frame cost, not the specified 95th percentile; the
  filter exposes no per-frame timing. Deferral D3. The live drop monitor is the
  steady-state guard.
- The application still bundles the shinchiro libmpv (no AnimeJaNai filter). Local
  in-app testing uses `-Pnuvio.windows.libmpv.runtimeDir=<ajn.1 directory>`; promoting
  the owned pin belongs to Integrated Windows delivery.

Native verification (2026-11-12, local-AI gate with per-task VRAM limits, owned
`nuviodv-windows-ajn.1` libmpv, RTX 4090 Laptop sm89):

- Installed runtime (the installer's own output): hidden `vo=null` preparation on a
  real 960x540 23.976 fps clip built the engine in 41.3 s (same engine identity as the
  reference package); `stream-mode=required` playback then reached `active` in 1.74 s,
  960x540 -> 1920x1080, nvdec, 0 dropped/delayed frames.
- Benchmark method (`untimed`, `frames=N`, `stream-mode=required`, `vo=null`): `frames`
  is honored exactly; mean cost 17.6 / 17.6 / 17.5 ms per frame for 240 / 480 / 960
  frames (sample size adequate), decode alone 0.7 ms, against a 33.4 ms budget at
  23.976 fps, so this class qualifies. Inter-frame gaps in this pipelined untimed mode
  (p95 about 33 ms) are not per-frame latency and are not used (D3).

In-app verification (2026-11-13, local distributable with the owned ajn.1 libmpv,
isolated app data, driven through the debug-only control bridge
`DesktopDebugControl` - env `NUVIO_DEBUG_CONTROL_DIR`, loopback + session token,
accessibility tree/actions, in-process input, window-only screenshots):

- Animation toggle verified on the poster sheet, details hero ("More actions"),
  Continue Watching sheet and live player panel; each writes the ledger with its
  surface (`posterSheet`, `details`, `continueWatchingSheet`, `player`) and playback
  honours it (explicit Off -> Skipped; live-action fixture -> "Title is not marked
  Animation").
- Settings: runtime install state, pending-engine preparation and post-prepare
  benchmark (540p Balanced 17.7 ms of 41.7 ms) -> playback Active 960x540 -> 1920x1080.
- Bugs found and fixed in-app:
  1. Details hero had no Animation toggle (added to "More actions").
  2. The Compose enhancement panel was invisible beneath the heavyweight native video
     surface; it is now an HTML modal in the WebView2 controls layer (all Windows
     player panels must live there).
  3. Pre-navigation release did not detach AnimeJaNai before native dispose.
  4. Exiting during AnimeJaNai (Vulkan) playback crashed 3/3 in the NVIDIA driver via
     `vkDestroySwapchainKHR`: `player_bridge.cpp shutdown()` destroyed mpv's `wid`
     container window before `mpv_terminate_destroy`, so a rendering VO recreated a
     swapchain on a dead surface. Shutdown now joins the event thread, terminates mpv
     outside `mpvMutex`, then destroys the UI resources. After the fix: 5/5 exits
     during Active playback clean, no new dumps.
  5. `buildWindowsPlayerBridge` skipped rebuilding whenever a DLL existed, so local
     native edits were silently stale (CI builds clean, unaffected); the `onlyIf` was
     removed.
  6. The budget fingerprint identified libmpv by size + timestamp, so every reinstall
     or re-extraction of an identical libmpv silently required re-preparation; it now
     uses the content SHA-256 (hashed once per run).

Android compile of the shared code (`:composeApp:compileAndroidMain`, gated ticket
`7a35882c6e127b71051bba2032406fd6`): attempt 1 failed only on pre-existing upstream
desktop-only pointer APIs in commonMain (`PointerButton`/`onPointerEvent`, since
upstream `d9d4e6b20`). The fork moved them behind expect/actual
(`core/ui/MouseNavigationButtons`, no-op on Android/iOS) and a common `pointerInput`
scroll handler, keeping desktop behaviour. Attempt 2 compiles; the fork's
`android-release.yml` can build again.

In-app real-metadata results (2026-11-13; public Cinemeta + Kitsu metadata, the local
fixture add-on supplying a 960x540 clip as the stream, isolated test profile):
- Kitsu `Sousou no Frieren` (genres Adventure/Comedy only): `On (suggested)` via
  `nuvio-anime-category`; S1E1 and S1E2 AnimeJaNai Active 960x540 -> 1920x1080. A
  player-surface Off switched the running episode to Skipped ("Animation is Off")
  and was ledgered (`previousExplicitValue` null, `initialSuggestedValue` true);
  S1E3 then stayed Skipped.
- Cinemeta `Frieren: Beyond Journey's End`: `On (suggested)` via `animation-genre`;
  S1E1 Active. The Kitsu Off was not merged into this IMDb-keyed title, as required
  for unverified aliases.
- Cinemeta `Zootopia` and `Coco`: `Off (suggested)` through
  `disney-produced-animated-movie` (TMDB studio `walt disney animation studios`).
  Explicit On then recorded `matchedExclusionIds` with `currentSuggestedValue`
  false, and playback ran AnimeJaNai Active, so an explicit choice overrides the
  exclusion.
- Cinemeta `Breaking Bad`: `Off (suggested)`; S1E1 Skipped ("Title is not marked
  Animation").

Known limitation: studio data comes only from TMDB enrichment (Cinemeta has no
production-company field). With TMDB enrichment disabled or its key rejected, a
Disney animated movie has unknown studio evidence and is suggested On through the
Animation genre, consistent with "missing metadata means unknown". The app does not
issue a hidden TMDB request behind a disabled user setting. Builds without a default
TMDB key need the user's own key for this exclusion.

Remaining before this stage is complete: iOS compile of the shared code (CI
`ios-test-build`; not buildable on this Windows host); live bypass under a real
frame-drop breach (unit-tested, not yet provoked in-app).

### Side effort after the full plan

User request 2026-11-12: once every stage of this plan is complete, deploy the
updated owned mpv module (the qualified `nuviodv-animejanai` runtime line) to the
user's other players as well:

- Pathweft (`Darkaxt/Pathweft`), which currently builds its own pinned mpv
  (`D:\BuildCache\Pathweft\mpv`, `*-pathweft.N`).
- VLC Redesign (`C:\Users\darka\Documents\Projects\VLC-Redesign`, `Darkaxt/vlc`).

Each needs its own integration point and verification within that project's
existing build/release process; nothing here claims either project is updated.
This side effort does not block the plan's completion criteria and starts only
after them.

### Native Windows player controls overlay

Promoted on 2026-11-13 to phase P8, the last phase to be considered (see P8). Its
measurement is taken during P5.

### Tracked deferrals

| ID | Deferred requirement | Target | Resolution condition |
| --- | --- | --- | --- |
| D2 | Additional AnimeJaNai quality/performance profiles. The pinned runtime documents only slot 1002, so no smaller fallback profile can be offered or downgraded to. | Integrated Windows delivery, or earlier if the runtime documents more slots | Further upstream profile slots are documented and pinned, each passes image-quality comparison on moving fixtures, and selection/downgrade chooses among prepared qualified profiles. |
| D3 | 95th-percentile per-frame processing cost (P4 initial acceptance budget). The in-app benchmark records mean decode+inference cost; the filter emits no per-frame timing. | Integrated Windows delivery (owned libmpv update) | The owned filter reports GPU-completed per-frame inference timing, the benchmark records its p95 against 80% of the output-frame interval, and the verification fixture shows no enhancement-induced steady-state drops. |
| D1 | AnimeJaNai on NuvioDV Android (asked 2026-11-08). AnimeJaNai has no Android inference backend (TensorRT/DirectML only); a mobile GPU backend such as ncnn/Vulkan plus model conversion would be required. | Separate Android port stage, after Integrated Windows delivery | Converted lightest model runs inside the Android libmpv pipeline; measured decode+inference+render frame time and sustained thermal behavior for 480p/720p 24 fps anime meet P4's budget on the user's device; same P1 rules, P2 per-title choice and P3 ledger semantics. Until then Android is unchanged and nothing claims Android support. |

## Sources

- https://github.com/allecsc/Stremio-Kai/blob/main/portable_config/scripts/profile-manager.lua
- https://github.com/allecsc/Stremio-Kai/blob/main/portable_config/svp_anime.vpy
- https://github.com/the-database/mpv-AnimeJaNai
- https://github.com/mpv-player/mpv
- https://github.com/the-database/mpv
- https://github.com/the-database/animejanai-inference
- https://github.com/anh-chu/nuvio-animejanai (integration precedent only; direct bridge integration is required here)
- https://www.svp-team.com/wiki/SVP:mpv
- https://www.svp-team.com/docs/hdr/
- https://mpv.io/manual/master/
- Existing runtime provenance: `WINDOWS_LIBMPV_RUNTIME.md`.
