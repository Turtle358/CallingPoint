package io.github.turtle358.callingpoint.ui.fragments;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.data.PinnedDataManager;
import io.github.turtle358.callingpoint.models.StationDepartureResponse;
import io.github.turtle358.callingpoint.models.StationMessage;
import io.github.turtle358.callingpoint.ui.adapters.DeparturesAdapter;
import io.github.turtle358.callingpoint.utils.StationHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StationFragment extends Fragment {

    private String fromCrs = "";
    private String toCrs = "";
    private String pinKey = "";
    private boolean isArrivals = false;
    private int currentOffset = 0;

    private TextView tvDirectionHeading, tvStationTitle, tvStationSubtitle, tvEmptyState;
    private ImageButton btnPinStation;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerDepartures;
    private Button btnLaterServices;

    private LinearLayout containerStationMessages;
    private TextView btnToggleMessages;

    private DeparturesAdapter adapter;
    private NationalRailAPI api;
    private PinnedDataManager pinnedDataManager;

    private boolean isMessagesExpanded = false;
    private List<UnifiedMessage> currentMessages = new ArrayList<>();

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_station, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            fromCrs = getArguments().getString("FROM_CRS", "");
            toCrs = getArguments().getString("TO_CRS", "");
            isArrivals = getArguments().getBoolean("IS_ARRIVALS", false);
        }

        pinKey = fromCrs + (toCrs.isEmpty() ? "" : ":" + toCrs);

        tvDirectionHeading = view.findViewById(R.id.tvDirectionHeading);
        tvStationTitle = view.findViewById(R.id.tvStationTitle);
        tvStationSubtitle = view.findViewById(R.id.tvStationSubtitle);
        btnPinStation = view.findViewById(R.id.btnPinStation);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshStation);
        recyclerDepartures = view.findViewById(R.id.recyclerDepartures);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        btnLaterServices = view.findViewById(R.id.btnLaterServices);
        containerStationMessages = view.findViewById(R.id.containerStationMessages);

        pinnedDataManager = new PinnedDataManager(requireContext());
        api = APIClient.getClient().create(NationalRailAPI.class);

        setupHeader();
        setupPinButton();

        recyclerDepartures.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DeparturesAdapter(requireContext(), new ArrayList<>());
        recyclerDepartures.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            swipeRefreshLayout.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            fetchTrains(false);
            fetchStationMessages();
        });

        btnLaterServices.setOnClickListener(v -> {
            btnLaterServices.setText("Loading...");
            btnLaterServices.setEnabled(false);
            fetchTrains(true);
        });

        fetchTrains(false);
        fetchStationMessages();
    }

    private void setupHeader() {
        if (isArrivals) {
            tvDirectionHeading.setText("Trains arriving at");
        } else {
            tvDirectionHeading.setText("Trains departing from");
        }

        tvStationTitle.setText(fromCrs);
        for (String name : StationHelper.getStationNames()) {
            if (fromCrs.equalsIgnoreCase(StationHelper.getCrsFromName(name))) {
                tvStationTitle.setText(name);
                break;
            }
        }

        if (!toCrs.isEmpty()) {
            String toName = toCrs;
            for (String name : StationHelper.getStationNames()) {
                if (toCrs.equalsIgnoreCase(StationHelper.getCrsFromName(name))) {
                    toName = name;
                    break;
                }
            }

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
            Toast.makeText(requireContext(), isNowPinned ? "Station Pinned!" : "Pin Removed", Toast.LENGTH_SHORT).show();
        });
    }

    private void updatePinIcon() {
        boolean isPinned = pinnedDataManager.isStationPinned(pinKey);
        btnPinStation.setImageResource(isPinned ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
    }


    private void fetchStationMessages() {
        List<String> queryStations = new ArrayList<>();
        queryStations.add(fromCrs);
        if (!toCrs.isEmpty()) {
            queryStations.add(toCrs);
        }

        api.getStationMessages(queryStations).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<StationMessage>> call, @NonNull Response<List<StationMessage>> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null) {
                    displayStationMessages(response.body());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<StationMessage>> call, @NonNull Throwable t) {
            }
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

            if (i > 0) {
                view.setVisibility(View.GONE);
            }

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

        TransitionManager.beginDelayedTransition((ViewGroup) requireView(), transition);

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
            TransitionManager.beginDelayedTransition((ViewGroup) requireView(), transition);

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
        btnToggleMessages = new TextView(requireContext());
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


    private void fetchTrains(boolean isAppending) {
        if (!isAppending) {
            currentOffset = 0;
            swipeRefreshLayout.setRefreshing(true);
            tvEmptyState.setVisibility(View.GONE);
            if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
        }

        String apiFilterCrs = toCrs.isEmpty() ? null : toCrs;
        String apiFilterType = null;
        if (apiFilterCrs != null) {
            apiFilterType = isArrivals ? "from" : "to";
        }

        Call<StationDepartureResponse> networkCall;
        if (isArrivals) {
            networkCall = api.getStationArrivals(fromCrs, 10, apiFilterCrs, apiFilterType, currentOffset, 120);
        } else {
            networkCall = api.getStationDepartures(fromCrs, 10, apiFilterCrs, apiFilterType, currentOffset, 120);
        }

        networkCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<StationDepartureResponse> call, @NonNull Response<StationDepartureResponse> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null) {
                    List<StationDepartureResponse.TrainService> allServices = response.body().trainServices;

                    if (allServices != null && !allServices.isEmpty()) {
                        int newTrainsAdded = isAppending ? adapter.appendData(allServices) : allServices.size();

                        if (newTrainsAdded == 0) {
                            handleEmptyOrOverlapResult(allServices, isAppending);
                        } else {
                            swipeRefreshLayout.setRefreshing(false);

                            if (!isAppending) {
                                adapter.updateData(allServices, isArrivals, fromCrs, toCrs);
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
                    if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                    if (!isAppending) showEmptyState("Connection Error: Please check your connection.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<StationDepartureResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);

                if (btnLaterServices != null) {
                    btnLaterServices.setText("View later services");
                    btnLaterServices.setEnabled(true);
                }
                if (!isAppending) showEmptyState("Network error. Please try again.");
            }
        });
    }

    private void showEmptyState(String message) {
        adapter.updateData(new ArrayList<>(), isArrivals, fromCrs, toCrs);
        recyclerDepartures.setVisibility(View.GONE);
        tvEmptyState.setText(message);
        tvEmptyState.setVisibility(View.VISIBLE);
    }

    private void handleEmptyOrOverlapResult(List<StationDepartureResponse.TrainService> fetchedServices, boolean isAppending) {
        if (currentOffset >= 119) {
            swipeRefreshLayout.setRefreshing(false);

            if (adapter.getItemCount() == 0) {
                if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                showEmptyState(isArrivals ? "No arrivals found within the next 2 hours." : "No direct trains found within the next 2 hours.");
            } else {
                if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "End of scheduled services.", Toast.LENGTH_SHORT).show();
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