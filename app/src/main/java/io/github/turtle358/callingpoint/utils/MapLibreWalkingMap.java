package io.github.turtle358.callingpoint.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.turtle358.callingpoint.R;

/** Renders a walking leg on a MapLibre vector map. */
public final class MapLibreWalkingMap {
    private static final String TAG = "MapLibreWalkingMap";
    private static final String WORKER = "https://callingpoint-api.callingpoint.co.uk";
    private static final ExecutorService ROUTES = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int BLUE = 0xFF4285F4;

    private MapLibreWalkingMap() {}

    public static String getStyleUrl(Context context) {
        boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return WORKER + "/maptiler/maps/"
                + (dark ? "streets-v4-dark" : "streets-v4") + "/style.json";
    }

    /** Clear callbacks when a RecyclerView holder's MapView is being destroyed. */
    public static void invalidate(MapView mapView) {
        mapView.setTag(R.id.map_route_request_tag, null);
    }


    public static void render(
            Context context,
            MapView mapView,
            String origin,
            String destination
    ) {
        render(context, mapView, origin, destination, null);
    }

    public static void render(
            Context context,
            MapView mapView,
            String origin,
            String destination,
            Runnable onMissingEndpoints
    ) {
        final String key = String.valueOf(origin) + "\n"
                + String.valueOf(destination);

        mapView.setTag(R.id.map_route_request_tag, key);

        if (origin == null || origin.trim().isEmpty()
                || destination == null || destination.trim().isEmpty()) {
            reportMissingEndpoints(mapView, key, onMissingEndpoints);
            return;
        }

        final Context app = context.getApplicationContext();
        final String styleUrl = getStyleUrl(context);

        ROUTES.execute(() -> {
            try {
                GeoPoint[] located = WalkLocator.resolve(
                        app, origin, destination
                );

                if (located == null
                        || located.length < 2
                        || located[0] == null
                        || located[1] == null) {

                    reportMissingEndpoints(
                            mapView, key, onMissingEndpoints
                    );
                    return;
                }

                LatLng start = new LatLng(
                        located[0].getLatitude(),
                        located[0].getLongitude()
                );

                LatLng end = new LatLng(
                        located[1].getLatitude(),
                        located[1].getLongitude()
                );

                if (!Double.isFinite(start.getLatitude())
                        || !Double.isFinite(start.getLongitude())
                        || !Double.isFinite(end.getLatitude())
                        || !Double.isFinite(end.getLongitude())
                        || Math.abs(start.getLatitude()) > 90
                        || Math.abs(end.getLatitude()) > 90
                        || Math.abs(start.getLongitude()) > 180
                        || Math.abs(end.getLongitude()) > 180) {

                    reportMissingEndpoints(
                            mapView, key, onMissingEndpoints
                    );
                    return;
                }

                float[] distance = new float[1];

                android.location.Location.distanceBetween(
                        start.getLatitude(),
                        start.getLongitude(),
                        end.getLatitude(),
                        end.getLongitude(),
                        distance
                );

                if (distance[0] < 5f) {
                    reportMissingEndpoints(
                            mapView, key, onMissingEndpoints
                    );
                    return;
                }

                List<LatLng> route = loadOsrm(
                        start, end, app.getPackageName()
                );

                if (route.size() < 2) {
                    route.clear();
                    route.add(start);
                    route.add(end);
                }

                MAIN.post(() -> {
                    if (!key.equals(
                            mapView.getTag(R.id.map_route_request_tag))) {
                        return;
                    }

                    mapView.getMapAsync(map -> {
                        if (!key.equals(
                                mapView.getTag(R.id.map_route_request_tag))) {
                            return;
                        }

                        map.setStyle(styleUrl, style -> {
                            if (!key.equals(
                                    mapView.getTag(
                                            R.id.map_route_request_tag))) {
                                return;
                            }

                            draw(map, style, route, start, end);
                        });
                    });
                });

            } catch (Exception e) {
                Log.e(TAG, "Walking map failed", e);
                reportMissingEndpoints(
                        mapView, key, onMissingEndpoints
                );
            }
        });
    }

    private static void reportMissingEndpoints(
            MapView mapView,
            String key,
            Runnable callback
    ) {
        MAIN.post(() -> {
            if (!key.equals(
                    mapView.getTag(R.id.map_route_request_tag))) {
                return;
            }

            Log.w(TAG, "Walking map hidden: endpoints unavailable");

            if (callback != null) {
                callback.run();
            }
        });
    }


    private static List<LatLng> loadOsrm(LatLng start, LatLng end, String appName) {
        List<LatLng> result = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String endpoint = "https://router.project-osrm.org/route/v1/foot/"
                    + start.getLongitude() + "," + start.getLatitude() + ";"
                    + end.getLongitude() + "," + end.getLatitude()
                    + "?overview=full&geometries=geojson";
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", appName + "/1.0");
            if (conn.getResponseCode() != 200) return result;

            StringBuilder response = new StringBuilder();
            try (InputStream stream = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(stream, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }

            JSONArray routes = new JSONObject(response.toString()).optJSONArray("routes");
            if (routes == null || routes.length() == 0) return result;
            JSONArray coordinates = routes.getJSONObject(0)
                    .getJSONObject("geometry").getJSONArray("coordinates");
            for (int i = 0; i < coordinates.length(); i++) {
                JSONArray coordinate = coordinates.getJSONArray(i);
                result.add(new LatLng(coordinate.getDouble(1), coordinate.getDouble(0)));
            }
        } catch (Exception e) {
            Log.w(TAG, "OSRM unavailable; showing direct connection", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return result;
    }

    private static void draw(MapLibreMap map, Style style,
                             List<LatLng> route, LatLng start, LatLng end) {
        List<Point> points = new ArrayList<>();
        for (LatLng p : route) points.add(Point.fromLngLat(p.getLongitude(), p.getLatitude()));

        style.addSource(new GeoJsonSource("cp-walk-route",
                Feature.fromGeometry(LineString.fromLngLats(points))));
        style.addLayer(new LineLayer("cp-walk-route-layer", "cp-walk-route")
                .withProperties(
                        PropertyFactory.lineColor(BLUE),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));

        style.addSource(new GeoJsonSource("cp-walk-start",
                Feature.fromGeometry(Point.fromLngLat(start.getLongitude(), start.getLatitude()))));
        style.addLayer(new CircleLayer("cp-walk-start-layer", "cp-walk-start")
                .withProperties(
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleColor(0xFFFFFFFF),
                        PropertyFactory.circleStrokeColor(BLUE),
                        PropertyFactory.circleStrokeWidth(3f)));

        style.addSource(new GeoJsonSource("cp-walk-end",
                Feature.fromGeometry(Point.fromLngLat(end.getLongitude(), end.getLatitude()))));
        style.addLayer(new CircleLayer("cp-walk-end-layer", "cp-walk-end")
                .withProperties(
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleColor(0xFF111827),
                        PropertyFactory.circleStrokeColor(0xFFFFFFFF),
                        PropertyFactory.circleStrokeWidth(2.5f)));

        double span = Math.abs(start.getLatitude() - end.getLatitude())
                + Math.abs(start.getLongitude() - end.getLongitude());
        if (span < 0.00001) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(start, 16), 350);
            return;
        }

        LatLngBounds.Builder bounds = new LatLngBounds.Builder()
                .include(start).include(end);
        for (LatLng p : route) bounds.include(p);
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 48), 350);
    }


}
