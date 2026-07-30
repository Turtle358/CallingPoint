package io.github.turtle358.callingpoint.ui.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import io.github.turtle358.callingpoint.ui.adapters.JourneyLegAdapter;
import io.github.turtle358.callingpoint.utils.PinnedJourneyManager;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class JourneyDetailsActivity extends AppCompatActivity {

    private JourneyLegAdapter adapter;
    private final Map<String, ServiceDetailsResponse> liveDataMap = new HashMap<>();
    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE);
        Configuration.getInstance().load(ctx, prefs);
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_journey_details);

        View topHeaderBar = findViewById(R.id.topHeaderBar);
        if (topHeaderBar != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(topHeaderBar, (v, insets) -> {
                v.getLayoutParams().height = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
                v.requestLayout();
                return insets;
            });
        }

        JourneyOption journey = (JourneyOption) getIntent().getSerializableExtra("JOURNEY_OPTION");

        if (journey == null || journey.legs == null || journey.legs.isEmpty()) {
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tvDetailTitle);
        TextView tvSummary = findViewById(R.id.tvDetailSummary);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        String primaryBrand = "walk";
        for (JourneyLeg leg : journey.legs) {
            String op = leg.operatorName;
            if (op == null || op.trim().isEmpty() || op.toLowerCase().contains("walk")) {
                continue;
            }
            if ("BUS".equalsIgnoreCase(leg.transportMode) || (op.matches("^[a-zA-Z]{0,2}\\d+[a-zA-Z]?$") && !op.equalsIgnoreCase("c2c"))) {
                primaryBrand = "bus";
            } else {
                primaryBrand = op;
            }
            break;
        }

        int brandColour = TOCBrandHelper.getColourForToc(primaryBrand);

        if (topHeaderBar != null) {
            topHeaderBar.setBackgroundColor(brandColour);
        }

        boolean isLightColour = ColorUtils.calculateLuminance(brandColour) > 0.5;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(isLightColour);

        String origin = journey.legs.get(0).fromStationName;
        String dest = journey.legs.get(journey.legs.size() - 1).toStationName;

        String titleText = origin + " to " + dest;
        tvTitle.setText(titleText);

        tvTitle.post(() -> {
            if (tvTitle.getLineCount() > 1) {
                tvTitle.setText(origin + "\nto " + dest);
            }
        });

        String summary = journey.departTime + " — " + journey.arriveTime + "  •  " + journey.duration;
        tvSummary.setText(summary);

        setupPinButton(journey);

        androidx.cardview.widget.CardView cardTimeline = findViewById(R.id.cardTimeline);
        androidx.cardview.widget.CardView cardWalkOnlyMap = findViewById(R.id.cardWalkOnlyMap);
        RecyclerView recyclerJourneyLegs = findViewById(R.id.recyclerJourneyLegs);

        boolean isWalkOnly = journey.legs.size() == 1 && "TUBE/WALK".equals(journey.legs.get(0).transportMode);

        if (isWalkOnly) {
            cardTimeline.setVisibility(View.GONE);
            cardWalkOnlyMap.setVisibility(View.VISIBLE);

            mapView = findViewById(R.id.mapViewTransfer);

            org.osmdroid.tileprovider.tilesource.ITileSource CARTO_VOYAGER = new org.osmdroid.tileprovider.tilesource.XYTileSource(
                    "CartoDB Voyager",
                    0, 19, 256, ".png",
                    new String[]{
                            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
                            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
                            "https://c.basemaps.cartocdn.com/rastertiles/voyager/"
                    });
            mapView.setTileSource(CARTO_VOYAGER);

            mapView.setMultiTouchControls(true);
            mapView.setTilesScaledToDpi(true);
            mapView.setBuiltInZoomControls(false);

            mapView.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN ||
                        event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            });

            new Thread(() -> {
                android.location.Geocoder geocoder = new android.location.Geocoder(this);
                try {
                    java.util.List<android.location.Address> originLoc = geocoder.getFromLocationName(origin + " Station, London", 1);
                    java.util.List<android.location.Address> destLoc = geocoder.getFromLocationName(dest + " Station, London", 1);

                    if (originLoc != null && !originLoc.isEmpty() && destLoc != null && !destLoc.isEmpty()) {
                        GeoPoint startPoint = new GeoPoint(originLoc.get(0).getLatitude(), originLoc.get(0).getLongitude());
                        GeoPoint endPoint = new GeoPoint(destLoc.get(0).getLatitude(), destLoc.get(0).getLongitude());

                        Polyline routeLine = new Polyline(mapView);
                        android.graphics.Paint paint = routeLine.getOutlinePaint();
                        paint.setColor(android.graphics.Color.parseColor("#4285F4"));
                        paint.setStrokeWidth(16.0f);
                        paint.setAntiAlias(true);
                        paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                        paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);

                        try {
                            JSONArray routes = getJsonArray(startPoint, endPoint);

                            if (routes.length() > 0) {
                                org.json.JSONArray coordinates = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
                                for (int i = 0; i < coordinates.length(); i++) {
                                    org.json.JSONArray coord = coordinates.getJSONArray(i);
                                    routeLine.addPoint(new GeoPoint(coord.getDouble(1), coord.getDouble(0)));
                                }
                            } else {
                                routeLine.addPoint(startPoint);
                                routeLine.addPoint(endPoint);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            routeLine.addPoint(startPoint);
                            routeLine.addPoint(endPoint);
                        }

                        runOnUiThread(() -> {
                            mapView.getOverlays().add(routeLine);

                            Marker startMarker = new Marker(mapView);
                            startMarker.setPosition(startPoint);
                            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                            startMarker.setIcon(createStartMarkerDrawable());
                            mapView.getOverlays().add(startMarker);

                            Marker endMarker = new Marker(mapView);
                            endMarker.setPosition(endPoint);
                            endMarker.setAnchor(0.5f, 0.714f);
                            endMarker.setIcon(createEndMarkerDrawable());
                            mapView.getOverlays().add(endMarker);

                            mapView.post(() -> {
                                BoundingBox bounds = BoundingBox.fromGeoPoints(java.util.Arrays.asList(startPoint, endPoint));
                                mapView.zoomToBoundingBox(bounds, true, 200);
                            });
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            findViewById(R.id.fabOpenExternalMap).setOnClickListener(v -> {
                try {
                    String destQuery = java.net.URLEncoder.encode(dest + " Station, London", StandardCharsets.UTF_8);
                    String uriString = "https://www.google.com/maps/dir/?api=1&destination=" + destQuery + "&travelmode=walking";
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Unable to open Maps app", Toast.LENGTH_SHORT).show();
                }
            });

        } else {
            cardTimeline.setVisibility(View.VISIBLE);
            cardWalkOnlyMap.setVisibility(View.GONE);

            recyclerJourneyLegs.setLayoutManager(new LinearLayoutManager(this));
            adapter = new JourneyLegAdapter(journey.legs);
            recyclerJourneyLegs.setAdapter(adapter);

            fetchLiveServiceDetails(journey);
        }
    }

    @NonNull
    private JSONArray getJsonArray(GeoPoint startPoint, GeoPoint endPoint) throws IOException, JSONException {
        String urlString = "https://router.project-osrm.org/route/v1/foot/"
                + startPoint.getLongitude() + "," + startPoint.getLatitude() + ";"
                + endPoint.getLongitude() + "," + endPoint.getLatitude()
                + "?overview=full&geometries=geojson";

        java.net.URL url = new java.net.URL(urlString);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", getPackageName() + "/1.0");

        java.io.InputStream in = new java.io.BufferedInputStream(conn.getInputStream());
        java.util.Scanner scanner = new java.util.Scanner(in).useDelimiter("\\A");
        String response = scanner.hasNext() ? scanner.next() : "";

        org.json.JSONObject json = new org.json.JSONObject(response);
        JSONArray routes = json.getJSONArray("routes");
        return routes;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.onDetach();
    }

    private void setupPinButton(JourneyOption journey) {
        ImageButton btnPinJourney = findViewById(R.id.btnPinJourney);
        PinnedJourneyManager pinnedManager = new PinnedJourneyManager(this);

        boolean isPinned = pinnedManager.isJourneyPinned(journey);
        btnPinJourney.setImageResource(isPinned ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);

        btnPinJourney.setOnClickListener(v -> {
            pinnedManager.toggleJourneyPin(journey);
            boolean isNowPinned = pinnedManager.isJourneyPinned(journey);
            btnPinJourney.setImageResource(isNowPinned ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
            Toast.makeText(this, isNowPinned ? "Journey Saved!" : "Journey Removed", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchLiveServiceDetails(JourneyOption journey) {
        NationalRailAPI api = APIClient.getClient().create(NationalRailAPI.class);

        int fetchCount = 0;
        for (JourneyLeg leg : journey.legs) {
            if ("TRAIN".equals(leg.transportMode) && leg.serviceId != null) {
                fetchCount++;
            }
        }

        if (fetchCount == 0) return;

        final int[] completedRequests = {0};
        final int totalRequests = fetchCount;

        for (JourneyLeg leg : journey.legs) {
            if ("TRAIN".equals(leg.transportMode) && leg.serviceId != null) {
                api.getServiceDetails(leg.serviceId).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ServiceDetailsResponse> call, @NonNull Response<ServiceDetailsResponse> response) {
                        completedRequests[0]++;
                        if (response.isSuccessful() && response.body() != null) {
                            liveDataMap.put(leg.serviceId, response.body());
                        }
                        checkAndPushData(completedRequests[0], totalRequests);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ServiceDetailsResponse> call, @NonNull Throwable t) {
                        completedRequests[0]++;
                        checkAndPushData(completedRequests[0], totalRequests);
                    }
                });
            }
        }
    }

    private void checkAndPushData(int completed, int total) {
        if (completed == total && adapter != null) {
            adapter.updateLiveData(liveDataMap);
        }
    }


    private android.graphics.drawable.Drawable createStartMarkerDrawable() {
        float density = getResources().getDisplayMetrics().density;
        int size = (int) (36 * density);

        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        paint.setColor(android.graphics.Color.WHITE);
        paint.setStyle(android.graphics.Paint.Style.FILL);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - (1 * density), paint);

        paint.setColor(android.graphics.Color.parseColor("#22000000"));
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - (1 * density), paint);

        paint.setColor(android.graphics.Color.BLACK);
        paint.setStyle(android.graphics.Paint.Style.FILL);
        canvas.drawCircle(size / 2f, size / 2f, 6 * density, paint);

        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }

    private android.graphics.drawable.Drawable createEndMarkerDrawable() {
        float density = getResources().getDisplayMetrics().density;
        int width = (int) (40 * density);
        int height = (int) (70 * density);

        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        paint.setColor(android.graphics.Color.BLACK);
        paint.setStyle(android.graphics.Paint.Style.FILL);
        android.graphics.RectF pillRect = new android.graphics.RectF(0, 0, width, height);
        canvas.drawRoundRect(pillRect, width / 2f, width / 2f, paint);

        paint.setColor(android.graphics.Color.WHITE);
        paint.setTextSize(10 * density);
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);

        android.graphics.Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float textY = (22 * density) - ((fontMetrics.descent + fontMetrics.ascent) / 2f);
        canvas.drawText("E N D", width / 2f, textY, paint);

        float dotCenterY = height - (20 * density);
        paint.setColor(android.graphics.Color.WHITE);
        canvas.drawCircle(width / 2f, dotCenterY, 9 * density, paint);

        paint.setColor(android.graphics.Color.BLACK);
        canvas.drawCircle(width / 2f, dotCenterY, 4 * density, paint);

        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }
}