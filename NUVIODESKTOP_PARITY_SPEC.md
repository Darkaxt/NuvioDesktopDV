# NuvioDesktop Personal Fork Parity Specification

## 2026-11-28 Gradle 9.9.2 wrapper (COMPLETE)

Request weekly-20261128-nuvio-gradle from LLM Coordinator: move from Gradle 9.4.1 to
9.9.2. Both wrappers change (`gradle/wrapper` and the unused-for-builds
`vendor/compose-media-player/gradle/wrapper`; the root build includes only
`vendor/compose-media-player/mediaplayer`), so no 9.4.1 reference remains. No
AGP/Kotlin/JDK change is required: Gradle 9.9.2 is tested with AGP 9.0-9.5.0-alpha10
and Kotlin 2.0.0-2.5.0-Beta1 and runs on JVM 17-27 (pins AGP 9.2.0, Kotlin 2.4.10,
local Zulu 21, CI JDK 25). Kotlin's own table lists Gradle only up to 9.7.0, so newer
Gradle may emit deprecation warnings; no KGP release lists 9.9.2 yet.

Verification (Gradle gate): original ticket 3186949f0bfa7c264b051deb4cbccbad attempt 2
(workflow a46fea31d6cd30100bdc3c40618ee2f4) failed 1 of 1321 tests
(PluginRuntimeDesktopTest concurrent scraper executions, QuickJsException at
setUnhandledPromiseRejection). The 9.4.1 control (6b92995c0a7650b635f4a5123edc92dc)
restored desktopTest FROM-CACHE and is not evidence. Renewed ticket
225a3e64e6de65657c7202eb2342d04b: `--version` 9.9.2 (0c35f7271ea127db984909498386cfb8);
executed `:composeApp:desktopTest :composeApp:packageReleaseMsi` BUILD SUCCESSFUL
(84c0937c609c7b83f8e3272a1ea04dfb); three forced reruns of PluginRuntimeDesktopTest
pass (ce743d48129a9f387f034e2192ee4d03, 48d3151acaf4618b78ab5aa82ca564cc,
71a5fcb53dd06cef6380eb2ab92ebdf1). Tree exit verified for all.

Open finding (not fixed): the concurrent QuickJS test is intermittently flaky (1 failure,
4 passes); suspected native quickjs-kt 1.0.15 concurrency race, unconfirmed. Non-blocking
warnings: `registering` deprecation (composeApp/build.gradle.kts:1740), Gradle 10
deprecations, and a configuration-cache problem in packageReleaseMsi.

## 2026-11-22 Desktop delivery completion (COMPLETE)

The user renewed the expired installation approval. Native broker request
63ef3b434cc59ab57b7a4e06e2b2ca59e4631526d4194cf9245e541a002d40c1 is accepted,
launched and completes elevated with command/MSI exit 0, no reboot. The downloaded
normal 0.1.38-nuviodv.1 MSI matches the previously qualified immutable SHA-256
6fde6db151209c5e3c835fb7f245c87fadf5d6543551f6d43e9741adbe9ee393. No rebuild or
new release is needed. This resolves the Desktop installation stage left BLOCKED
on November 20; no maintenance acceptance criteria, blockers or deferrals remain.

Actual installed executable and MSI registry report 1.1.3801, application
0.1.38-nuviodv.1 / code 46. Installed JAR equals the exact public JAR SHA-256
1deb95fc09b1f06a4d9cfbae000e96103d17a02d489cee77a3fbb1136b20d38b. Non-empty
provider hashes, Supabase/RPDB/updater configuration and pinned libmpv/MVTools
pass. Local/Roaming profile creation identities match the freshly captured old
installation. The installed app is relaunched, responsive with one main window.

Required pre/post validation uses Pipeline workflows 041849607f89a0114ee815503d942109
and b7a93261808f1798db17611b66e24f76, each capped at 512 MiB. Both exit 0 with
verified whole-tree exit and no memory failure; no Gradle build runs. Exact D:
scratch D:\Temp\nuviodv-install-20261122 is the installed Pipeline runtime's
required scratch exception. Preverify scratch is immediately removed through
c4b0e17bcafb1101b8026366f243dddc (3 members / 1116 bytes). Final cleanup
472eb0a3c65fd3af13627733cf42cfda removes E:\Temp\nuviodv-install-20261122 and remaining
exact D: scratch (13 members / 216936777 bytes), zero exclusions/errors/residuals.
Published artifacts, application profiles, settings and credentials are preserved.

## 2026-11-25 recurring maintenance

Authorization: Claude-owned NuvioDV maintenance task (taken over from Codex
2026-11-22): all four forks, normal releases and local Windows upgrade.
Inspected tips: Mobile 0d20af832 (47 missing), Desktop 04c94b543 (134 missing),
native master d32c910be6 (38 missing), Android wrapper FortunasXP (zero missing,
AAR run 42532012258 healthy; no rebuild).

| Maintenance stage | Status | Acceptance / remaining work |
| --- | --- | --- |
| Native master sync and qualified delivery | COMPLETE | Merge 3eb49ff4bb contains d32c910be6 (libmpv gpu-next render backend, API 2.7 additive). Run 45592148860 passes pinned build, 16/0 native tests and Windows WARP GPU-next SDR/P5/P8.1, publishing normal nuviodv-windows-dv.6. Independent ZIP 7c97c36a…56b8be / DLL 82b31152…a671 hashes, manifest pins, licenses and isolated API 2.7 load pass. Evidence 11b6a7e63e pushed; worktree removed. App DLL pins unchanged. |
| Desktop upstream sync and Windows delivery | COMPLETE | Merge db7d02d816 contains 04c94b543 (JDK 25 packaging, JVM heap flags, subtitle blur JNI, controller navigation); vi line-ending and version conflicts resolved; 0.1.39-nuviodv.1 / 47. Contracts and gated desktopTest (303 suites, 0 failures) + packageReleaseMsi pass. Windows-only run 45595585449 publishes normal 0.1.39-nuviodv.1. Public MSI SHA-256 d65074c9587f9f91ef7a9c245b033f2058a89efc8eb733cdb890556e43ff06e7 matches SHA256SUMS; verifier confirms version/code, SIMKL/MDBList/Trakt IDs, Supabase, RPDB key, updater Darkaxt/NuvioDesktopDV and pinned libmpv/MVTools. Broker-approved upgrade installs MSI 1.1.3901 (exit 0); installed JAR equals public JAR 21bdf1cb…8909; Roaming/Local profiles retained. |
| Android upstream sync and normal delivery | COMPLETE | Merge 7bed9843bc contains 0d20af832; failed daily sync 45419876570 repaired (vi line-ending and version conflicts). Contracts, focused host tests (61 suites) and full debug APK pass. Signed run 45596471577 publishes normal v1.1.0-nuviodv.6 / 12275; APK 6e6299ea…0a8c, existing signer 1fb94424…d571 (v2), identity, ARM64-only payload and AAR-identical native libraries pass. |
| Issue delta | COMPLETE | Candidates only: #2574 purple screen after minutes of playback (new 11-22 comment, no logs or player identified); #2754 libmpv addon subtitles with broken fonts/joined words (no logs). Baselines #1730/#1675/#1723 unchanged. |

## 2026-11-20 recurring maintenance

Authorization: existing heartbeat, all four source forks, normal releases and
local Windows upgrade. Preserve all shipped features and native application pins.
Inspected tips: Mobile e022cfdf7 (already contained in origin 0df56f67e), Desktop
f63acabea (40 missing commits), native master d11ffba253 (90 missing commits),
Android wrapper fee434bfc (zero missing). Canonical native feature checkout stays
on 814006b7fa; use a disposable detached master worktree only for native work.

