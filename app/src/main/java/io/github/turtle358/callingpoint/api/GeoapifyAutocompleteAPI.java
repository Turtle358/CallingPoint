package io.github.turtle358.callingpoint.api;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GeoapifyAutocompleteAPI {
    @GET("geoapify/autocomplete")
    Call<JsonObject> suggestions(@Query("text") String text);
}
