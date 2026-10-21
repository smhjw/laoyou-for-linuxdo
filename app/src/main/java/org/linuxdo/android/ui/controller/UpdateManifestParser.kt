package org.linuxdo.android.ui.controller

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.linuxdo.android.Config
import org.linuxdo.android.ui.UpdateInfo

/**
 * 版本清单与 GitHub Release 的解析。纯函数,不碰网络与 Android API,便于单测。
 *
 * 必升级触发规则(满足任一即为必升级):
 * 1. `version.json` 中 `forceUpdate: true`
 * 2. `version.json` 中 `minVersionCode` 大于当前 `versionCode`(或 `minVersionName` 高于当前版本)
 * 3. 回退 Release 正文包含 `[force]`、`[mandatory]`、`[必升]`、`[min_version: X.Y.Z]` 或 `[min_code: N]`
 */
object UpdateManifestParser {

    /**
     * 解析 `version.json`(也兼容 GitHub contents 接口返回的 base64 包装)。
     * 返回值第一项表示清单本身是否可读;可读但没有更新时第二项为 null。
     */
    fun parseVersionManifest(
        rawJson: String,
        currentVersion: String,
        currentCode: Int,
    ): Pair<Boolean, UpdateInfo?> {
        val outer = runCatching { Json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return false to null
        val root = if (outer["versionName"] == null && outer["encoding"]?.jsonPrimitive?.contentOrNull == "base64") {
            val encoded = outer["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            // GitHub 的 base64 每 60 字符插一个换行,MIME 解码器会跳过它们。
            val decoded = runCatching {
                String(java.util.Base64.getMimeDecoder().decode(encoded), Charsets.UTF_8)
            }.getOrNull().orEmpty()
            runCatching { Json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return false to null
        } else {
            outer
        }

        val latestVersion = root["versionName"]?.jsonPrimitive?.contentOrNull
            ?.removePrefix("v")?.removePrefix("V")?.substringBefore("-")?.trim().orEmpty()
        if (latestVersion.isEmpty()) return false to null

        val latestCode = root["versionCode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val isNewer = if (latestCode > 0 && currentCode > 0) {
            latestCode > currentCode || compareVersions(latestVersion, currentVersion) > 0
        } else {
            compareVersions(latestVersion, currentVersion) > 0
        }
        if (!isNewer) return true to null

        val title = root["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: "v$latestVersion 版本更新"
        val changelogElement = root["changelog"]
        val changelog = when {
            changelogElement == null -> ""
            runCatching { changelogElement.jsonArray }.isSuccess -> {
                changelogElement.jsonArray
                    .mapNotNull { runCatching { it.jsonPrimitive.contentOrNull?.trim() }.getOrNull() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
            }
            else -> runCatching { changelogElement.jsonPrimitive.contentOrNull?.trim().orEmpty() }.getOrDefault("")
        }.ifBlank { "优化使用体验并修复已知问题。" }

        val forceUpdate = root["forceUpdate"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val minVersionCode = root["minVersionCode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val minVersionName = root["minVersionName"]?.jsonPrimitive?.contentOrNull
            ?.removePrefix("v")?.removePrefix("V")?.trim()
        val mandatory = forceUpdate ||
            (minVersionCode != null && currentCode < minVersionCode) ||
            (!minVersionName.isNullOrBlank() && compareVersions(currentVersion, minVersionName) < 0)

        val apkUrl = root["apkUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: Config.UPDATE_RELEASES_PAGE_URL
        val releasePageUrl = root["releasePageUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: Config.UPDATE_RELEASES_PAGE_URL
        // 可选校验和:留空或缺省即跳过校验,不阻塞发版。
        val apkSha256 = root["apkSha256"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        return true to UpdateInfo(
            latestVersion = latestVersion,
            title = title,
            changelog = changelog,
            downloadUrl = apkUrl,
            releasePageUrl = releasePageUrl,
            mandatory = mandatory,
            apkSha256 = apkSha256,
        )
    }

    fun parseReleaseUpdateInfo(
        rawJson: String,
        currentVersion: String,
        currentCode: Int,
    ): UpdateInfo? {
        val root = runCatching { Json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
        val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val latestVersion = tagName.removePrefix("v").removePrefix("V").trim()
        if (latestVersion.isEmpty() || compareVersions(latestVersion, currentVersion) <= 0) {
            return null
        }
        val releaseName = root["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: "v$latestVersion 版本更新"
        val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: Config.UPDATE_RELEASES_PAGE_URL
        val assets = root["assets"]?.jsonArray.orEmpty()
        val apkUrl = assets.firstNotNullOfOrNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val url = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) url else null
        } ?: htmlUrl

        val rawBody = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return buildUpdateInfo(latestVersion, releaseName, rawBody, apkUrl, htmlUrl, currentVersion, currentCode)
    }

    fun parseReleaseHtmlFallback(
        finalUrl: String,
        html: String,
        currentVersion: String,
        currentCode: Int,
    ): UpdateInfo? {
        val tagName = finalUrl.substringAfter("/releases/tag/", "").substringBefore("?").substringBefore("#").trim()
            .ifBlank {
                Regex("""/releases/tag/(v?[0-9][^"'/\s<>]*)""").find(html)?.groupValues?.getOrNull(1).orEmpty()
            }
        val latestVersion = tagName.removePrefix("v").removePrefix("V").substringBefore("-").trim()
        if (latestVersion.isEmpty() || compareVersions(latestVersion, currentVersion) <= 0) {
            return null
        }
        val repoBase = Config.UPDATE_RELEASES_PAGE_URL.substringBefore("/releases")
        val apkUrl = "$repoBase/releases/download/$tagName/linuxdo-$latestVersion-release.apk"
        val bodyText = runCatching {
            org.jsoup.Jsoup.parse(html).select(".markdown-body").firstOrNull()?.wholeText()?.trim().orEmpty()
        }.getOrDefault("")
        return buildUpdateInfo(
            latestVersion = latestVersion,
            releaseName = "v$latestVersion 版本更新",
            rawBody = bodyText,
            apkUrl = apkUrl,
            htmlUrl = finalUrl.ifBlank { Config.UPDATE_RELEASES_PAGE_URL },
            currentVersion = currentVersion,
            currentCode = currentCode,
        )
    }

    private fun buildUpdateInfo(
        latestVersion: String,
        releaseName: String,
        rawBody: String,
        apkUrl: String,
        htmlUrl: String,
        currentVersion: String,
        currentCode: Int,
    ): UpdateInfo {
        val hasForceFlag = Regex("""\[(force|mandatory|必升)]|force_update\s*:\s*true""", RegexOption.IGNORE_CASE)
            .containsMatchIn(rawBody)
        val minVersionMatch = Regex("""\[min_version\s*:\s*v?([0-9.]+)]""", RegexOption.IGNORE_CASE)
            .find(rawBody)?.groupValues?.getOrNull(1)
        val minCodeMatch = Regex("""\[min_code\s*:\s*(\d+)]""", RegexOption.IGNORE_CASE)
            .find(rawBody)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val belowMinVersion = minVersionMatch != null && compareVersions(currentVersion, minVersionMatch) < 0
        val belowMinCode = minCodeMatch != null && currentCode < minCodeMatch
        val mandatory = hasForceFlag || belowMinVersion || belowMinCode

        val cleanChangelog = rawBody
            .replace(Regex("""\[(force|mandatory|必升)]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[min_version\s*:\s*v?[0-9.]+]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[min_code\s*:\s*\d+]""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { "优化使用体验并修复已知问题。" }

        return UpdateInfo(
            latestVersion = latestVersion,
            title = releaseName,
            changelog = cleanChangelog,
            downloadUrl = apkUrl,
            releasePageUrl = htmlUrl,
            mandatory = mandatory,
        )
    }

    fun compareVersions(v1: String, v2: String): Int {
        val s1 = v1.removePrefix("v").removePrefix("V").trim()
        val s2 = v2.removePrefix("v").removePrefix("V").trim()
        val parts1 = s1.substringBefore("-").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val parts2 = s2.substringBefore("-").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        // 当数字一致时(如 1.3.0 与 1.3.0-beta),正式版高于预发尝鲜版 (SemVer 规范)
        val isPre1 = s1.contains("beta", ignoreCase = true) || s1.contains("alpha", ignoreCase = true) || s1.contains("preview", ignoreCase = true)
        val isPre2 = s2.contains("beta", ignoreCase = true) || s2.contains("alpha", ignoreCase = true) || s2.contains("preview", ignoreCase = true)
        if (!isPre1 && isPre2) return 1
        if (isPre1 && !isPre2) return -1
        return 0
    }
}
