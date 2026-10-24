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
2. Keep the profile-scoped custom-poster/RPDB URL pattern local-only: cloud export must not contain it and cloud application must not overwrite the local value.
3. Preserve episode-thumbnail priority while unwrapping a valid HTTP(S) fallback from failed `meta.remaxku.eu/poster-cache/` artwork URLs.
4. Coalesce same-key metadata requests and rate-limit the shared metadata network boundary to two starts per second so Continue Watching enrichment cannot overwhelm the provider path.
5. Add the Android fork's native authenticated Trakt collection catalogue sources and website-compatible serialization.
6. Add the collection-management action that rebuilds collection-backed Home rows in current collection order, removes stale collection rows, preserves unrelated row state, persists/synchronizes once, and requires confirmation.
7. Route desktop updates to `Darkaxt/NuvioDesktop` and give the Windows package a distinct NuvioDV product name and MSI upgrade identity so it cannot replace the upstream Nuvio installation.

The inventory also established that the Android ARM64 AAR, Android FDSAN handling, Android decoder settings and Android-specific libplacebo patch are not portable Windows parity work. The bundled Windows player is already libmpv/Dolby-Vision aware; no Windows pink-screen reproduction or evidence supports changing that runtime in this stage.

### R3. Fork identity and updates

The installed personal build must not silently replace or update from an upstream build with different fork behavior. Any identity or updater adjustment must be the narrowest change needed for a coherent personal fork and must preserve user data unless the platform packaging system requires otherwise.

### R4. Verification

Run focused automated checks for the changed behavior and the repository's applicable Windows release verification. Every Gradle invocation must use the shared Gradle build gate and the required low-memory profile. Inspect actual results and record any limitation.

### R5. Delivery

Commit and push the verified implementation to `Darkaxt/NuvioDesktop:Dev`. Publish one normal, non-draft, non-prerelease Windows release from the exact verified commit, independently inspect the downloaded installer, install it locally, and launch only to the login boundary. Do not enter credentials or claim authenticated behavior.

## Staged plan and reconciliation ledger

| Stage | Status | Requirements | Acceptance evidence | Blockers | Tracked deferrals |
|---|---|---|---|---|---|
| 1. Parity inventory | COMPLETE | R1, inventory portion of R2 | Exact-commit source comparison and independent symbol/commit reconciliation classified the seven Windows gaps and Android-only exclusions above | None | None |
| 2. Integrated parity implementation | COMPLETE | R2, R3 | All seven applicable gaps compile together; the focused desktop parity tests passed through the shared Gradle gate with the required constrained profile | None | None |
| 3. Final verification and delivery | ACTIVE | R4, R5 | Recover upstream's public runtime client configuration without disclosure, build and inspect the MSI, commit/push, publish and independently retrieve the exact release, install it locally, and launch to the login boundary | None | None |

Only one stage may be ACTIVE. A stage closes only when all its acceptance criteria have fresh applicable evidence. Overall completion requires zero blockers and zero tracked deferrals.
