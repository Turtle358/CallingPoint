package io.github.turtle358.callingpoint.utils.Disruptions;

import android.graphics.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DisruptionSeverityManager {

    private static final Pattern DELAY_PATTERN = Pattern.compile("(\\d+)\\s*(?:min|minute)");

    public enum Severity {
        STRIKE("Planned Industrial Action", 4, Color.parseColor("#E056FD"), Color.parseColor("#2A1235")),
        HIGH("Major Disruption", 3, Color.parseColor("#FF5252"), Color.parseColor("#331414")),
        LOW("Minor Disruption", 2, Color.parseColor("#FFB74D"), Color.parseColor("#332410")),
        ENGINEERING_WORKS("Planned Engineering Works", 1, Color.parseColor("#4FC3F7"), Color.parseColor("#102533")),
        UNKNOWN("Minor Disruption", 0, Color.parseColor("#B0BEC5"), Color.parseColor("#212628"));

        private final String statusString;
        private final int priority;
        private final int accentColor;
        private final int backgroundColor;

        Severity(String statusString, int priority, int accentColor, int backgroundColor) {
            this.statusString = statusString;
            this.priority = priority;
            this.accentColor = accentColor;
            this.backgroundColor = backgroundColor;
        }

        public String getStatusString() { return statusString; }
        public int getPriority() { return priority; }
        public int getAccentColor() { return accentColor; }
        public int getBackgroundColor() { return backgroundColor; }

        public static Severity fromString(String statusString) {
            if (statusString == null) return UNKNOWN;
            for (Severity s : values()) {
                if (s.statusString.equals(statusString)) return s;
            }
            return UNKNOWN;
        }
    }

    public static Severity classify(String summary, String description) {
        if (summary == null && description == null) return Severity.UNKNOWN;

        String lowerSummary = summary != null ? summary.toLowerCase() : "";
        String lowerDesc = description != null ? description.toLowerCase() : "";
        String combined = lowerSummary + " " + lowerDesc;

        boolean isOverrun = combined.contains("overrun") || combined.contains("overrunning");

        boolean isEngineering = (lowerSummary.contains("engineering")
                || lowerSummary.contains("upgrade work")
                || lowerSummary.contains("track work")
                || lowerSummary.contains("maintenance work")
                || lowerDesc.contains("engineering work")
                || lowerDesc.contains("engineering-works-explained"))
                && !isOverrun;

        boolean isStrike = lowerSummary.contains("industrial action")
                || lowerSummary.contains("strike")
                || (!isEngineering && (lowerDesc.contains("industrial action") || lowerDesc.contains("strike")));

        if (isStrike) {
            return Severity.STRIKE;
        } else if (isEngineering) {
            return Severity.ENGINEERING_WORKS;
        }

        boolean hasSevereDelay = false;
        Matcher matcher = DELAY_PATTERN.matcher(combined);
        while (matcher.find()) {
            try {
                int minutes = Integer.parseInt(matcher.group(1));
                if (minutes >= 45) {
                    hasSevereDelay = true;
                    break;
                }
            } catch (NumberFormatException ignored) {}
        }

        if (hasSevereDelay ||
                lowerSummary.contains("major") ||
                lowerSummary.contains("severe") ||
                lowerSummary.contains("suspended") ||
                lowerSummary.contains("do not travel") ||
                lowerSummary.contains("closed") ||
                lowerSummary.contains("blocked")) {
            return Severity.HIGH;
        }

        return Severity.LOW;
    }

    public static boolean isTflPlanned(int statusSeverity) {
        return statusSeverity == 4 || statusSeverity == 5 || statusSeverity == 11;
    }
}