| Maintenance stage | Status | Acceptance / remaining work |
| --- | --- | --- |
| Android delivery and wrapper reconciliation | COMPLETE | Daily sync 44741250885 and release 44534094643 pass. Normal v1.1.0-nuviodv.3 / 12272 at 0df56f67e contains inspected upstream. Downloaded APK SHA-256 4a7f7051507bcb2125dea77ac98cc1229a6a4ae3cc8c630efe15a9fba958b159; existing signer/v2 signature, identity, all ten AAR libraries, provider hashes and updater pass. Wrapper run 42532012258/artifact 13637774468 healthy, unexpired; no native changes/rebuild. |
| Desktop upstream sync and Windows delivery | BLOCKED | Merge adc0ee5a5 contains inspected upstream. Full tests/local MSI pass; Windows-only run 44830008863 publishes normal 0.1.38-nuviodv.1 / 46. Public MSI SHA-256 6fde6db151209c5e3c835fb7f245c87fadf5d6543551f6d43e9741adbe9ee393 and contents/pins/provider hashes pass. Exact upgrade request ccff2debf7cd7f325585b77aaa4702b23140753a4579b6837d20a592144f8307 returns approval_timeout / launched=false. Installed MSI remains 1.1.3701; the new-version installed/continuity gate requires renewed genuine broker consent. |
| Native master sync and qualified delivery | COMPLETE | Merge 556759ef6e contains inspected d11ffba253; run 44831823224 passes pinned native build/contracts and actual Windows SDR/P5/P8.1 on attempt 1, publishing normal nuviodv-windows-dv.5. Independent public assets/source/lock/licenses/API/frame checks pass. Final evidence eaa4384755 is pushed; canonical feature HEAD/pins unchanged. Disposable worktree/private registration cleanup 2721dc7d25f620347b764b0bfca3cddd removes 992 members / 15985261 bytes, zero residuals. |
| Issue delta and final reconciliation | COMPLETE | All four live inspected upstream tips are contained in pushed fork branches; relevant sync/release/native/AAR runs succeed and all public releases are normal with independently verified assets. Issue delta/baselines reconciled, #2730 is an owner-review candidate only. Desktop installed version/configuration/JAR/profile identities remain unchanged after approval_timeout. Final cleanup e94349db971da6d23978c1bdcdaf854b removes 40 members / 638055512 bytes with no residuals. Overall local Desktop delivery remains incomplete at the explicit external consent blocker. |

Each maintenance stage is a complete independent product outcome; only one is
ACTIVE. Required implementation, integration and source reconciliation within
that outcome precede compilation and delivery. No parallel heavy workloads,
automatic feature-branch promotion or issue-driven patches. No tracked deferrals.
Scratch: E:\Temp\nuviodv-maintenance-20261120, registered in cleanup transaction
e94349db971da6d23978c1bdcdaf854b. Exact D: exceptions for native/worktree and MSI
small-file I/O will be recorded before creation and cleaned after last use.

Desktop source is ready for validation at the integrated merge of f63acabea:
all 40 upstream commits are present; owned workflows are retained, Desktop version
is 0.1.38-nuviodv.1 / 46. Windows event handling retains both filtered enhancement
logs and upstream VIDEO_RECONFIG-driven SDR-only RTX HDR. Existing start-option
JNI signatures, shutdown order, native pins, animation overrides/ledger, RPDB,
updater and provider configuration remain intact. Shared subtitle labels, download
redesign, focus/navigation, source fallback and ratings/Trakt changes are retained.
No unresolved merge/source gaps. Source contracts, full managed desktopTest and
packageReleaseMsi plus public MSI/installed checks are still unexecuted.
Readiness scope nuviodv-maintenance-20261120-desktop uses the single source stage
desktop-upstream-integration and exact relative source hashes. Required tasks:
:composeApp:desktopTest :composeApp:packageReleaseMsi, managed two workers,
no parallel projects, 3 GiB Gradle/Kotlin heaps and one 512 MiB test fork.
Keep the known-compatible Kotlin 2.4.10 daemon strategy; in-process compilation
has no demonstrated need here and would change an already qualified strategy.

Desktop validation: readiness 501dc44ab0047ab9697fffca676a04ee / attempt 1,
workflow ee9d2f957eee6332ec730539e9e10de6. Full desktopTest/packageReleaseMsi and
source/JNI contracts pass. Adapter build exit 0, shared mutex acquired, observed
two workers/no parallel/3 GiB Gradle, configured 3 GiB Kotlin and one 512 MiB test
fork; no memory failure, peak aggregate working set 5329207296 bytes. Single-use
Gradle daemon exit is verified. MSVC's surviving VCTIP.EXE telemetry descendant
(PID 18620, creation 134396869915292023) kept the native job alive after the adapter
finished. Supported original-owner stop retires this exact job: native exit stays
0, full tree exit verified, native workflow CANCELLED / wrapper 130. This is an
intentional lifecycle retirement, not a failed compile or a fully green native
workflow; no unchanged compilation was repeated. Initial unquoted PowerShell argv
was rejected before admission; the same original receipt was then used with exact
quoted arguments. No second build or new receipt was launched.

Local MSI SHA-256 dfbbea910b5475988ee552344a4fb86ac0e01cabfe321739965289d175149878
passes MSI 1.1.3801 / app 0.1.38-nuviodv.1 / 46, original UpgradeCode, configured
provider hashes, Supabase, RPDB/updater and both unchanged native pins. Extraction
is spent and removed immediately: transaction 3c1aa84c02a8d9c022115cb0fd440b42,
2 members / 98975001 bytes, zero exclusions/residuals. Public release and installed
upgrade remain required. Only this evidence text changes after compiled validation.

Desktop delivery is verified at normal tag 0.1.38-nuviodv.1, source adc0ee5a5,
Windows-only run 44830008863. Public MSI SHA-256
6fde6db151209c5e3c835fb7f245c87fadf5d6543551f6d43e9741adbe9ee393 matches GitHub and
SHA256SUMS.txt. Public JAR SHA-256
1deb95fc09b1f06a4d9cfbae000e96103d17a02d489cee77a3fbb1136b20d38b passes version/code,
original UpgradeCode, provider hashes, Supabase, RPDB/updater and native pins.
MSI remains unsigned as in the established personal channel. Public extraction
cleanup ccc8a35305ba47dc274393d53ee5886c removes 2 members / 98962381 bytes with no
exclusions/residuals; spent build logs cleanup 5df84ec5a700a3c9c019289156d8f24a
removes 3 / 588470. Native broker request ccff2debf7cd7f325585b77aaa4702b23140753a4579b6837d20a592144f8307
returns approval_timeout / launched=false. No install command ran and installed
MSI remains 1.1.3701. External blocker: genuine per-command broker consent for the
exact public upgrade; resolution is a newly requested/approved broker action,
then installed-JAR/profile/configuration verification. Do not automatically replay
the expired request. This independent installation blocker parks Desktop while
native maintenance proceeds. Source/public release are verified, local deployment
is incomplete.

Native normal merge 556759ef6e contains inspected d11ffba253. Original hosted run
44831823224 passes all gates on its first attempt and publishes normal
nuviodv-windows-dv.5. Public ZIP SHA-256
d44c0a66230b266c28c4d3b90067fac31822ec52b2be713c540f12983039f0c2, DLL
d8b1c1438cc44eff8ed9ff12f8d8ab044dd1b450dde5e2788182f7097ad9fdf2, API 131078 / 2.6,
all nine asset digests, exact source/lock, ten license directories, CLI/headers/import
library, zero-failure build evidence and all Windows SDR/P5/P8.1 GPU-next cases pass.
All three public frames are visually inspected and correctly mapped. Dependency
pins, WASAPI history and canonical feature HEAD 814006b7fa remain unchanged.
Final source/evidence eaa4384755 is retained on local/origin master. Immediate
API cleanup 0d45c034952c75a38fbf8b58e9c9d44f removes 2 members / 161508352 bytes;
worktree/private-registration cleanup 2721dc7d25f620347b764b0bfca3cddd removes
992 / 15985261. Both apply without exclusions/errors/residuals. Only the canonical
feature worktree remains. No application runtime promotion or new P7 acceptance.

