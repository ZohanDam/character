package com.zohandam.character

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

data class Release(val version: String, val page: String, val apk: String)

object Versions {
    fun parts(value: String): List<Int>? {
        val cleaned = value.removePrefix("v")
        if (!cleaned.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) return null
        return cleaned.split('.').map { it.toIntOrNull() ?: return null }
    }

    fun newer(candidate: String, installed: String): Boolean {
        val left = parts(candidate) ?: return false
        val right = parts(installed) ?: return false
        for (i in 0..2) {
            if (left[i] != right[i]) return left[i] > right[i]
        }
        return false
    }
}

object Updates {
    val repositoryUrl get() = "https://github.com/${BuildConfig.GITHUB_REPOSITORY}"

    fun latest(): Release? {
        val url = URI("https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest").toURL()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Character/${BuildConfig.VERSION_NAME}")
            when (connection.responseCode) {
                404 -> return null
                403, 429 -> error("GitHub is limiting update checks. Try again later.")
            }
            check(connection.responseCode == 200) { "GitHub could not be reached. Try again later." }
            val body = connection.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                check(text.length <= 1_000_000) { "Unexpected release response." }
                JSONObject(text)
            }
            check(!body.optBoolean("draft") && !body.optBoolean("prerelease")) { "No stable release available." }
            val tag = body.getString("tag_name")
            check(Versions.parts(tag) != null) { "Unsupported release version." }
            val assets = body.getJSONArray("assets")
            val expected = "Character-${tag.removePrefix("v")}.apk"
            var apk = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name") == expected) apk = asset.getString("browser_download_url")
            }
            if (apk.isNotEmpty()) {
                val uri = URI(apk)
                check(uri.scheme == "https" && uri.host == "github.com" &&
                    uri.path.startsWith("/${BuildConfig.GITHUB_REPOSITORY}/releases/download/")) {
                    "The release download address is invalid."
                }
            }
            return Release(tag.removePrefix("v"),
                "$repositoryUrl/releases/tag/$tag", apk)
        } finally { connection.disconnect() }
    }
}
