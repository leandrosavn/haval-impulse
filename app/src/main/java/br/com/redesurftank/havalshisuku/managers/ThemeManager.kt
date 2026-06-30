package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.util.Log
import android.util.Xml
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.ThemeMetadata
import br.com.redesurftank.havalshisuku.models.ThemeVersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
        // Temas hospedados no fork do usuário (leandrosavn) — assim controlamos os temas e os
        // ajustes chegam ao carro via re-download. (Era netseek/feature-new-screen-enhancements-v6.)
        const val THEME_REPO_URL = "https://github.com/leandrosavn/haval-impulse/tree/master/cluster-widgets/Themes"
        // Busca/baixa via raw.githubusercontent (CDN, SEM o limite de 60 req/h da api.github).
        // Lista os temas por um manifesto themes.json em vez de listar pastas pela API.
        const val RAW_BASE = "https://raw.githubusercontent.com/leandrosavn/haval-impulse/master/cluster-widgets/Themes/"

        @Volatile
        private var instance: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            return instance ?: synchronized(this) {
                instance ?: ThemeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getLocalThemes(): List<ThemeMetadata> {
        val results = mutableListOf<ThemeMetadata>()
        
        // Scan for subdirectories in themesDir
        themesDir.listFiles { file -> file.isDirectory }?.forEach { dir ->
            val xmlFile = File(dir, "theme.xml")
            if (xmlFile.exists()) {
                val metadata = parseThemeXml(xmlFile.inputStream(), dir.name, true)
                if (metadata != null) {
                    results.add(metadata.copy(isLocal = true, isDownloaded = true))
                }
            }
        }
        
        // Handle legacy flat HTML files if any (fallback)
        themesDir.listFiles { file -> file.extension == "html" }?.forEach { file ->
            results.add(
                ThemeMetadata(
                    name = file.nameWithoutExtension,
                    description = "Arraste e solte para instalar",
                    version = "1.0.0",
                    thumbnailUrl = "",
                    mainFile = file.name,
                    folderName = "",
                    isLocal = true,
                    isDownloaded = true
                )
            )
        }
        
        return results
    }

    fun getThemeMetadata(folderName: String): ThemeMetadata? {
        val dir = File(themesDir, folderName)
        if (!dir.exists()) return null
        val xmlFile = File(dir, "theme.xml")
        return if (xmlFile.exists()) {
            parseThemeXml(xmlFile.inputStream(), folderName, true)
        } else null
    }

    private fun parseThemeXml(inputStream: InputStream, folderName: String, isLocal: Boolean): ThemeMetadata? {
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            
            var name = ""
            var description = ""
            var version = ""
            var thumbnail = ""
            var mainFile = "index.html"
            var x: Int? = null
            var y: Int? = null
            var width: Int? = null
            var height: Int? = null
            
            var inAppDefaultPosition = false
            
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                val eventType = parser.eventType
                val tagName = parser.name
                
                if (eventType == XmlPullParser.START_TAG) {
                    when (tagName) {
                        "name" -> name = parser.nextText()
                        "description" -> description = parser.nextText()
                        "version" -> version = parser.nextText()
                        "thumbnail" -> thumbnail = parser.nextText()
                        "mainFile" -> mainFile = parser.nextText()
                        "AppDefaultPosition" -> inAppDefaultPosition = true
                        "x" -> if (inAppDefaultPosition) x = parser.nextText().toIntOrNull()
                        "y" -> if (inAppDefaultPosition) y = parser.nextText().toIntOrNull()
                        "width" -> if (inAppDefaultPosition) width = parser.nextText().toIntOrNull()
                        "height" -> if (inAppDefaultPosition) height = parser.nextText().toIntOrNull()
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (tagName == "AppDefaultPosition") {
                        inAppDefaultPosition = false
                    }
                }
            }
            
            if (name.isEmpty()) return null
            
            // Resolve thumbnail path
            val resolvedThumbnail = if (isLocal && !thumbnail.startsWith("http")) {
                File(File(themesDir, folderName), thumbnail).absolutePath
            } else {
                thumbnail
            }
            
            return ThemeMetadata(
                name = name,
                description = description,
                version = version,
                thumbnailUrl = resolvedThumbnail,
                mainFile = mainFile,
                folderName = folderName,
                isLocal = isLocal,
                x = x,
                y = y,
                width = width,
                height = height
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing theme.xml in $folderName", e)
            return null
        } finally {
            inputStream.close()
        }
    }

    suspend fun fetchThemesFromGithub(repoUrl: String): List<ThemeMetadata> {
        return withContext(Dispatchers.IO) {
            try {
                // Lê o manifesto themes.json via raw (CDN — SEM o limite de 60 req/h da api.github).
                val url = URL(RAW_BASE + "themes.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 15000

                if (conn.responseCode != 200) {
                    Log.e(TAG, "Failed to fetch themes manifest: ${conn.responseCode}")
                    return@withContext emptyList<ThemeMetadata>()
                }

                val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                val array = org.json.JSONObject(jsonString).getJSONArray("themes")
                val results = mutableListOf<ThemeMetadata>()

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val folder = obj.getString("folder")
                    val thumb = obj.optString("thumbnail", "thumbnail.png")
                    results.add(
                        ThemeMetadata(
                            name = obj.getString("name"),
                            description = obj.optString("description", ""),
                            version = obj.optString("version", ""),
                            thumbnailUrl = RAW_BASE + folder + "/" + thumb,
                            mainFile = obj.optString("mainFile", "index.html"),
                            folderName = folder,
                            isLocal = false
                        )
                    )
                }
                results
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching themes manifest", e)
                emptyList<ThemeMetadata>()
            }
        }
    }

    private suspend fun fetchThemeMetadataFromGithub(folderApiUrl: String, folderName: String): ThemeMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(folderApiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (conn.responseCode != 200) return@withContext null
                
                val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonString)
                var themeXmlDownloadUrl = ""
                
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.getString("name") == "theme.xml") {
                        themeXmlDownloadUrl = obj.getString("download_url")
                        break
                    }
                }
                
                if (themeXmlDownloadUrl.isEmpty()) return@withContext null
                
                // Fetch the theme.xml content
                val xmlUrl = URL(themeXmlDownloadUrl)
                val xmlConn = xmlUrl.openConnection() as HttpURLConnection
                val metadata = parseThemeXml(xmlConn.inputStream, folderName, false)
                
                if (metadata != null) {
                    // Resolve relative thumbnail URL to absolute GitHub raw URL if needed
                    val resolvedThumbnail = if (!metadata.thumbnailUrl.startsWith("http")) {
                        // Assuming thumbnail is in the same folder
                        themeXmlDownloadUrl.replace("theme.xml", metadata.thumbnailUrl)
                    } else {
                        metadata.thumbnailUrl
                    }
                    return@withContext metadata.copy(thumbnailUrl = resolvedThumbnail)
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching metadata for $folderName", e)
                null
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

    suspend fun downloadTheme(metadata: ThemeMetadata): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val destDir = File(themesDir, metadata.folderName)
                if (!destDir.exists()) destDir.mkdirs()

                // Baixa via raw (CDN, SEM rate limit da api.github). Arquivos padrão do tema.
                val files = listOf(metadata.mainFile, "theme.xml", "thumbnail.png")
                for (fileName in files) {
                    val fileConn =
                            URL(RAW_BASE + metadata.folderName + "/" + fileName).openConnection()
                                    as HttpURLConnection
                    fileConn.connectTimeout = 15000
                    fileConn.readTimeout = 30000
                    if (fileConn.responseCode != 200) {
                        if (fileName == "thumbnail.png") continue // thumbnail é opcional
                        Log.e(TAG, "Failed to download $fileName: ${fileConn.responseCode}")
                        return@withContext false
                    }
                    val destFile = File(destDir, fileName)
                    BufferedInputStream(fileConn.inputStream).use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading theme: ${metadata.name}", e)
                false
            }
        }
    }

    fun getThemeFile(folderName: String, filename: String): File? {
        val file = File(File(themesDir, folderName), filename)
        return if (file.exists()) file else null
    }
    
    fun isNewerVersion(current: String, remote: String): Boolean {
        if (current == remote) return false
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(currentParts.size, remoteParts.size)) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (c > r) return false
        }
        return false
    }

    fun deleteTheme(folderName: String): Boolean {
        val dir = File(themesDir, folderName)
        return if (dir.exists()) dir.deleteRecursively() else false
    }
}
