package io.github.turtle358.callingpoint.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeUtils {
    public static String getCountdownText(String scheduledTime, String estimatedTime) {
        String timeToUse = scheduledTime;
        if (estimatedTime != null && !estimatedTime.equalsIgnoreCase("On time")
                && !estimatedTime.equalsIgnoreCase("Delayed")
                && !estimatedTime.equalsIgnoreCase("Cancelled")) {
            timeToUse = estimatedTime;
        }

        if (timeToUse == null || timeToUse.isEmpty()) return "";
        if (estimatedTime != null && estimatedTime.equalsIgnoreCase("Cancelled")) return "Cancelled";

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.UK);
            sdf.setTimeZone(TimeZone.getTimeZone("Europe/London"));

            Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
            int currentTotalMins = (now.get(Calendar.HOUR_OF_DAY) * 60) + now.get(Calendar.MINUTE);

            Date targetDate = sdf.parse(timeToUse);
            Calendar target = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
            if (targetDate != null) {
                target.setTime(targetDate);
            }
            int targetTotalMins = (target.get(Calendar.HOUR_OF_DAY) * 60) + target.get(Calendar.MINUTE);

            if (targetTotalMins < currentTotalMins && (currentTotalMins - targetTotalMins) > 720) {
                targetTotalMins += 24 * 60;
            }

            int diffMins = targetTotalMins - currentTotalMins;

            if (diffMins <= 0) {
                return "Due";
            } else if (diffMins < 60) {
                return diffMins + " min";
            } else {
                int hours = diffMins / 60;
                int mins = diffMins % 60;
                return hours + " hr " + mins + " min";
            }

        } catch (ParseException e) {
            return estimatedTime != null ? estimatedTime : scheduledTime;
        }
    }
}