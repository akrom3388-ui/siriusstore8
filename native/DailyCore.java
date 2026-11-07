package uz.siriusstore.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DailyCore {
    public static final String PREFS = "sirius_native";
    public static final String ACTION = "uz.siriusstore.app.DAILY";
    static final String PERM_ERR = "SMS ruxsati yo'q";

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean canExact(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static boolean ru(JSONObject s) { return "ru".equals(s.optString("lang")); }

    static int smallIcon(Context ctx) {
        int id = ctx.getResources().getIdentifier("ic_stat_sirius", "drawable", ctx.getPackageName());
        return id != 0 ? id : android.R.drawable.ic_dialog_info;
    }

    /** Keyingi eslatma vaqtiga (sozlangan soat:daqiqa) signal qo'yadi + har soatda "o'tkazib yuborilmaganmi" zaxira vazifa. */
    public static void schedule(Context ctx) {
        int hour = 9, minute = 0;
        try {
            JSONObject s = new JSONObject(prefs(ctx).getString("snapshot", "{}"));
            hour = clamp(s.optInt("hour", 9), 0, 23);
            minute = clamp(s.optInt("minute", 0), 0, 59);
        } catch (Exception ignored) { }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, DailyReceiver.class);
        i.setAction(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 9001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long t = cal.getTimeInMillis();
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }

        PeriodicWorkRequest w = new PeriodicWorkRequest.Builder(DailyWorker.class, 1, TimeUnit.HOURS).build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("sirius_catchup", ExistingPeriodicWorkPolicy.KEEP, w);
    }

    static String normalizePhone(String raw) {
        if (raw == null) return "";
        String d = raw.replaceAll("[^0-9]", "");
        if (d.length() == 9) return "+998" + d;
        if (d.length() < 9) return "";
        return "+" + d;
    }

    static String money(double v) {
        return String.format(Locale.US, "%,d", (long) v).replace(',', ' ');
    }

    static String smsErrorText(int code) {
        switch (code) {
            case 1: return "umumiy xato (SIM/hisobni tekshiring)";
            case 2: return "aviarejim yoki radio o'chiq";
            case 3: return "xabar formati xato";
            case 4: return "tarmoq yo'q";
            default: return "xato kodi " + code;
        }
    }

    /**
     * SMS ni telefon SIM-kartasidan yuboradi va natijani kutadi (fon oqimida chaqiring, asosiy oqimda emas).
     * Muvaffaqiyatli bo'lsa "" qaytaradi, aks holda xato matni.
     */
    public static String sendSmsChecked(Context ctx, String rawPhone, String text) {
        Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        BroadcastReceiver rc = null;
        try {
            String phone = normalizePhone(rawPhone);
            if (phone.isEmpty()) return "raqam noto'g'ri";
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
                return PERM_ERR;
            if (text == null || text.isEmpty()) return "matn bo'sh";

            SmsManager sm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? app.getSystemService(SmsManager.class) : SmsManager.getDefault();
            ArrayList<String> parts = sm.divideMessage(text);
            final int n = parts.size();
            final String action = "uz.siriusstore.app.SMS_SENT_" + System.nanoTime();
            final CountDownLatch latch = new CountDownLatch(n);
            final AtomicReference<String> err = new AtomicReference<String>("");
            rc = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    int code = getResultCode();
                    if (code != Activity.RESULT_OK) err.compareAndSet("", smsErrorText(code));
                    latch.countDown();
                }
            };
            ContextCompat.registerReceiver(app, rc, new IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED);

            ArrayList<PendingIntent> sents = new ArrayList<PendingIntent>();
            for (int i = 0; i < n; i++) {
                Intent in = new Intent(action);
                in.setPackage(app.getPackageName());
                sents.add(PendingIntent.getBroadcast(app, i, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
            if (n > 1) sm.sendMultipartTextMessage(phone, null, parts, sents, null);
            else sm.sendTextMessage(phone, null, text, sents.get(0), null);

            // Natija kelmasa (ba'zi telefonlarda signal kelmaydi) — yuborilgan deb hisoblaymiz
            latch.await(25, TimeUnit.SECONDS);
            return err.get();
        } catch (SecurityException e) {
            return PERM_ERR;
        } catch (Exception e) {
            return String.valueOf(e.getMessage());
        } finally {
            if (rc != null) {
                try { app.unregisterReceiver(rc); } catch (Exception ignored) { }
            }
        }
    }

    static void notifyUser(Context ctx, int id, String title, String shortText, String bigText) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(new NotificationChannel("sirius_overdue", "Muddati o'tgan qarzdorlar",
                        NotificationManager.IMPORTANCE_HIGH));
            }
            Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            PendingIntent pi = open == null ? null : PendingIntent.getActivity(ctx, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "sirius_overdue")
                    .setSmallIcon(smallIcon(ctx))
                    .setContentTitle(title)
                    .setContentText(shortText)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);
            if (pi != null) b.setContentIntent(pi);
            nm.notify(id, b.build());
        } catch (Exception ignored) { }
    }

    /** Ilova ichidan to'g'ridan-to'g'ri bildirishnoma (ixtiyoriy matn). */
    public static void notifyPlain(Context ctx, String title, String body) {
        SyncCore.notifyOps(ctx, title, body);
    }

    static String defaultTpl(JSONObject s) {
        String store = s.optString("store", "SIRIUS STORE");
        String phone = s.optString("phone", "");
        if (ru(s)) {
            return "Уважаемый(ая) {name}! В магазине " + store + " за вами числится долг {amount} сум. Просим погасить его вовремя."
                    + (phone.isEmpty() ? "" : " Тел: " + phone);
        }
        return "Hurmatli {name}! " + store + " do'konida sizning {amount} so'm qarzingiz bor. Iltimos, to'lovni o'z vaqtida amalga oshiring."
                + (phone.isEmpty() ? "" : " Murojaat: " + phone);
    }

    static String ymd(Date d) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d); }

    /**
     * Kunlik tekshiruv.
     * force=false: faqat sozlangan vaqtdan keyingi 12 soat ichida va bugun hali bajarilmagan bo'lsa.
     * force=true: darhol (sinov uchun).
     */
    public static synchronized String run(Context ctx, boolean force) {
        try {
            SharedPreferences p = prefs(ctx);
            String snap = p.getString("snapshot", null);
            if (snap == null) return "Ma'lumot hali saqlanmagan (ilovani bir marta oching)";
            JSONObject s = new JSONObject(snap);
            String today = ymd(new Date());
            boolean smsEnabled = s.optBoolean("enabled", true);

            if (!force) {
                int startMin = clamp(s.optInt("hour", 9), 0, 23) * 60 + clamp(s.optInt("minute", 0), 0, 59);
                int endMin = Math.min(1439, startMin + 720);
                Calendar now = Calendar.getInstance();
                int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
                if (nowMin < startMin || nowMin > endMin) return "hozir vaqt emas";
                if (today.equals(p.getString("lastRunDate", ""))) return "bugun allaqachon bajarilgan";
            }

            boolean ruL = ru(s);
            String tpl = s.optString("tpl", "");
            if (tpl.isEmpty()) tpl = defaultTpl(s);
            String store = s.optString("store", "SIRIUS STORE");
            JSONArray clients = s.optJSONArray("clients");
            if (clients == null) clients = new JSONArray();
            // Boshqa telefon yangi ma'lumot kiritgan bo'lsa (ilova ochilmagan), botdagi eng yangi qarzdorlar ro'yxatidan foydalanamiz
            try {
                JSONArray rv = SyncCore.remoteDebtView(ctx);
                if (rv != null) clients = rv;
            } catch (Exception ignored) { }

            // 1) Kim muddati o'tgan va hali to'lamagan
            HashSet<String> currentIds = new HashSet<String>();
            ArrayList<JSONObject> overdue = new ArrayList<JSONObject>();
            double totalOverdue = 0;
            for (int i = 0; i < clients.length(); i++) {
                JSONObject c = clients.getJSONObject(i);
                if (c.optDouble("bal", 0) <= 0) continue;
                currentIds.add(c.optString("id"));
                double over = c.optDouble("over", 0);
                JSONArray its = c.optJSONArray("items");
                if (its != null) {              // har kuni qayta hisoblanadi (muddatlar bo'yicha)
                    over = 0;
                    for (int j = 0; j < its.length(); j++) {
                        JSONObject it = its.optJSONObject(j);
                        if (it == null) continue;
                        String d = it.optString("d", "");
                        if (d.length() > 0 && d.compareTo(today) < 0) over += it.optDouble("a", 0);
                    }
                    c.put("over", over);
                }
                if (over > 0) { overdue.add(c); totalOverdue += over; }
            }

            // 2) Kecha muddati o'tgan edi, bugun ro'yxatda yo'q => to'lab bo'lgan
            StringBuilder paid = new StringBuilder();
            JSONArray last = new JSONArray(p.getString("lastOverdue", "[]"));
            for (int i = 0; i < last.length(); i++) {
                JSONObject l = last.getJSONObject(i);
                if (!currentIds.contains(l.optString("id"))) paid.append("• ").append(l.optString("name")).append("\n");
            }

            // 3) Bildirishnoma: muddati o'tgan qarzdorlar ro'yxati (har kuni ertalab)
            if (overdue.size() > 0) {
                StringBuilder nb = new StringBuilder();
                for (int i = 0; i < overdue.size() && i < 25; i++) {
                    JSONObject c = overdue.get(i);
                    nb.append("• ").append(c.optString("name")).append(" — ").append(money(c.optDouble("over", 0))).append(" UZS\n");
                }
                if (overdue.size() > 25) nb.append("... +").append(overdue.size() - 25).append("\n");
                String t = ruL ? "Просроченные должники: " + overdue.size() : "Muddati o'tgan qarzdorlar: " + overdue.size() + " ta";
                String sh = (ruL ? "Итого: " : "Jami: ") + money(totalOverdue) + " UZS";
                notifyUser(ctx, 7001, t, sh, nb.toString().trim());
            }

            // 4) SMS yuborish (avto-eslatma yoqilgan bo'lsa yoki qo'lda sinov)
            boolean doSms = smsEnabled || force;
            int sent = 0, failed = 0;
            StringBuilder lines = new StringBuilder();
            StringBuilder smsNames = new StringBuilder();
            JSONArray pending = new JSONArray(p.getString("pending", "[]"));
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            String nowStr = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date());

            if (doSms) {
                for (JSONObject c : overdue) {
                    String name = c.optString("name");
                    double bal = c.optDouble("bal", 0);
                    String text = tpl.replace("{name}", name).replace("{amount}", money(bal));
                    String err = sendSmsChecked(ctx, c.optString("phone"), text);
                    boolean ok = err.isEmpty();
                    if (ok) sent++; else failed++;
                    lines.append(ok ? "✅ " : "❌ ").append(name).append(" (").append(c.optString("phone"))
                            .append(") — ").append(money(bal)).append(" UZS").append(ok ? "" : " [" + err + "]").append("\n");
                    smsNames.append(ok ? "✅ " : "❌ ").append(name).append(ok ? "" : " (" + err + ")").append("\n");

                    JSONObject log = new JSONObject();
                    log.put("id", c.optString("id"));
                    log.put("name", name);
                    log.put("phone", c.optString("phone"));
                    log.put("text", text);
                    log.put("date", nowStr);
                    log.put("ok", ok);
                    log.put("iso", iso.format(new Date()));
                    pending.put(log);
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
                }
                if (overdue.size() > 0) {
                    String t = ruL ? "СМС отправлено: " + sent + (failed > 0 ? ", ошибок: " + failed : "")
                            : "SMS yuborildi: " + sent + " ta" + (failed > 0 ? ", xato: " + failed + " ta" : "");
                    notifyUser(ctx, 7002, t, ruL ? "Кому отправлено — в уведомлении" : "Kimlarga ketgani — batafsil",
                            smsNames.toString().trim());
                }
            }

            if (pending.length() > 500) {
                JSONArray trimmed = new JSONArray();
                for (int i = pending.length() - 500; i < pending.length(); i++) trimmed.put(pending.get(i));
                pending = trimmed;
            }
            JSONArray nowOver = new JSONArray();
            for (JSONObject c : overdue) {
                JSONObject o = new JSONObject();
                o.put("id", c.optString("id"));
                o.put("name", c.optString("name"));
                nowOver.put(o);
            }
            p.edit().putString("pending", pending.toString())
                    .putString("lastOverdue", nowOver.toString())
                    .putString("lastRunDate", today).apply();

            // 5) Egasiga Telegram hisobot
            if (force || overdue.size() > 0 || paid.length() > 0) {
                StringBuilder r = new StringBuilder();
                r.append("⏰ ").append(store).append(ruL ? " — ежедневная проверка (" : " — kunlik tekshiruv (").append(nowStr).append(")\n\n");
                r.append(ruL ? "Должников всего: " : "Jami qarzdor: ").append(clients.length()).append("\n");
                r.append(ruL ? "Просрочено: " : "Muddati o'tgan: ").append(overdue.size()).append(" (").append(money(totalOverdue)).append(" UZS)\n");
                if (doSms) {
                    r.append(ruL ? "СМС отправлено: " : "SMS yuborildi: ").append(sent).append(" ✅   ")
                            .append(ruL ? "Не отправлено: " : "Yuborilmadi: ").append(failed).append(" ❌\n");
                } else {
                    r.append(ruL ? "Авто-СМС выключено\n" : "Avto-SMS o'chirilgan\n");
                }
                if (lines.length() > 0) r.append("\n").append(lines);
                if (paid.length() > 0) r.append(ruL ? "\nУже оплатили ✅:\n" : "\nTo'lab bo'lganlar ✅:\n").append(paid);
                if (doSms && failed > 0 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
                    r.append(ruL ? "\n⚠️ Нет разрешения на СМС!" : "\n⚠️ SMS ruxsati berilmagan! Telefon sozlamalaridan yoqing.");
                TgNet.sendMessage(s.optString("tgToken"), s.optString("tgChat"), r.toString());
            }

            return (ruL ? "Просрочено: " : "Muddati o'tgan: ") + overdue.size()
                    + (doSms ? (ruL ? ", СМС: " : ", SMS yuborildi: ") + sent + (ruL ? ", ошибок: " : ", yuborilmadi: ") + failed
                    : (ruL ? " (авто-СМС выключено)" : " (avto-SMS o'chirilgan)"))
                    + (paid.length() > 0 ? (ruL ? ", есть оплатившие" : ", to'lab bo'lganlar bor") : "");
        } catch (Exception e) {
            return "Xato: " + e;
        }
    }
}
