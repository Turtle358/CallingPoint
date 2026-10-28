package io.github.turtle358.callingpoint.api;

import java.util.List;

import io.github.turtle358.callingpoint.models.TflArrivalPrediction;
import io.github.turtle358.callingpoint.models.TflLineStatusResponse;
import io.github.turtle358.callingpoint.models.TflVehicleArrival;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import com.google.gson.JsonObject;

public interface TflAPI {

    @GET("tfl/StopPoint/{naptanId}/Arrivals")
    Call<List<TflArrivalPrediction>> getStopPointArrivals(
            @Path("naptanId") String naptanId
    );

    @GET("tfl/Line/Mode/{modes}/Status")
    Call<List<TflLineStatusResponse>> getLineStatus(
            @Path("modes") String modes
    );

    @GET("tfl/Journey/JourneyResults/{from}/to/{to}")
    Call<JsonObject> getJourneyResults(
            @Path("from") String fromId,
            @Path("to") String toId,
            @Query("nationalSearch") boolean nationalSearch,
            @Query("date") String date,
            @Query("time") String time,
            @Query("timeIs") String timeIs,
            @Query("journeyPreference") String journeyPreference,
            @Query("mode") String mode,
            @Query("accessibilityPreference") String accessibilityPreference
    );


    @GET("tfl/StopPoint/Search/{query}")
    Call<JsonObject> searchJourneyStations(
            @Path("query") String query,
            @Query("modes") String modes,
            @Query("maxResults") int maxResults
    );

    @GET("tfl/Vehicle/{vehicleId}/Arrivals")
    Call<List<TflVehicleArrival>> getVehicleArrivals(@Path("vehicleId") String vehicleId);
}