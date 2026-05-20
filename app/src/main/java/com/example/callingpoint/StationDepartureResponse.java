package com.example.callingpoint;

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

        // UPDATED: Variable and Class name now perfectly match the Fragment
        @SerializedName("subsequentCallingPoints")
        public List<CallingPointList> subsequentCallingPoints;

        @SerializedName("serviceID")
        public String serviceID;
    }

    // UPDATED: Class name capitalized
    public static class CallingPointList {
        // UPDATED: JSON key has a capital P!
        @SerializedName("callingPoint")
        public List<CallingPoint> callingPoint;
    }

    // UPDATED: Class name capitalized
    public static class CallingPoint {

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