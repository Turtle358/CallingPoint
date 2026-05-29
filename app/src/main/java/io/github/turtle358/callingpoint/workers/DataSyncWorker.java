package io.github.turtle358.callingpoint.workers;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.FileOutputStream;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;

public class DataSyncWorker extends Worker {

    private static final String TOCS_URL = "https://raw.githubusercontent.com/Turtle358/callingpoint-data/refs/heads/main/tocs.json";
    private static final String STATIONS_URL = "https://raw.githubusercontent.com/Turtle358/callingpoint-data/refs/heads/main/stations.json";

    public DataSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        NationalRailAPI api = APIClient.getClient().create(NationalRailAPI.class);

        boolean tocsSuccess = downloadAndSave(api, TOCS_URL, "tocs_remote.json");
        boolean stationsSuccess = downloadAndSave(api, STATIONS_URL, "stations_remote.json");

        if (tocsSuccess && stationsSuccess) {
            return Result.success();
        } else {
            return Result.failure();
        }
    }

    private boolean downloadAndSave(NationalRailAPI api, String url, String filename) {
        try {
            Call<ResponseBody> call = api.downloadRemoteData(url);
            Response<ResponseBody> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                String jsonString = response.body().string();

                FileOutputStream fos = getApplicationContext().openFileOutput(filename, Context.MODE_PRIVATE);
                fos.write(jsonString.getBytes());
                fos.close();
                return true;
            }
        } catch (Exception e) {
            Log.e("DataSyncWorker", "Failed to sync: " + filename, e);
        }
        return false;
    }
}