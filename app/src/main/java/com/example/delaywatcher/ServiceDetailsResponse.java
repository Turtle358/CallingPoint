package com.example.delaywatcher;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ServiceDetailsResponse {
    @SerializedName("locationName")
    public String locationName;
    @SerializedName("serviceType")
    public String serviceType;

    @SerializedName("std")
    public String std;

    @SerializedName("etd")
    public String etd;

    @SerializedName("platform")
    public String platform;

    @SerializedName("operator")
    public String operator;

    @SerializedName("previousCallingPoints")
    public List<CallingPointList> previousCallingPoints;

    @SerializedName("subsequentCallingPoints")
    public List<CallingPointList> subsequentCallingPoints;

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
    }
}