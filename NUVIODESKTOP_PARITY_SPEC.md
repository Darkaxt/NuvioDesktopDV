# NuvioDesktop Personal Fork Parity Specification

## Authority and scope

The user authorized an end-to-end personal fork of `NuvioMedia/NuvioDesktop`, a bounded comparison with the verified NuvioDV Android fork, implementation of missing applicable features, publication through the fork's normal release channel, local Windows installation, and launch to the login boundary.

The comparison baseline is:

- Android fork: `Darkaxt/NuvioMobile:cmp-rewrite` at `65c6a10c03e16d6198e67fd43edd899e80c5ffca`.
- Desktop upstream/fork baseline: `NuvioMedia/NuvioDesktop:Dev` / `Darkaxt/NuvioDesktop:Dev` at `897efe93`.

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
7. Route desktop updates to `Darkaxt/NuvioDesktop` and give the Windows package a distinct NuvioDV product name and MSI upgrade identity so it cannot replace the upstream Nuvio installation.

The Android ARM64 AAR, Android FDSAN handling, Android decoder settings and MediaCodec-specific patches are not directly portable to Windows. The bundled Windows player contains libmpv, libplacebo and Dolby Vision/RPU code, but binary strings alone do not establish its libplacebo revision, libdovi integration, or inclusion of the fork's required pink-output fix. The Windows runtime must therefore receive a reproducible provenance check and be replaced when that check cannot prove the required behavior.

### R3. Fork identity and updates

The installed personal build must not silently replace or update from an upstream build with different fork behavior. Any identity or updater adjustment must be the narrowest change needed for a coherent personal fork and must preserve user data unless the platform packaging system requires otherwise.

### R4. Verification

Run focused automated checks for the changed behavior and the repository's applicable Windows release verification. Every Gradle invocation must use the shared Gradle build gate and the required low-memory profile. Inspect actual results and record any limitation.

### R5. Delivery

Commit and push the verified implementation to `Darkaxt/NuvioDesktop:Dev`. Publish one normal, non-draft, non-prerelease Windows release from the exact verified commit, independently inspect the downloaded installer, install it locally, and launch only to the login boundary. Do not enter credentials or claim authenticated behavior.

### R6. Launch-background gradient quality

Prevent visible color banding in the generated profile mesh used by the launch and profile-selection backgrounds. Preserve the existing mesh geometry and colors, add no compressed texture dependency, and avoid temporal noise or per-frame work. Verify the dither distribution, the packaged Windows result, and the locally installed launch surface.

### R7. Dedicated RPDB integration

Desktop must expose RPDB as its own integration rather than treating the generic custom-poster URL editor as equivalent. The custom API key must use profile-scoped local storage and must never enter provider-credential or profile-settings cloud payloads. Enabling RPDB without a custom key must use the public key. Portrait artwork must prefer the RPDB URL only for supported movie/series IDs, retain the original source poster as an image-error fallback, and preserve Continue Watching's episode-thumbnail priority.

### R8. Windows Dolby Vision player provenance

Establish the exact source/build provenance of the bundled Windows `libmpv-2.dll`, including the relevant FFmpeg, libplacebo and libdovi state. The delivered runtime must support Dolby Vision base-layer/RPU playback through the Windows libmpv path and include a libplacebo revision containing the known pink-output correction. Android-only MediaCodec/P010 patches and Dolby Vision Profile 7 FEL enhancement-layer playback remain out of scope. If the existing binary cannot be proven to satisfy these requirements, replace it with a reproducible Windows build that does.

## Staged plan and reconciliation ledger

| Stage | Status | Requirements | Acceptance evidence | Blockers | Tracked deferrals |
|---|---|---|---|---|---|
| 1. Corrected parity inventory | COMPLETE | R1, inventory portion of R2, R8 provenance decision | Exact-commit source comparison established that Desktop's generic poster editor is not the Android fork's RPDB integration; binary inspection established only unproven DV/RPU capability and therefore requires source/build provenance before acceptance | None | None |
| 2. Initial seven-gap implementation | COMPLETE | Initial non-RPDB portions of R2, R3 | The first implementation integrated the stream-details, Continue Watching, metadata, Trakt, Home-layout, identity and updater changes and passed its focused checks; it is retained as prior work but is not final parity evidence | None | None |
| 3. Initial delivery | COMPLETE | Historical delivery evidence only | Published normal release `0.1.32-nuviodv.1` from commit `2d6ced41`, independently retrieved and package-verified the MSI, installed the matching launcher and application JAR, and launched the responsive NuvioDV window without entering credentials; this release is superseded because it lacks R7/R8 and R6 | None | None |
| 4. Corrected parity implementation and replacement delivery | ACTIVE | R2, R4, R5, R6, R7, R8 | Implement and verify dedicated RPDB behavior, prove or replace the Windows DV runtime, implement deterministic launch-background de-banding, run focused and release verification, publish one replacement normal release, independently inspect it, install it, and verify the launch boundary without entering credentials | None | None |

Only one stage may be ACTIVE. A stage closes only when all its acceptance criteria have fresh applicable evidence. Overall completion requires zero blockers and zero tracked deferrals.
