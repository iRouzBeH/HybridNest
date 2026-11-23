package dev.rouzbeh.hybridvpn.feature.dashboard

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val version: String,
    val releaseName: String,
    val htmlUrl: String,
    val publishedAt: String?,
    val notesSummary: String?
)

sealed class UpdateCheckResult {
    data class UpToDate(
        val installedVersion: String,
        val latestRelease: GitHubReleaseInfo
    ) : UpdateCheckResult()

    data class UpdateAvailable(
        val installedVersion: String,
        val latestRelease: GitHubReleaseInfo
    ) : UpdateCheckResult()

    data class NoPublishedRelease(
        val installedVersion: String,
        val releasesUrl: String = GitHubUpdateChecker.RELEASES_PAGE_URL
    ) : UpdateCheckResult()

    data class Error(
        val message: String,
        val releasesUrl: String = GitHubUpdateChecker.RELEASES_PAGE_URL
    ) : UpdateCheckResult()
}

object GitHubUpdateChecker {
    const val REPO_OWNER = "iRouzBeH"
    const val REPO_NAME = "HybridNest"
    const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases"
    const val ALLOWED_RELEASE_URL_PREFIX = "https://github.com/$REPO_OWNER/$REPO_NAME/"

    /**
     * Strips leading whitespace, leading 'v' or 'V', and trims the version string.
     */
    fun normalizeVersion(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("v", ignoreCase = true)) {
            trimmed.substring(1).trimStart()
        } else {
            trimmed
        }
    }

    /**
     * Semantic version comparison.
     * Returns negative if installed < latest, 0 if equal, positive if installed > latest.
     * Core numeric segments are compared after stripping build metadata (+...).
     * If core segments are equal, a stable release is greater than a pre-release suffix (-...).
     */
    fun compareVersions(installedRaw: String, latestRaw: String): Int {
        val instNorm = normalizeVersion(installedRaw)
        val latestNorm = normalizeVersion(latestRaw)

        if (instNorm.isBlank() && latestNorm.isBlank()) return 0
        if (instNorm.isBlank()) return -1
        if (latestNorm.isBlank()) return 1

        // Strip build metadata after '+'
        val instWithoutBuild = instNorm.substringBefore('+')
        val latestWithoutBuild = latestNorm.substringBefore('+')

        // Split core version and pre-release suffix after '-'
        val instParts = instWithoutBuild.split('-', limit = 2)
        val latestParts = latestWithoutBuild.split('-', limit = 2)

        val instCoreStr = instParts[0]
        val latestCoreStr = latestParts[0]

        val instPre = if (instParts.size > 1) instParts[1] else null
        val latestPre = if (latestParts.size > 1) latestParts[1] else null

        // Parse core dot-separated numeric segments
        val instSegments = instCoreStr.split('.').map { seg ->
            seg.filter { it.isDigit() }.toLongOrNull() ?: 0L
        }
        val latestSegments = latestCoreStr.split('.').map { seg ->
            seg.filter { it.isDigit() }.toLongOrNull() ?: 0L
        }

        val maxSegments = maxOf(instSegments.size, latestSegments.size)
        for (i in 0 until maxSegments) {
            val segInst = instSegments.getOrElse(i) { 0L }
            val segLatest = latestSegments.getOrElse(i) { 0L }
            if (segInst != segLatest) {
                return segInst.compareTo(segLatest)
            }
        }

        // Core numeric versions are equal. Compare pre-release suffixes if present.
        // Rule: A stable release (no pre-release suffix) has higher precedence than a pre-release.
        if (instPre == null && latestPre == null) return 0
        if (instPre == null && latestPre != null) return 1
        if (instPre != null && latestPre == null) return -1

        // Both have pre-release identifiers
        val instPreParts = instPre!!.split('.')
        val latestPreParts = latestPre!!.split('.')
        val maxPre = maxOf(instPreParts.size, latestPreParts.size)
        for (i in 0 until maxPre) {
            if (i >= instPreParts.size) return -1
            if (i >= latestPreParts.size) return 1
            val pInst = instPreParts[i]
            val pLatest = latestPreParts[i]
            val numInst = pInst.toLongOrNull()
            val numLatest = pLatest.toLongOrNull()
            if (numInst != null && numLatest != null) {
                if (numInst != numLatest) return numInst.compareTo(numLatest)
            } else if (numInst != null) {
                return -1
            } else if (numLatest != null) {
                return 1
            } else {
                val cmp = pInst.compareTo(pLatest)
                if (cmp != 0) return cmp
            }
        }
        return 0
    }

    /**
     * Sanitizes external URLs, ensuring they strictly begin with [ALLOWED_RELEASE_URL_PREFIX].
     * Falls back to [RELEASES_PAGE_URL] for any untrusted or invalid scheme.
     */
    fun sanitizeReleaseUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        return if (trimmed.startsWith(ALLOWED_RELEASE_URL_PREFIX)) {
            trimmed
        } else {
            RELEASES_PAGE_URL
        }
    }

    /**
     * Cleans and truncates raw release notes for compact UI presentation.
     */
    fun summarizeReleaseNotes(rawBody: String?, maxLines: Int = 6, maxChars: Int = 420): String? {
        if (rawBody.isNullOrBlank()) return null
        val lines = rawBody.lines()
            .map { line ->
                var cleaned = line.trim()
                while (cleaned.startsWith("#")) {
                    cleaned = cleaned.removePrefix("#").trimStart()
                }
                cleaned
            }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return null

        val truncatedByLines = lines.size > maxLines
        val selectedLines = lines.take(maxLines)
        var joined = selectedLines.joinToString("\n")

        var wasCharTruncated = false
        if (joined.length > maxChars) {
            joined = joined.take(maxChars).trimEnd()
            wasCharTruncated = true
        }

        if (truncatedByLines || wasCharTruncated) {
            if (!joined.endsWith("...")) {
                joined = "$joined..."
            }
        }

        return joined.ifBlank { null }
    }

    /**
     * Parses the GitHub Releases API JSON body into [GitHubReleaseInfo].
     * Returns null on malformed JSON, drafts, or pre-releases.
     */
    fun parseLatestReleaseJson(jsonBody: String): GitHubReleaseInfo? {
        return try {
            val root = JSONObject(jsonBody)
            if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) {
                return null
            }
            val tagName = root.optString("tag_name", "").trim()
            if (tagName.isBlank()) {
                return null
            }
            val rawName = root.optString("name", "").trim()
            val releaseName = rawName.ifBlank { tagName }
            val rawHtmlUrl = root.optString("html_url", "")
            val htmlUrl = sanitizeReleaseUrl(rawHtmlUrl)

            val rawPublishedAt = root.optString("published_at", "").trim()
            val publishedAt = if (rawPublishedAt.length >= 10 && rawPublishedAt[4] == '-' && rawPublishedAt[7] == '-') {
                rawPublishedAt.substring(0, 10)
            } else {
                rawPublishedAt.ifBlank { null }
            }

            val rawBody = root.optString("body", "").trim()
            val notesSummary = summarizeReleaseNotes(rawBody)

            GitHubReleaseInfo(
                tagName = tagName,
                version = normalizeVersion(tagName),
                releaseName = releaseName,
                htmlUrl = htmlUrl,
                publishedAt = publishedAt,
                notesSummary = notesSummary
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Evaluates HTTP status code and response body against the installed app version.
     */
    fun evaluateHttpResponse(installedVersion: String, httpCode: Int, responseBody: String?): UpdateCheckResult {
        return when (httpCode) {
            200 -> {
                val release = parseLatestReleaseJson(responseBody.orEmpty())
                if (release == null) {
                    UpdateCheckResult.Error("Received an unreadable release response from GitHub.")
                } else {
                    val cmp = compareVersions(installedVersion, release.version)
                    if (cmp < 0) {
                        UpdateCheckResult.UpdateAvailable(installedVersion, release)
                    } else {
                        UpdateCheckResult.UpToDate(installedVersion, release)
                    }
                }
            }
            404 -> UpdateCheckResult.NoPublishedRelease(installedVersion)
            403, 429 -> UpdateCheckResult.Error("GitHub API rate limit reached. Please try again later or open Releases directly.")
            else -> UpdateCheckResult.Error("GitHub returned HTTP $httpCode. Please try again later.")
        }
    }

    /**
     * Fetches the latest published release asynchronously via OkHttp.
     * When [useLocalXrayProxy] is true, routes through local Xray HTTP proxy (127.0.0.1:10809)
     * with transparent single fallback to direct connection.
     */
    suspend fun fetchLatestRelease(
        installedVersion: String,
        useLocalXrayProxy: Boolean = false
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val normVer = normalizeVersion(installedVersion).ifBlank { "1.0" }
        val userAgent = "HybridNest-Android/$normVer"

        fun buildClient(proxy: Proxy): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
                .retryOnConnectionFailure(false)
                .followRedirects(true)
                .followSslRedirects(true)
                .cache(null)
                .proxy(proxy)
                .build()
        }

        fun executeRequest(client: OkHttpClient): UpdateCheckResult {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", userAgent)
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string()
                return evaluateHttpResponse(installedVersion, code, body)
            }
        }

        if (useLocalXrayProxy) {
            val localProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10809))
            val proxyClient = buildClient(localProxy)
            try {
                try {
                    return@withContext executeRequest(proxyClient)
                } finally {
                    try { proxyClient.connectionPool.evictAll() } catch (_: Throwable) {}
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: IOException) {
                // Transparently fallback once to direct connection
                val directClient = buildClient(Proxy.NO_PROXY)
                try {
                    return@withContext executeRequest(directClient)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: SocketTimeoutException) {
                    return@withContext UpdateCheckResult.Error("Connection to GitHub timed out. Check your internet connection or try again.")
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: "network error"
                    return@withContext UpdateCheckResult.Error("Unable to reach GitHub ($msg).")
                } finally {
                    try { directClient.connectionPool.evictAll() } catch (_: Throwable) {}
                }
            } catch (ste: SocketTimeoutException) {
                return@withContext UpdateCheckResult.Error("Connection to GitHub timed out. Check your internet connection or try again.")
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "network error"
                return@withContext UpdateCheckResult.Error("Unable to reach GitHub ($msg).")
            }
        } else {
            val directClient = buildClient(Proxy.NO_PROXY)
            try {
                return@withContext executeRequest(directClient)
            } catch (ce: CancellationException) {
                throw ce
            } catch (ste: SocketTimeoutException) {
                return@withContext UpdateCheckResult.Error("Connection to GitHub timed out. Check your internet connection or try again.")
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "network error"
                return@withContext UpdateCheckResult.Error("Unable to reach GitHub ($msg).")
            } finally {
                try { directClient.connectionPool.evictAll() } catch (_: Throwable) {}
            }
        }
    }
}
