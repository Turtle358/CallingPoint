package com.example.callingpoint;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private Fragment liveBoardFragment;
    private Fragment disruptionsFragment;
    private Fragment settingsFragment;
    private Fragment activeFragment;

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
        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            liveBoardFragment = new LiveBoardFragment();
            disruptionsFragment = new DisruptionsFragment();
            settingsFragment = new SettingsFragment();

            fm.beginTransaction().add(R.id.fragment_container, settingsFragment, "3").hide(settingsFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, disruptionsFragment, "2").hide(disruptionsFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, liveBoardFragment, "1").commit();

            activeFragment = liveBoardFragment;

            String navigateTo = getIntent().getStringExtra("NAVIGATE_TO");
            if ("disruptions".equalsIgnoreCase(navigateTo)) {
                bottomNav.setSelectedItemId(R.id.nav_disruptions);
            }
        } else {
            liveBoardFragment = fm.findFragmentByTag("1");
            disruptionsFragment = fm.findFragmentByTag("2");
            settingsFragment = fm.findFragmentByTag("3");
            activeFragment = liveBoardFragment;
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_departures) {
                fm.beginTransaction().hide(activeFragment).show(liveBoardFragment).commit();
                activeFragment = liveBoardFragment;
                return true;
            } else if (itemId == R.id.nav_disruptions) {
                fm.beginTransaction().hide(activeFragment).show(disruptionsFragment).commit();
                activeFragment = disruptionsFragment;
                return true;
            } else if (itemId == R.id.nav_settings) {
                fm.beginTransaction().hide(activeFragment).show(settingsFragment).commit();
                activeFragment = settingsFragment;
                return true;
            }
            return false;
        });
    }
}