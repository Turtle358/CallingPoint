package com.example.delaywatcher;

import android.app.Application;

public class DelayWatcherApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ThemeHelper.applyTheme(this);
    }
}