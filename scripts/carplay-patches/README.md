# CarPlay APK Patching Pipeline

This directory mirrors the Android Auto patch workflow for the TS CarPlay app.

Before changing CarPlay focus, Surface, camera, HVAC, or display-transfer
behavior, read the regression contract:

- `docs/carplay-cluster-regression-contract.md`

## Safety Status

The old v2.4 patch archived here is unsafe for automatic mounting. In the
2026-05-24 vehicle test it produced a live `CarPlayDisplayActivity`/`SurfaceView`
on display 0 with either no frames or a white/dirty buffer after the native
CarPlay shortcut and controlled restarts.

The current target state is the 2026-05-29 CarPlay D3 focus/native-buffer patch v12:

- `TsCarPlayApp.apk` MD5 `ec5053d91d8364d9451937981e08a04a`;
- `TsCarPlayService.apk` MD5 `f0269fc640778825843762dcf55a8b83`;
- auto-mount version `app_visual_d0_focus_service_conditional_camera_native1904x704_v12`;
- service patch is HVAC-only by default and covers both the HVAC borrow edge
  (`priority=0x6`) and the symmetric HVAC release edge
  (`priority=0`, `action=1`, `borrowId=uiNotification`);
- the embedded service uses the conditional camera path generated with
  `--conditional-camera`: camera/AVM keeps stock behavior unless
  `persist.haval.carplay.desired_display == 3`, in which case `priority=0x7`
  and `backCameraStatusChangedTo(APP_ON/OFF)` are routed to `sendMessage(6)`;
- the unconditional camera service patch generated with `--include-camera`
  remains staged-only. In the car, that v7 service patch was enough to black out
  display 0 during initial CarPlay startup;
- visual app patch keeps `ts.car.carplay.view_state=foreground` on `onPause`,
  because the earlier secondary-display check was not reliable after a
  head-unit reboot and allowed HVAC close to pull CarPlay from D3 back to D0.
- visual app patch also ignores normal display-0 focus returns while CarPlay is
  still present in its focus stack, or while `mConnStatus == 2` and
  `persist.haval.carplay.desired_display == 3`. This blocks D0 apps such as
  Settings from changing the video route while the D3 Activity and Surface are
  still alive.
- visual app patch ignores stock `FINISH_ACTIVITY` broadcasts while the
  receiving `CarPlayDisplayActivity` is running on a secondary display. The
  same broadcast still finishes a display-0 Activity, but it no longer removes
  the D3 task when AppList/display-0 focus changes during HVAC close.
- visual app patch ignores `requestVideoFocus(1/2)` finish requests when the
  Activity is on a secondary display. Display 0 keeps the stock finish behavior.
- visual app patch changes only `fragment_display_surface.xml` for sizing:
  the `SurfaceView` uses `match_parent` instead of the stock centered
  `1896x700` viewport, which SurfaceFlinger reports as an aligned
  `1904x704` buffer inside the 1920x720 cluster.
- visual app patch also changes `CarPlayDisplayFragment$2.surfaceChanged` so
  `DisplayContract.Presenter.show(surface,w,h)` and `SurfaceHolder.setFixedSize()`
  receive `1904x704` on secondary displays while the Activity/window remain
  `1920x720`. Physical validation on 2026-05-29 showed this removes the gray D3
  CarPlay area and stays stable with AC and Settings opened on D0.

Before changing or deploying CarPlay patches, run:

```bash
python3 scripts/carplay-patches/verify_regression_lock.py
```

The runtime side of the protected state also depends on `DisplayAppLauncher`:
when `desiredCarPlayDisplayId=3` and the native head unit removes the visual
CarPlay task or recreates it on display 0 after HVAC/camera/app focus, Impulse
may recreate `CarPlayDisplayActivity` on display 3 without `force-stop`, then
remove the display-0 duplicate only after the D3 task exists. It also syncs
`persist.haval.carplay.desired_display` so the native visual APK can preserve
the D3 video route after a reboot.

On boot or app update, `CarPlayPatchManager` must also make sure the mounted
APKs are loaded into memory. If the visual CarPlay task is already active while
the mount changes, it reloads `com.ts.carplay.app` and `com.ts.carplay`, then
reopens the visual Activity on its current display. This is patch-load behavior,
not a normal display handoff strategy.

The target app on the Haval head unit is:

- package: `com.ts.carplay.app`
- APK path: `/system/app/TsCarPlayApp/TsCarPlayApp.apk`
- activity: `com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity`

## What the Patch Does

