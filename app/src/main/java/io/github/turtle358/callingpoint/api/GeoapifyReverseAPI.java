package io.github.turtle358.callingpoint.api;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GeoapifyReverseAPI {
    @GET("geoapify/reverse")
    Call<JsonObject> reverse(@Query("lat") double latitude, @Query("lon") double longitude);
}
