package io.github.turtle358.callingpoint.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.data.PinnedDataManager;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import retrofit2.Call;
import retrofit2.Response;

public class TrainTrackerWorker extends Worker {

    public TrainTrackerWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        PinnedDataManager pinnedDataManager = new PinnedDataManager(context);
        List<String> pinnedServices = pinnedDataManager.getPinnedServices();

        if (pinnedServices.isEmpty()) {
            return Result.success();
        }

        NationalRailAPI api = APIClient.getClient().create(NationalRailAPI.class);
        String apiKey = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .getString("SERVICE_DETAILS_KEY", "");

        if (apiKey == null || apiKey.isEmpty()) {
            return Result.failure();
        }

        for (String serviceId : pinnedServices) {
            try {
                Call<ServiceDetailsResponse> call = api.getServiceDetails(apiKey, serviceId);
                Response<ServiceDetailsResponse> response = call.execute();

                if (response.isSuccessful() && response.body() != null) {
                    ServiceDetailsResponse data = response.body();

                    String status = data.etd;
                    String scheduledTime = data.std;
                    String platform = data.platform;

                    if (status != null && !status.equalsIgnoreCase("On time")) {
                        android.content.SharedPreferences prefs = context.getSharedPreferences("NotifiedTrains", Context.MODE_PRIVATE);
                        String lastKnownStatus = prefs.getString(serviceId, "");
                        if (!status.equalsIgnoreCase(lastKnownStatus)) {
                            Log.d("TrainTracker", "New Delay or Change Detected! " + status);

                            sendNotification(data.operator, data.locationName, status, scheduledTime, platform, serviceId);
                            prefs.edit().putString(serviceId, status).apply();
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                return Result.retry();
            }
        }

        return Result.success();
    }

    private void sendNotification(String operator, String location, String status, String scheduledTime, String platform, String serviceID) {
        android.content.Intent mainIntent = new android.content.Intent(getApplicationContext(), io.github.turtle358.callingpoint.ui.activities.MainActivity.class);
        android.content.Intent detailsIntent = new android.content.Intent(getApplicationContext(), io.github.turtle358.callingpoint.ui.activities.ServiceDetailsActivity.class);
        detailsIntent.putExtra("SERVICE_ID", serviceID);

        android.app.TaskStackBuilder stackBuilder = android.app.TaskStackBuilder.create(getApplicationContext());
        stackBuilder.addNextIntent(mainIntent);
        stackBuilder.addNextIntent(detailsIntent);

        android.app.PendingIntent pendingIntent = stackBuilder.getPendingIntent(
                serviceID.hashCode(),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        android.widget.RemoteViews customView = new android.widget.RemoteViews(getApplicationContext().getPackageName(), R.layout.notification_custom_train);

        customView.setTextViewText(R.id.notifTitle, scheduledTime + " to " + location);
        customView.setTextViewText(R.id.notifOperator, operator);
        customView.setTextViewText(R.id.notifPlatform, (platform != null && !platform.isEmpty()) ? "Plat " + platform : "");

        int brandColour = TOCBrandHelper.getColorForToc(operator);

        customView.setInt(R.id.notifTocBar, "setBackgroundColor", brandColour);

        customView.setImageViewResource(R.id.notifLogo, TOCBrandHelper.getIconForToc(operator));

        String delayedTime = calculateDelayString(scheduledTime, status);

        if (status.equalsIgnoreCase("cancelled") || status.equalsIgnoreCase("canceled")) {
            customView.setTextViewText(R.id.notifStatus, "Cancelled");
            customView.setTextColor(R.id.notifStatus, android.graphics.Color.parseColor("#DE350B"));
        } else if (!delayedTime.isEmpty()) {
            customView.setTextViewText(R.id.notifStatus, delayedTime + " late");
            customView.setTextColor(R.id.notifStatus, android.graphics.Color.parseColor("#FF8C00"));
        } else {
            customView.setTextViewText(R.id.notifStatus, "On time");
            customView.setTextColor(R.id.notifStatus, android.graphics.Color.parseColor("#00875A"));
        }

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(getApplicationContext(), io.github.turtle358.callingpoint.CallingPointApp.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app_outline)
                .setColor(brandColour)
                .setStyle(new androidx.core.app.NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(customView)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        androidx.core.app.NotificationManagerCompat notificationManager = androidx.core.app.NotificationManagerCompat.from(getApplicationContext());

        if (androidx.core.content.ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private String calculateDelayString(String scheduledTime, String estimatedTime) {
        if (estimatedTime == null || !estimatedTime.matches("\\d{2}:\\d{2}")) {
            return "";
        }
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK);
            java.util.Date std = sdf.parse(scheduledTime);
            java.util.Date etd = sdf.parse(estimatedTime);

            if (std != null && etd != null) {
                long diffMs = etd.getTime() - std.getTime();
                long diffMins = diffMs / (60 * 1000);
                if (diffMins < -720) {
                    diffMins += 24 * 60;
                } else if (diffMins < 0) {
                    return "";
                }

                if (diffMins == 0) return "";

                long hours = diffMins / 60;
                long mins = diffMins % 60;

                if (hours > 0) {
                    return hours + " hr " + mins + " mins";
                } else {
                    return mins + " mins";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}