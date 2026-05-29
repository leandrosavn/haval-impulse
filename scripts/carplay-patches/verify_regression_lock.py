#!/usr/bin/env python3
"""
Verify the current CarPlay D3 blackout regression lock.

This is intentionally a static check. It guards the validated state from
2026-05-28 where HVAC on display 0 no longer blacks out CarPlay on display 3.
It does not replace the physical camera/AVM test.
"""

from pathlib import Path
import hashlib
import sys


ROOT = Path(__file__).resolve().parents[2]

EXPECTED_ASSET_MD5 = {
    "app/src/main/assets/carplay_patches/TsCarPlayApp.apk": "477529a8c454acbc25ab5adb848e18b4",
    "app/src/main/assets/carplay_patches/TsCarPlayService.apk": "4a76e74c5f9fc119287c5cc0f823856a",
}

REQUIRED_TOKENS = {
    "scripts/carplay-patches/patch_logic_app_focus.py": [
        "CP_KEEP_VIDEO_FOCUS_FOR_HVAC_ONLY",
        "CP_KEEP_CLUSTER_VIDEO_ON_SECONDARY_PAUSE",
        "priorityChanged patched: keep CarPlay video focus for HVAC uiNotification",
        "onPause patched: keep CarPlay video foreground on secondary display",
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
        "Do not force-stop CarPlay here.",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/services/ForegroundService.java": [
        "app_service_hvac_focus_v2",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/managers/DisplayAppLauncher.kt": [
        "auto-restore is disabled to avoid recreating Surface during native HVAC/camera/app transitions",
        "auto-restore disabled, waiting for explicit user handoff",
    ],
    "docs/carplay-cluster-regression-contract.md": [
        "Regra 29 - CarPlay D3 nao deve enviar background em onPause secundario",
        "se `getDisplay().getDisplayId() != 0`, `onPause()` retorna sem broadcast `background`",
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