Issue review since 2026-11-17T21:59:28Z reads 16 open updated issue bodies and all
their comment pages, plus 11 existing baselines and PR #2676. New high-relevance
candidate #2730 (updated 2026-11-20T14:27:28Z) explicitly reports Android libmpv,
GPU-next, hardware decoding, YUV420P disabled, approximately 23.8 descriptor/s,
32768-FD exhaustion after 1348 seconds, and 32245-32446 anon_inode:sync_file entries
in 16 tombstones. It corroborates the file-descriptor exhaustion class tracked by
#1730 and references #2444. This is upstream package com.nuvio.app evidence, not a
reproduction on our patched AAR and not proof that the existing c93aa134 pin
resolves this particular report. Keep it for owner-directed validation; no patch
or upstream comment is made. #1675/#1723 and the pink/ASS/audio-focus baselines
have no new logs. #2675 / PR #2676 remain unchanged and unmerged. #2685 is now
closed (2026-11-20T21:22:40Z), with no new comments/native evidence. #2731 fails
before playback in torrent metadata/mobile-network retrieval and is not evidence
against the native rendering path. Other updates concern metadata, preferences,
iOS or feature requests outside this bounded player maintenance.
Next check uses overlap cutoff 2026-11-20T22:03:13Z and compares candidate update
times so unchanged evidence is not notified again.

Final reconciliation completes after midnight local time (November 21), retaining
the original November 20 task identity. Live remote branch tips still match the
inspected upstream tips for all four forks, and ancestry checks pass. Relevant
daily sync, Android publication, Windows publication, native qualification and
unchanged ARM64 AAR workflows are all successful. Public latest releases are
normal/non-draft/non-prerelease with the independently verified exact source and
assets listed above. Non-documentation Desktop readiness pins are byte-unchanged
after successful compilation. Re-read of the actual installed old application
matches its complete before report, including JAR hash, provider hashes, updater,
native pins and Local/Roaming profile creation identities. Broker has no active
or pending request after its timeout. No command was launched or replayed.

Final reviewed cleanup e94349db971da6d23978c1bdcdaf854b applies to 40 exact members /
638055512 bytes, zero exclusions/errors/residuals. All task E: scratch, the exact
new locally generated MSI, both D: MSI extractions, API scratch, native detached
worktree/private registration and spent D: gate logs are absent. Public artifacts,
canonical repositories, installed version, settings and pre-existing outputs are
preserved. No ACTIVE stages or tracked deferrals remain. Desktop's genuine
per-command administrator-consent blocker remains unresolved; overall installed
delivery is explicitly incomplete, although source/public release maintenance
and all independent work are verified. Next action is a newly authorized native
broker request for the same public 0.1.38-nuviodv.1 MSI (re-download and verify its
recorded hash), followed by exact installed-JAR/configuration/profile checks.

## 2026-11-17 recurring maintenance

Authorization: the existing three-day heartbeat covers all four source forks,
normal releases and the local Windows upgrade. All worktrees were clean. No other
active Nuvio repair owner appeared in the current/pinned chat inventory. Mobile
was fast-forwarded to the already published `949e6a5df` / `.2` before integration.
Inspected upstream tips: Mobile `b2e3d67ba` (66 missing commits), Desktop `c0eb7bfa7`
(111), native master `f92afa75cd` (57), wrapper `fee434bfc` (zero missing).
Mobile sync `44094106133` failed closed at merge conflicts; release `43712275657`
is healthy. Desktop `43649060098`, native `43650453489` and unchanged AAR
`42532012258` are successful. These existing deliveries do not cover the new tips.

| Maintenance stage | Status | Acceptance / remaining work |
| --- | --- | --- |
| Android upstream sync and normal delivery | COMPLETE | Merge `60c33e2d7` contains inspected `b2e3d67ba`; source contracts, managed focused tests/full debug APK and CI full Linux suite pass. Run `44188988989` publishes normal `v1.1.0-nuviodv.1` / 12270. Downloaded APK SHA-256 `1707d8ec218dfab700239fa692bae0a59113f5969272bd9ff58d36474c1d4984`, signer, identity, all native pins, provider hashes and updater pass. Fresh daily sync `44190047181` succeeds. Evidence commit `d20411813` is pushed. |
| Desktop upstream sync and Windows delivery | COMPLETE | Merge `f926cd1ba` contains inspected `c0eb7bfa7`; full gated tests/MSI, source/JNI contracts and independent public checks pass. Windows-only run `44195032115` publishes normal `0.1.37-nuviodv.1` / 45. Public MSI SHA-256 `309a4a77d7ba9b312464568fa9802854f8a401629cc4def5352d2822d906df04` is installed by approved broker request `10aa18f5eb79f7ca7f897be78368242e2992174b1cb15d18f0756cd5b5a7809b`, exit 0/no reboot. MSI registry is 1.1.3701; installed/public JAR bytes, provider hashes, updater, runtime pins and profile creation identities match. Git-only cleanup repair `b7e1f2abc` is pushed and verified without recompiling unchanged app code. |
| Native master sync and qualified delivery | COMPLETE | Source `7e14bd6985` contains inspected upstream `f92afa75cd`; original build `44197733666` passes pinned compilation/contracts. Canonical fixture-host repair `22ff2fd7b7` and same-identity requalification `44200966496` attempt 2 pass Windows SDR/P5/P8.1 and publish normal `nuviodv-windows-dv.4`. Independent ZIP SHA-256 `55e88ac1b36c2af8a5f3477b32c519f42fc7764592d8253a0248db9e2a25c570`, lock/licenses/build/runtime evidence, frames and DLL API 2.6 pass. Final evidence `52df85e9d0` is pushed; exact worktree/private registration cleanup `bcfcc6399d2c596a8e947d5d0328f9c9` removes 989 members / 15936135 bytes, no residuals. Canonical feature HEAD and app pins are unchanged. |
| Issue delta and final reconciliation | COMPLETE | Complete issue/body/comment delta and baseline checks yield owner-review candidates #2675 / low-confidence #2685; no issue-driven patch or upstream comment. Mobile M1 repair `a8752bb08` and fresh no-op `44203646649` pass with no prior cleanup warning. All four inspected upstream tips are contained; latest workflows/releases, immutable public digests and installed Windows state pass. Reviewed final cleanup `62065b13c00778b026cdfdd5ead9ace1` removes all 66 spent members / 753678136 bytes, with zero exclusions/residuals. |

Tracked deferral M1 is resolved: Mobile repair `a8752bb08`, its source regression,
actual recursive Git command and fresh successful no-op daily sync `44203646649`
verify the unused orphan-Gitlink correction. No remaining tracked deferrals.
No ACTIVE stages or unresolved blockers remain. Wrapper source/native inputs are unchanged,
so no AAR rebuild or dependency promotion is required. Native source maintenance
runs independently of app changes. E: task scratch is
`E:\Temp\nuviodv-maintenance-20261117`, cleanup transaction
`62065b13c00778b026cdfdd5ead9ace1`; exact compiler/native small-file I/O exceptions
may use registered D: scratch and must be cleaned immediately after last use.

