package io.github.turtle358.callingpoint.utils;

import android.content.Context;

import org.maplibre.android.MapLibre;
import org.maplibre.android.module.http.HttpRequestUtil;

import io.github.turtle358.callingpoint.BuildConfig;
import okhttp3.OkHttpClient;

public class CallingPointMapNetwork {
    private static boolean initialized;

    private CallingPointMapNetwork() {}

    public static synchronized void initialize(Context context) {
        MapLibre.getInstance(context.getApplicationContext());
        if (initialized) return;

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    okhttp3.Request request = chain.request();
                    boolean isCallingPointMap =
                            "callingpoint-api.callingpoint.co.uk".equalsIgnoreCase(
                                    request.url().host())
                                    && request.url().encodedPath().startsWith("/maptiler/");

                    if (isCallingPointMap) {
                        request = request.newBuilder()
                                .header("x-callingpoint-secret", BuildConfig.APP_SECRET)
                                .build();
                    }
                    return chain.proceed(request);
                })
                .build();

        HttpRequestUtil.setOkHttpClient(client);
        MapLibre.getInstance(context.getApplicationContext());
        initialized = true;
    }
}
