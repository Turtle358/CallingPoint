package io.github.turtle358.callingpoint.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TflLineStatusResponse {
    @SerializedName("id")
    public String id;

    @SerializedName("name")
    public String name;

    @SerializedName("lineStatuses")
    public List<LineStatus> lineStatuses;

    public static class LineStatus {
        @SerializedName("statusSeverity")
        public int statusSeverity;

        @SerializedName("statusSeverityDescription")
        public String statusSeverityDescription;

        @SerializedName("reason")
        public String reason;
    }
}