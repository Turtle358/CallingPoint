package io.github.turtle358.callingpoint.ui.fragments;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import io.github.turtle358.callingpoint.models.JourneyLocation;
import io.github.turtle358.callingpoint.ui.activities.JourneySearchActivity;
import io.github.turtle358.callingpoint.utils.JourneySavedPlaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.turtle358.callingpoint.utils.Disruptions.DisruptionSyncManager;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.utils.WidgetPromoHelper;
import io.github.turtle358.callingpoint.widget.AppWidgetProvider;
import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.utils.ThemeHelper;
import io.github.turtle358.callingpoint.ui.adapters.TocSettingAdapter;
import io.github.turtle358.callingpoint.models.Toc;
import io.github.turtle358.callingpoint.workers.DataSyncWorker;

public class SettingsFragment extends Fragment {
    private RadioGroup themeRadioGroup;
    private View slidingPillTheme;
    private Set<String> trackedTocs;
    private SharedPreferences prefs;
    private TocSettingAdapter adapter;
    private JourneySavedPlaces savedPlaces;
    private TextView homePlaceDescription;
    private TextView workPlaceDescription;

    private BillingClient billingClient;
    private ProductDetails coffeeProductDetails;
    private static final String PRODUCT_ID_COFFEE = "tip_coffee_199";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        savedPlaces = new JourneySavedPlaces(requireContext());
        homePlaceDescription = view.findViewById(R.id.tvSettingsHomePlace);
        workPlaceDescription = view.findViewById(R.id.tvSettingsWorkPlace);

        view.findViewById(R.id.rowSettingsHome).setOnClickListener(v -> openSavedPlaceEditor(JourneySavedPlaces.HOME));
        view.findViewById(R.id.rowSettingsWork).setOnClickListener(v -> openSavedPlaceEditor(JourneySavedPlaces.WORK));
        view.findViewById(R.id.btnSettingsChangeHome).setOnClickListener(v -> showSavedPlaceActions(JourneySavedPlaces.HOME, v));
        view.findViewById(R.id.btnSettingsChangeWork).setOnClickListener(v -> showSavedPlaceActions(JourneySavedPlaces.WORK, v));
        updateSavedPlacesUi();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        Button btnBuyCoffee = view.findViewById(R.id.btnBuyCoffee);
        btnBuyCoffee.setEnabled(false);
        setupBillingClient(btnBuyCoffee);

        Button btnSettingsAddWidget = view.findViewById(R.id.btnSettingsAddWidget);
        btnSettingsAddWidget.setOnClickListener(v -> {
            prefs.edit().putBoolean("PREF_HIDE_WIDGET_BANNER", true).apply();

            WidgetPromoHelper.requestPinWidget(requireContext());
        });

        btnBuyCoffee.setOnClickListener(v -> {
            if (coffeeProductDetails != null) {
                launchPurchaseFlow();
            }
        });

        prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String savedTocs = prefs.getString("WIDGET_TRACKED_TOCS", "");
        trackedTocs = new HashSet<>();
        if (!savedTocs.trim().isEmpty()) {
            trackedTocs.addAll(Arrays.asList(savedTocs.split(",")));
        }

        Button btnReportBug = view.findViewById(R.id.btnReportBug);
        btnReportBug.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:callingpoint@outlook.com"));

