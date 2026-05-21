package io.github.turtle358.callingpoint.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;

import java.util.concurrent.TimeUnit;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.ui.fragments.DisruptionsFragment;
import io.github.turtle358.callingpoint.ui.fragments.LiveBoardFragment;
import io.github.turtle358.callingpoint.ui.fragments.SettingsFragment;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.workers.TrainTrackerWorker;

public class MainActivity extends AppCompatActivity {

    private Fragment liveBoardFragment;
    private Fragment disruptionsFragment;
    private Fragment settingsFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TOCBrandHelper.init(this);

        DynamicColors.applyToActivityIfAvailable(this);

        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES;

        WindowInsetsControllerCompat windowController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowController.setAppearanceLightStatusBars(!isDarkMode);

        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest trackerRequest = new PeriodicWorkRequest.Builder(TrainTrackerWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();



        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "TrainTracker",
                ExistingPeriodicWorkPolicy.KEEP,
                trackerRequest
        );

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        FragmentManager fm = getSupportFragmentManager();
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

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

        if (savedInstanceState == null) {
            liveBoardFragment = new LiveBoardFragment();
            disruptionsFragment = new DisruptionsFragment();
            settingsFragment = new SettingsFragment();

            fm.beginTransaction().add(R.id.fragment_container, settingsFragment, "3").hide(settingsFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, disruptionsFragment, "2").hide(disruptionsFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, liveBoardFragment, "1").commit();

            activeFragment = liveBoardFragment;

            Intent intent = getIntent();
            if (intent != null) {
                String navigateTo = intent.getStringExtra("NAVIGATE_TO");
                if ("disruptions".equalsIgnoreCase(navigateTo)) {
                    bottomNav.setSelectedItemId(R.id.nav_disruptions);
                }
            }
        } else {
            liveBoardFragment = fm.findFragmentByTag("1");
            disruptionsFragment = fm.findFragmentByTag("2");
            settingsFragment = fm.findFragmentByTag("3");
            activeFragment = liveBoardFragment;
        }
    }
}