package io.github.turtle358.callingpoint.api;

import java.util.List;

import io.github.turtle358.callingpoint.models.TflArrivalPrediction;
import io.github.turtle358.callingpoint.models.TflLineStatusResponse;
import io.github.turtle358.callingpoint.models.TflVehicleArrival;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

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
    Call<Object> getJourneyResults(
            @Path("from") String fromId,
            @Path("to") String toId,
            @Query("nationalSearch") boolean nationalSearch
    );

    @GET("tfl/Vehicle/{vehicleId}/Arrivals")
    Call<List<TflVehicleArrival>> getVehicleArrivals(@Path("vehicleId") String vehicleId);
}