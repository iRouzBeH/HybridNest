package dev.rouzbeh.hybridvpn.feature.dashboard

internal data class InfoPiece(
    val volumeValues: List<String>,   // in the order written, e.g. ["5.40 GB", "10.00 GB"]
    val timeAmount: Int?,             // number next to the time marker / day / hour word
    val timeUnit: String?             // "day" or "hour"
)

internal object InfoRemarkClassifier {
    // invisible chars: zero-width, bidi marks/controls, variation selectors (U+FE0F after emoji), NBSP, BOM
    private val INVISIBLE = Regex("[\u200B-\u200F\u202A-\u202E\u2066-\u2069\uFE00-\uFE0F\u00A0\uFEFF]")
    private val VOLUME = Regex("""(\d+(?:\.\d+)?)\s*(TB|GB|MB|KB|B)(?![A-Za-z])""", RegexOption.IGNORE_CASE)
    private val TIME_MARKED = Regex("""(?:⏳|⌛|📅|📆|🗓|⏰)\s*(\d+)""")
    private val TIME_WORD = Regex("""(\d+)\s*(days?|hours?|روز|ساعت)""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("\\s+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}_-]+")

    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            sb.append(
                when (ch) {
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')   // Persian digits
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')   // Arabic-Indic digits
                    '\u066B' -> '.'                                   // Arabic decimal separator
                    else -> ch
                }
            )
        }
        return SPACES.replace(INVISIBLE.replace(sb.toString(), ""), " ").trim()
    }

    /** Returns null when the remark is NOT a pure info entry (a real server name). */
    fun classify(raw: String): InfoPiece? {
        val text = normalize(raw)
        if (text.isEmpty()) return null

        val volumes = VOLUME.findAll(text)
            .map { "${it.groupValues[1]} ${it.groupValues[2].uppercase()}" }
            .toList()
        val marked = TIME_MARKED.find(text)
        val word = TIME_WORD.find(text)

        val time: Pair<Int, String>? = when {
            marked != null -> marked.groupValues[1].toIntOrNull()?.let { it to "day" }
            word != null -> word.groupValues[1].toIntOrNull()?.let {
                val w = word.groupValues[2].lowercase()
                it to (if (w.startsWith("hour") || w == "ساعت") "hour" else "day")
            }
            else -> null
        }
        if (volumes.isEmpty() && time == null) return null

        // What is left after removing every recognised token. Letters/digits left over mean "real server name",
        // except a lone id-like word (contains a digit, e.g. VIP-6293392523-94328) next to an explicit time marker
        // or when volume and time are both present in the same remark.
        val residue = text.replace(VOLUME, " ")
            .replace(TIME_MARKED, " ")
            .replace(TIME_WORD, " ")
            .let { if (time != null) it.replace(Regex("""\b(?:days?|hours?|روز|ساعت)\b""", RegexOption.IGNORE_CASE), " ") else it }
        val hasResidue = residue.any { it.isLetterOrDigit() }
        val residueWords = residue.split(NON_WORD).filter { it.isNotEmpty() }
        val residueLooksLikeId = residueWords.size <= 1 && residueWords.all { w -> w.any { it.isDigit() } }

        val idResidueAllowed = residueLooksLikeId && (marked != null || (volumes.isNotEmpty() && time != null))
        val isInfo = !hasResidue || idResidueAllowed
        if (!isInfo) return null

        return InfoPiece(volumes, time?.first, time?.second)
    }
}

/** remarks MUST be in the subscription's original order. Returns (how many leading entries were consumed, merged info). */
internal fun mergeLeadingInfo(remarks: List<String>): Pair<Int, InfoPiece?> {
    var consumed = 0
    var volumes: List<String> = emptyList()
    var amount: Int? = null
    var unit: String? = null
    for (r in remarks) {
        val p = InfoRemarkClassifier.classify(r) ?: break
        if (volumes.isEmpty() && p.volumeValues.isNotEmpty()) volumes = p.volumeValues
        if (amount == null && p.timeAmount != null) { amount = p.timeAmount; unit = p.timeUnit }
        consumed++
    }
    return if (consumed == 0) 0 to null else consumed to InfoPiece(volumes, amount, unit)
}

internal fun InfoPiece.toSubscriptionMeta(): SubscriptionMeta {
    val used = volumeValues.getOrNull(0) ?: ""
    val total = if (volumeValues.size > 1) volumeValues[1] else ""
    val usedGb = used.substringBefore(" ").toDoubleOrNull()
    val totalGb = total.substringBefore(" ").toDoubleOrNull()

    val timeUnitStr = when {
        timeAmount == null -> null
        timeUnit == "hour" -> if (timeAmount == 1) "Hour" else "Hours"
        else -> if (timeAmount == 1) "Day" else "Days"
    }
    val daysRawStr = if (timeAmount != null) "$timeAmount $timeUnitStr" else null

    return SubscriptionMeta(
        usedStr = used,
        totalStr = total,
        usedGb = usedGb,
        totalGb = totalGb,
        daysLeft = timeAmount,
        daysRaw = daysRawStr,
        daysNum = timeAmount?.toString()
    )
}
