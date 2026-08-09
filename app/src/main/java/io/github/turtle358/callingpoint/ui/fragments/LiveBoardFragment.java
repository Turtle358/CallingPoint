package io.github.turtle358.callingpoint.ui.fragments;


import android.content.Context;
import android.content.Intent;
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
import android.widget.Toast;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.data.PinnedDataManager;
import io.github.turtle358.callingpoint.ui.activities.StationActivity;
import io.github.turtle358.callingpoint.ui.adapters.PinnedServiceAdapter;
import io.github.turtle358.callingpoint.ui.adapters.PinnedStationAdapter;
import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.ui.adapters.StationAutoCompleteAdapter;
import io.github.turtle358.callingpoint.utils.RecentStationsManager;
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
    private View layoutEmptyPinnedStations;
    private View layoutEmptyPinnedServices;
    private ShimmerFrameLayout shimmerPinnedServices;
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
        layoutEmptyPinnedStations = view.findViewById(R.id.layoutEmptyPinnedStations);
        layoutEmptyPinnedServices = view.findViewById(R.id.layoutEmptyPinnedServices);
        shimmerPinnedServices = view.findViewById(R.id.shimmer_pinned_services_container);

        pinnedDataManager = new PinnedDataManager(requireContext());

        List<String> stations = StationHelper.getStationNames();

        // FIX: Two separate adapters to prevent the ghosting bug!
        StationAutoCompleteAdapter fromAdapter = new StationAutoCompleteAdapter(requireContext(), stations);
        StationAutoCompleteAdapter toAdapter = new StationAutoCompleteAdapter(requireContext(), stations);

        etFromCrs.setAdapter(fromAdapter);
        etToCrs.setAdapter(toAdapter);

        RecentStationsManager recentManager = new RecentStationsManager(requireContext());

        etFromCrs.setOnItemClickListener((parent, v, position, id) -> {
            recentManager.addStation(fromAdapter.getItem(position));
            etToCrs.requestFocus();
        });

        etToCrs.setOnItemClickListener((parent, v, position, id) -> {
            recentManager.addStation(toAdapter.getItem(position));
            hideKeyboard();
            btnSearch.performClick();
        });

        etFromCrs.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) etFromCrs.showDropDown();
        });
        etToCrs.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) etToCrs.showDropDown();
        });

        setupAutoCompleteEnterBehavior(etFromCrs, fromAdapter, etToCrs, null);
        setupAutoCompleteEnterBehavior(etToCrs, toAdapter, null, () -> btnSearch.performClick());

        recyclerPinnedStations.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerPinnedStations.setNestedScrollingEnabled(false);
        stationAdapter = new PinnedStationAdapter(requireContext(), currentPinnedStations, (from, to) -> launchStationActivity(from, to, false));
        recyclerPinnedStations.setAdapter(stationAdapter);

        recyclerPinnedServices.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerPinnedServices.setNestedScrollingEnabled(false);
        serviceAdapter = new PinnedServiceAdapter(requireContext(), liveServicesData);
        recyclerPinnedServices.setAdapter(serviceAdapter);

        setupDragAndDrop();
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

            String resolvedFromName = StationHelper.getNameFromCode(fromName);
            if (resolvedFromName.isEmpty()) resolvedFromName = fromName;

            String fromCrs = StationHelper.getCrsFromName(resolvedFromName);
            String fromNaptan = StationHelper.getNaptanFromName(resolvedFromName);

            String resolvedToName = StationHelper.getNameFromCode(toName);
            if (resolvedToName.isEmpty()) resolvedToName = toName;

            String toCrs = StationHelper.getCrsFromName(resolvedToName);
            String toNaptan = StationHelper.getNaptanFromName(resolvedToName);

            boolean isValidFrom = (fromCrs != null && !fromCrs.isEmpty()) || (fromNaptan != null && !fromNaptan.isEmpty());

            if (!isValidFrom) {
                Toast.makeText(requireContext(), "Please select a valid departing station", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!toName.isEmpty()) {
                boolean sameName = fromName.equalsIgnoreCase(toName);
                boolean sameCrs = fromCrs != null && !fromCrs.isEmpty() && fromCrs.equalsIgnoreCase(toCrs);
                boolean sameNaptan = fromNaptan != null && !fromNaptan.isEmpty() && fromNaptan.equalsIgnoreCase(toNaptan);

                if (sameName || sameCrs || sameNaptan) {
                    Toast.makeText(requireContext(), "Origin and Destination cannot be the same", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            hideKeyboard();
            boolean isArrivals = toggleBoardType.getCheckedRadioButtonId() == R.id.radioArrivals;

            String fromCodeToPass = (fromCrs != null && !fromCrs.isEmpty()) ? fromCrs : fromNaptan;
            String toCodeToPass = "";
            if (!toName.isEmpty()) {
                toCodeToPass = (toCrs != null && !toCrs.isEmpty()) ? toCrs : toNaptan;
            }

            launchStationActivity(fromCodeToPass, toCodeToPass, isArrivals);
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

    private void launchStationActivity(String fromCrs, String toCrs, boolean isArrivals) {
        Intent intent = new Intent(requireContext(), StationActivity.class);
        intent.putExtra("FROM_CRS", fromCrs);
        intent.putExtra("TO_CRS", toCrs);
        intent.putExtra("IS_ARRIVALS", isArrivals);
        startActivity(intent);
        requireActivity().overridePendingTransition(R.anim.fade_in_fast, R.anim.fade_out_fast);
    }

    private void loadPinnedData() {
        List<String> pinnedStations = pinnedDataManager.getPinnedStations();
        List<String> pinnedServices = pinnedDataManager.getPinnedServices();

        layoutEmptyPinnedStations.setVisibility(pinnedStations.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerPinnedStations.setVisibility(pinnedStations.isEmpty() ? View.GONE : View.VISIBLE);

        currentPinnedStations.clear();
        currentPinnedStations.addAll(pinnedStations);
        stationAdapter.notifyDataSetChanged();

        if (!pinnedServices.isEmpty()) {
            layoutEmptyPinnedServices.setVisibility(View.GONE);

            if (liveServicesData.isEmpty()) {
                recyclerPinnedServices.setVisibility(View.GONE);
                if (shimmerPinnedServices != null) {

                    android.widget.LinearLayout shimmerContainer = (android.widget.LinearLayout) shimmerPinnedServices.getChildAt(0);
                    if (shimmerContainer != null) {
                        shimmerContainer.removeAllViews();

                        LayoutInflater inflater = LayoutInflater.from(requireContext());
                        for (int i = 0; i < pinnedServices.size(); i++) {
                            inflater.inflate(R.layout.layout_shimmer_depature, shimmerContainer, true);
                        }
                    }

                    shimmerPinnedServices.setVisibility(View.VISIBLE);
                    shimmerPinnedServices.startShimmer();
                }
            }

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
            shimmerPinnedServices.stopShimmer();
            shimmerPinnedServices.setVisibility(View.GONE);
            recyclerPinnedServices.setVisibility(View.GONE);
            layoutEmptyPinnedServices.setVisibility(View.VISIBLE);

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

            shimmerPinnedServices.stopShimmer();
            shimmerPinnedServices.setVisibility(View.GONE);
            recyclerPinnedServices.setVisibility(View.VISIBLE);
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

    private void setupDragAndDrop() {
        androidx.recyclerview.widget.ItemTouchHelper stationTouchHelper = new androidx.recyclerview.widget.ItemTouchHelper(
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                        androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                        androidx.recyclerview.widget.ItemTouchHelper.LEFT | androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {

                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                        int fromPos = viewHolder.getBindingAdapterPosition();
                        int toPos = target.getBindingAdapterPosition();

                        if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                            stationAdapter.swapItems(fromPos, toPos);
                        }
                        return true;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

                        int position = viewHolder.getBindingAdapterPosition();
                        if (position == RecyclerView.NO_POSITION) return;

                        String unpinnedStation = currentPinnedStations.get(position);
                        currentPinnedStations.remove(position);
                        stationAdapter.notifyItemRemoved(position);
                        pinnedDataManager.saveAllPinnedStations(currentPinnedStations);

                        com.google.android.material.snackbar.Snackbar.make(requireView(), "Station unpinned", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                .setAction("Undo", v -> {
                                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                                    currentPinnedStations.add(position, unpinnedStation);
                                    stationAdapter.notifyItemInserted(position);
                                    recyclerPinnedStations.scrollToPosition(position);
                                    pinnedDataManager.saveAllPinnedStations(currentPinnedStations);
                                }).show();
                    }

                    @Override
                    public void onSelectedChanged(@androidx.annotation.Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                        super.onSelectedChanged(viewHolder, actionState);
                        if (viewHolder != null && (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG || actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE)) {
                            viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        }
                    }

                    @Override
                    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                        super.clearView(recyclerView, viewHolder);
                        pinnedDataManager.saveAllPinnedStations(currentPinnedStations);
                    }

                    @Override
                    public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                        if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE) {
                            drawSwipeBackground(c, viewHolder.itemView, dX);
                        }
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    }
                });
        stationTouchHelper.attachToRecyclerView(recyclerPinnedStations);

        androidx.recyclerview.widget.ItemTouchHelper serviceTouchHelper = new androidx.recyclerview.widget.ItemTouchHelper(
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                        androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                        androidx.recyclerview.widget.ItemTouchHelper.LEFT | androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {

                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                        int fromPos = viewHolder.getBindingAdapterPosition();
                        int toPos = target.getBindingAdapterPosition();

                        if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                            serviceAdapter.swapItems(fromPos, toPos);
                        }
                        return true;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

                        int position = viewHolder.getBindingAdapterPosition();
                        if (position == RecyclerView.NO_POSITION) return;

                        ServiceDetailsResponse unpinnedService = liveServicesData.get(position);
                        liveServicesData.remove(position);
                        serviceAdapter.notifyItemRemoved(position);
                        saveServiceOrder();

                        com.google.android.material.snackbar.Snackbar.make(requireView(), "Service unpinned", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                .setAction("Undo", v -> {
                                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                                    liveServicesData.add(position, unpinnedService);
                                    serviceAdapter.notifyItemInserted(position);
                                    recyclerPinnedServices.scrollToPosition(position);
                                    saveServiceOrder();
                                }).show();
                    }

                    @Override
                    public void onSelectedChanged(@androidx.annotation.Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                        super.onSelectedChanged(viewHolder, actionState);
                        if (viewHolder != null && (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG || actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE)) {
                            viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        }
                    }

                    @Override
                    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                        super.clearView(recyclerView, viewHolder);
                        saveServiceOrder();
                    }

                    @Override
                    public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                        if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE) {
                            drawSwipeBackground(c, viewHolder.itemView, dX);
                        }
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    }
                });
        serviceTouchHelper.attachToRecyclerView(recyclerPinnedServices);
    }

    private void drawSwipeBackground(android.graphics.Canvas c, View itemView, float dX) {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(android.graphics.Color.parseColor("#FFB4AB"));
        paint.setAntiAlias(true);

        float cornerRadius = itemView.getResources().getDisplayMetrics().density * 30;

        android.graphics.RectF background = new android.graphics.RectF(
                itemView.getLeft(),
                itemView.getTop(),
                itemView.getRight(),
                itemView.getBottom()
        );

        c.drawRoundRect(background, cornerRadius, cornerRadius, paint);

        android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete);
        if (icon != null) {
            icon.setTint(android.graphics.Color.parseColor("#690005"));
            int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
            int iconTop = itemView.getTop() + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
            int iconBottom = iconTop + icon.getIntrinsicHeight();

            if (dX > 0) {
                int iconLeft = itemView.getLeft() + iconMargin;
                int iconRight = iconLeft + icon.getIntrinsicWidth();
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                icon.draw(c);
            } else if (dX < 0) {
                int iconRight = itemView.getRight() - iconMargin;
                int iconLeft = iconRight - icon.getIntrinsicWidth();
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                icon.draw(c);
            }
        }
    }

    private void saveServiceOrder() {
        List<String> newServiceIdOrder = new ArrayList<>();
        for (ServiceDetailsResponse service : liveServicesData) {
            newServiceIdOrder.add(service.customServiceID);
        }
        pinnedDataManager.saveAllPinnedServices(newServiceIdOrder);
    }
}