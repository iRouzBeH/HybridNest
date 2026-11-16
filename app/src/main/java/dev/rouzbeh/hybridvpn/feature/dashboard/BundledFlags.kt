package dev.rouzbeh.hybridvpn.feature.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Synchronous local loader and in-memory cache for the bundled 1280px-wide
 * native-aspect-ratio lossless WebP country flags stored under `assets/flags/<code>.webp`.
 *
 * Never performs network requests or disk writes. Unknown/sentinel codes (`"un"`,
 * `"xx"`, `"__loading__"`, or unmapped codes) return `null` immediately.
 */
object BundledFlags {

    private const val MAX_CACHE_ENTRIES = 64

    val SUPPORTED_CODES: Set<String> = setOf(
        "ad", "ae", "af", "ag", "ai", "al", "am", "ao", "aq", "ar",
        "as", "at", "au", "aw", "ax", "az", "ba", "bb", "bd", "be",
        "bf", "bg", "bh", "bi", "bj", "bl", "bm", "bn", "bo", "bq",
        "br", "bs", "bt", "bv", "bw", "by", "bz", "ca", "cc", "cd",
        "cf", "cg", "ch", "ci", "ck", "cl", "cm", "cn", "co", "cr",
        "cu", "cv", "cw", "cx", "cy", "cz", "de", "dj", "dk", "dm",
        "do", "dz", "ec", "ee", "eg", "eh", "er", "es", "et", "eu",
        "fi", "fj", "fk", "fm", "fo", "fr", "ga", "gb", "gd", "ge",
        "gf", "gg", "gh", "gi", "gl", "gm", "gn", "gp", "gq", "gr",
        "gs", "gt", "gu", "gw", "gy", "hk", "hm", "hn", "hr", "ht",
        "hu", "id", "ie", "il", "im", "in", "io", "iq", "ir", "is",
        "it", "je", "jm", "jo", "jp", "ke", "kg", "kh", "ki", "km",
        "kn", "kp", "kr", "kw", "ky", "kz", "la", "lb", "lc", "li",
        "lk", "lr", "ls", "lt", "lu", "lv", "ly", "ma", "mc", "md",
        "me", "mf", "mg", "mh", "mk", "ml", "mm", "mn", "mo", "mp",
        "mq", "mr", "ms", "mt", "mu", "mv", "mw", "mx", "my", "mz",
        "na", "nc", "ne", "nf", "ng", "ni", "nl", "no", "np", "nr",
        "nu", "nz", "om", "pa", "pe", "pf", "pg", "ph", "pk", "pl",
        "pm", "pn", "pr", "ps", "pt", "pw", "py", "qa", "re", "ro",
        "rs", "ru", "rw", "sa", "sb", "sc", "sd", "se", "sg", "sh",
        "si", "sj", "sk", "sl", "sm", "sn", "so", "sr", "ss", "st",
        "sv", "sx", "sy", "sz", "tc", "td", "tf", "tg", "th", "tj",
        "tk", "tl", "tm", "tn", "to", "tr", "tt", "tv", "tw", "tz",
        "ua", "ug", "um", "us", "uy", "uz", "va", "vc", "ve", "vg",
        "vi", "vn", "vu", "wf", "ws", "xk", "ye", "yt", "za", "zm",
        "zw"
    )

    private val CODE_ALIASES: Map<String, String> = mapOf(
        "uk" to "gb",
        "cp" to "fr",
        "dg" to "io",
        "ac" to "sh",
        "ta" to "sh",
        "ea" to "es",
        "ic" to "es"
    )

    private val bitmapCache = object : LinkedHashMap<String, ImageBitmap>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    /**
     * Normalizes [rawCode] to a supported 2-letter lowercase flag code, or returns `null`
     * for `"un"`, `"xx"`, `"__loading__"`, blank, or unmapped codes.
     */
    fun normalizeCode(rawCode: String?): String? {
        val cleaned = rawCode?.lowercase()?.trim() ?: return null
        if (cleaned.length != 2 || cleaned == "un" || cleaned == "xx") return null
        val mapped = CODE_ALIASES[cleaned] ?: cleaned
        return mapped.takeIf { it in SUPPORTED_CODES }
    }

    fun isSupported(rawCode: String?): Boolean = normalizeCode(rawCode) != null

    fun assetPath(rawCode: String?): String? = normalizeCode(rawCode)?.let { "flags/$it.webp" }

    /**
     * Synchronously decodes and caches the bundled high-resolution WebP flag for [rawCode]
     * from app assets in full ARGB_8888 quality without scaling or dithering.
     * Returns `null` if [rawCode] is unknown/unsupported or if asset decoding fails.
     */
    fun getFlagBitmap(context: Context, rawCode: String?): ImageBitmap? {
        val code = normalizeCode(rawCode) ?: return null
        synchronized(bitmapCache) {
            bitmapCache[code]?.let { return it }
        }
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            val bitmap = context.assets.open("flags/$code.webp").use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
            }
            if (bitmap != null) {
                synchronized(bitmapCache) {
                    bitmapCache[code] = bitmap
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
