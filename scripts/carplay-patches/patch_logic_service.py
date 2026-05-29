"""
CarPlay Vendor Service Patching Pipeline (HVAC + Camera overlay fix)
========================================================================

Target: build_carplay/ts-service/  (com.ts.carplay vendor service)
APK on device: /vendor/app/TsCarPlayService/TsCarPlayService.apk

Separate from patch_logic.py (which targets the client app com.ts.carplay.app).

What this patch does
--------------------
One surgical default change in ScreenResourceManager to keep the CarPlay decoder
running while the HVAC overlay is open on display 0. Camera/AVM is intentionally
left stock unless --include-camera is passed, because reverse camera behavior is
safety-sensitive and must be validated separately.

1) accessoryScreenChange sparse-switch:
       0x6 -> :sswitch_1     (HVAC -> sendMessage 2 -> InBorrowUser pause)
   becomes
       0x6 -> :sswitch_2     (sendMessage 6 -> no case in InCarPlay -> ignored)

Optional with --include-camera:
   AND
       0x7 -> :sswitch_0     (camera -> sendMessage 3 -> InBorrowNever pause)
   becomes
       0x7 -> :sswitch_2     (sendMessage 6 -> ignored)

2) Optional with --include-camera, backCameraStatusChangedTo direct sendMessage calls:
       APP_ON  -> sendMessage(3) -> InBorrowNever pause
   becomes
       APP_ON  -> sendMessage(6) -> ignored
   AND
       APP_OFF -> sendMessage(0xc/12) -> unborrow
   becomes
       APP_OFF -> sendMessage(6) -> ignored (symmetric no-op)
   The mIsInBackCamera flag is still maintained so other readers behave correctly.

Camera path needs BOTH (1) and (2) because backCameraStatusChangedTo bypasses
the sparse-switch entirely.

Idempotency: each patch leaves a sentinel comment block so re-running this
script on an already-patched build is a no-op.
"""

import argparse
import os
import sys

BUILD_DIR = "build_carplay/ts-service"
SCREEN_RES_MANAGER = (
    f"{BUILD_DIR}/smali/com/ts/carplay/lib/resourcemanager/ScreenResourceManager.smali"
)

SPARSE_SENTINEL = "# CARPLAY_HVAC_KEEP_FOREGROUND_PATCH"
CAMERA_SENTINEL = "# CARPLAY_CAMERA_KEEP_FOREGROUND_PATCH"

# --- Sparse-switch patch (HVAC by default, camera only with --include-camera) ---

SPARSE_PATCHED_WITH_CAMERA = """    # CARPLAY_HVAC_KEEP_FOREGROUND_PATCH
    # 0x6 (HVAC uiNotification) re-routed from :sswitch_1 (sendMessage 2 -> InBorrowUser pause)
    # to :sswitch_2 (sendMessage 6 -> no case in InCarPlay -> ignored).
    # 0x7 (camera/AVM backupCamera) re-routed from :sswitch_0 (sendMessage 3 -> InBorrowNever pause)
    # to :sswitch_2 (sendMessage 6 -> ignored). Belt-and-suspenders with the
    # backCameraStatusChangedTo() patch below; this covers the path where camera
    # priority comes through accessoryScreenChange.
    # Keeps CarPlay decoder running on cluster while HVAC/camera overlays open on D0.
    :sswitch_data_0
    .sparse-switch
        0x0 -> :sswitch_3
        0x1 -> :sswitch_2
        0x6 -> :sswitch_2
        0x7 -> :sswitch_2
    .end sparse-switch"""

SPARSE_STOCK = """    :sswitch_data_0
    .sparse-switch
        0x0 -> :sswitch_3
        0x1 -> :sswitch_2
        0x6 -> :sswitch_1
        0x7 -> :sswitch_0
    .end sparse-switch"""

SPARSE_HVAC_ONLY = """    # CARPLAY_HVAC_KEEP_FOREGROUND_PATCH
    # 0x6 (HVAC uiNotification) re-routed from :sswitch_1 (sendMessage 2 -> InBorrowUser pause)
    # to :sswitch_2 (sendMessage 6 -> no case in InCarPlay -> ignored).
    # Keeps CarPlay decoder running on cluster while HVAC overlay is open on D0.
    # Camera (0x7) intentionally left at :sswitch_0 - that path is also reached via
    # backCameraStatusChangedTo() sendMessage(3) bypassing this sparse-switch.
    :sswitch_data_0
    .sparse-switch
        0x0 -> :sswitch_3
        0x1 -> :sswitch_2
        0x6 -> :sswitch_2
        0x7 -> :sswitch_0
    .end sparse-switch"""

# --- backCameraStatusChangedTo patch ---

CAMERA_PATCHED = """    # CARPLAY_CAMERA_KEEP_FOREGROUND_PATCH
    # APP_ON: keep mIsInBackCamera flag (other code may read it) but send
    # Message.what=6 (SCREEN_CHANGED_TO_CARPLAY, no-op in InCarPlay) instead of
    # Message.what=3 (HU_HIGHEST_SCREEN_START -> InBorrowNever pause).
    .line 1238
    const/4 v0, 0x1

    iput-boolean v0, p0, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager;->mIsInBackCamera:Z

    .line 1239
    iget-object v0, p0, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager;->mStateMachine:Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager$ScreenStateMachine;

    const/4 v1, 0x6

    invoke-virtual {v0, v1}, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager$ScreenStateMachine;->sendMessage(I)V

    goto :goto_0

    # APP_OFF: original sendMessage(12) was the unborrow trigger; since we never
    # entered borrow on the ON side, send 6 (ignored) here too. Symmetric no-op.
    .line 1240
    :cond_0
    sget-object v0, Lcom/ts/carplay/lib/common/CpConst$AppState;->APP_OFF:Lcom/ts/carplay/lib/common/CpConst$AppState;

    if-ne p1, v0, :cond_1

    .line 1241
    iget-object v0, p0, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager;->mStateMachine:Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager$ScreenStateMachine;

    const/4 v1, 0x6

    invoke-virtual {v0, v1}, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager$ScreenStateMachine;->sendMessage(I)V

    .line 1242
    const/4 v0, 0x0

    iput-boolean v0, p0, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager;->mIsInBackCamera:Z"""

