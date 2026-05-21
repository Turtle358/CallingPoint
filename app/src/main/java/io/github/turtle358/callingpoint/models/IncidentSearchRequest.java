package io.github.turtle358.callingpoint.models;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IncidentSearchRequest {
    @SerializedName("startDate")
    public String startDate;

    @SerializedName("endDate")
    public String endDate;

    @SerializedName("tocCodes")
    public List<String> tocCodes;
    public IncidentSearchRequest(String start, String end, List<String> tocCodes) {
        this.startDate = start;
        this.endDate = end;
        this.tocCodes = Objects.requireNonNullElseGet(tocCodes, ArrayList::new);
    }
}