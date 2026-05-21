package io.github.turtle358.callingpoint;

import android.app.Application;

public class CallingPointApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ThemeHelper.applyTheme(this);
    }
}