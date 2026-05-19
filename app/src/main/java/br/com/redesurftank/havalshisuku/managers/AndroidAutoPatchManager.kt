package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.util.Log
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import java.io.File
import java.io.FileOutputStream

object AndroidAutoPatchManager {
    private const val TAG = "AA_PATCH_MGR"
    private const val PATCH_DIR = "/data/local/tmp/aa_patches"
    private const val SERVICE_APK = "AndroidAutoService.apk"
    private const val APP_APK = "AndroidAutoApp.apk"

    const val VENDOR_SERVICE_PATH = "/vendor/app/AndroidAutoService/AndroidAutoService.apk"
    const val VENDOR_APP_PATH = "/vendor/app/AndroidAutoApp/AndroidAutoApp.apk"
    
    const val VENDOR_SERVICE_OAT = "/vendor/app/AndroidAutoService/oat"
    const val VENDOR_APP_OAT = "/vendor/app/AndroidAutoApp/oat"

    fun isPatchInstalled(): Boolean {
        val output = ShizukuUtils.runCommandAndGetOutput(arrayOf("ls", "-l", PATCH_DIR))
        val installed = output.contains(SERVICE_APK) && output.contains(APP_APK)
        Log.d(TAG, "isPatchInstalled: $installed (ls output: $output)")
        return installed
    }

    fun isMounted(): Boolean {
        // Use MD5 comparison for reliable mount detection
        val vendorAppMd5 = ShizukuUtils.runCommandAndGetOutput(arrayOf("md5sum", VENDOR_APP_PATH)).trim().split(" ")[0]
        val patchAppMd5 = ShizukuUtils.runCommandAndGetOutput(arrayOf("md5sum", "$PATCH_DIR/$APP_APK")).trim().split(" ")[0]
        
        val vendorServiceMd5 = ShizukuUtils.runCommandAndGetOutput(arrayOf("md5sum", VENDOR_SERVICE_PATH)).trim().split(" ")[0]
        val patchServiceMd5 = ShizukuUtils.runCommandAndGetOutput(arrayOf("md5sum", "$PATCH_DIR/$SERVICE_APK")).trim().split(" ")[0]

        val appMounted = vendorAppMd5 == patchAppMd5 && vendorAppMd5.isNotEmpty()
        val serviceMounted = vendorServiceMd5 == patchServiceMd5 && vendorServiceMd5.isNotEmpty()
        
        val mounted = serviceMounted && appMounted
        Log.d(TAG, "isMounted (MD5): $mounted (Service: $serviceMounted, App: $appMounted)")
        return mounted
    }

    fun installPatches(context: Context): Boolean {
        try {
            Log.i(TAG, "Starting patch installation...")
            ShizukuUtils.runCommandAndGetOutput(arrayOf("mkdir", "-p", PATCH_DIR))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("chmod", "777", PATCH_DIR))
            
            // Create empty oat dir
            ShizukuUtils.runCommandAndGetOutput(arrayOf("mkdir", "-p", "$PATCH_DIR/empty_oat"))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("chmod", "777", "$PATCH_DIR/empty_oat"))

