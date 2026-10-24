# NuvioDV Windows libmpv runtime

The NuvioDV Windows package pins the official x86-64 `libmpv-2.dll` from the
`shinchiro/mpv-winbuild-cmake` release below. The binary is tracked with Git LFS.

## Pinned artifact

- Release: `20261021`
- Asset: `mpv-dev-x86_64-20261021-git-e471496c56.7z`
- Archive SHA-256: `efc719eff3dc5954b4a5efc800632808bcec01cc9034940dd14e5eb7fe9ea633`
- `libmpv-2.dll` SHA-256: `04e4d27dc385dc18ddd93fbe69c75e635b864f3c6ec53a9122df54c2f7842f4a`
- Build workflow: `shinchiro/mpv-winbuild-cmake` run `39518569492`
- Build repository commit: `1f8d2e76255abc48f0456d9be99b4cab9c6566e6`
- mpv commit: `e471496c56f0f49c133bd88b50aed62b845f1909`
- FFmpeg commit: `9a861e9687d30e657d59521817cad18405995983`
- libplacebo commit at the workflow's forced update: `285d1cc8af2ab79b72ac662eb09736de8c12392b`

The workflow log records the libplacebo forced update at
`2026-10-21T00:02:03Z`. The last libplacebo commit at that point was
`285d1cc8`, which contains the `c93aa134` OpenGL sync-object leak correction.
The official Windows build removed the separate libdovi dependency in commit
`23e7ff758c26846422bb83109f03b7d692c271a0` because FFmpeg had completed RPU
parsing. The pinned DLL contains FFmpeg Dolby Vision RPU metadata handling,
libplacebo Dolby Vision reshape/rendering, and the newer Profile 7 split-layer
pipeline. NuvioDV does not claim full FEL enhancement-layer compatibility
without a real playback fixture.

## Updating the tracked DLL

After independently downloading and extracting the exact pinned asset, run:

```powershell
./scripts/install_windows_libmpv.ps1 -SourceDll <extracted-libmpv-2.dll>
```

The script rejects any DLL whose SHA-256 does not match the pinned artifact.