CAMERA_STOCK = """    .line 1238
    const/4 v0, 0x1

    iput-boolean v0, p0, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager;->mIsInBackCamera:Z

    .line 1239
    iget-object v0, p0, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager;->mStateMachine:Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager$ScreenStateMachine;

    const/4 v1, 0x3

    invoke-virtual {v0, v1}, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager$ScreenStateMachine;->sendMessage(I)V

    goto :goto_0

    .line 1240
    :cond_0
    sget-object v0, Lcom/ts/carplay/lib/common/CpConst$AppState;->APP_OFF:Lcom/ts/carplay/lib/common/CpConst$AppState;

    if-ne p1, v0, :cond_1

    .line 1241
    iget-object v0, p0, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager;->mStateMachine:Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager$ScreenStateMachine;

    const/16 v1, 0xc

    invoke-virtual {v0, v1}, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager$ScreenStateMachine;->sendMessage(I)V

    .line 1242
    const/4 v0, 0x0

    iput-boolean v0, p0, Lcom/ts/carplay/lib/resourcemanager/ScreenResourceManager;->mIsInBackCamera:Z"""


def read_file(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def write_file(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def apply_sparse_switch_patch(content, include_camera):
    desired = SPARSE_PATCHED_WITH_CAMERA if include_camera else SPARSE_HVAC_ONLY
    desired_label = "HVAC+camera" if include_camera else "HVAC-only"

    if desired.strip() in content:
        print(f"  [SKIP] sparse-switch {desired_label} patch already applied")
        return content, 0

    if include_camera and SPARSE_HVAC_ONLY in content:
        new_content = content.replace(SPARSE_HVAC_ONLY, SPARSE_PATCHED_WITH_CAMERA, 1)
        print("  [OK]   sparse-switch upgraded from HVAC-only to HVAC+camera")
        return new_content, 1

    if not include_camera and SPARSE_PATCHED_WITH_CAMERA in content:
        new_content = content.replace(SPARSE_PATCHED_WITH_CAMERA, SPARSE_HVAC_ONLY, 1)
        print("  [OK]   sparse-switch downgraded from HVAC+camera to HVAC-only")
        return new_content, 1

    if SPARSE_STOCK not in content:
        print("  [ERROR] sparse-switch stock block not found; smali shape changed.")
        return content, 0

    new_content = content.replace(SPARSE_STOCK, desired, 1)
    if include_camera:
        print("  [OK]   sparse-switch 0x6 and 0x7 routed to sswitch_2")
    else:
        print("  [OK]   sparse-switch 0x6 routed to sswitch_2; 0x7 left stock")
    return new_content, 1


def apply_camera_status_patch(content, include_camera):
    if include_camera and CAMERA_SENTINEL in content:
        print("  [SKIP] backCameraStatusChangedTo patch already applied")
        return content, 0

    if not include_camera and CAMERA_PATCHED in content:
        new_content = content.replace(CAMERA_PATCHED, CAMERA_STOCK, 1)
        print("  [OK]   backCameraStatusChangedTo reverted to stock for staged HVAC-only build")
        return new_content, 1

    if not include_camera:
        print("  [SKIP] backCameraStatusChangedTo left stock")
        return content, 0

    if CAMERA_STOCK not in content:
        print("  [ERROR] backCameraStatusChangedTo stock block not found; smali shape changed.")
        return content, 0

    new_content = content.replace(CAMERA_STOCK, CAMERA_PATCHED, 1)
    print("  [OK]   backCameraStatusChangedTo ON/OFF rerouted to sendMessage(6)")
    return new_content, 1


def main():
    parser = argparse.ArgumentParser(
        description="Patch TS CarPlay vendor service smali for staged HVAC/camera validation."
    )
    parser.add_argument(
        "--include-camera",
        action="store_true",
        help="also patch camera/AVM resource handling; default is HVAC-only",
    )
    args = parser.parse_args()

    print("=" * 70)
    stage = "HVAC + camera" if args.include_camera else "HVAC-only"
    print(f"   CarPlay Vendor Service Smali Patching Pipeline ({stage})")
    print("=" * 70)

    if not os.path.exists(SCREEN_RES_MANAGER):
        print(f"\nERROR: smali file not found at {SCREEN_RES_MANAGER}")
        print("\nDid you disassemble the stock vendor service APK first?")
        print(
            f"  java -jar tools/apktool_3.0.2.jar d -f -o {BUILD_DIR} <path-to-TsCarPlayService.apk>"
        )
        sys.exit(1)

    print(f"[ScreenResourceManager] Patching {SCREEN_RES_MANAGER}:")
    content = read_file(SCREEN_RES_MANAGER)

    content, sparse_changes = apply_sparse_switch_patch(content, args.include_camera)
    content, camera_changes = apply_camera_status_patch(content, args.include_camera)

    write_file(SCREEN_RES_MANAGER, content)
    changes = sparse_changes + camera_changes

    print("=" * 70)
    print(f"Patches complete! Total modifications: {changes}")
    print("=" * 70)


if __name__ == "__main__":
    main()
