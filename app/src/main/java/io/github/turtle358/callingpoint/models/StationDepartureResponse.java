package io.github.turtle358.callingpoint.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StationDepartureResponse {
    @SerializedName("trainServices")
    public List<TrainService> trainServices;

    public static class TrainService {
        @SerializedName("std")
        public String scheduledDepartureTime;

        @SerializedName("etd")
        public String estimatedDepartureTime;

        @SerializedName("sta")
        public String scheduledArrivalTime;

        @SerializedName("eta")
        public String estimatedArrivalTime;

        @SerializedName("ata")
        public String actualArrivalTime;

        @SerializedName("atd")
        public String actualDepartureTime;

        @SerializedName("origin")
        public List<Location> origin;

        @SerializedName("platform")
        public String platform;

        @SerializedName("operator")
        public String operatorName;

        @SerializedName("destination")
        public List<Location> destination;

        @SerializedName("subsequentCallingPoints")
        public List<CallingPointList> subsequentCallingPoints;

        @SerializedName("previousCallingPoints")
        public List<CallingPointList> previousCallingPoints;

        @SerializedName("serviceID")
        public String serviceID;
    }

    public static class CallingPointList {
        @SerializedName("callingPoint")
        public List<CallingPoint> callingPoint;
    }

    public static class CallingPoint {
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
    }

    public static class Location {
        @SerializedName("locationName")
        public String locationName;

        @SerializedName("crs")
        public String crs;

        @SerializedName("via")
        public String via;
    }
}