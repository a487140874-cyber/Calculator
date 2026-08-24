# Native installers

The native installers bundle Java and JavaFX, so end users do not install Java or Maven.
Packages must be built on their target operating system because `jpackage` does not
cross-compile native installers.

## macOS ARM64

Build requirements:

- Apple Silicon Mac (the script refuses to run elsewhere)
- A JDK 21+ containing `jpackage`. The script auto-detects it via `/usr/libexec/java_home`
  (prefers 23, falls back to 21). Override with `JAVA_HOME=/path/to/jdk` when needed.
- Maven is **not** required; the bundled `./mvnw` wrapper is used.

Run on an Apple Silicon Mac:

```sh
sh packaging/package-macos-arm64.sh
```

Output: `dist/macos-arm64/超长工作面采动覆岩承载结构能量积聚预测系统-1.0.0.dmg`

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

Output: `dist\windows-x64\超长工作面采动覆岩承载结构能量积聚预测系统-1.0.0.exe`

## 3D feature: Python environment

Java and JavaFX are bundled in the installer, but the 3D renderer shells out to Python.
Import, calculation, Excel export, and energy-image export all work **without** Python —
only the 3D image needs it.

Requirements: Python 3 with **NumPy** and **Matplotlib**.

The app probes these interpreters in order, and picks the first one that can
`import numpy, matplotlib`:

1. `$PYTHON_EXECUTABLE` (if set)
2. `python3` (from `PATH`)
3. `/opt/homebrew/bin/python3` (Homebrew, Apple Silicon)
4. `/usr/local/bin/python3` (Homebrew on Intel, or python.org)
5. `/Library/Frameworks/Python.framework/Versions/Current/bin/python3`
6. `/usr/bin/python3` (macOS system Python)
7. `python`

### macOS

```sh
brew install python
/opt/homebrew/bin/python3 -m pip install --break-system-packages numpy matplotlib
```

No Homebrew? Use the system interpreter instead:

```sh
/usr/bin/python3 -m pip install --user numpy matplotlib
```

> **Why absolute paths matter.** An `.app` launched from Finder does not inherit your
> shell `PATH` (it gets only `/usr/bin:/bin:/usr/sbin:/sbin`) and never reads
> `.zshrc`/`.bash_profile`. Installing into a shell-only Python — a pyenv shim, a conda
> env, a project venv — will therefore work in a terminal but fail inside the packaged
> app. Install into one of the absolute paths listed above, or point the app at your
> interpreter explicitly:
>
> ```sh
> launchctl setenv PYTHON_EXECUTABLE /path/to/your/python3
> ```
>
> (`launchctl setenv` lasts until logout; re-run it after a reboot.)

### Windows

```powershell
py -m pip install numpy matplotlib
```

### Verify

```sh
python3 -c "import numpy, matplotlib; print(numpy.__version__, matplotlib.__version__)"
```

## CI builds

The workflow `.github/workflows/build-native-installers.yml` builds downloadable artifacts
on native macOS ARM64 and Windows x64 runners. It can also be started manually from the
GitHub Actions page after the changes are pushed to GitHub.
