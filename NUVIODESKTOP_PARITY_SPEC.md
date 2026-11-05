# NuvioDesktop Personal Fork Parity Specification

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

## Staged plan and reconciliation ledger

| Stage | Status | Requirements | Acceptance evidence | Blockers | Tracked deferrals |
|---|---|---|---|---|---|
| 1. Corrected parity inventory | COMPLETE | R1, inventory portion of R2, R8 provenance decision | Exact-commit source comparison established that Desktop's generic poster editor is not the Android fork's RPDB integration; binary inspection established only unproven DV/RPU capability and therefore requires source/build provenance before acceptance | None | None |
| 2. Initial seven-gap implementation | COMPLETE | Initial non-RPDB portions of R2, R3 | The first implementation integrated the stream-details, Continue Watching, metadata, Trakt, Home-layout, identity and updater changes and passed its focused checks; it is retained as prior work but is not final parity evidence | None | None |
| 3. Initial delivery | COMPLETE | Historical delivery evidence only | Published normal release `0.1.32-nuviodv.1` from commit `2d6ced41`, independently retrieved and package-verified the MSI, installed the matching launcher and application JAR, and launched the responsive NuvioDV window without entering credentials; this release is superseded because it lacks R7/R8 and R6 | None | None |
| 4. Corrected parity implementation and replacement delivery | COMPLETE | R2, R4, R5, R6, R7, R8 | Commit `3cc7e7bd` integrates the dedicated local-only RPDB path, stream-derived player details, Continue Watching fallback/priority behavior, bounded metadata requests, Trakt collection sources, Home-layout rebuild, fork identity/update routing, pinned Windows libmpv `v0.41.0-1227-ge471496c5` with the required libplacebo/DV provenance, and deterministic overlay dithering. Focused checks and the gated Windows release build passed. GitHub Actions run `40114679682` built the final MSI; normal release `0.1.32-nuviodv.3` publishes the exact commit and MSI SHA-256 `6757ddef72e8f6b64103a49d61abde78191f808a0905aeadc3d9157e33605c76`. An independent public download verified version/configuration/RPDB/updater/runtime contents, MSI version `1.1.3203`, product and upgrade identities, and the pinned libmpv hash. The exact public package was installed through the persistent elevation broker; registry/product identity, one active application JAR, byte equality with the public package, responsive launch, preserved profiles, and the de-banded profile-selection surface were verified without entering credentials. | None | None |
| 5. Upstream synchronization, SIMKL repair, and replacement delivery | COMPLETE | R3, R4, R5, R9 | Commit `706e0289` contains the upstream `0.1.34` integration and release-path repairs while preserving the NuvioDV identity, RPDB/player behavior, fork updater target, and pinned libmpv. Focused contracts, the full desktop test evidence, and the gated Windows package build passed without memory failure. Windows-only workflow run `41724530160` published normal release `0.1.34-nuviodv.1` from the exact commit; macOS and Linux were skipped. The independently downloaded MSI SHA-256 `c7ab48f07456fffba6ba37d477c30fa01fceda81992478831daa2d8eb1017d13` matches both GitHub's asset digest and `SHA256SUMS.txt`. Its application JAR contains the verified non-empty upstream SIMKL identifier hash, targets `Darkaxt/NuvioDesktopDV`, and embeds pinned libmpv SHA-256 `04e4d27dc385dc18ddd93fbe69c75e635b864f3c6ec53a9122df54c2f7842f4a`. The exact public MSI was installed through the persistent elevation broker; registry version `1.1.3401`, installed/public JAR byte equality, packaged configuration, and a responsive `NuvioDV` window were verified without entering credentials. | None | None |
| 6. Current upstream synchronization and Windows delivery | COMPLETE | R3, R4, R5, R7, R8, R9 plus the desktop-version footer correction | Merge commit `7c4d426b` integrates upstream `a021aae1`, preserves the NuvioDV fork features and pinned Windows libmpv, advances the release to `0.1.35-nuviodv.1`, and retains the desktop-specific About version source. Both focused fork contracts and the gated full desktop test plus Windows MSI build passed with the required two-worker, non-parallel, 3 GB Gradle/Kotlin profile and no memory failure. Windows-only workflow `42095526359` skipped every macOS/Linux job and published normal release `0.1.35-nuviodv.1` from the exact merge commit. Independent public download verified the MSI/checksum digest `c65291d38dcc9965fa85e7c90c90007bcc4e9f2f0a04eb3d8a035506457b2de4`, packaged version `0.1.35-nuviodv.1` / code `37`, non-empty SIMKL and MDBList client identifiers, updater routing to `Darkaxt/NuvioDesktopDV`, RPDB public-key behavior, and pinned libmpv hash `04e4d27dc385dc18ddd93fbe69c75e635b864f3c6ec53a9122df54c2f7842f4a`. Its local-delivery criterion is satisfied by the verified newer replacement in stage 7 rather than by installing the obsolete `.1` package. | None | None |
| 7. Post-release upstream replacement sync | COMPLETE | R3, R4, R5, R7, R8, R9 | Merge commit `1330a82b` integrates current upstream `a81a207b`, preserves fork behavior, advances to `0.1.35-nuviodv.2` / code `38`, and passes the focused fork contracts plus the gated footer test and Windows MSI package build with the required two-worker, non-parallel, 3 GB Gradle/Kotlin profile and no memory failure. Windows-only workflow `42100075484` skipped every macOS/Linux job and published normal release `0.1.35-nuviodv.2` from that exact commit. Independent public download verified the MSI/checksum digest `48c501c1ec875805eb28cc5c22ab56920b1c30327f3f9d1b23e5b6c979f1b32e`, packaged version/code, non-empty SIMKL and MDBList identifiers, updater routing to `Darkaxt/NuvioDesktopDV`, RPDB public-key behavior, and pinned libmpv hash `04e4d27dc385dc18ddd93fbe69c75e635b864f3c6ec53a9122df54c2f7842f4a`. Fresh elevation-broker request `bd3b4c1f3365404fad5b085e755fa862` installed the verified public MSI successfully. Post-install verification confirmed application version `0.1.35-nuviodv.2` / code `38`, MSI and executable version `1.1.3502`, updater routing to `Darkaxt/NuvioDesktopDV`, non-empty SIMKL and MDBList identifiers, and preserved pre-existing Local and Roaming NuvioDV profile directories. | None | None |

Only one stage may be ACTIVE. A stage closes only when all its acceptance criteria have fresh applicable evidence. Overall completion requires zero blockers and zero tracked deferrals.
