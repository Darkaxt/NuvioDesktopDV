<div align="center">

  <img src="https://nuvio.tv/assets/nuvio-app-logo-wordmark.webp" alt="Nuvio" width="320" />

  <p>
    A free, open-source media app for Windows, macOS, and Linux.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/NuvioMedia/NuvioDesktop/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

## NuvioDV personal fork

This is a personal fork of [Nuvio Desktop](https://github.com/NuvioMedia/NuvioDesktop). It carries the desktop equivalents of the focused NuvioDV Android fixes: stream-derived playback details, reliable Continue Watching artwork resolution, bounded metadata requests, Trakt collection sources, collection-layout repair, and profile-local custom poster credentials.

The Windows package uses the separate **NuvioDV** product, storage, shortcut, and updater identity so it can coexist with standard Nuvio. Download this fork from [Darkaxt/NuvioDesktop releases](https://github.com/Darkaxt/NuvioDesktop/releases/latest). Fork-only patches may be retired when upstream provides equivalent fixes. Broader player expansion is outside the current scope.

## ⚠️ Alpha Software - Slow Development - Testers Only

Nuvio Desktop is currently in alpha and is intended only for testers. It is under development and is not suitable for daily use.

Expect breaking changes with every update. Features, settings, stored data, and compatibility may change or stop working without notice. Do not rely on this build as your primary media app, and report any issues you encounter during testing.

## Get Nuvio Desktop

Download the latest NuvioDV build from [the fork's GitHub Releases](https://github.com/Darkaxt/NuvioDesktop/releases/latest).

- Windows: MSI installer
- macOS: DMG installer
- Linux: DEB, RPM, Flatpak, or AppImage

## Build from source

```bash
git clone https://github.com/Darkaxt/NuvioDesktop.git
cd NuvioDesktop
```

### Run the app

```bash
./gradlew :composeApp:run
```

On Windows PowerShell:

```powershell
.\gradlew.bat :composeApp:run
```

### Package the app

Build a release package for the current host:

```bash
./gradlew :composeApp:packageReleaseDistributionForCurrentOS
```

For platform-specific packaging:

Windows PowerShell:

```powershell
.\gradlew.bat :composeApp:packageReleaseMsi --rerun-tasks
```

macOS:

```bash
./scripts/build-macos-release-dmgs.sh --package-only
```

Linux:

```bash
./gradlew :composeApp:packageReleaseDeb
```

The shared app is built with Kotlin Multiplatform and Compose Multiplatform.

## License

[GNU General Public License v3.0](./LICENSE)