Desktop source reconciliation: normal merge of `c0eb7bfa7` retains the complete
upstream controller/input, exact-seek, subtitle and RTX HDR changes. Release becomes
`0.1.37-nuviodv.1` / code 45 (upstream 37, previous fork 44). Combine upstream
active-profile state with animation preloading and combine HDR storage fields with
machine-local AnimeJaNai/SVP fields. The Vietnamese conflict has no fork semantic
changes, so retain upstream XML values and normalize only line endings/whitespace.
Keep owned non-Desktop workflows; Desktop's VERSION_BUILD_KEY change is required
by the updated metadata helper. Its normal-publication guard remains intact.

Both Windows JNI creation paths now forward the new HDR boolean in matching
Kotlin/C++ argument order, including the fork's start-option path. A cross-language
signature contract verifies both entry points. RTX HDR owns d3d11va/d3d11vpp, so it
excludes the existing VapourSynth enhancements just as RTX VSR already does;
otherwise enhancement options can override decoding beneath the HDR filter.
Session regressions check the exclusion before runtime/title lookup. No native
pin changes or new enhancement functionality are included. The full Desktop tests,
native bridge/MSI packaging, normal public verification and installation remain
required. Kotlin 2.4.10 keeps its established daemon strategy and the managed
3 GiB Gradle/Kotlin, two-worker/no-parallel/one-test-fork profile.

Windows validation passes on original readiness `a36192b5297ad1bc4c6ee64b16a3903c`
/ attempt 1 / workflow `fa043d31d72c62dc19025e7d589ded1e`. Full `desktopTest`,
native bridge compilation and release MSI complete with exit 0, exact unchanged
compiled inputs and verified whole-tree/daemon exit. The adapter verifies shared
mutex acquisition, two workers/no parallel projects, observed 3 GiB Gradle heap,
configured 3 GiB Kotlin daemon heap and one 512 MiB test fork. No memory failure
or budget increase; peak aggregate working set 5642375168 bytes. New coexistence
tests actually execute without skips and pass. No emulator, inference or WSL build
is used. Local MSI SHA-256
`b529678c75e52b97ff02be929b93353dd5c5f59a04d88f6d6e757a003e914d3a` verifies version
`1.1.3701`, application `0.1.37-nuviodv.1` / 45, original upgrade identity,
provider-client hashes against the installed baseline, Supabase configuration,
RPDB/updater and exact libmpv/MVTools pins. Normal public delivery/installation
remain unverified. Spent D: gate logs are removed through reviewed transaction
`97b79e4d65c4ae8abc44da287b6def4b` (3 members / 584972 bytes); the exact local MSI
extraction is removed through `0becfb36a081aaa83d0add8b76c715bb` (2 / 98879806).
Both apply with zero exclusions/residuals after required evidence is inspected.

Normal public Windows delivery passes: run `44195032115` publishes
`0.1.37-nuviodv.1` from `f926cd1baa8c108cea8241d2f3a86d773bf3e867`; all Linux/macOS
jobs are skipped. Independently downloaded MSI SHA-256
`309a4a77d7ba9b312464568fa9802854f8a401629cc4def5352d2822d906df04` matches GitHub
and SHA256SUMS. Its version/code/upgrade identity, all provider-client hashes,
Supabase/RPDB/updater and both native pins pass. Public application JAR SHA-256 is
`22bbef7580da8c15b02e796da2d325a9aa9ea11e3d1db71651a1dc668767d71d`.
The exact public MSI extraction was immediately removed through reviewed
`e467eb4cd5459ceddb0b6bec5def11a9` (2 members / 98867185 bytes), no residuals.
Broker-managed installation and installed/public readback remain required.

CI's successful metadata job exposed a real post-checkout cleanup defect:
`git submodule foreach --recursive` exits 128 because `libass-android` lacks a
`.gitmodules` URL. The new Git-index regression additionally identifies orphan
`vendor/quickjs-kt`. Both directories are empty, neither is included by the build,
and the actual Android dependencies are bundled AARs / declared Maven artifacts.
Remove only those unused index gitlinks, preserving their directories and AARs;
retain the legitimate MPVKit mapping. The new contract fails before that repair,
then all contracts and the actual recursive cleanup command pass. This Git/CI-only
correction changes no compiled input or public MSI; it requires no duplicate app
compilation/publication. Mobile's matching checkout warning is assigned to the
final reconciliation stage for diagnosis and any same bounded metadata repair.

Local Windows delivery passes: approved broker request
`10aa18f5eb79f7ca7f897be78368242e2992174b1cb15d18f0756cd5b5a7809b` executes the
hash-bound public MSI, installer exit 0 / no reboot. Registry ProductCode
`AFA0F649-D3D2-3FEA-B854-F2E93318BF83` reports `1.1.3701`. Installed/public JAR
bytes match exactly; provider-client hashes and native pins match the baseline,
and Local/Roaming profile directory creation identities are unchanged. The app is
relaunched after installed-package verification. The MSI is unsigned as in the
existing personal release channel; GitHub/SHA256SUMS/source/package checks establish
its delivery identity rather than claiming an Authenticode signer. No playback or
visual UI acceptance beyond these maintenance gates is claimed.

Final issue/release/source reconciliation passes before cleanup. The complete
open-issue API delta since `2026-11-14T23:21:00Z` returns one page with 34 entries
(16 issues, 18 PRs). All issue bodies and comments on the seven commented issues
are reviewed. All nine prior native-player baseline issue records are checked;
all comments on the commented baselines have no post-cutoff updates. #1730 remains
the high-relevance historical descriptor-exhaustion/libplacebo baseline covered
by the pinned `c93aa134` ancestry; #1675/#1723 remain low-confidence. No new
DV/libplacebo/GPU-next/hardware-decoder/pink-output/long-playback-failure evidence.

Owner-review candidates only; no issue-driven changes or upstream comments:
- #2675 (updated `2026-11-15T16:13:32Z`): reproducible delayed add-on subtitle
  auto-selection failure, including a reported host test and shared Desktop
  reproduction. Current shared `PlayerScreenRuntimeEffects.kt` retains the early
  all-preferences-applied return, and `PlayerScreenRuntimeTrackActions.kt` retains
  the primary-only later-match guard. Upstream fix PR #2676 is open/unmerged.
  This is a stronger shared-player candidate, not evidence of a native pipeline
  crash; a fork reproduction/PR review is the next owner-directed step.
- #2685 (updated `2026-11-16T07:49:38Z`): injected-ad streams reset the displayed
  timeline, so seeking can jump to 00:10. The reporter also names Windows/macOS,
  but provides no mpv logs. Low confidence for this fork; require a sample and
  native-player logs before assigning the cause. #2693 explicitly uses ExoPlayer
  and includes TV reports, so it does not establish a fork libmpv defect.
Other updates concern UI/catalogs/downloads/iOS distribution or feature requests.
Use heartbeat start `2026-11-17T21:59:28Z` as a conservative overlapping next-query
checkpoint; compare candidate timestamps above to avoid duplicate notifications.

Fresh remote/source checks contain all inspected tips: Mobile `b2e3d67ba`, Desktop
`c0eb7bfa7`, native master `f92afa75cd`, wrapper `fee434bfc`. The native canonical
feature binding is still clean at `814006b7fa`; source/evidence master is retained
at `52df85e9d0`. Wrapper source `27bde9f7` remains unchanged, and its successful
run `42532012258` artifact `13637774468` is unexpired through `2027-02-06`.
The latest Mobile sync/release (`44203646649` / `44188988989`), Desktop release
`44195032115` and native qualification `44200966496` attempt 2 all succeed.
Independent latest-release readback matches every previously downloaded asset
digest and exact source commit. All 64 Mobile, 10 Desktop and seven native public
releases are normal; latest endpoints resolve to the three verified deliveries.
The installed Windows package/profile/configuration result remains applicable;
no compiled input changed after its checks. Final reviewed cleanup
`62065b13c00778b026cdfdd5ead9ace1` is applied: 66 exact spent members / 753678136
bytes, zero exclusions/errors/residuals. The E: task root, all D: native/gate/API/MSI
scratch and both generated C: verification packages are confirmed absent. Public
releases, installed files/profiles, maintained repositories and shared Git state
are preserved. All required maintenance criteria pass; final evidence is committed
and pushed from this verified state without another software release for records.