            intent.putExtra(Intent.EXTRA_SUBJECT, "Bug Report - CallingPoint");

            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                Toast.makeText(getContext(), "No email app found.", Toast.LENGTH_SHORT).show();
                Toast.makeText(getContext(), "Send report to: callingpoint@outlook.com", Toast.LENGTH_LONG).show();
            }
        });

        Button btnPrivacyPolicy = view.findViewById(R.id.btnPrivacyPolicy);
        btnPrivacyPolicy.setOnClickListener(v -> {
            String url = "https://callingpoint.co.uk/privacy.html";

            androidx.browser.customtabs.CustomTabsIntent.Builder builder =
                    new androidx.browser.customtabs.CustomTabsIntent.Builder();


            androidx.browser.customtabs.CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(getContext(), Uri.parse(url));
        });

        themeRadioGroup = view.findViewById(R.id.themeRadioGroup);
        slidingPillTheme = view.findViewById(R.id.slidingPillTheme);
        setupThemeControl();

        setupAppIconControl(view);

        RecyclerView recyclerTocs = view.findViewById(R.id.recyclerTocsSettings);
        recyclerTocs.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<Toc> allTocs = TOCBrandHelper.getVisibleTocs();
        List<Toc> nationalRail = new java.util.ArrayList<>();
        List<Toc> tflServices = new java.util.ArrayList<>();

        for (Toc t : allTocs) {
            String manager = TOCBrandHelper.getManagerForToc(t.name);
            boolean isTfl = "tfl".equalsIgnoreCase(manager) || "tfl-national".equalsIgnoreCase(manager) || "london buses".equalsIgnoreCase(manager);
            if (isTfl) {
                tflServices.add(t);
            } else {
                nationalRail.add(t);
            }
        }

        List<Toc> groupedTocs = new java.util.ArrayList<>(nationalRail);
        if (!nationalRail.isEmpty() && !tflServices.isEmpty()) {
            Toc separator = new Toc();
            separator.code = "SEPARATOR";
            separator.name = "Transport for London";
            groupedTocs.add(separator);
        }
        groupedTocs.addAll(tflServices);

        adapter = new TocSettingAdapter(requireContext(), groupedTocs, trackedTocs, (tocCode, isTracked) -> {
            if (isTracked) trackedTocs.add(tocCode);
            else trackedTocs.remove(tocCode);
            saveTrackedTocs();
        });

        recyclerTocs.setAdapter(adapter);

        Button btnManualSync = view.findViewById(R.id.btnManualSync);
        btnManualSync.setOnClickListener(v -> {
            btnManualSync.setEnabled(false);
            btnManualSync.setText("Updating...");

            OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(DataSyncWorker.class)
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .build();

            WorkManager workManager = WorkManager.getInstance(requireContext());
            workManager.enqueue(syncRequest);

            workManager.getWorkInfoByIdLiveData(syncRequest.getId())
                    .observe(getViewLifecycleOwner(), workInfo -> {
                        if (workInfo != null) {
                            if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                                TOCBrandHelper.init(requireContext());
                                StationHelper.init(requireContext());

                                Toast.makeText(requireContext(), "Reference data updated successfully!", Toast.LENGTH_SHORT).show();
                                btnManualSync.setEnabled(true);
                                btnManualSync.setText("Update");
                            } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                                Toast.makeText(requireContext(), "Update failed. Please try again later.", Toast.LENGTH_SHORT).show();
                                btnManualSync.setEnabled(true);
                                btnManualSync.setText("Update");
                            }
                        }
                    });
        });

        android.widget.TextView tvAttributions = view.findViewById(R.id.tvAttributions);
        String attributionHtml = "Powered by data from the <b>National Rail Data Portal</b>, <b>Transport for London (TfL)</b>, and <b>OpenStreetMap</b>.<br>Some icons sourced from <b>SVGRepo.com</b> under open licenses.";
        tvAttributions.setText(android.text.Html.fromHtml(attributionHtml, android.text.Html.FROM_HTML_MODE_COMPACT));
    }

    /** Reuses the existing journey location autocomplete, including stations and Geoapify addresses. */
    private void openSavedPlaceEditor(@NonNull String slot) {
        Intent edit = new Intent(requireContext(), JourneySearchActivity.class);
        edit.putExtra(JourneySearchActivity.EXTRA_EDIT_SAVED_PLACE, slot);
        startActivity(edit);
    }

    /** A saved place's Change button doubles as its action menu; Set opens the picker directly. */
    private void showSavedPlaceActions(@NonNull String slot, @NonNull View anchor) {
        if (savedPlaces == null || savedPlaces.get(slot) == null) {
            openSavedPlaceEditor(slot);
            return;
        }

        String label = JourneySavedPlaces.HOME.equals(slot) ? "Home" : "Work";
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, "Change location");
        menu.getMenu().add(0, 2, 1, "Remove " + label);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openSavedPlaceEditor(slot);
                return true;
            }
            if (item.getItemId() == 2) {
                confirmClearSavedPlace(slot);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void confirmClearSavedPlace(@NonNull String slot) {
        if (savedPlaces == null || savedPlaces.get(slot) == null) return;
        String label = JourneySavedPlaces.HOME.equals(slot) ? "Home" : "Work";
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove " + label + "?")
                .setMessage("This removes your saved " + label.toLowerCase(java.util.Locale.UK)
                        + " location. Routes using this shortcut may need updating.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    savedPlaces.clear(slot);
                    updateSavedPlacesUi();
                })
                .show();
    }

    private void updateSavedPlacesUi() {
        if (savedPlaces == null || getView() == null) return;
        JourneyLocation home = savedPlaces.get(JourneySavedPlaces.HOME);
        JourneyLocation work = savedPlaces.get(JourneySavedPlaces.WORK);
        if (homePlaceDescription != null) {
            homePlaceDescription.setText(home == null ? "Not set" : home.displayName());
        }
        if (workPlaceDescription != null) {
            workPlaceDescription.setText(work == null ? "Not set" : work.displayName());
        }
        View v = getView();
        ((TextView) v.findViewById(R.id.btnSettingsChangeHome)).setText(home == null ? "Set" : "Change");
        ((TextView) v.findViewById(R.id.btnSettingsChangeWork)).setText(work == null ? "Set" : "Change");
        v.findViewById(R.id.btnSettingsChangeHome).setContentDescription(
                home == null ? "Set Home location" : "Change or remove Home location");
        v.findViewById(R.id.btnSettingsChangeWork).setContentDescription(
                work == null ? "Set Work location" : "Change or remove Work location");
    }

    private void setupThemeControl() {
        int currentTheme = ThemeHelper.getSavedTheme(requireContext());
        if (currentTheme == ThemeHelper.THEME_LIGHT) {
            themeRadioGroup.check(R.id.radioThemeLight);
        } else if (currentTheme == ThemeHelper.THEME_DARK) {
            themeRadioGroup.check(R.id.radioThemeDark);
        } else {
            themeRadioGroup.check(R.id.radioThemeAuto);
        }

        themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            View selectedButton = group.findViewById(checkedId);
            if (selectedButton != null) {
                slidingPillTheme.animate()
                        .translationX(selectedButton.getX())
                        .setDuration(200)
                        .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                        .start();
            }

            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (checkedId == R.id.radioThemeLight) {
                    ThemeHelper.saveTheme(requireContext(), ThemeHelper.THEME_LIGHT);
                } else if (checkedId == R.id.radioThemeDark) {
                    ThemeHelper.saveTheme(requireContext(), ThemeHelper.THEME_DARK);
                } else if (checkedId == R.id.radioThemeAuto) {
                    ThemeHelper.saveTheme(requireContext(), ThemeHelper.THEME_AUTO);
                }
            }, 250);
        });

        themeRadioGroup.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (themeRadioGroup.getWidth() > 0) {
                    themeRadioGroup.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    View selectedButton = themeRadioGroup.findViewById(themeRadioGroup.getCheckedRadioButtonId());
                    if (selectedButton != null) {
                        slidingPillTheme.getLayoutParams().width = selectedButton.getWidth();
                        slidingPillTheme.requestLayout();
                        slidingPillTheme.setTranslationX(selectedButton.getX());
                    }
                }
            }
        });
    }

    private void saveTrackedTocs() {
        String joined = TextUtils.join(",", trackedTocs);

        prefs.edit()
                .putString("WIDGET_TRACKED_TOCS", joined)
                .putLong("LAST_SYNC_TIME", 0)
                .apply();

        DisruptionSyncManager.syncAsync(requireContext(), null);
    }

    private void setupAppIconControl(View view) {
        View btnIconDefault = view.findViewById(R.id.btnIconDefault);
        View btnIconAlt = view.findViewById(R.id.btnIconAlt);

        PackageManager pm = requireContext().getPackageManager();
        String packageName = requireContext().getPackageName();
        ComponentName defaultAlias = new ComponentName(packageName, packageName + ".MainActivityDefault");
        ComponentName altAlias = new ComponentName(packageName, packageName + ".MainActivityAlt");

        int defaultState = pm.getComponentEnabledSetting(defaultAlias);
        boolean isAltActive = (defaultState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        btnIconDefault.setAlpha(isAltActive ? 0.4f : 1.0f);
        btnIconAlt.setAlpha(isAltActive ? 1.0f : 0.4f);

        btnIconDefault.setOnClickListener(v -> {
            if (btnIconDefault.getAlpha() == 1.0f) return;
            btnIconDefault.setAlpha(1.0f);
            btnIconAlt.setAlpha(0.4f);
            switchIcon(false, pm, defaultAlias, altAlias);
        });

        btnIconAlt.setOnClickListener(v -> {
            if (btnIconAlt.getAlpha() == 1.0f) return;
            btnIconAlt.setAlpha(1.0f);
            btnIconDefault.setAlpha(0.4f);
            switchIcon(true, pm, defaultAlias, altAlias);
        });
    }

    private void switchIcon(boolean useAltIcon, PackageManager pm, ComponentName defaultAlias, ComponentName altAlias) {
        if (useAltIcon) {
            pm.setComponentEnabledSetting(altAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(defaultAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        } else {
            pm.setComponentEnabledSetting(defaultAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(altAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }

        Toast.makeText(requireContext(), "Icon updated! It may take a moment to change on your home screen.", Toast.LENGTH_LONG).show();
    }


    private void setupBillingClient(Button btnBuyCoffee) {
        PendingPurchasesParams pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build();

        billingClient = BillingClient.newBuilder(requireContext())
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(pendingParams)
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails(btnBuyCoffee);
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
            }
        });
    }

    private void queryProductDetails(Button btnBuyCoffee) {
        QueryProductDetailsParams queryProductDetailsParams =
                QueryProductDetailsParams.newBuilder()
                        .setProductList(
                                Collections.singletonList(
                                        QueryProductDetailsParams.Product.newBuilder()
                                                .setProductId(PRODUCT_ID_COFFEE)
                                                .setProductType(BillingClient.ProductType.INAPP)
                                                .build()))
                        .build();

        billingClient.queryProductDetailsAsync(
                queryProductDetailsParams,
                (billingResult, productDetailsResult) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK &&
                            productDetailsResult.getProductDetailsList() != null &&
                            !productDetailsResult.getProductDetailsList().isEmpty()) {

                        coffeeProductDetails = productDetailsResult.getProductDetailsList().get(0);

                        if (getActivity() != null) {
                            requireActivity().runOnUiThread(() -> btnBuyCoffee.setEnabled(true));
                        }
                    }
                }
        );
    }

    private void launchPurchaseFlow() {
        if (getActivity() == null) return;

        ImmutableList<BillingFlowParams.ProductDetailsParams> productDetailsParamsList =
                ImmutableList.of(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(coffeeProductDetails)
                                .build()
                );

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build();

        billingClient.launchBillingFlow(requireActivity(), billingFlowParams);
    }

    private final PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(requireContext(), "Purchase error. Please try again.", Toast.LENGTH_SHORT).show();
        }
    };

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            ConsumeParams consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();

            billingClient.consumeAsync(consumeParams, (billingResult, outToken) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    if (getActivity() != null) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Thank you so much for the coffee! ☕", Toast.LENGTH_LONG).show();
                        });
                    }
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        checkWidgetStatus();
        updateSavedPlacesUi();

        if (adapter != null && prefs != null) {
            String savedTocs = prefs.getString("WIDGET_TRACKED_TOCS", "");
            trackedTocs.clear();
            if (!savedTocs.trim().isEmpty()) {
                trackedTocs.addAll(Arrays.asList(savedTocs.split(",")));
            }
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (billingClient != null) {
            billingClient.endConnection();
        }
    }

    private void checkWidgetStatus() {
        if (getView() == null || getContext() == null) return;

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(requireContext());
        ComponentName provider = new ComponentName(requireContext(), AppWidgetProvider.class);

        int[] activeWidgetIds = appWidgetManager.getAppWidgetIds(provider);
        boolean hasWidget = activeWidgetIds != null && activeWidgetIds.length > 0;

        Button btnSettingsAddWidget = getView().findViewById(R.id.btnSettingsAddWidget);
        android.widget.TextView tvWidgetDescription = getView().findViewById(R.id.tvWidgetDescription);

        if (hasWidget) {
            tvWidgetDescription.setText("Active on home screen");
            btnSettingsAddWidget.setText("Added");
            btnSettingsAddWidget.setEnabled(false);
            btnSettingsAddWidget.setAlpha(0.5f);
        } else {
            tvWidgetDescription.setText("Add to home screen");
            btnSettingsAddWidget.setText("Add");
            btnSettingsAddWidget.setEnabled(true);
            btnSettingsAddWidget.setAlpha(1.0f);
        }
    }
}