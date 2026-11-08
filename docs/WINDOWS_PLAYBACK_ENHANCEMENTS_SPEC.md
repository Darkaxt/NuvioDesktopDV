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

## P2. Final independent per-series toggle

Place a two-state `Animation` control beside the series playback/watch/list/more
actions shown in the user's reference. Fit into the existing action layout with
an accessible label, keyboard focus and a clear On/Off state. Do not use a
three-state Auto/Yes/No switch.

Before a user choice exists, show the rule-suggested initial value. Metadata can
refine this suggestion until the user interacts; do not persist a suggestion as
though it were an explicit choice. Once the user sets On or Off, that boolean is
final for this profile/series and all episodes, regardless of subsequent metadata,
rule changes or Disney exclusions. On classifies it as animation and permits
AnimeJaNai; Off classifies it as non-animation and permits SVP where otherwise eligible.
No rule may silently undo, periodically reset or re-prompt an explicit choice.

Resolve the series by canonical provider namespace/ID and preserve known aliases;
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
exclusion rule IDs, and the minimal normalized metadata evidence used. Store no
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
{"schemaVersion":1,"eventId":"example-unique-event","timestampUtc":"2026-11-08T16:00:00Z","profileId":"example-profile","titleId":"tmdb:tv:123","titleType":"series","title":"Example series","animation":true,"previousExplicitValue":null,"initialSuggestedValue":false,"currentSuggestedValue":false,"ruleSetVersion":2,"ruleSetSha256":"<actual-file-sha256>","matchedRuleIds":[],"matchedExclusionIds":[],"evidence":{"genres":[],"networks":[],"originCountries":["JP"]}}
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
Disney; explicit Off overriding Anime; rule update preserving both choices;
all series episodes/launch routes; profile isolation; interrupted/concurrent ledger
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
and keep the fork-specific delta limited to required player support. Record the
matching `animejanai-inference` ABI, runtime/model pins and build recipe. Prefer
reviewable upstream history/merges and a small documented patch delta over opaque
binary replacement or repeated wholesale copying of another distribution.

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

Before these feature stages, the user's subsequent 2026-11-08 instruction
authorizes the owned Windows Dolby Vision baseline and README update in
`Darkaxt/mpv`. Its authoritative bounded specification is
`D:\Projects\mpv\DOCS\NUVIODV_WINDOWS_RUNTIME_SPEC.md`. It builds and verifies
the existing DV pipeline with pinned dependencies and publishes a native artifact;
it does not implement AnimeJaNai/SVP or replace this application's runtime pin.
The required later feature/application checks below remain in force.

All stages are NOT STARTED. No runtime feature is implemented by this specification.

| Stage | Requirements | Required end-to-end evidence |
| --- | --- | --- |
| Owned native runtime integration and qualification | P7, runtime portions of P4-P6 | Owned mpv history/build recipe -> upstream merge with AnimeJaNai -> real Nuvio SDR/DV/upscaling/SVP compatibility -> verified native artifact and maintenance coverage |
| Animation decisions and adaptive AnimeJaNai | P1-P4, relevant P6 | Qualified owned runtime -> prepared compatible engine -> real metadata -> suggested toggle -> durable final override -> measured model/profile/status across episodes |
| Licensed adaptive SVP | P5, relevant P1-P3/P6 | Updated licensed runtime -> eligible live-action stream -> measured interpolation -> clean transitions |
| Integrated Windows delivery | P6-P7, all prior requirements | Exact owned runtime pin, reconciled behavior, normal Windows release, package/install/runtime verification |

Resolve metadata field mappings, canonical aliases, ledger filesystem semantics,
benchmark metric availability, AnimeJaNai/SVP runtime compatibility, model quality,
inference-engine preparation and replacement libmpv provenance in their owning stages.
The native-runtime stage qualifies the actual player paths with controlled
configuration; it does not implement or claim completion of the later automatic
classification, settings, ledger or adaptive-profile features. It must establish
both AnimeJaNai and SVP compatibility before either dependent feature stage starts.
These are unverified implementation requirements, not completed feasibility proofs.

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
