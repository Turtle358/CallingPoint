package com.example.delaywatcher;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {
    private EditText etCustomerKey;
    private Button btnSaveSettings;
    private RadioGroup themeRadioGroup;
    private Set<String> trackedTocs;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        TOCBrandHelper.init(this);
        prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        String savedTocs = prefs.getString("WIDGET_TRACKED_TOCS", "");
        trackedTocs = new HashSet<>();
        if (!savedTocs.trim().isEmpty()) {
            trackedTocs.addAll(Arrays.asList(savedTocs.split(",")));
        }
        etCustomerKey = findViewById(R.id.etCustomerKey);
        btnSaveSettings = findViewById(R.id.btnSaveSettings);
        etCustomerKey.setText(prefs.getString("CUSTOMER_KEY", ""));
        ImageButton btnTogglePassword = findViewById(R.id.btnTogglePassword);
        btnTogglePassword.setOnClickListener(v -> {
            android.graphics.Typeface currentTypeface = etCustomerKey.getTypeface();
            boolean isVisible = etCustomerKey.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

            if (isVisible) {
                etCustomerKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            } else {
                etCustomerKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnTogglePassword.setImageResource(R.drawable.ic_visibility);
            }
            etCustomerKey.setTypeface(currentTypeface);
            etCustomerKey.setSelection(etCustomerKey.getText().length());
        });

        themeRadioGroup = findViewById(R.id.themeRadioGroup);
        setupThemeControl();
        RecyclerView recyclerTocs = findViewById(R.id.recyclerTocsSettings);
        recyclerTocs.setLayoutManager(new LinearLayoutManager(this));

        List<Toc> allTocs = TOCBrandHelper.getAllTocs();

        TocSettingAdapter adapter = new TocSettingAdapter(allTocs, trackedTocs, (tocCode, isTracked) -> {
            if (isTracked) {
                trackedTocs.add(tocCode);
            } else {
                trackedTocs.remove(tocCode);
            }
            saveTrackedTocs();
        });

        recyclerTocs.setAdapter(adapter);

        btnSaveSettings.setOnClickListener(v -> {
            prefs.edit().putString("CUSTOMER_KEY", etCustomerKey.getText().toString().trim()).apply();
            Toast.makeText(this, "API Key Saved", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void setupThemeControl() {
        int currentTheme = ThemeHelper.getSavedTheme(this);
        if (currentTheme == ThemeHelper.THEME_LIGHT) {
            themeRadioGroup.check(R.id.radioThemeLight);
        } else if (currentTheme == ThemeHelper.THEME_DARK) {
            themeRadioGroup.check(R.id.radioThemeDark);
        } else {
            themeRadioGroup.check(R.id.radioThemeAuto);
        }

        themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioThemeLight) {
                ThemeHelper.saveTheme(this, ThemeHelper.THEME_LIGHT);
            } else if (checkedId == R.id.radioThemeDark) {
                ThemeHelper.saveTheme(this, ThemeHelper.THEME_DARK);
            } else if (checkedId == R.id.radioThemeAuto) {
                ThemeHelper.saveTheme(this, ThemeHelper.THEME_AUTO);
            }
        });
    }

    private void saveTrackedTocs() {
        String joined = TextUtils.join(",", trackedTocs);
        prefs.edit().putString("WIDGET_TRACKED_TOCS", joined).apply();
        refreshWidget();
    }

    private void refreshWidget() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName thisWidget = new ComponentName(this, AppWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

        Intent intent = new Intent(this, AppWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        sendBroadcast(intent);
    }
}