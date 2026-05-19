package com.example.delaywatcher;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StationDepartureResponse {
    @SerializedName("trainServices")
    public List<TrainService> trainServices;

    public static class TrainService {
        @SerializedName("std")
        public String std;

        @SerializedName("etd")
        public String etd;

        @SerializedName("platform")
        public String platform;

        @SerializedName("operator")
        public String operatorName;

        @SerializedName("destination")
        public List<Location> destination;

        @SerializedName("subsequentCallingPoints")
        public List<SubsequentCallingPointList> subsequentCallingPoints;
    }

    public static class SubsequentCallingPointList {
        @SerializedName("callingPoint")
        public List<CallingPoint> callingPoint;
    }

    public static class CallingPoint {
        @SerializedName("locationName")
        public String locationName;

        @SerializedName("crs")
        public String crs;
    }

    public static class Location {
        @SerializedName("locationName")
        public String locationName;

        @SerializedName("crs")
        public String crs;
    }
}