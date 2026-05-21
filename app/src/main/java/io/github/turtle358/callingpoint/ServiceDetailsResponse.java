package io.github.turtle358.callingpoint;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ServiceDetailsResponse {

    public String customServiceID;

    @SerializedName("locationName")
    public String locationName;

    @SerializedName("std")
    public String std;

    @SerializedName("etd")
    public String etd;

    @SerializedName("ata")
    public String ata;

    @SerializedName("atd")
    public String atd;

    @SerializedName("platform")
    public String platform;

    @SerializedName("operator")
    public String operator;

    @SerializedName("previousCallingPoints")
    public List<CallingPointList> previousCallingPoints;

    @SerializedName("subsequentCallingPoints")
    public List<CallingPointList> subsequentCallingPoints;

    @SerializedName("formation")
    public Formation formation;

    public static class CallingPointList {
        @SerializedName("callingPoint")
        public List<TimelinePoint> callingPoint;
    }

    public static class TimelinePoint {
        @SerializedName("locationName")
        public String locationName;


        @SerializedName("st")
        public String scheduledTime;

        @SerializedName("et")
        public String estimatedTime;

        @SerializedName("at")
        public String actualTime;

        @SerializedName("isCancelled")
        public boolean isCancelled;
    }

    public static class Formation {
        @SerializedName("coaches")
        public List<Coach> coaches;
    }

    public static class Coach {
        @SerializedName("number")
        public String number;

        @SerializedName("toilet")
        public Toilet toilet;
    }

    public static class Toilet {
        @SerializedName(value = "value", alternate = {"type", "Type", "Value", "$", "#text"})
        public String value;

    }
}