package uz.siriusstore.app;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class DailyWorker extends Worker {
    public DailyWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            DailyCore.run(getApplicationContext(), false);
        } catch (Exception ignored) { }
        return Result.success();
    }
}
