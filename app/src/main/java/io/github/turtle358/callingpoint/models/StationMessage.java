package io.github.turtle358.callingpoint.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StationMessage {

    @SerializedName("crsCode")
    public String crsCode;

    @SerializedName("creationTime")
    public String creationTime;

    @SerializedName("stationDisruptions")
    public List<StationDisruption> stationDisruptions;

    @SerializedName("stationAlerts")
    public String stationAlerts;

    public static class StationDisruption {
        @SerializedName("category")
        public String category;

        @SerializedName("severity")
        public String severity;

        @SerializedName("summary")
        public String summary;

        @SerializedName("description")
        public String description;
    }
}