- Expands `CarPlayDisplayActivity` `configChanges` so stack moves/resizes do not recreate the activity.
- Changes `CarPlayDisplayActivity` from `singleInstance` to `standard`, so
  Impulse can create the destination Activity before removing the source
  Activity during display handoff. This avoids a no-Surface gap that leaves
  video black until the phone cable is reconnected.
- Changes the projection `SurfaceView` to `match_parent`, so the native
  CarPlay surface follows the Activity window size while staying full-height.
- The Impulse launcher keeps CarPlay fullscreen on both display 0 and cluster
  3: `0,0,1920,720`. The native patch must also keep Display 3 at `720`;
  mixing `540` and `720` video/surface profiles leaves stale buffers on this
  head unit.
- Forces `SurfaceHolder.setFixedSize()` from the current display target.
  In the supported Haval layout, `CarPlayManager.setSurfaceSize()` receives
  `1904x704` on cluster 3, while the Activity/window stay `1920x720`.
- Changes the CarPlay video config to read
  `persist.haval.carplay.video.height`. The launcher keeps this property at
  `720` for display 0 and cluster 3.
- When CarPlay is sent back to a secondary display, Impulse recreates the
  CarPlay display Activity instead of preserving the old stack with
  `am display move-stack`; reusing that stack can keep a stale Surface transform.
- Forces `onWindowFocusChanged()` to behave as focused.
- Changes `onPause()` to keep broadcasting `foreground` instead of `background`.
- Makes `requestVideoFocus(1/2)` ignore the stock `finish()` path that tears down the surface.
- Blocks the remaining self-finish paths from `switchFragmentType()` and
  `FINISH_ACTIVITY` broadcasts.
- Blocks the service-side `VideoModel.finishCarPlayActivity()` and keeps
  normal central UI focus changes from calling `changeVideoFocus(0, ...)`,
  so touching the MMI/app list does not blank the CarPlay projection.
- Keeps CarPlay video focus when the native service reports
  `backcamera/backupCamera`, so AVM/RVC can appear on the central display
  without blanking the cluster projection.
- Keeps CarPlay video focus for `com.beantechs.hvac/uiNotification`, so the
  native A/C overlay does not blank the cluster projection while it is open.
- Adds `FLAG_NOT_FOCUSABLE` to `CarPlayDisplayActivity`, so the native CarPlay
  shortcut does not steal global window focus from Display 0 or hide the native
  navigation bar.
- Mirrors the Android Auto focus-preservation behavior: `destroySurface()`,
  `surfaceDestroyed()`, `hide()`, and `onDestroyView()` no longer release or
  detach the Surface, so a camera/AC overlay can leave the last CarPlay frame
  static instead of forcing a renderer teardown/recreate.
- `DETACH_RENDER` is not part of the normal display-transfer path anymore;
  transfers preserve the current Surface and rely on the new Activity to
  reattach with `REFRESH_RENDER`. Calling `CarPlayManager.setSurface(null)` can
  leave video black until the phone cable is reconnected.
- Adds an `onConfigurationChanged()` hook that requests a relayout.

## Build

Run from the repository root:

```bash
mkdir -p build_carplay

./tools/headunit-dev/headunit.sh pull-file \
  /system/app/TsCarPlayApp/TsCarPlayApp.apk TsCarPlayApp.apk

java -jar tools/apktool_3.0.2.jar d -f -o build_carplay/ts-app \
  tools/headunit-dev/output/pulled-files/TsCarPlayApp.apk

python3 scripts/carplay-patches/patch_logic.py

java -jar tools/apktool_3.0.2.jar b \
  -o build_carplay/TsCarPlayApp_unsigned.apk build_carplay/ts-app

zipalign -f 4 build_carplay/TsCarPlayApp_unsigned.apk \
  build_carplay/TsCarPlayApp_aligned.apk

apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --out build_carplay/TsCarPlayApp_signed.apk \
  build_carplay/TsCarPlayApp_aligned.apk
```

For the in-app mount flow, copy the signed APK to:

```text
app/src/main/assets/carplay_patches/TsCarPlayApp.apk
```

Then rebuild/deploy the Haval app and use the CarPlay patch card in the
Install Apps screen.

## Direct Vehicle Deploy

For rapid testing, deploy the signed APK directly. This starts a local HTTP
server and asks the head unit to download the APK with `curl`, avoiding the
slow telnet/base64 path:

```bash
python3 scripts/carplay-patches/deploy_to_car.py \
  --apk build_carplay/TsCarPlayApp_signed.apk
```
