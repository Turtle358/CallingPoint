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

        @SerializedName("subsequentcallingpoints")
        public List<SubsequentcallingpointList> subsequentcallingpoints;

        @SerializedName("serviceID")
        public String serviceID;
    }

    public static class SubsequentcallingpointList {
        @SerializedName("callingpoint")
        public List<callingpoint> callingpoint;
    }

    public static class callingpoint {
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