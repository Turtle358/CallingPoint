package io.github.turtle358.callingpoint.models;

import com.google.gson.annotations.SerializedName;

public class TflVehicleArrival {
    @SerializedName("naptanId")
    public String naptanId;

    @SerializedName("stationName")
    public String stationName;

    @SerializedName("expectedArrival")
    public String expectedArrival;

    @SerializedName("timeToStation")
    public int timeToStation;

    @SerializedName("platformName")
    public String platformName;

    @SerializedName("lineName")
    public String lineName;
}