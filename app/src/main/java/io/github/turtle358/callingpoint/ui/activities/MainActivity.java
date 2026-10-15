package io.github.turtle358.callingpoint.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.color.DynamicColors;

import java.util.concurrent.TimeUnit;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.ui.adapters.MainPageAdapter;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.workers.TrainTrackerWorker;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private View[] navItems;
    private ImageView[] navIcons;
    private TextView[] navLabels;

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

        // Enable edge-to-edge rendering with transparent navigation bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.bg_screen));
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

        // Adjust floating card bottom margin according to system gesture bar insets
        View bottomNavCard = findViewById(R.id.bottom_nav_card);
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavCard, (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            int defaultMarginPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());

            params.bottomMargin = bottomInset + defaultMarginPx;
            v.setLayoutParams(params);
            return insets;
        });

        // Initialize pill navigation components
        navItems = new View[]{
                findViewById(R.id.nav_item_departures),
                findViewById(R.id.nav_item_journey),
                findViewById(R.id.nav_item_disruptions),
                findViewById(R.id.nav_item_settings)
        };

        navIcons = new ImageView[]{
                findViewById(R.id.icon_departures),
                findViewById(R.id.icon_journey),
                findViewById(R.id.icon_disruptions),
                findViewById(R.id.icon_settings)
        };

        navLabels = new TextView[]{
                findViewById(R.id.label_departures),
                findViewById(R.id.label_journey),
                findViewById(R.id.label_disruptions),
                findViewById(R.id.label_settings)
        };

        for (int i = 0; i < navItems.length; i++) {
            final int pageIndex = i;
            navItems[i].setOnClickListener(v -> selectPage(pageIndex));
        }

        MainPageAdapter pagerAdapter = new MainPageAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(3);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePillNavigation(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    hideKeyboard();
                }
            }
        });

        updatePillNavigation(viewPager.getCurrentItem());

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

    private void selectPage(int targetPage) {
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
    }

    private void updatePillNavigation(int selectedPosition) {
        TypedValue typedValue = new TypedValue();

        // Active pill background color (secondary container or fallback)
        int activeBgColor = getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, typedValue, true)
                ? typedValue.data : ContextCompat.getColor(this, R.color.bg_card);

        // Active text/icon color (onSecondaryContainer or primary)
        int activeContentColor = getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSecondaryContainer, typedValue, true)
                ? typedValue.data : ContextCompat.getColor(this, R.color.nav_item_color_state);

        int inactiveTextColor = ContextCompat.getColor(this, R.color.nav_item_color_state);

        for (int i = 0; i < navItems.length; i++) {
            boolean isSelected = (i == selectedPosition);

            if (isSelected) {
                // Draw rounded pill highlight for active tab
                GradientDrawable pillDrawable = new GradientDrawable();
                pillDrawable.setShape(GradientDrawable.RECTANGLE);
                pillDrawable.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));
                pillDrawable.setColor(activeBgColor);
                navItems[i].setBackground(pillDrawable);

                // Show icon on active tab only (placed left of label)
                navIcons[i].setVisibility(View.VISIBLE);
                navIcons[i].setImageTintList(ColorStateList.valueOf(activeContentColor));
                navLabels[i].setTextColor(activeContentColor);
            } else {
                // Clear background and hide icon for unselected tabs
                navItems[i].setBackground(null);
                navIcons[i].setVisibility(View.GONE);
                navLabels[i].setTextColor(inactiveTextColor);
            }
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
                Intent stationIntent = new Intent(this, StationActivity.class);
                stationIntent.putExtra("FROM_CRS", openCrs);
                stationIntent.putExtra("TO_CRS", "");
                stationIntent.putExtra("IS_ARRIVALS", false);
                startActivity(stationIntent);
                overridePendingTransition(R.anim.fade_in_fast, R.anim.fade_out_fast);
                intent.removeExtra("OPEN_STATION_CRS");
            }
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }
}