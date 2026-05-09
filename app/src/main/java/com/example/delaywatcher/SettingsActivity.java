package com.example.delaywatcher;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {
    private EditText etCustomerKey;
    private Button btnSaveSettings;
    private Set<String> trackedTocs;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);
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