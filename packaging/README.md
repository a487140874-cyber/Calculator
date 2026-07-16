# Native installers

The native installers bundle Java and JavaFX, so end users do not install Java or Maven.
Packages must be built on their target operating system because `jpackage` does not
cross-compile native installers.

## macOS ARM64

Run on an Apple Silicon Mac:

```sh
sh packaging/package-macos-arm64.sh
```

Output: `dist/macos-arm64/Calculator-1.0.0.dmg`

The local package is unsigned. macOS Gatekeeper may ask the user to confirm opening it.
Public distribution requires an Apple Developer ID signature and notarization.

## Windows x64

Build requirements:

- Windows x64
- JDK 23 x64 (`JAVA_HOME` must point to it, used by `jpackage`)
- JDK 21 x64 (`BUILD_JAVA_HOME` should point to it, used by Maven/Lombok)
- WiX Toolset supported by that JDK's `jpackage`

Run in PowerShell:

```powershell
.\packaging\package-windows-x64.ps1
```

Output: `dist\windows-x64\Calculator-1.0.0.exe`

## 3D feature

Java and JavaFX are bundled. The current 3D renderer still requires a Python 3
installation containing NumPy and Matplotlib. Set `PYTHON_EXECUTABLE` when Python is
installed in a non-standard location. Import, calculation, Excel export, and energy-image
export do not require Python.

## CI builds

The workflow `.github/workflows/build-native-installers.yml` builds downloadable artifacts
on native macOS ARM64 and Windows x64 runners. It can also be started manually from the
GitHub Actions page after the changes are pushed to GitHub.
