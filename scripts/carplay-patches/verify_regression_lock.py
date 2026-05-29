#!/usr/bin/env python3
"""
Verify the current CarPlay D3 blackout regression lock.

This is intentionally a static check. It guards the validated state from
2026-05-28 where HVAC on display 0 must not black out or pull CarPlay from
display 3 back to display 0.
It does not replace the physical camera/AVM test.
"""

from pathlib import Path
import hashlib
import sys


ROOT = Path(__file__).resolve().parents[2]

EXPECTED_ASSET_MD5 = {
    "app/src/main/assets/carplay_patches/TsCarPlayApp.apk": "6fa2ec71f8a10e11a8de94ab03987344",
    "app/src/main/assets/carplay_patches/TsCarPlayService.apk": "4a76e74c5f9fc119287c5cc0f823856a",
}

REQUIRED_TOKENS = {
    "scripts/carplay-patches/patch_logic_app_focus.py": [
        "CP_KEEP_VIDEO_FOCUS_FOR_HVAC_ONLY",
        "CP_KEEP_CLUSTER_VIDEO_ON_SECONDARY_PAUSE",
        "CP_KEEP_CLUSTER_VIDEO_FOREGROUND_ON_ANY_PAUSE",
        "priorityChanged patched: keep CarPlay video focus for HVAC uiNotification",
        "onPause patched: keep CarPlay video foreground and suppress background",
    ],
    "scripts/carplay-patches/patch_logic_service.py": [
        "CARPLAY_HVAC_KEEP_FOREGROUND_PATCH",
        "Camera (0x7) intentionally left at :sswitch_0",
        "--include-camera",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/managers/CarPlayPatchManager.kt": [
        "private const val PATCH_RUNTIME_ENABLED = true",
        "private const val SERVICE_APK = \"TsCarPlayService.apk\"",
        "const val SYSTEM_SERVICE_PATH = \"/vendor/app/TsCarPlayService/TsCarPlayService.apk\"",
        "reloadCarPlayProcessesIfIdle(\"AUTO_MOUNT_AFTER_BOOT\")",
        "CarPlay visual task is active; not reloading processes to avoid dropping the session",
        "Bundled CarPlay HVAC focus patches refreshed; re-applying mounts",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/services/ForegroundService.java": [
        "app_service_hvac_focus_v3",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/managers/DisplayAppLauncher.kt": [
        "restoreCarPlayFromMainDisplayToCluster",
        "recreateMissingCarPlayVisualTaskOnCluster",
        "CARPLAY_CLUSTER_WATCHDOG_NO_TASK",
        "Restoring CarPlay from display 0 stack",
        "ActivityManager reused display-0 CarPlay; defocusing once more and retrying cluster start",
        "CLEAN_DISPLAY0_DUPLICATE",
    ],
    "docs/carplay-cluster-regression-contract.md": [
        "Regra 29 - CarPlay nao deve enviar background em onPause",
        "`onPause()` envia `ts.car.carplay.view_state=foreground`, nunca `background`",
        "watchdog pode restaurar o visual no D3",
        "sem `force-stop`, defocando antes o D0",
    ],
}


def file_md5(path: Path) -> str:
    digest = hashlib.md5()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def main() -> int:
    failures: list[str] = []

    print("Checking CarPlay regression lock...")

    for relative_path, expected in EXPECTED_ASSET_MD5.items():
        path = ROOT / relative_path
        if not path.exists():
            failures.append(f"missing asset: {relative_path}")
            continue
        actual = file_md5(path)
        if actual != expected:
            failures.append(f"{relative_path} md5 {actual} != expected {expected}")
        else:
            print(f"[OK] {relative_path} md5 {actual}")

    for relative_path, tokens in REQUIRED_TOKENS.items():
        path = ROOT / relative_path
        if not path.exists():
            failures.append(f"missing file: {relative_path}")
            continue
        content = read_text(path)
        for token in tokens:
            if token not in content:
                failures.append(f"{relative_path} missing token: {token}")
        if all(token in content for token in tokens):
            print(f"[OK] {relative_path} sentinels present")

    if failures:
        print("\nRegression lock FAILED:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("\nCarPlay regression lock OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
