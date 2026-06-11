package io.github.turtle358.callingpoint.api;

import java.util.List;

import io.github.turtle358.callingpoint.models.IncidentResponse;
import io.github.turtle358.callingpoint.models.IncidentSearchRequest;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import io.github.turtle358.callingpoint.models.StationDepartureResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import okhttp3.ResponseBody;
import retrofit2.http.Url;

public interface NationalRailAPI {

    @POST("1010-disruptions-experience-api-11_0/disruptions/incidents/search")
    Call<List<IncidentResponse>> searchIncidents(
            @Header("x-apikey") String customerKey,
            @Body IncidentSearchRequest requestBody
    );

    @GET("1010-live-departure-board-dep1_2/LDBWS/api/20220120/GetDepBoardWithDetails/{crs}")
    Call<StationDepartureResponse> getStationDepartures(
            @Header("x-apikey") String customerKey,
            @Path("crs") String crsCode,
            @Query("numRows") int numRows,
            @Query("filterCrs") String filterCrs,
            @Query("filterType") String filterType,
            @Query("timeOffset") int timeOffset,
            @Query("timeWindow") int timeWindow
    );

    @GET("1010-live-arrival-board-arr/LDBWS/api/20220120/GetArrBoardWithDetails/{crs}")
    Call<StationDepartureResponse> getStationArrivals(
            @Header("x-apikey") String customerKey,
            @Path("crs") String crsCode,
            @Query("numRows") int numRows,
            @Query("filterCrs") String filterCrs,
            @Query("filterType") String filterType,
            @Query("timeOffset") int timeOffset,
            @Query("timeWindow") int timeWindow
    );

    @GET("1010-service-details1_2/LDBWS/api/20220120/GetServiceDetails/{serviceID}")
    Call<ServiceDetailsResponse> getServiceDetails(
            @Header("x-apikey") String serviceDetailsKey,
            @Path("serviceID") String serviceID
    );

    @GET
    Call<ResponseBody> downloadRemoteData(@Url String url);
}