## 2026-11-14 recurring maintenance

Existing authorization covers normal releases and the local Windows upgrade.
Inspected upstream tips: mobile `ba149964c`, Desktop `c8ee76215`, native mpv
`bb4823e5f0`. Mobile origin contains its inspected upstream and has successful
sync `43590697115` / normal `.7` release `43591048141`; fast-forward the clean
local checkout without rebuilding. Android library remains zero behind with
successful AAR run `42532012258`, so preserve its pin and do not rebuild it.
Desktop's prior `.5` blockage was recovered and superseded by normal `.7`, now
installed as MSI `1.1.3507`. Preserve current AnimeJaNai/animation decisions,
adaptive playback and all other authorized fork behavior, including the current
owned DLL `03efdd2f3a88685a4f1cd6793baea4ac3d89fdd825d2684fa19a37f7c85daa9f`.
Do not implement new AnimeJaNai/SVP features or promote another native runtime.

| Maintenance stage | Status | Acceptance / remaining work |
| --- | --- | --- |
| Desktop upstream and Windows delivery | COMPLETE | Merge `a73c18937` contains inspected upstream `c8ee76215`; full gated tests/MSI, contracts and independent local/public package checks pass. Windows-only run `43649060098` publishes normal `0.1.36-nuviodv.1` / code 44 from that exact application commit. Public MSI SHA-256 `6cd3be30b22886c7d3f84dfad327d46baec2eebd0d51840401912994ac6f29fd` matches GitHub and SHA256SUMS. Broker request `55000481d7f66c6d3589983ee713f11cc78f8cbb4ca8dc28bdcf6e8661768074` installs with exit 0/no reboot. Installed/public JAR SHA-256 `0e7a0e447eabb5a07fa4d6525f6ac0db5ce72629622bbc00575512982b5e66fa` matches; MSI registry and executable version 1.1.3601, provider hashes, updater, runtime pins and original profile creation identities pass. |
| Native master sync and delivery | COMPLETE | Normal `nuviodv-windows-dv.3`, source `436e1f060a`, run `43650453489`, contains inspected upstream `bb4823e5f0` plus the existing WASAPI patch. Full pinned build and actual Windows GPU-next SDR/P5/P8.1 checks pass. Independent public asset hashes, dependency/source manifest, licenses, DLL API 2.6 and DV frames pass; ZIP SHA-256 `2d18f5a13d58570a5118620615190d4af8bec23d68627b60e3e93fed1090a478`. Evidence commit `02c516dad3` is pushed to master; canonical feature checkout and application pins are unchanged. Exact spent worktree/private registration cleanup `62e604a9bcb328253cde303169eb2878` removed 987 members / 15915976 bytes. |
| Issue delta and final reconciliation | COMPLETE | Issue delta and in-place schedule correction pass. The later Mobile tip `6b7f15e40` is delivered as normal `v0.6.13-nuviodv.1` / code 12268, source `1c1847736`, run `43654900049`; full Linux host suite, local focused tests/debug package and independent signed public APK checks pass. All four source branches contain their freshly inspected upstream tips; public release census and installed Windows checks pass. Reviewed final cleanup `8765ae50f6f55f7b288f5c7c83b53ece` removed all 60 spent members / 741102037 bytes with no exclusions or residuals. Evidence-only documentation records the complete verified outcome. |

No blockers, tracked deferrals or ACTIVE stages. The old phantom run `43096139223` has
been superseded by verified deliveries and is historical, not an active build.
Use `E:\Temp\nuviodv-maintenance-20261114` for disposable reports/downloads.
Compiler/native extraction I/O may use exact registered D: task scratch; remove
it immediately after its last consumer. Existing project build bindings remain.

The following source/validation/delivery notes retain the execution history;
the table above records the final reconciled status.

Desktop source boundary: version `0.1.36-nuviodv.1` / code 44 integrates all 55
inspected commits. Folder routing keeps `catalogRouteKey()` plus the upstream
`folderCatalogTabLabel`; XML key comparisons retain all Bulgarian keys, and
incoming translation line endings are normalized without changing XML values.
The release contract now checks upstream base 0.1.36. Release/RPDB source contracts
pass and both runtime files match `runtime-pins.sha256`; the current AnimeJaNai,
SVP, classification/ledger, player-details, RPDB and credential wiring remain.
No known source gap remains; source is ready for validation, not verified.
Run full `desktopTest` and `packageReleaseMsi` once through the managed Gradle gate
with a readiness snapshot of the integrated files. Use existing WebView2 SDK
1.0.4258.31 locally; the public CI build retains its own pinned SDK. Kotlin
2.4.10/Compose 1.13 retains the previously verified daemon strategy rather than
changing compilation mode during this merge. Initial Gradle/Kotlin budgets remain
3 GiB each, two workers, no parallel projects and one test fork. This is a scoped
local profile, not a host memory guarantee. Final installation uses the public MSI.

Validation correction: original readiness `cf0023e0878edf46a1d9787bbaea300d`,
attempt 1 / workflow `a8fc67754fdf0d81b4a8f0f2e61fd7b3` failed with native exit 1
and verified whole-tree exit, peak working set 5438730240 bytes, no memory failure.
`ServerWatchedTest.mirrorsCatalogMarksWhenAddonMetadataIsOn` exceeded its 15-second
deadline. The shared matcher converts its IMDb-only requests through real TMDB
and can fetch real title metadata even with a fake server. The fixture now uses
valid non-converting Kitsu IDs and a complete fake exact-ID lookup, retaining
movie/episode/unmatched/mark/unmark assertions. It joins the actual job rather
than racing an arbitrary deadline. No production mirroring behavior changes.
Revalidate on the original failure-linked receipt. Full-suite evidence remains
required because the first fail-fast run never passed the complete suite; MSI
packaging also remains unexecuted. The new reusable read-only MSI/JAR verifier
has checked the installed `.7` baseline without exposing credential values.

Corrected original attempt 2 / workflow `86bfd9c8d58abc94b462e1572e0cecd5`
completed full Desktop tests and the release MSI with native exit 0 and verified
whole-tree exit. It waited for the other host build rather than overlapping it;
peak working set was 2809020416 bytes. No memory budget increase or WSL was used.
Independent local MSI inspection verifies `1.1.3601`, desktop version/code 44,
the existing upgrade identity, exact current libmpv/MVTools pins, configured
Supabase/SIMKL/MDBList/Trakt and fork updater/RPDB. Provider-client hashes match
the installed `.7` baseline. Local MSI SHA-256 is
`68573680d1515359d43908b27738737bd8c26f4eda4f2db3cb5d70aa1422c9a3`.
The reusable read-only verifier was exercised against both the installed package
and this MSI. No architecture consolidation is needed for this bounded merge;
the one test-fixture correction and the verifier serve the current delivery.
Normal public publication, independent public MSI checks and installation remain.

Delivery reconciliation: a broad repository ignore rule initially omitted the
read-only verifier from the merge commit. Follow-up `e1b6b98a8` tracks it explicitly;
its only delta from the application commit is that verification script. The
established ephemeral release branch at `a73c18937` therefore publishes the exact
validated application/version commit without weakening the final-version guard or
changing the version again. The branch was removed after release verification;
the tag and Dev retain all source history. Both D: JAR extraction copies and the
compiler scratch were removed through reviewed cleanup immediately after use.

