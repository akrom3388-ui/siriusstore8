package uz.siriusstore.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class DailyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        // Har qanday holatda (signal, telefon yoqilishi, ilova yangilanishi) keyingi vaqtni qayta rejalashtiramiz
        DailyCore.schedule(ctx);
        SyncCore.schedule(ctx);
        if (intent != null && DailyCore.ACTION.equals(intent.getAction())) {
            WorkManager.getInstance(ctx).enqueue(new OneTimeWorkRequest.Builder(DailyWorker.class).build());
        }
    }
}
