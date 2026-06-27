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
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;

import java.util.concurrent.TimeUnit;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.ui.adapters.MainPagerAdapter;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.workers.TrainTrackerWorker;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;

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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg_screen));
        getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg_card));

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
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
        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_navigation);

        MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(3);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            final int targetPage;

            if (itemId == R.id.nav_departures) targetPage = 0;
            else if (itemId == R.id.nav_journey) targetPage = 1;
            else if (itemId == R.id.nav_disruptions) targetPage = 2;
            else if (itemId == R.id.nav_settings) targetPage = 3;
            else return false;

            if (viewPager.getCurrentItem() != targetPage) {
                if (Math.abs(viewPager.getCurrentItem() - targetPage) > 1) {
                    viewPager.animate()
                            .alpha(0f)
                            .setDuration(100)
                            .withEndAction(() -> {
                                viewPager.setCurrentItem(targetPage, false);
                                viewPager.animate()
                                        .alpha(1f)
                                        .setDuration(150)
                                        .start();
                            })
                            .start();
                } else {
                    viewPager.setCurrentItem(targetPage, true);
                }
            }
            return true;
        });
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                bottomNav.getMenu().getItem(position).setChecked(true);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    hideKeyboard();
                }
            }
        });

        if (savedInstanceState == null) {
            Intent intent = getIntent();
            if (intent != null) {
                String navigateTo = intent.getStringExtra("NAVIGATE_TO");
                if ("disruptions".equalsIgnoreCase(navigateTo)) {
                    viewPager.setCurrentItem(2, false);
                }
            }
            handleIntentExtras(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntentExtras(intent);
    }

    private void handleIntentExtras(Intent intent) {
        if (intent != null) {
            String openCrs = intent.getStringExtra("OPEN_STATION_CRS");
            if (openCrs != null) {
                viewPager.setCurrentItem(0, false);

                io.github.turtle358.callingpoint.ui.fragments.StationFragment fragment = new io.github.turtle358.callingpoint.ui.fragments.StationFragment();
                Bundle args = new Bundle();
                args.putString("FROM_CRS", openCrs);
                args.putString("TO_CRS", "");
                args.putBoolean("IS_ARRIVALS", false);
                fragment.setArguments(args);

                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit();

                intent.removeExtra("OPEN_STATION_CRS");
            }
        }
    }

    private void hideKeyboard() {
        android.view.View view = this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }
}