Final delivery audit: all 62 Mobile, nine Desktop and six native releases are
normal/non-draft; latest endpoints resolve to `v0.6.13-nuviodv.1`,
`0.1.36-nuviodv.1` and `nuviodv-windows-dv.3`. Independently downloaded mobile APK
SHA-256 `cb631f31fb6a10498cd5ed243a8db1c5cb7097eed659fba2383a9ce3ac3323a5`
has the expected existing signer/v2 signature, package/version/code, provider
identifiers, fork updater and all ten pinned AAR native hashes. The public C++
runtime is byte-identical; local debug symbol stripping was separately proven
against every allocated ELF section without a production patch.

Fresh installed Windows inspection still matches the exact public JAR and
MSI version 1.1.3601, configured providers/updater and the original profile
identities. Both feature runtime pins remain unchanged. Source refresh confirms
zero missing commits at Mobile `6b7f15e40`, Desktop `c8ee76215`, native master
`bb4823e5f0` and Android wrapper `fee434bfc`; native canonical feature HEAD remains
`814006b7fa`. Wrapper artifact `42532012258` is available and unexpired.

Issue delta covers all 18 open issues (31 API entries including 13 PRs) updated
since November 11 21:31 UTC and all new comments; baselines have no new updates.
No newly actionable native-player evidence or issue-driven patch was found.
Mobile maintenance specification records the next overlapping issue cutoff.
The existing three-day heartbeat was updated in place to preserve shipped
AnimeJaNai/SVP/Anime4K behavior, current native feature checkout and immutable
app pins. Read-back verifies the same cadence, active state, target chat and quiet
notification intent. No new feature or additional schedule was introduced.

Original Desktop build lifecycle receipts were rechecked before exact gate-cache
cleanup `5f99079321a8bbf11fb05731e8e6cfa1` (205 members / 6748814 bytes). The later
Mobile gate cache cleanup `d06866618e44d001da5601dc918610fb` removed 10 members /
3362963 bytes after verified tree exit. Native API/worktree scratch was already
removed. Final E: verification copies/scripts and the exact local MSI/debug APK
were removed through inspected transaction `8765ae50f6f55f7b288f5c7c83b53ece`
(60 members / 741102037 bytes), without exclusions, warnings or residuals.
Installed apps, canonical repositories, user data, credentials and public release
assets are retained. Maintenance is COMPLETE; no required deferral remains.
Public delivery and local installation criteria are now satisfied; no Desktop
blocker or consolidation requirement remains for this round.

## Authority and scope

The user authorized an end-to-end personal fork of `NuvioMedia/NuvioDesktop`, a bounded comparison with the verified NuvioDV Android fork, implementation of missing applicable features, publication through the fork's normal release channel, local Windows installation, and launch to the login boundary.

The comparison baseline is:

- Android fork: `Darkaxt/NuvioMobileDV:cmp-rewrite` at `65c6a10c03e16d6198e67fd43edd899e80c5ffca`.
- Desktop upstream/fork baseline: `NuvioMedia/NuvioDesktop:Dev` / `Darkaxt/NuvioDesktopDV:Dev` at `897efe93`.

## Requirements

### R1. Evidence-based parity inventory

Identify user-visible behavior present in the Android fork but absent or incomplete in the desktop baseline. Each candidate must cite concrete source evidence in both repositories and be classified as:

- applicable to Windows desktop and required for this fork;
- already present/equivalent on desktop; or
- Android-specific or otherwise inapplicable.

Do not infer parity gaps from names alone, and do not broaden this fork into unrelated desktop redesign or generalized cross-platform work.

### R2. Applicable fork behavior

Implement every applicable, missing fork-specific behavior identified by R1, preserving the desktop architecture and upstream behavior. At minimum, the inventory must explicitly assess:

- player stream-derived technical details and its setting;
- RPDB custom-key local profile behavior;
- Continue Watching episode-artwork resolution and priority;
- Dolby Vision/libdovi and long-playback pipeline protections where the desktop player path can actually use them;
- release/update routing needed for a personal fork.

Android-only APK identity, Android decoder settings, Android package installation, and mobile-only UI are excluded unless a directly equivalent Windows requirement is proven.

The completed inventory established these required Windows gaps:

1. Add the persisted inline, stream-derived playback details row, sourcing codec, dimensions, dynamic range and frame rate from the native desktop player's actual media properties.
2. Add the Android fork's dedicated RPDB integration: a profile-scoped enabled state, the public `t0-free-rpdb` default, an optional profile-scoped custom API key that remains local-only, a dedicated Integrations settings page, RPDB poster URL resolution, and source-poster fallback without changing episode-thumbnail priority.
3. Preserve episode-thumbnail priority while unwrapping a valid HTTP(S) fallback from failed `meta.remaxku.eu/poster-cache/` artwork URLs.
4. Coalesce same-key metadata requests and rate-limit the shared metadata network boundary to two starts per second so Continue Watching enrichment cannot overwhelm the provider path.
5. Add the Android fork's native authenticated Trakt collection catalogue sources and website-compatible serialization.
6. Add the collection-management action that rebuilds collection-backed Home rows in current collection order, removes stale collection rows, preserves unrelated row state, persists/synchronizes once, and requires confirmation.
7. Route desktop updates to `Darkaxt/NuvioDesktopDV` and give the Windows package a distinct NuvioDV product name and MSI upgrade identity so it cannot replace the upstream Nuvio installation.

The Android ARM64 AAR, Android FDSAN handling, Android decoder settings and MediaCodec-specific patches are not directly portable to Windows. The bundled Windows player contains libmpv, libplacebo and Dolby Vision/RPU code, but binary strings alone do not establish its libplacebo revision, libdovi integration, or inclusion of the fork's required pink-output fix. The Windows runtime must therefore receive a reproducible provenance check and be replaced when that check cannot prove the required behavior.

### R3. Fork identity and updates

The installed personal build must not silently replace or update from an upstream build with different fork behavior. Any identity or updater adjustment must be the narrowest change needed for a coherent personal fork and must preserve user data unless the platform packaging system requires otherwise.

### R4. Verification

Run focused automated checks for the changed behavior and the repository's applicable Windows release verification. Every Gradle invocation must use the shared Gradle build gate and the required low-memory profile. Inspect actual results and record any limitation.

### R5. Delivery

Commit and push the verified implementation to `Darkaxt/NuvioDesktopDV:Dev`. Publish one normal, non-draft, non-prerelease Windows release from the exact verified commit, independently inspect the downloaded installer, install it locally, and launch only to the login boundary. Do not enter credentials or claim authenticated behavior.

### R6. Launch-background gradient quality

Prevent visible color banding in the generated profile mesh used by the launch and profile-selection backgrounds. Preserve the existing mesh geometry and colors, add no compressed texture dependency, and avoid temporal noise or per-frame work. Verify the dither distribution, the packaged Windows result, and the locally installed launch surface.

### R7. Dedicated RPDB integration

Desktop must expose RPDB as its own integration rather than treating the generic custom-poster URL editor as equivalent. The custom API key must use profile-scoped local storage and must never enter provider-credential or profile-settings cloud payloads. Enabling RPDB without a custom key must use the public key. Portrait artwork must prefer the RPDB URL only for supported movie/series IDs, retain the original source poster as an image-error fallback, and preserve Continue Watching's episode-thumbnail priority.

### R8. Windows Dolby Vision player provenance

Establish the exact source/build provenance of the bundled Windows `libmpv-2.dll`, including the relevant FFmpeg, libplacebo and libdovi state. The delivered runtime must support Dolby Vision base-layer/RPU playback through the Windows libmpv path and include a libplacebo revision containing the known pink-output correction. Android-only MediaCodec/P010 patches and Dolby Vision Profile 7 FEL enhancement-layer playback remain out of scope. If the existing binary cannot be proven to satisfy these requirements, replace it with a reproducible Windows build that does.

