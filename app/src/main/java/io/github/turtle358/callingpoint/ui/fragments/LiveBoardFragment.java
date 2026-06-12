package io.github.turtle358.callingpoint.ui.fragments;

import static io.github.turtle358.callingpoint.utils.InlineAutocompleteManager.applyInlineAutocomplete;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.data.PinnedDataManager;
import io.github.turtle358.callingpoint.ui.adapters.PinnedServiceAdapter;
import io.github.turtle358.callingpoint.ui.adapters.PinnedStationAdapter;
import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.ui.adapters.StationAutoCompleteAdapter;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LiveBoardFragment extends Fragment {

    private AutoCompleteTextView etFromCrs;
    private AutoCompleteTextView etToCrs;
    private RadioGroup toggleBoardType;
    private View slidingPill;
    private RecyclerView recyclerPinnedStations;
    private RecyclerView recyclerPinnedServices;
    private TextView tvEmptyPinnedStations;
    private TextView tvEmptyPinnedServices;
    private PinnedDataManager pinnedDataManager;
    private PinnedStationAdapter stationAdapter;
    private PinnedServiceAdapter serviceAdapter;
    private final List<String> currentPinnedStations = new ArrayList<>();
    private final List<ServiceDetailsResponse> liveServicesData = new ArrayList<>();
    private Handler autoRefreshHandler;
    private Runnable autoRefreshRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_live_board, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etFromCrs = view.findViewById(R.id.etFromCrs);
        etToCrs = view.findViewById(R.id.etToCrs);
        toggleBoardType = view.findViewById(R.id.toggleBoardType);
        slidingPill = view.findViewById(R.id.slidingPill);
        ImageButton btnSwap = view.findViewById(R.id.btnSwapStations);
        Button btnSearch = view.findViewById(R.id.btnSearchDepartures);

        recyclerPinnedStations = view.findViewById(R.id.recyclerPinnedStations);
        recyclerPinnedServices = view.findViewById(R.id.recyclerPinnedServices);
        tvEmptyPinnedStations = view.findViewById(R.id.tvEmptyPinnedStations);
        tvEmptyPinnedServices = view.findViewById(R.id.tvEmptyPinnedServices);

        pinnedDataManager = new PinnedDataManager(requireContext());

        List<String> stations = StationHelper.getStationNames();
        StationAutoCompleteAdapter autoCompleteAdapter = new StationAutoCompleteAdapter(requireContext(), stations);
        etFromCrs.setAdapter(autoCompleteAdapter);
        etToCrs.setAdapter(autoCompleteAdapter);
        applyInlineAutocomplete(etFromCrs);
        applyInlineAutocomplete(etToCrs);
        setupAutoCompleteEnterBehavior(etFromCrs, autoCompleteAdapter, etToCrs, null);
        setupAutoCompleteEnterBehavior(etToCrs, autoCompleteAdapter, null, () -> btnSearch.performClick());

        recyclerPinnedStations.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerPinnedStations.setNestedScrollingEnabled(false);
        stationAdapter = new PinnedStationAdapter(requireContext(), currentPinnedStations, (from, to) -> launchStationFragment(from, to, false));
        recyclerPinnedStations.setAdapter(stationAdapter);

        recyclerPinnedServices.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerPinnedServices.setNestedScrollingEnabled(false);
        serviceAdapter = new PinnedServiceAdapter(requireContext(), liveServicesData);
        recyclerPinnedServices.setAdapter(serviceAdapter);

        setupToggleAnimation();

        btnSwap.setOnClickListener(v -> {
            String tempText = etFromCrs.getText().toString();
            etFromCrs.setText(etToCrs.getText().toString());
            etToCrs.setText(tempText);
            etFromCrs.dismissDropDown();
            etToCrs.dismissDropDown();
        });

        btnSearch.setOnClickListener(v -> {
            String fromName = etFromCrs.getText().toString().trim();
            String toName = etToCrs.getText().toString().trim();
            String fromCrs = StationHelper.getCrsFromName(fromName);
            String toCrs = toName.isEmpty() ? "" : StationHelper.getCrsFromName(toName);

            if (fromCrs == null && fromName.length() == 3) fromCrs = fromName.toUpperCase();
            if (toCrs == null && toName.length() == 3) toCrs = toName.toUpperCase();

            if (fromCrs != null && fromCrs.length() == 3) {
                hideKeyboard();
                boolean isArrivals = toggleBoardType.getCheckedRadioButtonId() == R.id.radioArrivals;
                launchStationFragment(fromCrs, toCrs, isArrivals);
            } else {
                Toast.makeText(requireContext(), "Please select a valid departing station", Toast.LENGTH_SHORT).show();
            }
        });

        autoRefreshHandler = new Handler(Looper.getMainLooper());
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadPinnedData();
                autoRefreshHandler.postDelayed(this, 30000);
            }
        };
    }

    private void setupToggleAnimation() {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        boolean preferArrivals = prefs.getBoolean("PREFER_ARRIVALS", false);

        if (preferArrivals) {
            toggleBoardType.check(R.id.radioArrivals);
            etFromCrs.setHint("Arriving at");
            etToCrs.setHint("Coming from (optional)");
        } else {
            toggleBoardType.check(R.id.radioDepartures);
            etFromCrs.setHint("Departing from");
            etToCrs.setHint("Going to (optional)");
        }

        toggleBoardType.setOnCheckedChangeListener((group, checkedId) -> {
            View selectedButton = group.findViewById(checkedId);
            if (selectedButton != null) {
                slidingPill.animate()
                        .translationX(selectedButton.getX())
                        .setDuration(200)
                        .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                        .start();
            }

            android.content.SharedPreferences.Editor editor = prefs.edit();
            if (checkedId == R.id.radioArrivals) {
                etFromCrs.setHint("Arriving at");
                etToCrs.setHint("Coming from (optional)");
                editor.putBoolean("PREFER_ARRIVALS", true);
            } else {
                etFromCrs.setHint("Departing from");
                etToCrs.setHint("Going to (optional)");
                editor.putBoolean("PREFER_ARRIVALS", false);
            }
            editor.apply();
        });

        toggleBoardType.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (toggleBoardType.getWidth() > 0) {
                    toggleBoardType.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    View selectedButton = toggleBoardType.findViewById(toggleBoardType.getCheckedRadioButtonId());
                    if (selectedButton != null) {
                        slidingPill.getLayoutParams().width = selectedButton.getWidth();
                        slidingPill.requestLayout();
                        slidingPill.setTranslationX(selectedButton.getX());
                    }
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        autoRefreshRunnable.run();
    }

    @Override
    public void onPause() {
        super.onPause();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    private void launchStationFragment(String fromCrs, String toCrs, boolean isArrivals) {
        StationFragment fragment = new StationFragment();
        Bundle args = new Bundle();
        args.putString("FROM_CRS", fromCrs);
        args.putString("TO_CRS", toCrs);
        args.putBoolean("IS_ARRIVALS", isArrivals);
        fragment.setArguments(args);

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void loadPinnedData() {
        List<String> pinnedStations = pinnedDataManager.getPinnedStations();
        List<String> pinnedServices = pinnedDataManager.getPinnedServices();

        tvEmptyPinnedStations.setVisibility(pinnedStations.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerPinnedStations.setVisibility(pinnedStations.isEmpty() ? View.GONE : View.VISIBLE);

        tvEmptyPinnedServices.setVisibility(pinnedServices.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerPinnedServices.setVisibility(pinnedServices.isEmpty() ? View.GONE : View.VISIBLE);

        currentPinnedStations.clear();
        currentPinnedStations.addAll(pinnedStations);
        stationAdapter.notifyDataSetChanged();

        if (!pinnedServices.isEmpty()) {
            NationalRailAPI api = APIClient.getClient().create(NationalRailAPI.class);

            List<ServiceDetailsResponse> tempServicesData = new ArrayList<>();
            final int totalRequests = pinnedServices.size();
            final int[] completedRequests = {0};

            for (String serviceId : pinnedServices) {
                api.getServiceDetails(serviceId).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ServiceDetailsResponse> call, @NonNull Response<ServiceDetailsResponse> response) {
                        if (!isAdded()) return;
                        completedRequests[0]++;

                        if (response.isSuccessful() && response.body() != null) {
                            ServiceDetailsResponse data = response.body();
                            data.customServiceID = serviceId;
                            tempServicesData.add(data);
                        } else {
                            if (response.code() != 429) {
                                pinnedDataManager.toggleServicePin(serviceId);
                            }
                        }

                        updateUIWhenFinished(completedRequests[0], totalRequests, tempServicesData, pinnedServices);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ServiceDetailsResponse> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        completedRequests[0]++;
                        updateUIWhenFinished(completedRequests[0], totalRequests, tempServicesData, pinnedServices);
                    }
                });
            }
        } else {
            liveServicesData.clear();
            serviceAdapter.notifyDataSetChanged();
        }
    }

    private void updateUIWhenFinished(int completed, int total, List<ServiceDetailsResponse> tempServices, List<String> originalPinnedOrder) {
        if (completed == total) {
            tempServices.sort((a, b) -> {
                int indexA = originalPinnedOrder.indexOf(a.customServiceID);
                int indexB = originalPinnedOrder.indexOf(b.customServiceID);
                return Integer.compare(indexA, indexB);
            });

            liveServicesData.clear();
            liveServicesData.addAll(tempServices);
            serviceAdapter.notifyDataSetChanged();
        }
    }

    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void setupAutoCompleteEnterBehavior(AutoCompleteTextView autoCompleteTextView, ArrayAdapter<String> adapter, View nextFocus, Runnable onDone) {
        autoCompleteTextView.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterPressed = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;

            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || isEnterPressed) {

                String currentText = autoCompleteTextView.getText().toString();
                if (!currentText.isEmpty() && adapter.getCount() > 0) {
                    String firstMatch = adapter.getItem(0);
                    if (firstMatch != null) {
                        autoCompleteTextView.setText(firstMatch);
                        autoCompleteTextView.dismissDropDown();
                        autoCompleteTextView.setSelection(firstMatch.length());
                    }
                }

                if (nextFocus != null) {
                    nextFocus.requestFocus();
                } else if (onDone != null) {
                    onDone.run();
                }
                return true;
            }
            return false;
        });
    }
}