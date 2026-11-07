package uz.siriusstore.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class SyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        SyncCore.schedule(ctx);
        if (intent != null && SyncCore.ACTION.equals(intent.getAction())) {
            WorkManager.getInstance(ctx).enqueue(new OneTimeWorkRequest.Builder(SyncWorker.class).build());
        }
    }
}