### R9. Upstream synchronization and SIMKL release configuration

Synchronize the fork with the current `NuvioMedia/NuvioDesktop:Dev` history while preserving the verified NuvioDV identity, updater routing, RPDB behavior, player features, and pinned Windows libmpv provenance. The Windows release configuration must require a non-empty `SIMKL_CLIENT_ID`; absence of that property must fail the release before packaging. The packaged and installed application must contain the same non-empty public SIMKL client identifier as the corresponding upstream release without exposing its value in logs or committed source. Advance the fork version from the current upstream base version, publish one normal non-draft/non-prerelease release from the exact verified commit, and upgrade the local NuvioDV installation while preserving user profiles.

### R10. Native Windows maximized frame geometry

When maximized on Windows, NuvioDV must fill the current monitor's work area with no exposed or clipped resize-frame strip on any edge. The invisible native resize frame must extend outside the work area while visible content and chrome remain flush with it. Preserve the taskbar reservation, per-monitor placement, mixed-DPI behavior, restore behavior, and native Windows caption controls. Verify the geometry at the installed application's real Windows/DWM boundary and compare the visible result with the supplied maximized-window reference.

## Staged plan and reconciliation ledger

### 2026-11-11 scheduled Desktop maintenance

The current stage is the Desktop stage in the cross-repository ledger at
`D:\Projects\mpv\DOCS\NUVIODV_WINDOWS_RUNTIME_SPEC.md`; no separate concurrent
implementation stage is opened here. Integrate inspected upstream `37ce07d35`
(62 commits), preserve R3/R7-R10 and all delivered fork behavior, publish a normal
Windows-only release, independently verify its MSI/configuration and upgrade the
local installation through the elevation broker with profiles preserved.

Source reconciliation: the single Bulgarian resource conflict resolves to
upstream only after XML key comparison proves every fork key remains (34 added).
Upstream's AutoSync/auth/player changes are integrated. The Trakt catalogue test
recorder receives the same Mutex correction established by Android's Linux suite.
Desktop already retains correct post-credits targets and realistic legacy-progress
fixtures. Updater, RPDB, About version, banding and maximized-window sources remain
connected; the DLL remains SHA-256 `04e4d27dc385dc18ddd93fbe69c75e635b864f3c6ec53a9122df54c2f7842f4a`.
The upstream base stays 0.1.35; advance to `0.1.35-nuviodv.5` / code `41`.
All required source paths are present with no unresolved merge or known source gap.
Source is ready for validation, not verified or delivered. Run the fork contracts,
full desktop tests and Windows release MSI package under the managed Gradle gate.
Use existing local toolchain bindings, two workers/no parallel projects, initial
3 GiB Gradle/Kotlin heaps and one test fork. Final delivery uses the exact verified
public Windows MSI; native Windows mpv promotion and AnimeJaNai/SVP remain separate.

Local validation passed: readiness receipt `c226ceb54996f66cdbdad30ea40057b9`,
workflow `57e876d77864bd2beb852e4bc2a68e06` completed full desktop tests and
`:composeApp:packageReleaseMsi` with native exit zero and verified whole-tree exit.
The shared Gradle gate retains two workers/no parallel projects, initial 3 GiB
Gradle/Kotlin heaps and one test fork; no memory failure occurred. The existing
WebView2 SDK `1.0.4258.31` was used locally; final CI retains pinned `1.0.4078.44`.
Read-only MSI table/cabinet inspection verifies product `NuvioDV`, MSI `1.1.3505`,
upgrade identity `9710d835-a9d6-5e42-a15f-6ff37d179456`, actual desktop version/code,
unchanged provider-client hashes, updater/RPDB and exact libmpv pin. Local candidate
SHA-256 is `97e0d7dfead0466a8f047505e917f83cff82f275b0b7e56b7926af4d0c7d4aa0`.
Normal CI publication, independent public MSI verification and supported local
upgrade remain required; this local candidate is not the installation source.

External delivery blocker: original Windows-only publish run `43096139223`
targets verified source `868730fb1736b184768a02952667a6514ad93701`. It failed
internally before any job around GitHub incident `dvy0t3mvh1lg`; the supported
rerun was accepted at 20:39 UTC. After GitHub reported full backlog recovery at
21:21 UTC, its run API still says queued / attempt 1 / no jobs, while its attempt
API says completed / startup_failure. Cancellation was refused because GitHub
considers the run completed. No build was interrupted and no duplicate dispatched.
There is no `.5` public release or MSI to verify/install, so delivery is BLOCKED,
not complete. Local installation stays `0.1.35-nuviodv.4` / code 40 / MSI 1.1.3504.
Readiness and successful local verification remain valid for unchanged application
code; documentation-only evidence commits after the dispatched source do not alter
the pending build. Resume by inspecting the same run/attempt until consistent,
use supported rerun only when terminal, then wait for actual workflow completion,
verify normal `.5` public release/tag/source and download its exact MSI/checksum.
Independently inspect the package/configuration, install through the elevation
broker and verify installed/public JAR equality, version and preserved profiles.
Exact pending recovery inputs are retained at
`E:\Temp\nuviodv-maintenance-20261111-recovery`: the verified installed-baseline
hash report and read-only MSI/JAR, installer and installed-package helpers.
They contain no secret values. Remove this exact recovery folder transactionally
after the original Desktop delivery consumer finishes. All bulk downloads and
local preflight MSI copies from the completed checks are expendable.

Recovery (2026-11-12): run `43096139223` stayed a wedged phantom (queued in the run
API, startup_failure in the attempt API; cancel/force-cancel return HTTP 409) and is
left alone; it does not block new runs. A dispatch from Dev (`43209887104`) correctly
failed the metadata guard because documentation commits follow the version bump.
Branch `release/0.1.35-nuviodv.5` was created at exactly `868730fb1` and publish run
`43210104541` (`mode=publish`, `target=windows`) succeeded. Public release
`0.1.35-nuviodv.5` (published 2026-11-12T11:40:19Z, not draft/prerelease) has tag ->
`868730fb1736b184768a02952667a6514ad93701` and Windows-only assets
`NuvioDV-Windows-x64-0.1.35-nuviodv.5.msi` (SHA-256
`572ea3cdde6f44164004b1c2e34ed2b2e498bbcafd2d71f106caccccb57f8c84`, matches
`SHA256SUMS.txt` and the GitHub asset digest) plus `SHA256SUMS.txt`. Independent
public-MSI inspection passed: product `NuvioDV`, MSI `1.1.3505`, the upgrade code
above, desktop `0.1.35-nuviodv.5` / code 41, provider-client hashes equal to the
installed baseline, updater `Darkaxt/NuvioDesktopDV`, RPDB present, JAR SHA-256
`29f7ccea…aff7`, libmpv SHA-256 `04e4d27d…2f4a`. The release branch was deleted.
Local installation COMPLETE (2026-11-12): two earlier broker requests ended
`approval_timeout` with nothing launched because the approval dialog was not shown
(placement bug, fixed in LLM Coordinator). After the Coordinator update, the user
accepted one broker request: msiexec exit 0, no reboot. Installed-vs-public
verification passed: installed JAR SHA-256 equals the public MSI JAR
(`29f7ccea…aff7`), desktop `0.1.35-nuviodv.5` / code 41, MSI `1.1.3505`, product
`{C3313280-A0ED-3929-A178-73924104C7D7}`, provider-client hashes unchanged, and
both existing profile directories preserved. Desktop `.5` delivery is complete.

