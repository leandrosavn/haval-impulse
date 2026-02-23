package br.com.redesurftank.havalshisuku.models

data class ReleaseInfo(
    val tag: String,
    val downloadUrl: String,
    val isPrerelease: Boolean
)

data class UpdateCheckResult(
    val latestRelease: ReleaseInfo?,
    val latestPreview: ReleaseInfo?
)
