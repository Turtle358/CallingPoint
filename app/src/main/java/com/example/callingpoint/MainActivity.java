package com.example.callingpoint;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TOCBrandHelper.init(this);
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES;

        WindowInsetsControllerCompat windowController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowController.setAppearanceLightStatusBars(!isDarkMode);

        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_departures) {
                selectedFragment = new DeparturesFragment();
            } else if (itemId == R.id.nav_disruptions) {
                selectedFragment = new DisruptionsFragment();
            } else if (itemId == R.id.nav_settings) {
                selectedFragment = new SettingsFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });
        if (savedInstanceState == null) {
            String navigateTo = getIntent().getStringExtra("NAVIGATE_TO");

            if ("disruptions".equals(navigateTo)) {
                bottomNav.setSelectedItemId(R.id.nav_disruptions);
            } else {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new DeparturesFragment())
                        .commit();
            }
        }
    }
}