package io.github.turtle358.callingpoint.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ServiceDetailsResponse {

    public String customServiceID;

    @SerializedName("isCancelled")
    public boolean isCancelled;

    @SerializedName("locationName")
    public String locationName;

    @SerializedName("crs")
    public String crs;

    @SerializedName("std")
    public String scheduledDepartureTime;

    @SerializedName("etd")
    public String estimatedDepartureTime;

    @SerializedName("ata")
    public String actualArrivalTime;

    @SerializedName("atd")
    public String actualDepartureTime;

    @SerializedName("sta")
    public String scheduledArrivalTime;

    @SerializedName("eta")
    public String estimatedArrivalTime;

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

    @SerializedName("length")
    public int length;

    public static class CallingPointList {
        @SerializedName("callingPoint")
        public List<TimelinePoint> callingPoint;
    }

    public static class TimelinePoint {
        @SerializedName("locationName")
        public String locationName;

        @SerializedName("crs")
        public String crs;

        @SerializedName("st")
        public String scheduledTime;

        @SerializedName("et")
        public String estimatedTime;

        @SerializedName("at")
        public String actualTime;

        @SerializedName("isCancelled")
        public boolean isCancelled;

        @SerializedName("ata")
        public String actualArrivalTime;

        @SerializedName("atd")
        public String actualDepatureTime;

        @SerializedName("platform")
        public String platform;
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