package io.github.turtle358.callingpoint.utils.Disruptions

import android.graphics.Color
import java.util.Locale
import java.util.regex.Pattern
import androidx.core.graphics.toColorInt

object DisruptionSeverityManager {
    private val DELAY_PATTERN: Pattern = Pattern.compile("(\\d+)\\s*(?:min|minute)")

    @JvmStatic
    fun classify(summary: String?, description: String?): Severity {
        if (summary == null && description == null) return Severity.UNKNOWN

        val lowerSummary = summary?.lowercase(Locale.getDefault()) ?: ""
        val lowerDesc = description?.lowercase(Locale.getDefault()) ?: ""
        val combined = lowerSummary + " " + lowerDesc

        val isOverrun = combined.contains("overrun") || combined.contains("overrunning")

        val isEngineering = (lowerSummary.contains("engineering")
                || lowerSummary.contains("upgrade work")
                || lowerSummary.contains("track work")
                || lowerSummary.contains("maintenance work")
                || lowerDesc.contains("engineering work")
                || lowerDesc.contains("engineering-works-explained"))
                && !isOverrun

        val isStrike = lowerSummary.contains("industrial action")
                || lowerSummary.contains("strike")
                || (!isEngineering && (lowerDesc.contains("industrial action") || lowerDesc.contains(
            "strike"
        )))

        if (isStrike) {
            return Severity.STRIKE
        } else if (isEngineering) {
            return Severity.ENGINEERING_WORKS
        }

        var hasSevereDelay = false
        val matcher = DELAY_PATTERN.matcher(combined)
        while (matcher.find()) {
            try {
                val minutes = matcher.group(1).toInt()
                if (minutes >= 45) {
                    hasSevereDelay = true
                    break
                }
            } catch (ignored: NumberFormatException) {
            }
        }

        if (hasSevereDelay ||
            lowerSummary.contains("major") ||
            lowerSummary.contains("severe") ||
            lowerSummary.contains("suspended") ||
            lowerSummary.contains("do not travel") ||
            lowerSummary.contains("closed") ||
            lowerSummary.contains("blocked")
        ) {
            return Severity.HIGH
        }

        return Severity.LOW
    }

    @JvmStatic
    fun isTflPlanned(statusSeverity: Int): Boolean {
        return statusSeverity == 4 || statusSeverity == 5 || statusSeverity == 11
    }

    enum class Severity(
        @JvmField val statusString: String,
        @JvmField val priority: Int,
        @JvmField val accentColor: Int,
        val backgroundColor: Int
    ) {
        STRIKE(
            "Planned Industrial Action",
            4,
            Color.parseColor("#E056FD"),
            Color.parseColor("#2A1235")
        ),
        HIGH("Major Disruption", 3, Color.parseColor("#FF5252"), Color.parseColor("#331414")),
        LOW("Minor Disruption", 2, Color.parseColor("#FFB74D"), Color.parseColor("#332410")),
        ENGINEERING_WORKS(
            "Planned Engineering Works",
            1,
            "#4FC3F7".toColorInt(),
            "#102533".toColorInt()
        ),
        UNKNOWN("Minor Disruption", 0, "#B0BEC5".toColorInt(), "#212628".toColorInt());

        companion object {
            @JvmStatic
            fun fromString(statusString: String?): Severity {
                if (statusString == null) return Severity.UNKNOWN
                for (s in entries) {
                    if (s.statusString == statusString) return s
                }
                return Severity.UNKNOWN
            }
        }
    }
}