#!/usr/bin/env python3
"""
Verify the current CarPlay D3 blackout regression lock.

This is intentionally a static check. It guards the validated state from
2026-05-29 where HVAC, normal display-0 apps, and camera/AVM must not black out
or pull CarPlay from display 3 back to display 0. The protected runtime keeps
the visual app patch and uses a conditional camera service patch: camera stays
stock on display 0, but is ignored when the desired CarPlay target is display 3.
It does not replace the physical camera/AVM test.
"""

from pathlib import Path
import hashlib
import sys


ROOT = Path(__file__).resolve().parents[2]

EXPECTED_ASSET_MD5 = {
    "app/src/main/assets/carplay_patches/TsCarPlayApp.apk": "ec5053d91d8364d9451937981e08a04a",
    "app/src/main/assets/carplay_patches/TsCarPlayService.apk": "f0269fc640778825843762dcf55a8b83",
}

REQUIRED_TOKENS = {
    "scripts/carplay-patches/patch_logic_app_focus.py": [
        "CP_KEEP_VIDEO_FOCUS_FOR_HVAC_D0_APPS_AND_NORMAL_RETURN",
        "CP_KEEP_CLUSTER_VIDEO_ON_SECONDARY_PAUSE",
        "CP_KEEP_CLUSTER_VIDEO_FOREGROUND_ON_ANY_PAUSE",
        "CP_IGNORE_FINISH_BROADCAST_ON_SECONDARY_DISPLAY",
        "CP_IGNORE_REQUEST_VIDEO_FOCUS_FINISH_ON_SECONDARY_DISPLAY",
        "CP_SURFACE_MATCH_PARENT_FULLSCREEN",
        "CP_SURFACE_SHOW_NATIVE_1904_704_ON_SECONDARY",
        "priorityChanged patched: keep CarPlay video focus for HVAC uiNotification",
        "priorityChanged patched: keep CarPlay video focus for D3 display0 normal app",
        "onPause patched: keep CarPlay video foreground and suppress background",
        "finish receiver patched: ignore finish on secondary display",
        "requestVideoFocus patched: ignore finish on secondary display",
        "clean 1904x704 buffer",
        "persist.haval.carplay.desired_display",
    ],
    "scripts/carplay-patches/patch_logic_service.py": [
        "CARPLAY_HVAC_KEEP_FOREGROUND_PATCH",
        "CARPLAY_HVAC_RELEASE_KEEP_FOREGROUND_PATCH",
        "CARPLAY_CAMERA_KEEP_FOREGROUND_PATCH",
        "CARPLAY_CAMERA_CONDITIONAL_KEEP_FOREGROUND_PATCH",
        "HVAC release patched: ignore unborrow for uiNotification",
        "0x7 (camera/AVM backupCamera) re-routed from :sswitch_0",
        "backCameraStatusChangedTo ON/OFF rerouted to sendMessage(6)",
        "--include-camera",
        "--conditional-camera",
        "persist.haval.carplay.desired_display",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/managers/CarPlayPatchManager.kt": [
        "private const val PATCH_RUNTIME_ENABLED = true",
        "private const val SERVICE_APK = \"TsCarPlayService.apk\"",
        "const val SYSTEM_SERVICE_PATH = \"/vendor/app/TsCarPlayService/TsCarPlayService.apk\"",
        "reloadCarPlayProcessesAfterPatchMount(\"AUTO_MOUNT_AFTER_BOOT\")",
        "CarPlay visual task is active; reloading visual and host so mounted HVAC focus patches are loaded",
        "am stack start 0 -f 0x14000000",
        "Bundled CarPlay HVAC focus patches refreshed; re-applying mounts",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/services/ForegroundService.java": [
        "app_visual_d0_focus_service_conditional_camera_native1904x704_v12",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/managers/DisplayAppLauncher.kt": [
        "restoreCarPlayFromMainDisplayToCluster",
        "recreateMissingCarPlayVisualTaskOnCluster",
        "CARPLAY_CLUSTER_WATCHDOG_NO_TASK",
        "BOOT_USB_CARPLAY_D0_AUTOSTART",
        "A plain explicit start is the stable display-0 clean-start path",
        "Skipping missing CarPlay visual restore because no recent cluster visual was observed",
        "prevents post-reboot Impulse launch loop",
        "Clearing stale desired CarPlay cluster target because no recent cluster visual/session was observed",
        "CARPLAY_CLUSTER_WATCHDOG_START_NO_CLUSTER_TASK",
        "packageName == App.getContext().packageName",
        "am start -f 0x14000000 -n $CARPLAY_PACKAGE/$escapedActivity",
        "Restoring CarPlay from display 0 stack",
        "ActivityManager reused display-0 CarPlay; defocusing once more and retrying cluster start",
        "CLEAN_DISPLAY0_DUPLICATE",
        "persist.haval.carplay.desired_display",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/projectors/InstrumentProjector2.kt": [
        "Camera/AVM/HVAC no longer hide the cluster Presentation",
        "removes the protected Mapa overlay",
        "projectionCardOverlayAllowed",
        "CLUSTER_INPUT_KEY",
        "no_recent_input",
        "return false",
    ],
    "cluster-widgets/default/src/core/main.js": [
        "projectionCardOverlayAllowed",
        "projectionCardOverlayActive: isProjectionCardOverlayActive()",
    ],
    "docs/carplay-cluster-regression-contract.md": [
        "Regra 29 - CarPlay nao deve enviar background em onPause",
        "`onPause()` envia `ts.car.carplay.view_state=foreground`, nunca `background`",
        "Regra 31 - D3 ignora FINISH_ACTIVITY de AppList/display 0",
        "CP_IGNORE_FINISH_BROADCAST_ON_SECONDARY_DISPLAY",
        "Regra 32 - D3 ignora requestVideoFocus finish e app normal do D0",
        "CP_IGNORE_REQUEST_VIDEO_FOCUS_FINISH_ON_SECONDARY_DISPLAY",
        "`SurfaceView` nativo deve usar `match_parent`",
        "CP_SURFACE_MATCH_PARENT_FULLSCREEN",
        "CP_SURFACE_SHOW_NATIVE_1904_704_ON_SECONDARY",
        "Regra 33 - Service embarcado usa camera condicional por desired_display",
        "Regra 34 - Mapa permanece visivel durante camera/AVM/HVAC",
        "nao podem esconder a `Presentation` inteira por `windowAlpha=0`",
        "projectionCardOverlayAllowed=true",
        "sem tecla fisica recente",
        "somente apos input real recente do",
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
