package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.ThemeVersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ThemeManager private constructor(val context: Context) {
    private val TAG = "ThemeManager"
    private val themesDir = File(context.filesDir, "themes")

    init {
        if (!themesDir.exists()) {
            themesDir.mkdirs()
        }
    }

    companion object {
        private const val TAG = "ThemeManager"
        const val THEME_REPO_URL = "https://github.com/netseek/haval-app-tool-multimidia/tree/feature/new-screen-enhancements-v6/cluster-widgets/Themes"
        
        @Volatile
        private var instance: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            return instance ?: synchronized(this) {
                instance ?: ThemeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getLocalThemes(): List<File> {
        return themesDir.listFiles { file -> file.extension == "html" }?.toList() ?: emptyList()
    }

    suspend fun fetchThemesFromGithub(repoUrl: String): List<ThemeVersionInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val apiUrl = convertToGithubApiUrl(repoUrl)
                Log.d(TAG, "Fetching themes from API: $apiUrl")
                
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (conn.responseCode != 200) {
                    val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(TAG, "Failed to fetch themes: ${conn.responseCode} - $errorBody")
                    return@withContext emptyList<ThemeVersionInfo>()
                }
                
                parseGithubContents(conn.inputStream.bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching themes from GitHub", e)
                emptyList<ThemeVersionInfo>()
            }
        }
    }

    private fun convertToGithubApiUrl(webUrl: String): String {
        // Handle webUrl examples:
        // 1. https://github.com/user/repo
        // 2. https://github.com/user/repo/tree/main/folder
        // 3. https://github.com/user/repo/tree/master/folder/sub
        
        var url = webUrl.trim()
        if (url.endsWith("/")) url = url.substring(0, url.length - 1)
        
        if (!url.startsWith("https://github.com/")) return url // Already an API URL or invalid
        
        val parts = url.replace("https://github.com/", "").split("/")
        if (parts.size < 2) return url
        
        val owner = parts[0]
        val repo = parts[1]
        
        return if (parts.size >= 5 && parts[2] == "tree") {
            // Check for branch patterns with slashes (e.g., feature/name)
            if (parts.size >= 6 && (parts[3] == "feature" || parts[3] == "fix" || parts[3] == "release")) {
                val branch = "${parts[3]}/${parts[4]}"
                val path = parts.subList(5, parts.size).joinToString("/")
                "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch"
            } else {
                val branch = parts[3]
                val path = parts.subList(4, parts.size).joinToString("/")
                "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch"
            }
        } else {
            "https://api.github.com/repos/$owner/$repo/contents"
        }
    }

    private fun parseGithubContents(jsonString: String): List<ThemeVersionInfo> {
        val results = mutableListOf<ThemeVersionInfo>()
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.getString("name")
            if (name.endsWith(".html")) {
                results.add(
                    ThemeVersionInfo(
                        name = name,
                        downloadUrl = obj.getString("download_url"),
                        size = obj.getLong("size")
                    )
                )
            }
        }
        return results
    }

    suspend fun downloadTheme(theme: ThemeVersionInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val destFile = File(themesDir, theme.name)
                val url = URL(theme.downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                
                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading theme: ${theme.name}", e)
                false
            }
        }
    }

    fun getThemeFile(filename: String): File? {
        val file = File(themesDir, filename)
        return if (file.exists()) file else null
    }
    
    fun deleteTheme(filename: String): Boolean {
        val file = File(themesDir, filename)
        return if (file.exists()) file.delete() else false
    }
}
