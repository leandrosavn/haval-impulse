package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.util.Log
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import java.io.File
import java.io.FileOutputStream

object CarPlayPatchManager {
    private const val TAG = "CARPLAY_PATCH_MGR"
    private const val PATCH_DIR = "/data/local/tmp/carplay_patches"
    private const val APP_APK = "TsCarPlayApp.apk"
    private const val PATCH_RUNTIME_ENABLED = false
    private const val DISABLED_REASON =
        "CarPlay native patch disabled: current patch caused black/dirty frames on display 0"

    const val SYSTEM_APP_PATH = "/system/app/TsCarPlayApp/TsCarPlayApp.apk"

    fun isPatchInstalled(): Boolean {
        val output = ShizukuUtils.runCommandAndGetOutput(arrayOf("ls", "-l", PATCH_DIR))
        val installed = output.contains(APP_APK)
        Log.d(TAG, "isPatchInstalled: $installed (ls output: $output)")
        return installed
    }

    fun isMounted(): Boolean {
        // Use MD5 comparison for reliable mount detection
        val systemAppMd5 = ShizukuUtils.runCommandAndGetOutput(arrayOf("md5sum", SYSTEM_APP_PATH)).trim().split(" ")[0]
        val patchAppMd5 = ShizukuUtils.runCommandAndGetOutput(arrayOf("md5sum", "$PATCH_DIR/$APP_APK")).trim().split(" ")[0]

        val appMounted = systemAppMd5 == patchAppMd5 && systemAppMd5.isNotEmpty()
        
        Log.d(TAG, "isMounted (MD5): $appMounted (System: $systemAppMd5, Patch: $patchAppMd5)")
        return appMounted
    }

