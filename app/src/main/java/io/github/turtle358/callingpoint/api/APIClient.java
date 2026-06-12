package io.github.turtle358.callingpoint.api;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.io.IOException;
import io.github.turtle358.callingpoint.BuildConfig;

public class APIClient {
    private static final String BASE_URL = "https://callingpoint-api.callingpoint.dpdns.org/";

    private static final String APP_SECRET = BuildConfig.APP_SECRET;

    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {

            Interceptor headerInterceptor = new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request originalRequest = chain.request();

                    Request requestWithHeaders = originalRequest.newBuilder()
                            .header("x-callingpoint-secret", APP_SECRET)
                            .build();

                    return chain.proceed(requestWithHeaders);
                }
            };

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(headerInterceptor)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}