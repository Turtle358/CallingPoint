package io.github.turtle358.callingpoint.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.QueryMap;

public interface TransitousAPI {
    @GET("transitous/v1/geocode")
    Call<JsonArray> geocode(@QueryMap Map<String, String> query);

    @GET("transitous/v6/plan")
    Call<JsonObject> plan(@QueryMap Map<String, String> query);
}
