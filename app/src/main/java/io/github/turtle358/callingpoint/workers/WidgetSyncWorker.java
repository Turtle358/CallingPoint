package io.github.turtle358.callingpoint.workers;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import io.github.turtle358.callingpoint.utils.Disruptions.DisruptionSyncManager;

public class WidgetSyncWorker extends Worker {

    public WidgetSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean success = DisruptionSyncManager.syncSync(getApplicationContext(), null);

        return success ? Result.success() : Result.retry();
    }
}