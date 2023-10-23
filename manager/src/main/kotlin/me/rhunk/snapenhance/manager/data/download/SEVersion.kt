package me.rhunk.snapenhance.manager.data.download

data class SEVersion(
    val versionName: String,
    val releaseDate: String,
    val downloadAssets: Map<String, SEArtifact>,
)