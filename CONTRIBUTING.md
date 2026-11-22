# Contributing to GODstein Modular Launcher Studio

Thank you for helping test, expand, and improve **GODstein**! This project was created by [@ferzferax](https://github.com/ferzferax) with a simple goal: letting users extract the best modular launcher features from open-source Android projects, eliminate telemetry/bloat, and assemble a clean, personalized launcher.

---

## Ground Rules

> [!IMPORTANT]
> **Attribution & Open-Source Respect:** Every module or patch added to `acervo/patches_catalog.json` **must** include proper attribution (`author`, `origin_app`, and `github_url`) linking back to the original open-source repository so users can explore and support the original creators' work.

> [!CAUTION]
> **No DRM / Paid License Bypass Contributions:** Pull requests or issues designed to bypass Google Play licensing, paid DRM, or in-app billing in third-party proprietary apps will not be accepted. Only open-source extractions, privacy/debloat filters, UI bugfixes, and modular features for the GODstein skeleton are accepted.

---

## Step-by-Step: How to Test & Contribute

### 1. Testing the APK on Your Android Device
1. Download `GODstein_Launcher.apk` from the [Releases](https://github.com/ferzferax/GODstein-Modular-Launcher/releases) page or `dist/GODstein_Launcher.apk`.
2. Install it via ADB or directly on your device (`Android 8.0+ / API 26+` tested up to `Android 16`).
3. Test the core flows:
   - 3D Fibonacci Holo Sphere rotation, inertia, and app launching.
   - Clean Bottom Dock (pin/unpin via long-press).
   - App Drawer keyboard focus (`showSoftInput`) and Auto-Launch on single match.
   - **Credits & Modules Modal** (`⚡ GODstein` chip or double-tap on empty sphere space).
4. Open a [Bug Report](.github/ISSUE_TEMPLATE/bug_report.md) if you notice rendering glitches, OEM keyboard issues, or layout problems.

### 2. Analyzing New Open-Source Launchers (`Destrinchador`)
Use the built-in analyzer to inspect any open-source launcher APK before extracting ideas or modules:

```bash
python godstein_studio.py --analyze path/to/open-source-launcher.apk
```

This generates a JSON diagnostic report inside `acervo/analyzed_apks/` detailing permissions, telemetry flags, and detected launcher capabilities.

### 3. Adding a New Module to the Patch Vault (`acervo/patches_catalog.json`)
1. Add your clean Java/Android module inside `skeleton/src/com/ferzferaz/godstein/`.
2. Register the module flag and original GitHub credit inside `acervo/patches_catalog.json`:
   ```json
   {
     "id": "your-module-id",
     "name": "Module Display Name",
     "description": "What this module does.",
     "enabled": true,
     "category": "home_layout | dock | drawer_search | gestures | privacy",
     "origin_app": "Original App Name",
     "author": "Original Author",
     "github_url": "https://github.com/owner/repo",
     "license": "MIT / GPL-3.0 / Apache-2.0",
     "morphe_compatible": true,
     "config_key": "ENABLE_YOUR_MODULE"
   }
   ```
3. Rebuild and export the updated APK + Morphe patch bundle:
   ```bash
   python godstein_studio.py --build
   ```
4. Test on device via `python godstein_studio.py --install` and submit your Pull Request!
