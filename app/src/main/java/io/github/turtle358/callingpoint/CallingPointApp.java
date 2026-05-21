package io.github.turtle358.callingpoint;

import android.app.Application;

import io.github.turtle358.callingpoint.utils.ThemeHelper;

public class CallingPointApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ThemeHelper.applyTheme(this);
    }
}