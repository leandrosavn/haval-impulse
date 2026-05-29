"""
CarPlay app focus patch for HVAC validation.

Target: build_carplay/ts-app-hvac-focus/ (com.ts.carplay.app)

This is intentionally smaller than the archived app patch. It changes:
- VideoModel.lambda$priorityChanged$3 so the visual app does not forward HVAC
  uiNotification focus to the vendor CarPlay service.
- CarPlayDisplayActivity.onPause so a visible CarPlay Activity on a secondary
  display does not broadcast "background" and drop the decoder route.

It does not change Activity launchMode, layout, SurfaceView sizing, or finish
paths.
"""

import os
import re
import sys

BUILD_DIR = "build_carplay/ts-app-hvac-focus"
VIDEO_MODEL = f"{BUILD_DIR}/smali/com/ts/carplay/app/service/model/video/VideoModel.smali"
DISPLAY_ACTIVITY = f"{BUILD_DIR}/smali/com/ts/carplay/app/ui/display/view/CarPlayDisplayActivity.smali"
VIDEO_SENTINEL = "# CP_KEEP_VIDEO_FOCUS_FOR_HVAC_ONLY"
LIFECYCLE_SENTINEL = "# CP_KEEP_CLUSTER_VIDEO_ON_SECONDARY_PAUSE"

PATCHED_METHOD = r""".method public static synthetic lambda$priorityChanged$3(Lcom/ts/carplay/app/service/model/video/VideoModel;Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;I)V
    .locals 4
    .param p1, "inF"    # Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;
    .param p2, "action"    # I

    # CP_KEEP_VIDEO_FOCUS_FOR_HVAC_ONLY
    if-eqz p1, :cond_return

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getBid()Ljava/lang/String;

    move-result-object v0

    const-string v1, "uiNotification"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_stock

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getFocus()Ljava/lang/String;

    move-result-object v0

    const-string v1, "com.beantechs.hvac"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_keep_focus

    const-string v1, "uiNotification"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_keep_focus

    goto :cond_stock

    :cond_keep_focus
    const-string v0, "VideoModel"

    const-string v1, "priorityChanged patched: keep CarPlay video focus for HVAC uiNotification"

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    :cond_stock
    iget-object v0, p0, Lcom/ts/carplay/app/service/model/video/VideoModel;->mCarPlayRemoteManager:Lcom/ts/carplay/app/service/manager/CarPlayRemoteManager;

    if-eqz v0, :cond_return

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getPty()I

    move-result v0

    if-nez v0, :cond_change_focus

    if-nez p2, :cond_change_focus

    const-string v0, "VideoModel"

    const-string v1, "normol screens have no exit action"

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    goto :cond_return

    :cond_change_focus
    const-string v0, "VideoModel"

    const-string v1, "changeVideoFocus"

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    iget-object v0, p0, Lcom/ts/carplay/app/service/model/video/VideoModel;->mCarPlayRemoteManager:Lcom/ts/carplay/app/service/manager/CarPlayRemoteManager;

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getPty()I

    move-result v1

    const/4 v2, 0x1

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getBid()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v0, v1, v2, v3, p2}, Lcom/ts/carplay/app/service/manager/CarPlayRemoteManager;->changeVideoFocus(IZLjava/lang/String;I)V

    :cond_return
    return-void
.end method"""

PATCHED_ON_PAUSE = r""".method protected onPause()V
    .locals 3

    .line 141
    invoke-super {p0}, Landroid/app/Activity;->onPause()V

    .line 142
    sget-object v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->TAG:Ljava/lang/String;

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "Lifecycle --- onPause: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    # CP_KEEP_CLUSTER_VIDEO_ON_SECONDARY_PAUSE
    invoke-virtual {p0}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->getDisplay()Landroid/view/Display;

    move-result-object v0

    if-eqz v0, :cond_send_background

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v1

    if-eqz v1, :cond_send_background

    sget-object v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->TAG:Ljava/lang/String;

    const-string v1, "onPause patched: keep CarPlay video foreground on secondary display"

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    .line 143
    :cond_send_background
    new-instance v0, Landroid/content/Intent;

    invoke-direct {v0}, Landroid/content/Intent;-><init>()V

    .line 144
    .local v0, "intent":Landroid/content/Intent;
    const-string v1, "ts.car.carplay.view_state"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    .line 145
    const-string v1, "state"

    const-string v2, "background"

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 146
    invoke-virtual {p0, v0}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->sendBroadcast(Landroid/content/Intent;)V

    .line 147
    return-void
.end method"""


def main():
    for path in (VIDEO_MODEL, DISPLAY_ACTIVITY):
        if not os.path.exists(path):
            print(f"ERROR: smali file not found: {path}")
            sys.exit(1)

    with open(VIDEO_MODEL, "r", encoding="utf-8") as f:
        video_content = f.read()

    if VIDEO_SENTINEL in video_content:
        print("[SKIP] HVAC focus patch already applied")
    else:
        pattern = (
            r"\.method public static synthetic lambda\$priorityChanged\$3"
            r"\(Lcom/ts/carplay/app/service/model/video/VideoModel;"
            r"Lcom/ts/carplay/app/service/model/video/VideoModel\$FocusModeInfo;I\)V"
            r".*?\.end method"
        )
        video_content, count = re.subn(pattern, PATCHED_METHOD, video_content, count=1, flags=re.DOTALL)
        if count != 1:
            print("ERROR: priorityChanged lambda method not found or matched more than once")
            sys.exit(1)

        with open(VIDEO_MODEL, "w", encoding="utf-8") as f:
            f.write(video_content)
        print("[OK] VideoModel priorityChanged now keeps CarPlay video focus during HVAC uiNotification")

    with open(DISPLAY_ACTIVITY, "r", encoding="utf-8") as f:
        activity_content = f.read()

    if LIFECYCLE_SENTINEL in activity_content:
        print("[SKIP] secondary-display onPause patch already applied")
        return

    pattern = r"\.method protected onPause\(\)V.*?\.end method"
    activity_content, count = re.subn(pattern, PATCHED_ON_PAUSE, activity_content, count=1, flags=re.DOTALL)
    if count != 1:
        print("ERROR: CarPlayDisplayActivity.onPause method not found or matched more than once")
        sys.exit(1)

    with open(DISPLAY_ACTIVITY, "w", encoding="utf-8") as f:
        f.write(activity_content)

    print("[OK] CarPlayDisplayActivity.onPause now preserves video state on secondary displays")


if __name__ == "__main__":
    main()
