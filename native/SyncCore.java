package uz.siriusstore.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/** Telefonlar o'rtasida sinxronlash: fonda (ilova yopiq) yangi operatsiyalarni tekshiradi va bildirishnoma beradi. */
public class SyncCore {
    public static final String ACTION = "uz.siriusstore.app.SYNC";
    static final long INTERVAL_MS = 5 * 60 * 1000L;

    static JSONObject cfg(Context ctx) {
        try { return new JSONObject(DailyCore.prefs(ctx).getString("syncCfg", "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    static boolean enabled(Context ctx) {
        JSONObject c = cfg(ctx);
        return c.optBoolean("enabled", false) && !c.optString("token").isEmpty();
    }

    /** Har 5 daqiqada signal + har 15 daqiqada zaxira vazifa. */
    public static void schedule(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(ctx, SyncReceiver.class);
            i.setAction(ACTION);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 9002, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (!enabled(ctx)) {
                am.cancel(pi);
                WorkManager.getInstance(ctx).cancelUniqueWork("sirius_sync_periodic");
                return;
            }
            long t = System.currentTimeMillis() + INTERVAL_MS;
            try {
                if (DailyCore.canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            }
            PeriodicWorkRequest w = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES).build();
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("sirius_sync_periodic", ExistingPeriodicWorkPolicy.KEEP, w);
        } catch (Exception ignored) { }
    }

    /** Ko'rilmagan operatsiya id lari. silent=true bo'lsa hammasini "ko'rilgan" deb belgilaydi va bo'sh qaytaradi. */
    public static synchronized JSONArray filterUnseen(Context ctx, JSONArray ids, boolean silent) {
        JSONArray fresh = new JSONArray();
        try {
            SharedPreferences p = DailyCore.prefs(ctx);
            JSONArray seen = new JSONArray(p.getString("seenOps", "[]"));
            HashSet<String> set = new HashSet<>();
            for (int i = 0; i < seen.length(); i++) set.add(seen.optString(i));
            for (int i = 0; i < ids.length(); i++) {
                String id = ids.optString(i, "");
                if (id.isEmpty() || set.contains(id)) continue;
                set.add(id);
                seen.put(id);
                if (!silent) fresh.put(id);
            }
            if (seen.length() > 800) {
                JSONArray t = new JSONArray();
                for (int i = seen.length() - 800; i < seen.length(); i++) t.put(seen.get(i));
                seen = t;
            }
            p.edit().putString("seenOps", seen.toString()).apply();
        } catch (Exception ignored) { }
        return fresh;
    }

    public static void notifyOps(Context ctx, String title, String body) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(new NotificationChannel("sirius_sync", "Yangi operatsiyalar",
                        NotificationManager.IMPORTANCE_HIGH));
            }
            Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            PendingIntent pi = open == null ? null : PendingIntent.getActivity(ctx, 1, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String first = body.contains("\n") ? body.substring(0, body.indexOf('\n')) : body;
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "sirius_sync")
                    .setSmallIcon(DailyCore.smallIcon(ctx))
                    .setContentTitle(title)
                    .setContentText(first)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);
            if (pi != null) b.setContentIntent(pi);
            nm.notify((int) (System.currentTimeMillis() % 100000) + 8000, b.build());
        } catch (Exception ignored) { }
    }

    /** Botdagi eng yangi (hali ilovaga qo'shilmagan) qarzdorlar ro'yxati; yo'q bo'lsa null. */
    public static JSONArray remoteDebtView(Context ctx) {
        try {
            if (!enabled(ctx)) return null;
            JSONObject c = cfg(ctx);
            if (!c.optBoolean("init", false)) return null;
            String token = c.optString("token");
            String[] ptr = TgNet.pointer(token);
            if (ptr == null || ptr[0].equals(c.optString("lastRev")) || ptr[2].equals(c.optString("dev"))) return null;
            JSONObject remote = new JSONObject(TgNet.download(token, ptr[1]));
            return remote.optJSONArray("debt");
        } catch (Exception e) {
            return null;
        }
    }

    /** Fonda: bot ko'rsatkichi o'zgargan bo'lsa fayl yuklab, boshqa telefondan kelgan yangi operatsiyalarni xabar qiladi. */
    public static synchronized void check(Context ctx) {
        try {
            if (!enabled(ctx)) return;
            JSONObject c = cfg(ctx);
            if (!c.optBoolean("init", false)) return;          // ilovada birinchi sinxron hali bo'lmagan
            String token = c.optString("token");
            String me = c.optString("dev");
            String[] ptr = TgNet.pointer(token);
            if (ptr == null) return;
            SharedPreferences p = DailyCore.prefs(ctx);
            if (ptr[0].equals(c.optString("lastRev")) || ptr[0].equals(p.getString("workerRev", ""))) return;
            if (ptr[2].equals(me)) return;

            JSONObject remote = new JSONObject(TgNet.download(token, ptr[1]));
            JSONArray ops = remote.optJSONArray("ops");
            p.edit().putString("workerRev", ptr[0]).apply();
            if (ops == null) return;

            JSONArray ids = new JSONArray();
            for (int i = 0; i < ops.length(); i++) {
                JSONObject o = ops.optJSONObject(i);
                if (o == null || me.equals(o.optString("dev"))) continue;
                ids.put(o.optString("id"));
            }
            JSONArray fresh = filterUnseen(ctx, ids, false);
            if (fresh.length() == 0) return;

            HashSet<String> freshSet = new HashSet<>();
            for (int i = 0; i < fresh.length(); i++) freshSet.add(fresh.optString(i));
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (int i = 0; i < ops.length(); i++) {
                JSONObject o = ops.optJSONObject(i);
                if (o == null || !freshSet.contains(o.optString("id"))) continue;
                n++;
                if (n <= 12) sb.append("• ").append(o.optString("txt"))
                        .append(o.optString("dn").isEmpty() ? "" : "  (" + o.optString("dn") + ")").append("\n");
            }
            boolean ru = "ru".equals(c.optString("lang"));
            String title = n == 1 ? (ru ? "SIRIUS STORE: новая операция" : "SIRIUS STORE: yangi operatsiya")
                    : (ru ? "SIRIUS STORE: новых операций " + n : "SIRIUS STORE: " + n + " ta yangi operatsiya");
            notifyOps(ctx, title, sb.toString().trim());
        } catch (Exception ignored) { }
    }
}