            val assets = arrayOf(SERVICE_APK, APP_APK)
            for (assetName in assets) {
                Log.d(TAG, "Copying asset: $assetName")
                val inputStream = context.assets.open("aa_patches/$assetName")
                val tempFile = File(context.cacheDir, assetName)
                val outputStream = FileOutputStream(tempFile)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                val destPath = "$PATCH_DIR/$assetName"
                val cpOut = ShizukuUtils.runCommandAndGetOutput(arrayOf("cp", tempFile.absolutePath, destPath))
                Log.d(TAG, "Copy output for $assetName: $cpOut")
                
                ShizukuUtils.runCommandAndGetOutput(arrayOf("chmod", "644", destPath))
                ShizukuUtils.runCommandAndGetOutput(arrayOf("chcon", "u:object_r:vendor_app_file:s0", destPath))
                tempFile.delete()
            }
            val success = isPatchInstalled()
            Log.i(TAG, "Installation success: $success")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install patches", e)
            return false
        }
    }

    fun applyMounts(): Boolean {
        if (!isPatchInstalled()) {
            Log.e(TAG, "Cannot apply mounts: Patches not installed")
            return false
        }
        
        try {
            Log.i(TAG, "Applying bind mounts...")
            // 1. Unmount existing if any (aggressive)
            ShizukuUtils.runCommandAndGetOutput(arrayOf("umount", "-l", VENDOR_SERVICE_PATH))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("umount", "-l", VENDOR_APP_PATH))
            
            if (ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "[ -d $VENDOR_SERVICE_OAT ] && echo exists")).contains("exists")) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("umount", "-l", VENDOR_SERVICE_OAT))
            }
            if (ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "[ -d $VENDOR_APP_OAT ] && echo exists")).contains("exists")) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("umount", "-l", VENDOR_APP_OAT))
            }

            // 2. Apply Bind Mounts
            val res1 = ShizukuUtils.runCommandAndGetOutput(arrayOf("mount", "--bind", "$PATCH_DIR/$SERVICE_APK", VENDOR_SERVICE_PATH))
            if (res1.contains("error", ignoreCase = true) || res1.contains("failed", ignoreCase = true)) {
                Log.e(TAG, "Failed to mount Service APK: $res1")
            }
            
            val res2 = ShizukuUtils.runCommandAndGetOutput(arrayOf("mount", "--bind", "$PATCH_DIR/$APP_APK", VENDOR_APP_PATH))
            if (res2.contains("error", ignoreCase = true) || res2.contains("failed", ignoreCase = true)) {
                Log.e(TAG, "Failed to mount App APK: $res2")
            }
            
            // Mount empty oat only if target exists
            val checkOat1 = ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "[ -d $VENDOR_SERVICE_OAT ] && echo exists"))
            if (checkOat1.contains("exists")) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("mount", "--bind", "$PATCH_DIR/empty_oat", VENDOR_SERVICE_OAT))
            }

            val checkOat2 = ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "[ -d $VENDOR_APP_OAT ] && echo exists"))
            if (checkOat2.contains("exists")) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("mount", "--bind", "$PATCH_DIR/empty_oat", VENDOR_APP_OAT))
            }
            
            Log.d(TAG, "Mount Service Result: $res1")
            Log.d(TAG, "Mount App Result: $res2")

            // 3. Wipe dalvik cache to force reload
            ShizukuUtils.runCommandAndGetOutput(arrayOf("rm", "-rf", "/data/dalvik-cache/arm64/*AndroidAuto*"))

            // 4. Force stop apps
            ShizukuUtils.runCommandAndGetOutput(arrayOf("am", "force-stop", "com.ts.androidauto"))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("am", "force-stop", "com.ts.androidauto.app"))

            val success = isMounted()
            if (success) {
                Log.w(TAG, "Patches mounted successfully")
            } else {
                Log.e(TAG, "Mount verification failed. Check if Shizuku has root permissions.")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply mounts", e)
            return false
        }
    }

    fun removeMounts(): Boolean {
        try {
            Log.i(TAG, "Removing mounts...")
            ShizukuUtils.runCommandAndGetOutput(arrayOf("umount", "-l", VENDOR_SERVICE_PATH))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("umount", "-l", VENDOR_APP_PATH))
            
            if (ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "[ -d $VENDOR_SERVICE_OAT ] && echo exists")).contains("exists")) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("umount", "-l", VENDOR_SERVICE_OAT))
            }
            if (ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "[ -d $VENDOR_APP_OAT ] && echo exists")).contains("exists")) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("umount", "-l", VENDOR_APP_OAT))
            }
            
            ShizukuUtils.runCommandAndGetOutput(arrayOf("am", "force-stop", "com.ts.androidauto"))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("am", "force-stop", "com.ts.androidauto.app"))
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove mounts", e)
            return false
        }
    }
    
    fun uninstallPatches(): Boolean {
        Log.i(TAG, "Uninstalling patches...")
        removeMounts()
        val res = ShizukuUtils.runCommandAndGetOutput(arrayOf("rm", "-rf", PATCH_DIR))
        return !isPatchInstalled()
    }
    
    /**
     * Auto-mount patches if installed but not yet mounted.
     * Designed to be called from ForegroundService on boot after Shizuku is ready.
     * No-op if patches aren't installed or are already mounted.
     */
    fun ensureMounted() {
        try {
            if (isPatchInstalled() && !isMounted()) {
                Log.i(TAG, "Patches installed but not mounted — auto-mounting...")
                val success = applyMounts()
                Log.i(TAG, "Auto-mount result: $success")
            } else if (isMounted()) {
                Log.d(TAG, "Patches already mounted, nothing to do")
            } else {
                Log.d(TAG, "No patches installed, skipping auto-mount")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-mount failed", e)
        }
    }

    fun getDiagnostics(): String {
        val id = ShizukuUtils.runCommandAndGetOutput(arrayOf("id"))
        val mounts = ShizukuUtils.runCommandAndGetOutput(arrayOf("mount"))
        val patchFiles = ShizukuUtils.runCommandAndGetOutput(arrayOf("ls", "-lR", PATCH_DIR))
        val vendorFiles = ShizukuUtils.runCommandAndGetOutput(arrayOf("ls", "-lR", "/vendor/app/AndroidAuto*"))
        val sb = StringBuilder()
        sb.append("ID: $id\n\n")
        sb.append("Mounts:\n$mounts\n\n")
        sb.append("Patch Files:\n$patchFiles\n\n")
        sb.append("Vendor Files:\n$vendorFiles\n\n")

        sb.append("--- Integrity Check ---\n")
        sb.append("OAT Mounts:\n")
        val oatCheck1 = ShizukuUtils.runCommandAndGetOutput(arrayOf("ls", "/vendor/app/AndroidAutoService/oat")).trim()
        val oatCheck2 = ShizukuUtils.runCommandAndGetOutput(arrayOf("ls", "/vendor/app/AndroidAutoApp/oat")).trim()
        sb.append("  Service OAT: ${if (oatCheck1.isEmpty()) "EMPTY (OK)" else "NOT EMPTY (FAILED)"}\n")
        sb.append("  App OAT:     ${if (oatCheck2.isEmpty()) "EMPTY (OK)" else "NOT EMPTY (FAILED)"}\n\n")
        val targets = arrayOf(
            "/vendor/app/AndroidAutoService/AndroidAutoService.apk",
            "/vendor/app/AndroidAutoApp/AndroidAutoApp.apk"
        )
        for (target in targets) {
            val vendorMd5 = ShizukuUtils.runCommandAndGetOutput(arrayOf("md5sum", target)).trim().split(" ")[0]
            val fileName = target.substring(target.lastIndexOf("/") + 1)
            val patchMd5 = ShizukuUtils.runCommandAndGetOutput(arrayOf("md5sum", "$PATCH_DIR/$fileName")).trim().split(" ")[0]

            sb.append("$fileName:\n")
            sb.append("  Vendor: $vendorMd5\n")
            sb.append("  Patch:  $patchMd5\n")
            if (vendorMd5 == patchMd5 && vendorMd5.isNotEmpty()) {
                sb.append("  Result: MATCH (Mounted)\n")
            } else {
                sb.append("  Result: MISMATCH (Not Mounted or Failed)\n")
            }
        }
        return sb.toString()
    }
}
