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

        val isEngineering = combined.contains("engineering") && !isOverrun

        val isTempAmendedTimetable = combined.contains("temporary") && combined.contains("timetable") && !combined.contains("reopened") && !combined.contains("points failure")

        val isStrike = lowerSummary.contains("industrial action")
                || lowerSummary.contains("strike")
                || (!isEngineering && (lowerDesc.contains("industrial action") || lowerDesc.contains(
            "strike"
        )))

        if (isStrike) {
            return Severity.STRIKE
        } else if (isEngineering) {
            return Severity.ENGINEERING_WORKS
        } else if (isTempAmendedTimetable) {
            return Severity.AMENDED_TIMETABLE
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
            "Planned Industrial Action", 5, "#E056FD".toColorInt(), "#2A1235".toColorInt()),
        HIGH("Major Disruption", 4, "#FF5252".toColorInt(), "#331414".toColorInt()),
        LOW("Minor Disruption", 3, "#FFB74D".toColorInt(), "#332410".toColorInt()),
        AMENDED_TIMETABLE("Temporary Amended Timetable", 2, "#FF8550".toColorInt(), "#26130D".toColorInt()),
        ENGINEERING_WORKS("Planned Engineering Works",1,"#4FC3F7".toColorInt(),"#102533".toColorInt()),
        UNKNOWN("Information", 0, "#4FC3F7".toColorInt(), "#102533".toColorInt());

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