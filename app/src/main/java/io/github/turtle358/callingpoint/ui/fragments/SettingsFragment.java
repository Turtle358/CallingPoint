package io.github.turtle358.callingpoint.ui.fragments;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.turtle358.callingpoint.utils.StationHelper;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String savedTocs = prefs.getString("WIDGET_TRACKED_TOCS", "");
        trackedTocs = new HashSet<>();
        if (!savedTocs.trim().isEmpty()) {
            trackedTocs.addAll(Arrays.asList(savedTocs.split(",")));
        }



        themeRadioGroup = view.findViewById(R.id.themeRadioGroup);
        slidingPillTheme = view.findViewById(R.id.slidingPillTheme);
        setupThemeControl();

        setupAppIconControl(view);

        RecyclerView recyclerTocs = view.findViewById(R.id.recyclerTocsSettings);
        recyclerTocs.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<Toc> allTocs = TOCBrandHelper.getVisibleTocs();

        TocSettingAdapter adapter = new TocSettingAdapter(allTocs, trackedTocs, (tocCode, isTracked) -> {
            if (isTracked) trackedTocs.add(tocCode);
            else trackedTocs.remove(tocCode);
            saveTrackedTocs();
        });



        recyclerTocs.setAdapter(adapter);


        Button btnManualSync = view.findViewById(R.id.btnManualSync);
        btnManualSync.setOnClickListener(v -> {
            btnManualSync.setEnabled(false);
            btnManualSync.setText("Updating reference data...");

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
                                btnManualSync.setText("Check for Reference Data Updates");
                            } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                                Toast.makeText(requireContext(), "Update failed. Please try again later.", Toast.LENGTH_SHORT).show();
                                btnManualSync.setEnabled(true);
                                btnManualSync.setText("Check for Reference Data Updates");
                            }
                        }
                    });
        });
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
        prefs.edit().putString("WIDGET_TRACKED_TOCS", joined).apply();
        refreshWidget();
    }

    private void refreshWidget() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(requireContext());
        ComponentName thisWidget = new ComponentName(requireContext(), AppWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

        Intent intent = new Intent(requireContext(), AppWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        requireContext().sendBroadcast(intent);
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
}