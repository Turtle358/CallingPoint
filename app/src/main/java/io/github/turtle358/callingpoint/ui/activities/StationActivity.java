package io.github.turtle358.callingpoint.ui.activities;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.api.TflAPI;
import io.github.turtle358.callingpoint.data.PinnedDataManager;
import io.github.turtle358.callingpoint.models.StationDepartureResponse;
import io.github.turtle358.callingpoint.models.StationMessage;
import io.github.turtle358.callingpoint.models.TflArrivalPrediction;
import io.github.turtle358.callingpoint.ui.adapters.DeparturesAdapter;
import io.github.turtle358.callingpoint.utils.StationHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StationActivity extends AppCompatActivity {

    private String passedCode = "";
    private String resolvedStationName = "";
    private String activeCrs = "";
    private String activeNaptan = "";

    private String toCrs = "";
    private String pinKey = "";
    private boolean isArrivals = false;
    private int currentOffset = 0;
    private boolean isShowingTfl = false;

    private View rootView;

    private TextView tvDirectionHeading, tvStationTitle, tvStationSubtitle, tvEmptyState;
    private ImageButton btnPinStation;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerDepartures;
    private Button btnLaterServices;
    private MaterialButtonToggleGroup toggleServiceType;

    private LinearLayout containerStationMessages;
    private TextView btnToggleMessages;

    private DeparturesAdapter adapter;
    private NationalRailAPI api;
    private TflAPI tflApi;
    private PinnedDataManager pinnedDataManager;

    private boolean isMessagesExpanded = false;
    private List<UnifiedMessage> currentMessages = new ArrayList<>();

    private ShimmerFrameLayout shimmerView;

    private static class UnifiedMessage implements Comparable<UnifiedMessage> {
        String text;
        String severity;
        String category;
        int severityWeight;

        UnifiedMessage(String text, String severity, String category) {
            this.text = text;
            this.severity = severity != null ? severity : "Information";
            this.category = category != null ? category : "Station Alert";

            String sevLower = this.severity.toLowerCase();
            if (sevLower.contains("severe")) severityWeight = 4;
            else if (sevLower.contains("major")) severityWeight = 3;
            else if (sevLower.contains("minor")) severityWeight = 2;
            else severityWeight = 1;
        }

        @Override
        public int compareTo(UnifiedMessage other) {
            return Integer.compare(other.severityWeight, this.severityWeight);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        setContentView(R.layout.activity_station);
        rootView = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        shimmerView = findViewById(R.id.shimmer_view_container);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (getIntent().getExtras() != null) {
            Bundle extras = getIntent().getExtras();
            passedCode = extras.getString("FROM_CRS", "");
            toCrs = extras.getString("TO_CRS", "");
            isArrivals = extras.getBoolean("IS_ARRIVALS", false);
        }

        resolvedStationName = StationHelper.getNameFromCode(passedCode);
        if (resolvedStationName.isEmpty()) resolvedStationName = passedCode;

        activeCrs = StationHelper.getCrsFromName(resolvedStationName);
        activeNaptan = StationHelper.getNaptanFromName(resolvedStationName);

        pinKey = passedCode + (toCrs.isEmpty() ? "" : ":" + toCrs);

        tvDirectionHeading = findViewById(R.id.tvDirectionHeading);
        tvStationTitle = findViewById(R.id.tvStationTitle);
        tvStationSubtitle = findViewById(R.id.tvStationSubtitle);
        btnPinStation = findViewById(R.id.btnPinStation);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshStation);
        recyclerDepartures = findViewById(R.id.recyclerDepartures);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        btnLaterServices = findViewById(R.id.btnLaterServices);
        containerStationMessages = findViewById(R.id.containerStationMessages);
        toggleServiceType = findViewById(R.id.toggleServiceType);

        pinnedDataManager = new PinnedDataManager(this);
        api = APIClient.getClient().create(NationalRailAPI.class);
        tflApi = APIClient.getClient().create(TflAPI.class);

        setupHeader();
        setupPinButton();
        checkTflAvailability();
        setupToggle();

        recyclerDepartures.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeparturesAdapter(this, new ArrayList<>());
        recyclerDepartures.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            swipeRefreshLayout.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            if (isShowingTfl) {
                fetchTflTrains();
            } else {
                fetchTrains(false);
            }
            if (activeCrs != null && !activeCrs.isEmpty()) {
                fetchStationMessages();
            }
        });

        btnLaterServices.setOnClickListener(v -> {
            btnLaterServices.setText("Loading...");
            btnLaterServices.setEnabled(false);
            fetchTrains(true);
        });

        if (activeCrs != null && !activeCrs.isEmpty()) {
            fetchTrains(false);
            fetchStationMessages();
        } else if (activeNaptan != null && !activeNaptan.isEmpty()) {
            isShowingTfl = true;
            fetchTflTrains();
        } else {
            showEmptyState("Station not found.");
        }
    }

    private void setupToggle() {
        toggleServiceType.setVisibility(View.GONE);

        toggleServiceType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnNationalRail) {
                    isShowingTfl = false;
                    fetchTrains(false);
                } else if (checkedId == R.id.btnTfl) {
                    isShowingTfl = true;
                    fetchTflTrains();
                }
            }
        });
    }

    private void checkTflAvailability() {
        if (activeNaptan == null || activeNaptan.isEmpty()) return;

        if (activeCrs == null || activeCrs.isEmpty()) return;

        tflApi.getStopPointArrivals(activeNaptan).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<TflArrivalPrediction>> call, @NonNull Response<List<TflArrivalPrediction>> response) {
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    android.transition.TransitionManager.beginDelayedTransition((ViewGroup) rootView);
                    toggleServiceType.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<TflArrivalPrediction>> call, @NonNull Throwable t) {
            }
        });
    }

    private void setupHeader() {
        if (isArrivals) {
            tvDirectionHeading.setText("Trains arriving at");
        } else {
            tvDirectionHeading.setText("Trains departing from");
        }

        tvStationTitle.setText(resolvedStationName);

        if (!toCrs.isEmpty()) {
            String toName = StationHelper.getNameFromCode(toCrs);
            if (toName.isEmpty()) toName = toCrs;

            String prefix = isArrivals ? "from " : "to ";
            tvStationSubtitle.setText(prefix + toName);
            tvStationSubtitle.setVisibility(View.VISIBLE);
        }
    }

    private void setupPinButton() {
        updatePinIcon();
        btnPinStation.setOnClickListener(v -> {
            pinnedDataManager.toggleStationPin(pinKey);
            updatePinIcon();
            boolean isNowPinned = pinnedDataManager.isStationPinned(pinKey);
            Toast.makeText(this, isNowPinned ? "Station Pinned!" : "Pin Removed", Toast.LENGTH_SHORT).show();
        });
    }

    private void updatePinIcon() {
        boolean isPinned = pinnedDataManager.isStationPinned(pinKey);
        btnPinStation.setImageResource(isPinned ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
    }

    private void fetchStationMessages() {
        if (activeCrs == null || activeCrs.isEmpty()) return;

        List<String> queryStations = new ArrayList<>();
        queryStations.add(activeCrs);
        if (!toCrs.isEmpty()) {
            String toResolvedCrs = StationHelper.getCrsFromName(StationHelper.getNameFromCode(toCrs));
            if (toResolvedCrs != null && !toResolvedCrs.isEmpty()) {
                queryStations.add(toResolvedCrs);
            }
        }

        api.getStationMessages(queryStations).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<StationMessage>> call, @NonNull Response<List<StationMessage>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    displayStationMessages(response.body());
                }
            }
            @Override
            public void onFailure(@NonNull Call<List<StationMessage>> call, @NonNull Throwable t) { }
        });
    }

    private void displayStationMessages(List<StationMessage> messages) {
        currentMessages.clear();
        for (StationMessage msg : messages) {
            if (msg.stationAlerts != null && !msg.stationAlerts.trim().isEmpty()) {
                currentMessages.add(new UnifiedMessage(msg.stationAlerts, "Information", "Station Alert"));
            }
            if (msg.stationDisruptions != null) {
                for (StationMessage.StationDisruption disruption : msg.stationDisruptions) {
                    if (disruption.summary != null && !disruption.summary.trim().isEmpty()) {
                        currentMessages.add(new UnifiedMessage(disruption.summary, disruption.severity, disruption.category));
                    }
                }
            }
        }
        Collections.sort(currentMessages);
        buildMessageViews();
    }

    private void buildMessageViews() {
        containerStationMessages.removeAllViews();
        isMessagesExpanded = false;

        if (currentMessages.isEmpty()) {
            containerStationMessages.setVisibility(View.GONE);
            return;
        }
        containerStationMessages.setVisibility(View.VISIBLE);

        for (int i = 0; i < currentMessages.size(); i++) {
            View view = createMessageView(currentMessages.get(i));
            if (i > 0) view.setVisibility(View.GONE);
            containerStationMessages.addView(view);
        }

        if (currentMessages.size() > 1) {
            setupToggleButton();
            containerStationMessages.addView(btnToggleMessages);
        }
    }

    private void toggleMessagesExpansion() {
        android.transition.AutoTransition transition = new android.transition.AutoTransition();
        transition.setDuration(200);
        TransitionManager.beginDelayedTransition((ViewGroup) rootView, transition);

        isMessagesExpanded = !isMessagesExpanded;

        for (int i = 1; i < currentMessages.size(); i++) {
            View child = containerStationMessages.getChildAt(i);
            if (child != null) {
                child.setVisibility(isMessagesExpanded ? View.VISIBLE : View.GONE);
            }
        }
        if (isMessagesExpanded) {
            btnToggleMessages.setText("Show less");
        } else {
            btnToggleMessages.setText("Show " + (currentMessages.size() - 1) + " more alerts");
        }
    }

    private View createMessageView(UnifiedMessage msg) {
        View view = getLayoutInflater().inflate(R.layout.item_station_message, containerStationMessages, false);
        View colorBar = view.findViewById(R.id.severityColourBar);
        TextView tvSeverity = view.findViewById(R.id.tvSeverityLabel);
        TextView tvContent = view.findViewById(R.id.tvMessageContent);

        tvSeverity.setText(msg.category + " • " + msg.severity);
        String cleanHtml = msg.text.replace("\\n", "<br/>");
        tvContent.setText(HtmlCompat.fromHtml(cleanHtml, HtmlCompat.FROM_HTML_MODE_COMPACT));
        tvContent.setMovementMethod(LinkMovementMethod.getInstance());

        int colour;
        switch (msg.severityWeight) {
            case 4: colour = 0xFFD32F2F; break;
            case 3: colour = 0xFFD32F2F; break;
            case 2: colour = 0xFFFBC02D; break;
            default: colour = 0xFF1976D2; break;
        }
        colorBar.setBackgroundColor(colour);

        final boolean[] isCardExpanded = {false};
        View.OnClickListener expandClickListener = v -> {
            android.transition.AutoTransition transition = new android.transition.AutoTransition();
            transition.setDuration(200);
            TransitionManager.beginDelayedTransition((ViewGroup) rootView, transition);

            isCardExpanded[0] = !isCardExpanded[0];
            if (isCardExpanded[0]) {
                tvContent.setMaxLines(Integer.MAX_VALUE);
            } else {
                tvContent.setMaxLines(5);
            }
        };
        view.setOnClickListener(expandClickListener);
        tvContent.setOnClickListener(expandClickListener);

        return view;
    }

    private void setupToggleButton() {
        btnToggleMessages = new TextView(this);
        btnToggleMessages.setText("Show " + (currentMessages.size() - 1) + " more alerts");
        btnToggleMessages.setTextColor(getResources().getColor(R.color.brand_primary, null));

        float density = getResources().getDisplayMetrics().density;
        int topPadding = (int) (8 * density);
        int bottomPadding = (int) (32 * density);

        btnToggleMessages.setPadding(0, topPadding, 0, bottomPadding);
        btnToggleMessages.setTextSize(14f);
        btnToggleMessages.setTypeface(null, android.graphics.Typeface.BOLD);
        btnToggleMessages.setGravity(android.view.Gravity.CENTER);

        btnToggleMessages.setOnClickListener(v -> toggleMessagesExpansion());
    }

    private void fetchTflTrains() {
        showShimmer();
        if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);

        tflApi.getStopPointArrivals(activeNaptan).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<TflArrivalPrediction>> call, @NonNull Response<List<TflArrivalPrediction>> response) {
                if (isFinishing() || isDestroyed()) return;
                hideShimmer();
                swipeRefreshLayout.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<TflArrivalPrediction> predictions = response.body();

                    Collections.sort(predictions, (p1, p2) -> Integer.compare(p1.timeToStation, p2.timeToStation));

                    List<StationDepartureResponse.TrainService> mappedServices = new ArrayList<>();
                    java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.UK);
                    isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK);
                    timeFormat.setTimeZone(java.util.TimeZone.getTimeZone("Europe/London"));

                    for (TflArrivalPrediction pred : predictions) {
                        StationDepartureResponse.TrainService tService = new StationDepartureResponse.TrainService();

                        tService.serviceID = pred.id;

                        tService.operatorName = normalizeTflLineName(pred.lineName);

                        try {
                            java.util.Date expectedDate = isoFormat.parse(pred.expectedArrival);
                            if (expectedDate != null) {
                                tService.std = timeFormat.format(expectedDate);
                                tService.etd = "On time";
                            }
                        } catch (Exception e) {
                            tService.std = "--:--";
                            tService.etd = "Unknown";
                        }

                        if (pred.platformName != null) {
                            tService.platform = pred.platformName.replace("Platform ", "")
                                    .replace("Northbound - ", "")
                                    .replace("Southbound - ", "")
                                    .replace("Eastbound - ", "")
                                    .replace("Westbound - ", "");
                        } else {
                            tService.platform = "-";
                        }

                        StationDepartureResponse.Location dest = new StationDepartureResponse.Location();
                        dest.locationName = pred.destinationName != null ? pred.destinationName : "Check Front of Train";
                        tService.destination = new ArrayList<>();
                        tService.destination.add(dest);

                        StationDepartureResponse.Location origin = new StationDepartureResponse.Location();
                        origin.locationName = resolvedStationName;
                        tService.origin = new ArrayList<>();
                        tService.origin.add(origin);

                        mappedServices.add(tService);
                    }

                    adapter.updateData(mappedServices, false, activeCrs, toCrs);
                    recyclerDepartures.setVisibility(View.VISIBLE);
                } else {
                    showEmptyState("No TfL services currently expected.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<TflArrivalPrediction>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                hideShimmer();
                swipeRefreshLayout.setRefreshing(false);
                showEmptyState("Failed to connect to TfL API.");
            }
        });
    }

    private String normalizeTflLineName(String rawName) {
        if (rawName == null) return "TfL Service";
        String lower = rawName.toLowerCase();

        System.out.println(lower);
        if (lower.contains("h&c") || lower.contains("hammersmith")) return "Hammersmith & City Line";
        if (lower.equals("elizabeth")) return "Elizabeth line";
        if (lower.equals("dlr")) return "DLR";
        if (lower.contains("overground")) return "London Overground";
        if (lower.equals("tram")) return "London Trams";

        if (!lower.endsWith(" line") && !lower.endsWith(" dlr") && !lower.endsWith("overground") && !lower.endsWith("trams")) {
            return rawName + " Line";
        }
        return rawName;
    }

    private void fetchTrains(boolean isAppending) {
        if (!isAppending) {
            currentOffset = 0;
            tvEmptyState.setVisibility(View.GONE);
            if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);

            if (adapter.getItemCount() == 0) {
                showShimmer();
            } else {
                swipeRefreshLayout.setRefreshing(true);
            }
        }

        String apiFilterCrs = null;
        String toResolvedCrs = StationHelper.getCrsFromName(StationHelper.getNameFromCode(toCrs));
        if (toResolvedCrs != null && !toResolvedCrs.isEmpty()) {
            apiFilterCrs = toResolvedCrs;
        }

        String apiFilterType = null;
        if (apiFilterCrs != null) {
            apiFilterType = isArrivals ? "from" : "to";
        }

        Call<StationDepartureResponse> networkCall;
        if (isArrivals) {
            networkCall = api.getStationArrivals(activeCrs, 10, apiFilterCrs, apiFilterType, currentOffset, 120);
        } else {
            networkCall = api.getStationDepartures(activeCrs, 10, apiFilterCrs, apiFilterType, currentOffset, 120);
        }

        networkCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<StationDepartureResponse> call, @NonNull Response<StationDepartureResponse> response) {
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null) {
                    List<StationDepartureResponse.TrainService> allServices = response.body().trainServices;

                    if (allServices != null && !allServices.isEmpty()) {
                        int newTrainsAdded = isAppending ? adapter.appendData(allServices) : allServices.size();

                        if (newTrainsAdded == 0) {
                            handleEmptyOrOverlapResult(allServices, isAppending);
                        } else {
                            swipeRefreshLayout.setRefreshing(false);
                            hideShimmer();

                            if (!isAppending) {
                                adapter.updateData(allServices, isArrivals, activeCrs, toResolvedCrs);
                                recyclerDepartures.setVisibility(View.VISIBLE);
                            }

                            currentOffset = calculateNextOffset(allServices);

                            if (btnLaterServices != null && currentOffset < 119) {
                                btnLaterServices.setVisibility(View.VISIBLE);
                                btnLaterServices.setText("View later services");
                                btnLaterServices.setEnabled(true);
                            } else if (btnLaterServices != null) {
                                btnLaterServices.setVisibility(View.GONE);
                            }
                        }
                    } else {
                        handleEmptyOrOverlapResult(null, isAppending);
                    }
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                    hideShimmer();
                    if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                    if (!isAppending) showEmptyState("Connection Error: Please check your connection.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<StationDepartureResponse> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                swipeRefreshLayout.setRefreshing(false);
                hideShimmer();

                if (btnLaterServices != null) {
                    btnLaterServices.setText("View later services");
                    btnLaterServices.setEnabled(true);
                }
                if (!isAppending) showEmptyState("Network error. Please try again.");
            }
        });
    }

    private void showShimmer() {
        recyclerDepartures.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.GONE);
        shimmerView.setVisibility(View.VISIBLE);
        shimmerView.startShimmer();
    }

    private void hideShimmer() {
        shimmerView.stopShimmer();
        shimmerView.setVisibility(View.GONE);
    }

    private void showEmptyState(String message) {
        adapter.updateData(new ArrayList<>(), isArrivals, activeCrs, toCrs);
        recyclerDepartures.setVisibility(View.GONE);
        tvEmptyState.setText(message);
        tvEmptyState.setVisibility(View.VISIBLE);
    }

    private void handleEmptyOrOverlapResult(List<StationDepartureResponse.TrainService> fetchedServices, boolean isAppending) {
        hideShimmer();

        if (currentOffset >= 119) {
            swipeRefreshLayout.setRefreshing(false);

            if (adapter.getItemCount() == 0) {
                if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                showEmptyState(isArrivals ? "No arrivals found within the next 2 hours." : "No direct trains found within the next 2 hours.");
            } else {
                if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                Toast.makeText(this, "End of scheduled services.", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (adapter.getItemCount() == 0) {
                tvEmptyState.setVisibility(View.VISIBLE);
                tvEmptyState.setText("Scanning forward for services...");
            } else if (btnLaterServices != null) {
                btnLaterServices.setText("Scanning...");
                btnLaterServices.setEnabled(false);
            }

            currentOffset = calculateNextOffset(fetchedServices);
            fetchTrains(true);
        }
    }

    private int calculateNextOffset(List<StationDepartureResponse.TrainService> fetchedServices) {
        if (fetchedServices == null || fetchedServices.isEmpty()) {
            return Math.min(119, currentOffset + 30);
        }

        StationDepartureResponse.TrainService lastService = fetchedServices.get(fetchedServices.size() - 1);
        String timeStr = lastService.std != null ? lastService.std : lastService.sta;

        if (timeStr == null || !timeStr.contains(":")) return Math.min(119, currentOffset + 10);

        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK);
            java.util.Date trainTime = sdf.parse(timeStr);

            java.util.Calendar nowCal = java.util.Calendar.getInstance();
            int currentH = nowCal.get(java.util.Calendar.HOUR_OF_DAY);
            int currentM = nowCal.get(java.util.Calendar.MINUTE);
            String nowStr = String.format(java.util.Locale.UK, "%02d:%02d", currentH, currentM);
            java.util.Date nowTime = sdf.parse(nowStr);

            long diffMins = (trainTime.getTime() - nowTime.getTime()) / 60000;
            if (diffMins < 0) diffMins += 24 * 60;

            int nextOffset = (int) diffMins;
            if (nextOffset <= currentOffset) {
                nextOffset = currentOffset + 5;
            }

            return Math.min(119, nextOffset);

        } catch (Exception e) {
            return Math.min(119, currentOffset + 10);
        }
    }
}