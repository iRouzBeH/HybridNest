package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.feature.dashboard.GitHubReleaseInfo
import dev.rouzbeh.hybridvpn.feature.dashboard.GitHubUpdateChecker
import dev.rouzbeh.hybridvpn.feature.dashboard.UpdateCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {

    // =========================================================================
    // 1. Version Normalization & Semantic Version Comparison
    // =========================================================================

    @Test
    fun normalizeVersion_stripsPrefixesAndWhitespace() {
        assertEquals("1.0", GitHubUpdateChecker.normalizeVersion("1.0"))
        assertEquals("1.1", GitHubUpdateChecker.normalizeVersion("v1.1"))
        assertEquals("1.2.3", GitHubUpdateChecker.normalizeVersion("  V1.2.3  "))
        assertEquals("2.0-beta.1", GitHubUpdateChecker.normalizeVersion("v2.0-beta.1"))
    }

    @Test
    fun compareVersions_olderThanNewerRelease_returnsNegative() {
        // "1.0" vs "v1.1" -> < 0 (update available)
        assertTrue(GitHubUpdateChecker.compareVersions("1.0", "v1.1") < 0)
        // "1.0" vs "v1.0.1" -> < 0 (update available)
        assertTrue(GitHubUpdateChecker.compareVersions("1.0", "v1.0.1") < 0)
    }

    @Test
    fun compareVersions_equivalentVersions_returnsZero() {
        // "1.1" vs "v1.1.0" -> 0 (up to date)
        assertEquals(0, GitHubUpdateChecker.compareVersions("1.1", "v1.1.0"))
        assertEquals(0, GitHubUpdateChecker.compareVersions("v1.1", "1.1.0.0"))
    }

    @Test
    fun compareVersions_numericComparison_notStringComparison() {
        // "1.2" vs "v1.10" -> < 0 (numeric, not string comparison)
        assertTrue(GitHubUpdateChecker.compareVersions("1.2", "v1.10") < 0)
        assertTrue(GitHubUpdateChecker.compareVersions("1.10", "1.2") > 0)
    }

    @Test
    fun compareVersions_installedNewerThanRelease_returnsPositive() {
        // "1.1" vs "v1.0.9" -> > 0 (installed newer than release)
        assertTrue(GitHubUpdateChecker.compareVersions("1.1", "v1.0.9") > 0)
    }

    @Test
    fun compareVersions_preReleaseVsStable() {
        // "1.1.0-beta.1" vs "v1.1.0" -> < 0 (stable > pre-release)
        assertTrue(GitHubUpdateChecker.compareVersions("1.1.0-beta.1", "v1.1.0") < 0)
        // "1.1.0" vs "v1.1.0-rc1" -> > 0 (stable > pre-release)
        assertTrue(GitHubUpdateChecker.compareVersions("1.1.0", "v1.1.0-rc1") > 0)
        // "1.1.0-alpha" vs "1.1.0-beta" -> < 0
        assertTrue(GitHubUpdateChecker.compareVersions("1.1.0-alpha", "1.1.0-beta") < 0)
    }

    @Test
    fun compareVersions_ignoresBuildMetadata() {
        // "v1.1.0+build12" vs "1.1" -> 0 (build metadata ignored)
        assertEquals(0, GitHubUpdateChecker.compareVersions("v1.1.0+build12", "1.1"))
        assertEquals(0, GitHubUpdateChecker.compareVersions("1.1.0+sha.abcdef", "v1.1.0+sha.123456"))
    }

    // =========================================================================
    // 2. Safe URL Sanitization
    // =========================================================================

    @Test
    fun sanitizeReleaseUrl_validUrl_preserved() {
        val validUrl = "https://github.com/iRouzBeH/HybridNest/releases/tag/v1.1"
        assertEquals(validUrl, GitHubUpdateChecker.sanitizeReleaseUrl(validUrl))
    }

    @Test
    fun sanitizeReleaseUrl_externalOrUntrustedUrl_fallsBackToReleasesPage() {
        val untrusted1 = "https://evil.example.com/apk"
        val untrusted2 = "http://github.com/iRouzBeH/HybridNest/releases/tag/v1.1"
        val phishing = "https://github.com/anotherUser/HybridNest/releases"

        assertEquals(GitHubUpdateChecker.RELEASES_PAGE_URL, GitHubUpdateChecker.sanitizeReleaseUrl(untrusted1))
        assertEquals(GitHubUpdateChecker.RELEASES_PAGE_URL, GitHubUpdateChecker.sanitizeReleaseUrl(untrusted2))
        assertEquals(GitHubUpdateChecker.RELEASES_PAGE_URL, GitHubUpdateChecker.sanitizeReleaseUrl(phishing))
    }

    @Test
    fun sanitizeReleaseUrl_nullOrBlank_fallsBackToReleasesPage() {
        assertEquals(GitHubUpdateChecker.RELEASES_PAGE_URL, GitHubUpdateChecker.sanitizeReleaseUrl(null))
        assertEquals(GitHubUpdateChecker.RELEASES_PAGE_URL, GitHubUpdateChecker.sanitizeReleaseUrl(""))
        assertEquals(GitHubUpdateChecker.RELEASES_PAGE_URL, GitHubUpdateChecker.sanitizeReleaseUrl("   "))
    }

    // =========================================================================
    // 3. Release Notes Summarization
    // =========================================================================

    @Test
    fun summarizeReleaseNotes_blankReturnsNull() {
        assertNull(GitHubUpdateChecker.summarizeReleaseNotes(null))
        assertNull(GitHubUpdateChecker.summarizeReleaseNotes(""))
        assertNull(GitHubUpdateChecker.summarizeReleaseNotes("   \n\t  "))
    }

    @Test
    fun summarizeReleaseNotes_cleansHeadingsAndTruncatesLines() {
        val rawBody = """
            ### What's Changed
            * Real GitHub release checks
            * Polished glass dialogs
            * Improved network error reporting
            * Page size alignment
            * Fix minor typos
            * Additional diagnostic info
            * Extra line that should be truncated
        """.trimIndent()

        val summary = GitHubUpdateChecker.summarizeReleaseNotes(rawBody, maxLines = 4)
        assertNotNull(summary)
        assertTrue(summary!!.startsWith("What's Changed"))
        assertTrue(summary.endsWith("..."))
        val lines = summary.lines()
        assertTrue(lines.size <= 4)
    }

    // =========================================================================
    // 4. GitHub Releases JSON Parsing
    // =========================================================================

    @Test
    fun parseLatestReleaseJson_validPayload_extractsFields() {
        val json = """
            {
                "tag_name": "v1.1",
                "name": "HybridNest v1.1",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/iRouzBeH/HybridNest/releases/tag/v1.1",
                "published_at": "2026-11-23T18:30:00Z",
                "body": "## Release Highlights\n- Native diagnostics\n- Real update checker"
            }
        """.trimIndent()

        val release = GitHubUpdateChecker.parseLatestReleaseJson(json)
        assertNotNull(release)
        assertEquals("v1.1", release!!.tagName)
        assertEquals("1.1", release.version)
        assertEquals("HybridNest v1.1", release.releaseName)
        assertEquals("https://github.com/iRouzBeH/HybridNest/releases/tag/v1.1", release.htmlUrl)
        assertEquals("2026-11-23", release.publishedAt)
        assertNotNull(release.notesSummary)
        assertTrue(release.notesSummary!!.contains("Release Highlights"))
    }

    @Test
    fun parseLatestReleaseJson_draftOrPrerelease_returnsNull() {
        val draftJson = """
            {
                "tag_name": "v1.2",
                "draft": true,
                "prerelease": false,
                "html_url": "https://github.com/iRouzBeH/HybridNest/releases/tag/v1.2"
            }
        """.trimIndent()
        assertNull(GitHubUpdateChecker.parseLatestReleaseJson(draftJson))

        val prereleaseJson = """
            {
                "tag_name": "v1.2-rc1",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/iRouzBeH/HybridNest/releases/tag/v1.2-rc1"
            }
        """.trimIndent()
        assertNull(GitHubUpdateChecker.parseLatestReleaseJson(prereleaseJson))
    }

    @Test
    fun parseLatestReleaseJson_malformedOrMissingTagName_returnsNull() {
        assertNull(GitHubUpdateChecker.parseLatestReleaseJson("not-a-json"))
        assertNull(GitHubUpdateChecker.parseLatestReleaseJson("{}"))
        assertNull(GitHubUpdateChecker.parseLatestReleaseJson("""{"name": "No Tag"}"""))
        assertNull(GitHubUpdateChecker.parseLatestReleaseJson("""{"tag_name": ""}"""))
    }

    // =========================================================================
    // 5. HTTP Response Evaluation
    // =========================================================================

    @Test
    fun evaluateHttpResponse_200_newerTag_returnsUpdateAvailable() {
        val json = """
            {
                "tag_name": "v1.1",
                "name": "HybridNest v1.1",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/iRouzBeH/HybridNest/releases/tag/v1.1"
            }
        """.trimIndent()

        val result = GitHubUpdateChecker.evaluateHttpResponse("1.0", 200, json)
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val update = result as UpdateCheckResult.UpdateAvailable
        assertEquals("1.0", update.installedVersion)
        assertEquals("v1.1", update.latestRelease.tagName)
    }

    @Test
    fun evaluateHttpResponse_200_equalTag_returnsUpToDate() {
        val json = """
            {
                "tag_name": "v1.0",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/iRouzBeH/HybridNest/releases/tag/v1.0"
            }
        """.trimIndent()

        val result = GitHubUpdateChecker.evaluateHttpResponse("1.0", 200, json)
        assertTrue(result is UpdateCheckResult.UpToDate)
        val upToDate = result as UpdateCheckResult.UpToDate
        assertEquals("1.0", upToDate.installedVersion)
        assertEquals("v1.0", upToDate.latestRelease.tagName)
    }

    @Test
    fun evaluateHttpResponse_200_olderTag_returnsUpToDate() {
        val json = """
            {
                "tag_name": "v0.9",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/iRouzBeH/HybridNest/releases/tag/v0.9"
            }
        """.trimIndent()

        val result = GitHubUpdateChecker.evaluateHttpResponse("1.0", 200, json)
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun evaluateHttpResponse_200_corruptedJson_returnsError() {
        val result = GitHubUpdateChecker.evaluateHttpResponse("1.0", 200, "<not-json>")
        assertTrue(result is UpdateCheckResult.Error)
        val error = result as UpdateCheckResult.Error
        assertTrue(error.message.contains("unreadable"))
    }

    @Test
    fun evaluateHttpResponse_404_returnsNoPublishedRelease() {
        val result = GitHubUpdateChecker.evaluateHttpResponse("1.0", 404, """{"message":"Not Found"}""")
        assertTrue(result is UpdateCheckResult.NoPublishedRelease)
    }

    @Test
    fun evaluateHttpResponse_403_and_429_returnsRateLimitError() {
        val res403 = GitHubUpdateChecker.evaluateHttpResponse("1.0", 403, "rate limit exceeded")
        assertTrue(res403 is UpdateCheckResult.Error)
        assertTrue((res403 as UpdateCheckResult.Error).message.contains("rate limit", ignoreCase = true))

        val res429 = GitHubUpdateChecker.evaluateHttpResponse("1.0", 429, "too many requests")
        assertTrue(res429 is UpdateCheckResult.Error)
        assertTrue((res429 as UpdateCheckResult.Error).message.contains("rate limit", ignoreCase = true))
    }

    @Test
    fun evaluateHttpResponse_500_returnsGenericHttpError() {
        val result = GitHubUpdateChecker.evaluateHttpResponse("1.0", 500, "Internal Server Error")
        assertTrue(result is UpdateCheckResult.Error)
        assertTrue((result as UpdateCheckResult.Error).message.contains("HTTP 500"))
    }
}
