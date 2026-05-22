# Working conventions for agents touching `scripts/`

Short, binding rules for any agent (human or LLM) doing build / decompile /
intermediate work under `scripts/`.

## TL;DR

- **All build / decompile output goes in `scripts/.build/`** (or `scripts/.tmp/`
  for short-lived scratch).
- Both paths are **gitignored**. Anything outside them is committable by default,
  so don't leak intermediate artifacts elsewhere.
- The historical `build_v19/` at the repo root is also gitignored and existing
  scripts still use it (see "Legacy paths" below) — don't churn that yet, but
  **do not introduce new code that writes to the repo root**.

## Where intermediate artifacts go

| Kind of artifact | Path | Why |
|---|---|---|
| Disassembled APK trees (apktool output) | `scripts/.build/<apk-name>/` | Large, regeneratable, never commit. |
| Reassembled / aligned / signed APKs (intermediates) | `scripts/.build/*.apk` | Validated builds are promoted to a milestone folder; intermediates stay here. |
| Pulled binaries from the car for analysis (e.g. `AndroidAutoService_pulled.apk`) | `scripts/.build/` | Same — large, derived from device state. |
| Short-lived scratch (logs, parsed dumps, screencaps) | `scripts/.tmp/` | Same idea, even more disposable. |
| Downloaded vendor tools (apktool, dexer, etc.) | `tools/` (repo root) | Already gitignored. Keep there — many docs reference it. |

If you find yourself wanting to write somewhere outside these three roots,
that's a smell — pause and ask whether the file should be a tracked artifact
(commit it to its proper home) or a temp file (route it through `.build/` /
`.tmp/`).

## What goes in `scripts/milestones/`

Only **validated, releasable** artifacts. A milestone folder is a tagged
state of "this APK was tested on the car and works." Specifically:

- The **signed** APK (no unsigned/aligned-only intermediates)
- A **snapshot** of the `patch_logic.py` (or equivalent) that produced it
- The matching `deploy_to_car.py` (or `.ps1`) script
- A `README.md` documenting the patch layers, MD5, build commands

The build pipeline that *produced* the milestone APK runs in `scripts/.build/`.
Once the result is signed and validated, copy the signed APK and the patch
script into the milestone folder.

## What goes in `app/src/main/assets/aa_patches/` and `app/src/main/assets/carplay_patches/`

The signed, validated APKs that the Impulse app will bundle and bind-mount on
the car. These ARE committed (yes, binary APKs). Treat them as the
release-channel artifact for end users. Updating these requires:

1. Building a new patched APK in `scripts/.build/`
2. Signing it
3. Validating on the car (don't ship without on-vehicle test)
4. Copying the signed APK to the assets folder
5. Rebuilding the Impulse app

## Legacy paths (don't churn for now)

The `aa-patches` workflow currently uses `build_v19/` at the repo root. Many
docs and milestone READMEs reference it. We're leaving this as-is to avoid
churning a working pipeline — but **new** scripts, new patch efforts, and any
ad-hoc one-off work should use `scripts/.build/`. If you're refactoring the
aa-patches pipeline anyway, migrating `build_v19/` → `scripts/.build/aa/` is
welcome but make sure to update every README and milestone snapshot at the
same time.

## Cleanup on the way out

When you finish a piece of work:

1. **Verify gitignore is doing its job.** Run `git status --short`. Any
   untracked file under `scripts/.build/`, `scripts/.tmp/`, `tools/`,
   `build_*/` should NOT appear. If it does, fix the gitignore before
   committing.
2. **Wipe stale intermediates.** Remove any APK in `scripts/.build/` that
   isn't the latest. The Windows command is `Remove-Item scripts/.build/*.apk`
   (skip the ones you still need). On Bash / WSL: `rm scripts/.build/*.apk`.
3. **Clean `$env:TEMP` files** you created (logcat dumps, dumpsys captures,
   screencaps). Common patterns: `car_log*.txt`, `sf_*.txt`, `top_*.txt`,
   `ps_*.txt`, `cluster*.png`, `bundled_*.apk`, `extracted_*.apk`.

## When pushing to git

- **Stage selectively.** `git add <specific files>` — never `git add -A` or
  `git add .` blindly, because parallel work from other agents (or your own
  scratch) may have left untracked files you don't intend to commit.
- **Commit messages** follow the existing style:
  `feat(patches): integrate CarPlay & AndroidAuto patches, milestones, …`
- **Re-check** with `git diff --cached --stat` before committing. If you see
  a file you don't recognize, investigate first.

## Connecting to the Car (ADB & Telnet)

The car's multimedia system (MMI) connects to the developer machine via Wi-Fi and supports both **ADB** and **Telnet** connections.

### Subnet and IP Discovery
- **Subnet:** The MMI operates on the `192.168.33.x` subnet.
- **Dynamic IP:** The car's IP address can change between sessions.
- **Auto-Discovery Utility:**
  - Run the dedicated IP auto-detection script to fetch the current IP:
    ```powershell
    .\scripts\Get-Car-IP.ps1
    ```
    This pings the subnet to refresh the ARP table, checks for active ports (ADB/Telnet), and outputs the active MMI IP.
  - For automation or within other scripts, use the `-Raw` flag to output *only* the IP string:
    ```powershell
    $ip = .\scripts\Get-Car-IP.ps1 -Raw
    ```
- **Connection & Internet Routing:** 
  - To automatically find the MMI, share your PC's internet with it, and maintain a persistent connection, run:
    ```powershell
    .\scripts\Share-Internet-And-Connect.ps1
    ```

### ADB (Port 5555)
- **Port:** `5555`
- **Connect Command:**
  ```powershell
  adb connect <CAR_IP>:5555
  ```
- **Verification:** Run `adb devices` to check if the MMI is recognized.
- **Root Check:** Run `adb shell id`. It must output `uid=0(root)`. If ADB connects but lacks root permissions, route your commands/scripts through Telnet instead.

### Telnet (Port 23)
- **Port:** `23`
- **Purpose:** Direct root shell access without password. Used for deployment scripting and as a fallback when ADB is unavailable or lacks root.
- **Connect Command:**
  ```powershell
  telnet <CAR_IP> 23
  ```
- **Automation Fallback:** Many scripts (e.g., `Share-Internet-And-Connect.ps1`, `tools/headunit-dev/headunit.sh`) automatically fall back to Telnet/Base64 transmission if ADB connectivity fails or lacks root.

### Common Deployment Workflows
- **Haval Shisuku / Impulse App:** Build, deploy, and install using `.\scripts\Deploy-To-Car.ps1`.
- **Android Auto Patches:** Deploy manually using the script located in the milestone or patch directory (e.g., `python scripts/aa-patches/deploy_to_car.py --ip <CAR_IP>`). This pushes the APK to `/data/local/tmp/` and binds it over `/vendor/app/AndroidAutoApp/AndroidAutoApp.apk` via a root telnet/adb mount.

## Apktool, signing tools, keystore — known paths

These are referenced verbatim in many of the README files. Keep them stable.

| Tool | Path |
|------|------|
| apktool | `tools/apktool_3.0.2.jar` (gitignored — download from `iBotPeaches/Apktool` releases if missing) |
| zipalign | `C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\zipalign.exe` |
| apksigner | `C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\apksigner.bat` |
| Debug keystore | `C:\Users\vanes\.android\debug.keystore` (password `android`) |
| ADB | `C:\Users\vanes\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