| Stage | Status | Requirements | Acceptance evidence | Blockers | Tracked deferrals |
|---|---|---|---|---|---|
| 1. Corrected parity inventory | COMPLETE | R1, inventory portion of R2, R8 provenance decision | Exact-commit source comparison established that Desktop's generic poster editor is not the Android fork's RPDB integration; binary inspection established only unproven DV/RPU capability and therefore requires source/build provenance before acceptance | None | None |
| 2. Initial seven-gap implementation | COMPLETE | Initial non-RPDB portions of R2, R3 | The first implementation integrated the stream-details, Continue Watching, metadata, Trakt, Home-layout, identity and updater changes and passed its focused checks; it is retained as prior work but is not final parity evidence | None | None |
| 3. Initial delivery | COMPLETE | Historical delivery evidence only | Published normal release `0.1.32-nuviodv.1` from commit `2d6ced41`, independently retrieved and package-verified the MSI, installed the matching launcher and application JAR, and launched the responsive NuvioDV window without entering credentials; this release is superseded because it lacks R7/R8 and R6 | None | None |
| 4. Corrected parity implementation and replacement delivery | COMPLETE | R2, R4, R5, R6, R7, R8 | Commit `3cc7e7bd` integrates the dedicated local-only RPDB path, stream-derived player details, Continue Watching fallback/priority behavior, bounded metadata requests, Trakt collection sources, Home-layout rebuild, fork identity/update routing, pinned Windows libmpv `v0.41.0-1227-ge471496c5` with the required libplacebo/DV provenance, and deterministic overlay dithering. Focused checks and the gated Windows release build passed. GitHub Actions run `40114679682` built the final MSI; normal release `0.1.32-nuviodv.3` publishes the exact commit and MSI SHA-256 `6757ddef72e8f6b64103a49d61abde78191f808a0905aeadc3d9157e33605c76`. An independent public download verified version/configuration/RPDB/updater/runtime contents, MSI version `1.1.3203`, product and upgrade identities, and the pinned libmpv hash. The exact public package was installed through the persistent elevation broker; registry/product identity, one active application JAR, byte equality with the public package, responsive launch, preserved profiles, and the de-banded profile-selection surface were verified without entering credentials. | None | None |
| 5. Upstream synchronization, SIMKL repair, and replacement delivery | COMPLETE | R3, R4, R5, R9 | Commit `706e0289` contains the upstream `0.1.34` integration and release-path repairs while preserving the NuvioDV identity, RPDB/player behavior, fork updater target, and pinned libmpv. Focused contracts, the full desktop test evidence, and the gated Windows package build passed without memory failure. Windows-only workflow run `41724530160` published normal release `0.1.34-nuviodv.1` from the exact commit; macOS and Linux were skipped. The independently downloaded MSI SHA-256 `c7ab48f07456fffba6ba37d477c30fa01fceda81992478831daa2d8eb1017d13` matches both GitHub's asset digest and `SHA256SUMS.txt`. Its application JAR contains the verified non-empty upstream SIMKL identifier hash, targets `Darkaxt/NuvioDesktopDV`, and embeds pinned libmpv SHA-256 `04e4d27dc385dc18ddd93fbe69c75e635b864f3c6ec53a9122df54c2f7842f4a`. The exact public MSI was installed through the persistent elevation broker; registry version `1.1.3401`, installed/public JAR byte equality, packaged configuration, and a responsive `NuvioDV` window were verified without entering credentials. | None | None |
| 6. Current upstream synchronization and Windows delivery | COMPLETE | R3, R4, R5, R7, R8, R9 plus the desktop-version footer correction | Merge commit `7c4d426b` integrates upstream `a021aae1`, preserves the NuvioDV fork features and pinned Windows libmpv, advances the release to `0.1.35-nuviodv.1`, and retains the desktop-specific About version source. Both focused fork contracts and the gated full desktop test plus Windows MSI build passed with the required two-worker, non-parallel, 3 GB Gradle/Kotlin profile and no memory failure. Windows-only workflow `42095526359` skipped every macOS/Linux job and published normal release `0.1.35-nuviodv.1` from the exact merge commit. Independent public download verified the MSI/checksum digest `c65291d38dcc9965fa85e7c90c90007bcc4e9f2f0a04eb3d8a035506457b2de4`, packaged version `0.1.35-nuviodv.1` / code `37`, non-empty SIMKL and MDBList client identifiers, updater routing to `Darkaxt/NuvioDesktopDV`, RPDB public-key behavior, and pinned libmpv hash `04e4d27dc385dc18ddd93fbe69c75e635b864f3c6ec53a9122df54c2f7842f4a`. Its local-delivery criterion is satisfied by the verified newer replacement in stage 7 rather than by installing the obsolete `.1` package. | None | None |
| 7. Post-release upstream replacement sync | COMPLETE | R3, R4, R5, R7, R8, R9 | Merge commit `1330a82b` integrates current upstream `a81a207b`, preserves fork behavior, advances to `0.1.35-nuviodv.2` / code `38`, and passes the focused fork contracts plus the gated footer test and Windows MSI package build with the required two-worker, non-parallel, 3 GB Gradle/Kotlin profile and no memory failure. Windows-only workflow `42100075484` skipped every macOS/Linux job and published normal release `0.1.35-nuviodv.2` from that exact commit. Independent public download verified the MSI/checksum digest `48c501c1ec875805eb28cc5c22ab56920b1c30327f3f9d1b23e5b6c979f1b32e`, packaged version/code, non-empty SIMKL and MDBList identifiers, updater routing to `Darkaxt/NuvioDesktopDV`, RPDB public-key behavior, and pinned libmpv hash `04e4d27dc385dc18ddd93fbe69c75e635b864f3c6ec53a9122df54c2f7842f4a`. Fresh elevation-broker request `bd3b4c1f3365404fad5b085e755fa862` installed the verified public MSI successfully. Post-install verification confirmed application version `0.1.35-nuviodv.2` / code `38`, MSI and executable version `1.1.3502`, updater routing to `Darkaxt/NuvioDesktopDV`, non-empty SIMKL and MDBList identifiers, and preserved pre-existing Local and Roaming NuvioDV profile directories. | None | None |
| 8. Windows maximized-frame repair and delivery | COMPLETE | R3, R4, R5, R10 | The installed `.2` build reproduced an 11/11/12/11 px DWM inset at 150% DPI. Commit `6e942e5d1` corrects outer maximized bounds and adds geometry regression tests; merge `2c4d3523e` integrates upstream `00e50ef2c`, and `649342d06` restores upstream post-credits classification. Focused contracts and the full gated desktop tests/MSI package passed with two workers, no parallel projects, 3 GB Gradle/Kotlin heaps, one test fork, verified daemon exit and no memory failure. Final version commit `f7f806942` was published by Windows-only workflow `42495538879` as normal release `0.1.35-nuviodv.4` / code `40`; the first `.3` dispatch correctly rejected an application change after its version bump and published nothing. Public MSI SHA-256 `5a93e5b39b121afedb2a7d35d1fbf3776a30d3b4ddb2cc29a0d80d51480d0a3d` matches GitHub and SHA256SUMS; packaged updater, SIMKL/MDBList, RPDB and pinned libmpv were verified. Broker request `4006d6b4c3c34b76b488a95fc2a75924` installed MSI `1.1.3504`; installed/public application JAR hashes match and profiles remain present. Live installed geometry is outer rect `-11,-11..5131,2099`, DWM rect `0,0..5120,2088`, with zero visible gaps on every edge. The owner confirmed the maximized window works. | None | None |

Only one stage may be ACTIVE. A stage closes only when all its acceptance criteria have fresh applicable evidence. Overall completion requires zero blockers and zero tracked deferrals.
