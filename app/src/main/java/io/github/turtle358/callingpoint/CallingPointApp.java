package io.github.turtle358.callingpoint;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import io.github.turtle358.callingpoint.utils.ThemeHelper;

public class CallingPointApp extends Application {
    public static final String CHANNEL_ID = "train_delay_alerts";

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.applyTheme(this);

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
    }
}