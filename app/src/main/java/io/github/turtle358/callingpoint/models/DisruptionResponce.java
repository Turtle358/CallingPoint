package io.github.turtle358.callingpoint.models;

import com.google.gson.annotations.SerializedName;

public class DisruptionResponce {

    public static class ServiceIndicator {
        public String description = "";
        public String plannedDescription = "";

        @SerializedName("tocCode")
        public String tocCode;

        @SerializedName("tocName")
        public String tocName;

        @SerializedName("status")
        public String status;
        @SerializedName("serviceStatus")
        public String serviceStatus;

        @SerializedName("statusDescription")
        public String statusDescription;
        public String getBestStatus() {
            if (status != null && !status.trim().isEmpty()) return status;
            if (serviceStatus != null && !serviceStatus.trim().isEmpty()) return serviceStatus;
            if (statusDescription != null && !statusDescription.trim().isEmpty()) return statusDescription;
            return "Good Service";
        }
    }
}