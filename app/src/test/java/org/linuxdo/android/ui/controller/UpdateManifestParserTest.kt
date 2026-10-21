package org.linuxdo.android.ui.controller

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.linuxdo.android.ui.controller.UpdateManifestParser.compareVersions

class UpdateManifestParserTest {

    private val manifest = """
        {
          "versionCode": 14,
          "versionName": "1.4.0",
          "title": "v1.4.0",
          "changelog": ["1、新界面", " ", "2、修复"],
          "apkUrl": "https://example.com/app.apk",
          "apkSha256": "abc123"
        }
    """.trimIndent()

    @Test
    fun compareVersions_ordersNumericParts() {
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(compareVersions("v1.3.1", "1.3.0") > 0)
        assertEquals(0, compareVersions("1.3", "1.3.0"))
    }

    @Test
    fun compareVersions_releaseBeatsPrerelease() {
        assertTrue(compareVersions("1.3.0", "1.3.0-beta") > 0)
        assertTrue(compareVersions("1.3.0-alpha", "1.3.0") < 0)
    }

    @Test
    fun manifest_newerVersionIsParsed() {
        val (parsed, info) = UpdateManifestParser.parseVersionManifest(manifest, "1.3.1", 13)
        assertTrue(parsed)
        requireNotNull(info)
        assertEquals("1.4.0", info.latestVersion)
        assertEquals("1、新界面\n2、修复", info.changelog)
        assertEquals("https://example.com/app.apk", info.downloadUrl)
        assertEquals("abc123", info.apkSha256)
        assertFalse(info.mandatory)
    }

    @Test
    fun manifest_sameVersionIsReadableButNoUpdate() {
        val (parsed, info) = UpdateManifestParser.parseVersionManifest(manifest, "1.4.0", 14)
        assertTrue(parsed)
        assertNull(info)
    }

    @Test
    fun manifest_minVersionCodeMakesItMandatory() {
        val forced = manifest.replace("\"versionCode\": 14,", "\"versionCode\": 14, \"minVersionCode\": 14,")
        val (_, info) = UpdateManifestParser.parseVersionManifest(forced, "1.3.1", 13)
        assertTrue(requireNotNull(info).mandatory)
    }

    @Test
    fun manifest_githubContentsBase64WithLineBreaksIsDecoded() {
        // GitHub contents 接口每 60 字符换一次行。
        val encoded = Base64.getEncoder().encodeToString(manifest.toByteArray()).chunked(60).joinToString("\n")
        val wrapped = """{"encoding": "base64", "content": "${encoded.replace("\n", "\\n")}"}"""
        val (parsed, info) = UpdateManifestParser.parseVersionManifest(wrapped, "1.3.1", 13)
        assertTrue(parsed)
        assertEquals("1.4.0", requireNotNull(info).latestVersion)
    }

    @Test
    fun manifest_garbageIsNotParsed() {
        assertEquals(false to null, UpdateManifestParser.parseVersionManifest("<html>", "1.3.1", 13))
    }

    @Test
    fun release_forceFlagAndApkAssetAreRead() {
        val release = """
            {"tag_name": "v1.5.0", "name": "1.5.0", "html_url": "https://example.com/r",
             "body": "[force] 重要修复",
             "assets": [{"name": "notes.txt", "browser_download_url": "https://x/notes.txt"},
                        {"name": "app.apk", "browser_download_url": "https://x/app.apk"}]}
        """.trimIndent()
        val info = requireNotNull(UpdateManifestParser.parseReleaseUpdateInfo(release, "1.3.1", 13))
        assertTrue(info.mandatory)
        assertEquals("https://x/app.apk", info.downloadUrl)
        assertEquals("重要修复", info.changelog)
    }
}
