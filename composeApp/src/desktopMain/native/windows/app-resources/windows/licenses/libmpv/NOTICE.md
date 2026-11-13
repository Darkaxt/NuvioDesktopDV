# NuvioDV Windows libmpv runtime

NuvioDV for Windows bundles `libmpv-2.dll` and `ajn-mvtools.dll` from the
`nuviodv-windows-ajn.1` release of the NuvioDV mpv fork.

- mpv is licensed under the GNU General Public License version 2 or later
  (`LICENSE.GPL`, `Copyright`). `ajn-mvtools.dll` licence: `licenses/ajn-mvtools`.
- Bundled dependency licences (FFmpeg, libplacebo, LuaJIT, libarchive, OpenSSL,
  and others): `dependency-licenses/`.
- Exact source revisions and the build image: `sources.json` and `manifest.json`.

Corresponding source code:

- mpv: https://github.com/Darkaxt/mpv, commit
  `a77256324b69b518cf6d03029ba8d6b1aa7e07df`
- Release with the build recipe, evidence and checksums:
  https://github.com/Darkaxt/mpv/releases/tag/nuviodv-windows-ajn.1
- Dependencies: the repositories and commits listed in `sources.json`.
