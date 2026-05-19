package com.example.delaywatcher;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface NationalRailAPI {

    @POST("1010-disruptions-experience-api-11_0/disruptions/incidents/search")
    Call<List<IncidentResponse>> searchIncidents(
            @Header("x-apikey") String customerKey,
            @Body IncidentSearchRequest requestBody
    );

    @GET("1010-live-departure-board-dep1_2/LDBWS/api/20220120/GetDepBoardWithDetails/{crs}")
    Call<StationDepartureResponse> getStationDepartures(
            @Header("x-apikey") String customerKey,
            @Path("crs") String crsCode
    );

    @GET("1010-service-details1_2/LDBWS/api/20220120/GetServiceDetails/{serviceID}")
    Call<ServiceDetailsResponse> getServiceDetails(
            @Header("x-apikey") String serviceDetailsKey,
            @Path("serviceID") String serviceID
    );
}