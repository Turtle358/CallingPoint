package io.github.turtle358.callingpoint.utils.JourneyPlanner;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TransitousTime {
    private TransitousTime() { }

    public static String format(long millis, String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.UK);
        formatter.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        return formatter.format(new Date(millis));
    }

    public static long parse(String value) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Missing journey time");
        String normalized = value.replaceFirst("\\.\\d+(?=Z|[+-]\\d{2}:?\\d{2}$)", "")
                .replaceFirst("Z$", "+0000").replaceFirst("([+-]\\d{2}):(\\d{2})$", "$1$2");
        return parseExact(normalized, "yyyy-MM-dd'T'HH:mm:ssZ");
    }

    public static long parseExact(String value, String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.UK);
        formatter.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        formatter.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = formatter.parse(value, position);
        if (parsed == null || position.getIndex() != value.length()) {
            throw new IllegalArgumentException("Invalid journey date or time");
        }
        return parsed.getTime();
    }
}
