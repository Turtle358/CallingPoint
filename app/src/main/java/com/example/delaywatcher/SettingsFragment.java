package com.example.delaywatcher;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsFragment extends Fragment {
    private EditText etCustomerKey, etLdbwsKey;
    private Button btnSaveSettings;
    private RadioGroup themeRadioGroup;
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

        etCustomerKey = view.findViewById(R.id.etCustomerKey);
        etLdbwsKey = view.findViewById(R.id.etLdbwsKey);
        btnSaveSettings = view.findViewById(R.id.btnSaveSettings);

        etCustomerKey.setText(prefs.getString("CUSTOMER_KEY", ""));
        etLdbwsKey.setText(prefs.getString("LDBWS_KEY", ""));

        ImageButton btnTogglePassword = view.findViewById(R.id.btnTogglePassword);
        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility(etCustomerKey, btnTogglePassword));

        ImageButton btnToggleLdbwsPassword = view.findViewById(R.id.btnToggleLdbwsPassword);
        btnToggleLdbwsPassword.setOnClickListener(v -> togglePasswordVisibility(etLdbwsKey, btnToggleLdbwsPassword));

        themeRadioGroup = view.findViewById(R.id.themeRadioGroup);
        setupThemeControl();

        RecyclerView recyclerTocs = view.findViewById(R.id.recyclerTocsSettings);
        recyclerTocs.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<Toc> allTocs = TOCBrandHelper.getAllTocs();

        TocSettingAdapter adapter = new TocSettingAdapter(allTocs, trackedTocs, (tocCode, isTracked) -> {
            if (isTracked) trackedTocs.add(tocCode);
            else trackedTocs.remove(tocCode);
            saveTrackedTocs();
        });

        recyclerTocs.setAdapter(adapter);

        btnSaveSettings.setOnClickListener(v -> {
            prefs.edit()
                    .putString("CUSTOMER_KEY", etCustomerKey.getText().toString().trim())
                    .putString("LDBWS_KEY", etLdbwsKey.getText().toString().trim())
                    .apply();
            Toast.makeText(requireContext(), "API Keys Saved", Toast.LENGTH_SHORT).show();
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
            if (checkedId == R.id.radioThemeLight) {
                ThemeHelper.saveTheme(requireContext(), ThemeHelper.THEME_LIGHT);
            } else if (checkedId == R.id.radioThemeDark) {
                ThemeHelper.saveTheme(requireContext(), ThemeHelper.THEME_DARK);
            } else if (checkedId == R.id.radioThemeAuto) {
                ThemeHelper.saveTheme(requireContext(), ThemeHelper.THEME_AUTO);
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

    private void togglePasswordVisibility(EditText editText, ImageButton button) {
        android.graphics.Typeface currentTypeface = editText.getTypeface();
        boolean isVisible = editText.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        if (isVisible) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            button.setImageResource(R.drawable.ic_visibility_off);
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            button.setImageResource(R.drawable.ic_visibility);
        }
        editText.setTypeface(currentTypeface);
        editText.setSelection(editText.getText().length());
    }
}