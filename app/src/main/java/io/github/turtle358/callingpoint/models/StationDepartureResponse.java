package io.github.turtle358.callingpoint.models;

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


        @SerializedName("serviceID")
        public String serviceID;
    }

    public static class Location {
        @SerializedName("locationName")
        public String locationName;

    }
}