    fun installPatches(context: Context): Boolean {
        if (!PATCH_RUNTIME_ENABLED) {
            Log.w(TAG, "installPatches skipped. $DISABLED_REASON")
            return false
        }

        try {
            Log.i(TAG, "Starting CarPlay patch installation...")
            ShizukuUtils.runCommandAndGetOutput(arrayOf("mkdir", "-p", PATCH_DIR))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("chmod", "777", PATCH_DIR))

            Log.d(TAG, "Copying asset: $APP_APK")
            val inputStream = context.assets.open("carplay_patches/$APP_APK")
            val tempFile = File(context.cacheDir, APP_APK)
            val outputStream = FileOutputStream(tempFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            val destPath = "$PATCH_DIR/$APP_APK"
            val cpOut = ShizukuUtils.runCommandAndGetOutput(arrayOf("cp", tempFile.absolutePath, destPath))
            Log.d(TAG, "Copy output for $APP_APK: $cpOut")
            
            ShizukuUtils.runCommandAndGetOutput(arrayOf("chmod", "644", destPath))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("chcon", "u:object_r:system_file:s0", destPath))
            tempFile.delete()

            val success = isPatchInstalled()
            Log.i(TAG, "Installation success: $success")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install CarPlay patches", e)
            return false
        }
    }

    fun applyMounts(): Boolean {
        if (!PATCH_RUNTIME_ENABLED) {
            Log.w(TAG, "applyMounts skipped. $DISABLED_REASON")
            return false
        }

        if (!isPatchInstalled()) {
            Log.e(TAG, "Cannot apply mounts: CarPlay patches not installed")
            return false
        }
        
        try {
            Log.i(TAG, "Applying CarPlay bind mounts...")
            // 1. Unmount existing if any (aggressive)
            ShizukuUtils.runCommandAndGetOutput(arrayOf("umount", "-l", SYSTEM_APP_PATH))

            // 2. Apply Bind Mount
            val res = ShizukuUtils.runCommandAndGetOutput(arrayOf("mount", "--bind", "$PATCH_DIR/$APP_APK", SYSTEM_APP_PATH))
            if (res.contains("error", ignoreCase = true) || res.contains("failed", ignoreCase = true)) {
                Log.e(TAG, "Failed to mount CarPlay APK: $res")
            }
            
            Log.d(TAG, "Mount CarPlay Result: $res")

            // 3. Wipe dalvik cache to force reload
            ShizukuUtils.runCommandAndGetOutput(arrayOf("rm", "-rf", "/data/dalvik-cache/arm/system@app@TsCarPlayApp@TsCarPlayApp.apk*"))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("rm", "-rf", "/data/dalvik-cache/arm64/system@app@TsCarPlayApp@TsCarPlayApp.apk*"))

            // 4. Force stop app
            ShizukuUtils.runCommandAndGetOutput(arrayOf("am", "force-stop", "com.ts.carplay.app"))

            val success = isMounted()
            if (success) {
                Log.w(TAG, "CarPlay patches mounted successfully")
            } else {
                Log.e(TAG, "CarPlay mount verification failed. Check if Shizuku has root permissions.")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply CarPlay mounts", e)
            return false
        }
    }

    fun removeMounts(): Boolean {
        try {
            Log.i(TAG, "Removing CarPlay mounts...")
            ShizukuUtils.runCommandAndGetOutput(arrayOf("umount", "-l", SYSTEM_APP_PATH))
            
            ShizukuUtils.runCommandAndGetOutput(arrayOf("am", "force-stop", "com.ts.carplay.app"))
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove CarPlay mounts", e)
            return false
        }
    }
    
    fun uninstallPatches(): Boolean {
        Log.i(TAG, "Uninstalling CarPlay patches...")
        removeMounts()
        ShizukuUtils.runCommandAndGetOutput(arrayOf("rm", "-rf", PATCH_DIR))
        return !isPatchInstalled()
    }
    
    /**
     * Auto-mount patches if installed but not yet mounted.
     * Designed to be called from ForegroundService on boot after Shizuku is ready.
     * No-op if patches aren't installed or are already mounted.
     */
    fun ensureMounted() {
        if (!PATCH_RUNTIME_ENABLED) {
            Log.w(TAG, "ensureMounted skipped. $DISABLED_REASON")
            return
        }

        try {
            if (isPatchInstalled() && !isMounted()) {
                Log.i(TAG, "CarPlay patches installed but not mounted — auto-mounting...")
                val success = applyMounts()
                Log.i(TAG, "Auto-mount CarPlay result: $success")
            } else if (isMounted()) {
                Log.d(TAG, "CarPlay patches already mounted, nothing to do")
            } else {
                Log.d(TAG, "No CarPlay patches installed, skipping auto-mount")
            }
        } catch (e: Exception) {
            Log.e(TAG, "CarPlay auto-mount failed", e)
        }
    }

    fun getDiagnostics(): String {
        val id = ShizukuUtils.runCommandAndGetOutput(arrayOf("id"))
        val mounts = ShizukuUtils.runCommandAndGetOutput(arrayOf("mount"))
        val patchFiles = ShizukuUtils.runCommandAndGetOutput(arrayOf("ls", "-lR", PATCH_DIR))
        val vendorFiles = ShizukuUtils.runCommandAndGetOutput(arrayOf("ls", "-la", "/system/app/TsCarPlayApp"))
        val sb = StringBuilder()
        sb.append("ID: $id\n\n")
        sb.append("Mounts:\n$mounts\n\n")
        sb.append("Patch Files:\n$patchFiles\n\n")
        sb.append("System CarPlay Files:\n$vendorFiles\n\n")

        sb.append("--- Integrity Check ---\n")
        val systemMd5 = ShizukuUtils.runCommandAndGetOutput(arrayOf("md5sum", SYSTEM_APP_PATH)).trim().split(" ")[0]
        val patchMd5 = ShizukuUtils.runCommandAndGetOutput(arrayOf("md5sum", "$PATCH_DIR/$APP_APK")).trim().split(" ")[0]

        sb.append("$APP_APK:\n")
        sb.append("  System: $systemMd5\n")
        sb.append("  Patch:  $patchMd5\n")
        if (systemMd5 == patchMd5 && systemMd5.isNotEmpty()) {
            sb.append("  Result: MATCH (Mounted)\n")
        } else {
            sb.append("  Result: MISMATCH (Not Mounted or Failed)\n")
        }
        return sb.toString()
    }
}
