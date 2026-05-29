package io.github.turtle358.callingpoint;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.utils.ThemeHelper;
import io.github.turtle358.callingpoint.workers.DataSyncWorker;

public class CallingPointApp extends Application {
    public static final String CHANNEL_ID = "train_delay_alerts";

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.applyTheme(this);
        TOCBrandHelper.init(this);
        StationHelper.init(this);

        createNotificationChannel();
    }

    private void createNotificationChannel() {
        CharSequence name = "Train Delay Alerts";
        String description = "Notifies you when a pinned train is delayed or cancelled.";

        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }

        PeriodicWorkRequest dataSyncRequest = new PeriodicWorkRequest.Builder(
                DataSyncWorker.class,
                1, TimeUnit.DAYS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "RemoteDataSync",
                ExistingPeriodicWorkPolicy.KEEP,
                dataSyncRequest
        );
    }
}