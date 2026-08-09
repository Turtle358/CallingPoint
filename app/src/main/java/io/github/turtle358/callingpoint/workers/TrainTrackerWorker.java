package io.github.turtle358.callingpoint.workers;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
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

        for (String serviceId : pinnedServices) {
            try {
                Call<ServiceDetailsResponse> call = api.getServiceDetails(serviceId);
                Response<ServiceDetailsResponse> response = call.execute();

                if (response.isSuccessful() && response.body() != null) {
                    ServiceDetailsResponse data = response.body();
                    String status = data.etd;

                    if (status != null && !status.equalsIgnoreCase("On time")) {
                        android.content.SharedPreferences prefs = context.getSharedPreferences("NotifiedTrains", Context.MODE_PRIVATE);
                        String lastKnownStatus = prefs.getString(serviceId, "");
                        if (!status.equalsIgnoreCase(lastKnownStatus)) {
                            Log.d("TrainTracker", "New Delay or Change Detected! " + status);

                            sendNotification(data, serviceId);
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
    private void sendNotification(ServiceDetailsResponse data, String serviceID) {
        Context appContext = getApplicationContext();

        android.content.Intent mainIntent = new android.content.Intent(appContext, io.github.turtle358.callingpoint.ui.activities.MainActivity.class);
        android.content.Intent detailsIntent = new android.content.Intent(appContext, io.github.turtle358.callingpoint.ui.activities.ServiceDetailsActivity.class);
        detailsIntent.putExtra("SERVICE_ID", serviceID);

        android.app.TaskStackBuilder stackBuilder = android.app.TaskStackBuilder.create(appContext);
        stackBuilder.addNextIntent(mainIntent);
        stackBuilder.addNextIntent(detailsIntent);

        android.app.PendingIntent pendingIntent = stackBuilder.getPendingIntent(
                serviceID.hashCode(),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        android.widget.RemoteViews expandedView = new android.widget.RemoteViews(appContext.getPackageName(), R.layout.notification_custom_train);

        int brandColour = TOCBrandHelper.getColourForToc(data.operator);
        boolean isLightColour = ColorUtils.calculateLuminance(brandColour) > 0.5;

        expandedView.setImageViewResource(R.id.notifLogo, TOCBrandHelper.getIconForToc(data.operator));
        expandedView.setInt(R.id.notifOperatorPillBg, "setColorFilter", brandColour);
        expandedView.setTextColor(R.id.notifOperatorName, isLightColour ? Color.BLACK : Color.WHITE);
        expandedView.setTextViewText(R.id.notifOperatorName, data.operator);
        expandedView.setTextViewText(R.id.notifPlatform, (data.platform != null && !data.platform.isEmpty()) ? "Plat " + data.platform : "Plat -");

        expandedView.setInt(R.id.notifDotTop, "setColorFilter", brandColour);
        expandedView.setInt(R.id.notifDotBottom, "setColorFilter", brandColour);
        expandedView.setInt(R.id.notifTimelineLine, "setBackgroundColor", ColorUtils.setAlphaComponent(brandColour, 102));

        List<ServiceDetailsResponse.TimelinePoint> journey = new ArrayList<>();
        if (data.previousCallingPoints != null && !data.previousCallingPoints.isEmpty() && data.previousCallingPoints.get(0).callingPoint != null) {
            journey.addAll(data.previousCallingPoints.get(0).callingPoint);
        }

        ServiceDetailsResponse.TimelinePoint anchor = new ServiceDetailsResponse.TimelinePoint();
        anchor.locationName = data.locationName;
        anchor.scheduledTime = data.std != null ? data.std : "";
        anchor.estimatedTime = data.etd;
        anchor.actualTime = (data.atd != null) ? data.atd : data.ata;
        journey.add(anchor);

        if (data.subsequentCallingPoints != null && !data.subsequentCallingPoints.isEmpty() && data.subsequentCallingPoints.get(0).callingPoint != null) {
            journey.addAll(data.subsequentCallingPoints.get(0).callingPoint);
        }

        String origin = data.locationName;
        String topTime = data.std != null ? data.std : "--:--";

        String destination = origin;
        String bottomTime = "--:--";
        if (data.subsequentCallingPoints != null && !data.subsequentCallingPoints.isEmpty() && data.subsequentCallingPoints.get(0).callingPoint != null) {
            List<ServiceDetailsResponse.TimelinePoint> subsequent = data.subsequentCallingPoints.get(0).callingPoint;
            destination = subsequent.get(subsequent.size() - 1).locationName;
            bottomTime = subsequent.get(subsequent.size() - 1).scheduledTime;
        }

        expandedView.setTextViewText(R.id.notifDepTime, topTime);
        expandedView.setTextViewText(R.id.notifOriginStation, origin);
        expandedView.setTextViewText(R.id.notifArrTime, bottomTime);
        expandedView.setTextViewText(R.id.notifDestStation, destination);
        expandedView.setInt(R.id.notifDepTime, "setPaintFlags", Paint.ANTI_ALIAS_FLAG);

        android.widget.RemoteViews collapsedView = new android.widget.RemoteViews(appContext.getPackageName(), R.layout.notification_custom_train_small);

        collapsedView.setInt(R.id.notifTocBar, "setBackgroundColor", brandColour);
        collapsedView.setTextViewText(R.id.notifTitle, origin + " to " + destination);
        collapsedView.setTextViewText(R.id.notifPlatform, (data.platform != null && !data.platform.isEmpty()) ? "Plat " + data.platform : "Plat -");
        collapsedView.setImageViewResource(R.id.notifLogo, TOCBrandHelper.getIconForToc(data.operator));
        collapsedView.setTextViewText(R.id.notifOperator, data.operator);


        ServiceDetailsResponse.TimelinePoint trueNextStop = null;
        int trueNextStopIndex = -1;
        for (int i = 0; i < journey.size(); i++) {
            ServiceDetailsResponse.TimelinePoint point = journey.get(i);
            boolean hasPassed = point.actualTime != null && !point.actualTime.isEmpty();
            if (point.estimatedTime != null && (point.estimatedTime.equalsIgnoreCase("Arrived") || point.estimatedTime.equalsIgnoreCase("Departed"))) {
                hasPassed = true;
            }
            if (!hasPassed) {
                trueNextStop = point;
                trueNextStopIndex = i;
                break;
            }
        }

        if (trueNextStop != null) {
            String estTime = (trueNextStop.estimatedTime != null && !trueNextStop.estimatedTime.isEmpty()) ? trueNextStop.estimatedTime : "On time";

            if ("On time".equalsIgnoreCase(estTime)) {
                expandedView.setTextViewText(R.id.notifStatus, "On time");
                expandedView.setTextColor(R.id.notifStatus, Color.parseColor("#00875A"));

                collapsedView.setTextViewText(R.id.notifStatus, "On time");
                collapsedView.setTextColor(R.id.notifStatus, Color.parseColor("#00875A"));
            } else if ("Cancelled".equalsIgnoreCase(estTime) || "Canceled".equalsIgnoreCase(estTime)) {
                expandedView.setInt(R.id.notifDepTime, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
                expandedView.setTextViewText(R.id.notifStatus, "Cancelled");
                expandedView.setTextColor(R.id.notifStatus, Color.parseColor("#DE350B"));

                collapsedView.setTextViewText(R.id.notifStatus, "Cancelled");
                collapsedView.setTextColor(R.id.notifStatus, Color.parseColor("#DE350B"));
            } else {
                expandedView.setInt(R.id.notifDepTime, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
                expandedView.setTextViewText(R.id.notifStatus, "Exp " + estTime);
                expandedView.setTextColor(R.id.notifStatus, Color.parseColor("#FF8C00"));

                collapsedView.setTextViewText(R.id.notifStatus, "Exp " + estTime);
                collapsedView.setTextColor(R.id.notifStatus, Color.parseColor("#FF8C00"));
            }

            int stopsLeft = journey.size() - trueNextStopIndex - 1;
            String durationStr = "";
            if (stopsLeft > 0) {
                ServiceDetailsResponse.TimelinePoint finalDest = journey.get(journey.size() - 1);
                durationStr = calculateDuration(trueNextStop.scheduledTime, finalDest.scheduledTime);
            }

            StringBuilder details = new StringBuilder();
            if (!durationStr.isEmpty()) details.append(durationStr).append(" • ");
            if (stopsLeft > 0) {
                details.append(stopsLeft).append(stopsLeft == 1 ? " stop • " : " stops • ");
            } else {
                details.append("Last stop • ");
            }
            details.append("Next calling at ").append(trueNextStop.locationName);
            expandedView.setTextViewText(R.id.notifServiceDetails, details.toString());
        } else {
            expandedView.setTextViewText(R.id.notifStatus, "Terminated");
            expandedView.setTextColor(R.id.notifStatus, Color.parseColor("#666666"));
            expandedView.setTextViewText(R.id.notifServiceDetails, "Last stop");

            collapsedView.setTextViewText(R.id.notifStatus, "Terminated");
            collapsedView.setTextColor(R.id.notifStatus, Color.parseColor("#666666"));
        }

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(appContext, io.github.turtle358.callingpoint.CallingPointApp.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app_outline)
                .setColor(brandColour)
                .setStyle(new androidx.core.app.NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        androidx.core.app.NotificationManagerCompat notificationManager = androidx.core.app.NotificationManagerCompat.from(appContext);

        if (androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private String calculateDuration(String start, String end) {
        if (start == null || end == null || !start.contains(":") || !end.contains(":")) return "";
        try {
            String[] sParts = start.split(":");
            String[] eParts = end.split(":");
            int sMins = Integer.parseInt(sParts[0]) * 60 + Integer.parseInt(sParts[1]);
            int eMins = Integer.parseInt(eParts[0]) * 60 + Integer.parseInt(eParts[1]);

            int diff = eMins - sMins;
            if (diff < 0) diff += 1440;

            int h = diff / 60;
            int m = diff % 60;

            if (h > 0) return h + "h " + m + "m";
            return m + "m";
        } catch (Exception e) {
            return "";
        }
    }
}