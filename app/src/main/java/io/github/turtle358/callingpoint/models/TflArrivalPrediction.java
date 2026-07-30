package io.github.turtle358.callingpoint.models;

import com.google.gson.annotations.SerializedName;

public class TflArrivalPrediction {

    @SerializedName("id")
    public String id;

    @SerializedName("vehicleId")
    public String vehicleId;

    @SerializedName("naptanId")
    public String naptanId;

    @SerializedName("stationName")
    public String stationName;

    @SerializedName("lineId")
    public String lineId;

    @SerializedName("lineName")
    public String lineName;

    @SerializedName("platformName")
    public String platformName;

    @SerializedName("direction")
    public String direction;

    @SerializedName("destinationName")
    public String destinationName;

    @SerializedName("timeToStation")
    public int timeToStation;

    @SerializedName("expectedArrival")
    public String expectedArrival;

    @SerializedName("currentLocation")
    public String currentLocation;
}