package io.github.turtle358.callingpoint.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.graphics.ColorUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.DisruptionResponce;
import io.github.turtle358.callingpoint.ui.activities.MainActivity;
import io.github.turtle358.callingpoint.utils.Disruptions.DisruptionSyncManager;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class AppWidgetProvider extends android.appwidget.AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        TOCBrandHelper.init(context);
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String selectedTocs = prefs.getString("WIDGET_TRACKED_TOCS", "");
        List<String> tocFilter = new ArrayList<>();
        if (!selectedTocs.trim().isEmpty()) {
            for (String s : selectedTocs.split(",")) {
                if (!s.trim().isEmpty()) tocFilter.add(s.trim());
            }
        }
        String cachedJson = prefs.getString("CACHED_TOC_DATA", null);
        List<DisruptionResponce.ServiceIndicator> filteredData = new ArrayList<>();

        if (cachedJson != null) {
            java.lang.reflect.Type type = new TypeToken<ArrayList<DisruptionResponce.ServiceIndicator>>(){}.getType();
            List<DisruptionResponce.ServiceIndicator> allCached = new Gson().fromJson(cachedJson, type);

            for (DisruptionResponce.ServiceIndicator si : allCached) {
                if (tocFilter.isEmpty() || tocFilter.contains(si.tocCode)) {
                    filteredData.add(si);
                }
            }
        }
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, filteredData);
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager manager, int appWidgetId, List<DisruptionResponce.ServiceIndicator> data) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_disruption);

        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        views.setTextViewText(R.id.widgetTime, timeSdf.format(new Date()));

        renderWidgetVisuals(views, data);
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.putExtra("NAVIGATE_TO", "disruptions");
        PendingIntent piApp = PendingIntent.getActivity(context, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.titleContainer, piApp);

        Intent refreshIntent = new Intent(context, AppWidgetProvider.class);
        refreshIntent.setAction("io.github.turtle358.callingpoint.ACTION_REFRESH_WIDGET");
        refreshIntent.putExtra("IS_GOOD_SERVICE", data.isEmpty());

        PendingIntent piRefresh = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        views.setOnClickPendingIntent(R.id.widgetRefresh, piRefresh);

        manager.updateAppWidget(appWidgetId, views);
    }

    private void renderWidgetVisuals(RemoteViews views, List<DisruptionResponce.ServiceIndicator> disruptedTocs) {
        views.setViewVisibility(R.id.widgetRefresh, View.VISIBLE);
        views.setViewVisibility(R.id.widgetProgressDark, View.GONE);
        views.setViewVisibility(R.id.widgetProgressLight, View.GONE);

        if (disruptedTocs.isEmpty()) {
            views.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.bg_widget_blue);
            views.setTextViewText(R.id.titleLine1, "Good service");
            views.setTextViewText(R.id.titleLine2, "on selected lines");
            views.setTextColor(R.id.titleLine1, Color.WHITE);
            views.setTextColor(R.id.titleLine2, Color.WHITE);
            views.setTextColor(R.id.widgetTime, Color.WHITE);
            views.setInt(R.id.widgetRefresh, "setColorFilter", Color.WHITE);
            views.setInt(R.id.widgetLogo, "setColorFilter", Color.WHITE);

            views.setViewVisibility(R.id.layoutPills, View.GONE);
            views.setViewVisibility(R.id.layoutBars, View.GONE);
            views.setViewVisibility(R.id.iconAlert, View.GONE);
        } else {
            views.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.bg_widget_rounded);
            views.setTextViewText(R.id.titleLine1, "Status");
            views.setTextViewText(R.id.titleLine2, "Disruption");

            int dark = Color.parseColor("#111111");
            views.setTextColor(R.id.titleLine1, dark);
            views.setTextColor(R.id.titleLine2, dark);
            views.setTextColor(R.id.widgetTime, dark);
            views.setInt(R.id.widgetRefresh, "setColorFilter", dark);
            views.setInt(R.id.widgetLogo, "setColorFilter", dark);

            views.setViewVisibility(R.id.iconAlert, View.VISIBLE);

            if (disruptedTocs.size() <= 2) {
                views.setViewVisibility(R.id.layoutPills, View.VISIBLE);
                views.setViewVisibility(R.id.layoutBars, View.GONE);

                updatePill(views, R.id.badge1_text, disruptedTocs.get(0));

                if (disruptedTocs.size() == 2) {
                    updatePill(views, R.id.badge2_text, disruptedTocs.get(1));
                } else {
                    views.setViewVisibility(R.id.badge2_text, View.GONE);
                }
            } else {
                views.setViewVisibility(R.id.layoutPills, View.GONE);
                views.setViewVisibility(R.id.layoutBars, View.VISIBLE);

                int[] barIds = {R.id.bar1, R.id.bar2, R.id.bar3, R.id.bar4, R.id.bar5};
                for (int id : barIds) views.setViewVisibility(id, View.GONE);

                int maxBars = Math.min(disruptedTocs.size(), 5);
                for (int i = 0; i < maxBars; i++) {
                    int colour = TOCBrandHelper.getColourForToc(disruptedTocs.get(i).tocName);
                    views.setInt(barIds[i], "setColorFilter", colour);
                    views.setViewVisibility(barIds[i], View.VISIBLE);
                }
            }
        }
    }

    private void updatePill(RemoteViews views, int textId, DisruptionResponce.ServiceIndicator toc) {
        int colour = TOCBrandHelper.getColourForToc(toc.tocName);
        int secondaryColour = TOCBrandHelper.getSecondaryColourForToc(toc.tocName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(textId, "setBackgroundTintList", ColorStateList.valueOf(colour));
        }

        String acronym = TOCBrandHelper.getAcronymForCode(toc.tocCode);
        views.setTextViewText(textId, acronym != null ? acronym : toc.tocCode);

        views.setViewVisibility(textId, View.VISIBLE);

        views.setTextColor(textId, secondaryColour);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if ("io.github.turtle358.callingpoint.ACTION_REFRESH_WIDGET".equals(intent.getAction())) {
            boolean isGoodService = intent.getBooleanExtra("IS_GOOD_SERVICE", false);
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, AppWidgetProvider.class);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_disruption);

            views.setViewVisibility(R.id.widgetRefresh, View.GONE);
            if (isGoodService) {
                views.setViewVisibility(R.id.widgetProgressLight, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.widgetProgressDark, View.VISIBLE);
            }
            appWidgetManager.updateAppWidget(thisWidget, views);

            final PendingResult pendingResult = goAsync();
            new Thread(() -> {
                DisruptionSyncManager.syncSync(context, null);
                pendingResult.finish();
            }).start();
        }
    }
}