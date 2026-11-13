# NuvioDV Windows libmpv runtime

The NuvioDV Windows package pins the owned `nuviodv-windows-ajn.1` release of
[Darkaxt/mpv](https://github.com/Darkaxt/mpv/releases/tag/nuviodv-windows-ajn.1).
Both runtime files are tracked with Git LFS in
`composeApp/src/desktopMain/native/windows/runtime/`.

## Pinned artifact

- Release: `nuviodv-windows-ajn.1` (published 2026-11-12, source commit
  `a77256324b69b518cf6d03029ba8d6b1aa7e07df`)
- Asset: `nuviodv-windows-x64.zip`, SHA-256
  `0fcba959f8554024b81053fd292eb8f64133911dc392ccb70716a76e202ab44b`
- `libmpv-2.dll` SHA-256: `79ca36e40e7998e89b6e14a6ad12082816c8a8856eede0165aba58c682586779`
- `ajn-mvtools.dll` SHA-256: `6b2aab46aef61b040eac9e934b4d83b6f843a0ac58dee4fa9fdaf3b0a19b68bf`
- FFmpeg `9a861e9687d30e657d59521817cad18405995983`, libplacebo
  `9fabf6d5057c40089afb020017c6347002b07308` (contains the `c93aa134` OpenGL
  sync-object leak correction); the full lock is in the release's `sources.json`.

`composeApp/src/desktopMain/native/windows/runtime-pins.sha256` is the single
pin list. The release workflow checks the LFS files against it before building
and requires both DLLs in the packaged application; `WindowsRuntimePinTest`
checks the same list. The application never downloads or updates libmpv itself.

Dolby Vision support comes from FFmpeg (HEVC decoding and RPU metadata) and
libplacebo (reshaping and GPU-next rendering); no separate libdovi is used on
Windows. This build passed native Windows GPU-next SDR and Dolby Vision
Profile 5/8.1, AnimeJaNai and SVP qualification (see
`WINDOWS_PLAYBACK_ENHANCEMENTS_SPEC.md`). NuvioDV does not claim full Profile 7
FEL enhancement-layer compatibility without a real playback fixture.

The installed application carries mpv's licence and source notices in
`app/resources/licenses/libmpv` (from
`composeApp/src/desktopMain/native/windows/app-resources/`): `NOTICE.md` with
the corresponding-source links, `Copyright`, `LICENSE.GPL`, `manifest.json`,
`sources.json`, `licenses/` and `dependency-licenses/`. The release workflow
checks they are present in the MSI.

## Updating the pinned runtime

Update `runtime-pins.sha256` and this document for the new release, download and
independently verify its ZIP, extract it, then run with PowerShell 7:

```powershell
pwsh ./scripts/install_windows_libmpv.ps1 -SourceDir <extracted-nuviodv-windows-x64>
```

The script rejects any file whose SHA-256 does not match the pin list, then
refreshes the licence notices (`NOTICE.md` is maintained by hand).

Previous pin: the external `shinchiro/mpv-winbuild-cmake` release `20261021`
(`libmpv-2.dll` SHA-256
`04e4d27dc385dc18ddd93fbe69c75e635b864f3c6ec53a9122df54c2f7842f4a`, mpv
`e471496c56`), which had no AnimeJaNai filter.

## Owned Windows successor

The separate [Darkaxt/mpv](https://github.com/Darkaxt/mpv) fork now owns the
Windows build recipe. Its bounded Dolby Vision baseline is specified in
`DOCS/NUVIODV_WINDOWS_RUNTIME_SPEC.md` there. That native work preserves FFmpeg
RPU handling, libplacebo reshaping/GPU-next and the sync-object leak correction.
It does not copy the Android JNI wrapper or add a redundant Windows libdovi.
Current mpv requires libplacebo API 387, so the owned candidate uses a newer
immutable revision containing the correction rather than the API 377 DLL of the previous external pin.

The first normal [owned native release](https://github.com/Darkaxt/mpv/releases/tag/nuviodv-windows-dv.1)
targets source `b473072ac2e9a36d595dda9a3d071e8fccb74448` and passed Windows
GPU-next SDR and Dolby Vision Profile 5/8.1 qualification. The public ZIP was
independently verified with SHA-256
`a4ed90b0f7a890b2f8be642c00b4a34ba1b4ec8d9e1a013b836c0b368691a8e8`;
its DLL SHA-256 is `ee27b3ca2b621a23a5d16c074767ee13fd81bc2cc215bf695cc085981e4591db`.

Native artifact publication does not by itself promote a build into this app.
`WINDOWS_PLAYBACK_ENHANCEMENTS_SPEC.md`, requirement P7, assigns ownership of the
mpv source fork carrying AnimeJaNai support and upstream synchronization. A newer
qualified artifact replaces the pin above only after its Dolby Vision,
libplacebo, AnimeJaNai and SVP checks and the application integration checks pass.
