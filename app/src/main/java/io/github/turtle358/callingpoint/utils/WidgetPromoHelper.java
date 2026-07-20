package io.github.turtle358.callingpoint.utils; // Make sure this matches your package

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import io.github.turtle358.callingpoint.widget.AppWidgetProvider;

public class WidgetPromoHelper {

    public static void requestPinWidget(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager appWidgetManager = context.getSystemService(AppWidgetManager.class);

            ComponentName myProvider = new ComponentName(context, AppWidgetProvider.class);

            if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported()) {

                boolean acceptedByOs = appWidgetManager.requestPinAppWidget(myProvider, null, null);

                if (acceptedByOs) {
                    System.out.println("Opening widget prompt...");
                } else {
                    System.out.println("Error: OS rejected widget component.");
                }

            } else {
                System.out.println("Launcher doesn't support automatic pinning.");
                Toast.makeText(context, "Your launcher doesn't support automatic pinning.", Toast.LENGTH_LONG).show();
            }
        } else {
            System.out.println("Widget api success");
            Toast.makeText(context, "Long-press your home screen to add the widget!", Toast.LENGTH_LONG).show();
        }
    }
}