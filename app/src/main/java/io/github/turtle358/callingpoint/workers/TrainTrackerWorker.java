package io.github.turtle358.callingpoint.workers; // Adjust if you put it in the 'data' folder

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

                    if (status != null && !status.equalsIgnoreCase("On time")) {
                        Log.d("TrainTracker", "Train Delayed or Cancelled! " + status);

                        sendNotification(data.operator, data.locationName, status, scheduledTime);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                return Result.retry();
            }
        }

        return Result.success();
    }

    private void sendNotification(String operator, String location, String status, String scheduledTime) {
        android.content.Intent intent = new android.content.Intent(getApplicationContext(), io.github.turtle358.callingpoint.ui.activities.MainActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);

        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                getApplicationContext(),
                0,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE
        );
        String title = "The" + scheduledTime + " " + operator + "service";
        String delayedTime = calculateDelayString(scheduledTime, status);
        String message;
        if (status.equalsIgnoreCase("cancelled") || status.equalsIgnoreCase("canceled")) {
            message = "has been cancelled.";
        } else if (!delayedTime.isEmpty()) {
            message = "is delayed by " + delayedTime + " (expected at " + status + ").";
        } else {
            message = "is currently showing as " + status + ".";
        }
        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(getApplicationContext(), io.github.turtle358.callingpoint.CallingPointApp.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app_outline)
                .setContentTitle(title)
                .setContentText(message)
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