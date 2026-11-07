package uz.siriusstore.app;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.Executor;

@CapacitorPlugin(
        name = "Sirius",
        permissions = {
                @Permission(alias = "sms", strings = { Manifest.permission.SEND_SMS }),
                @Permission(alias = "call", strings = { Manifest.permission.CALL_PHONE })
        }
)
public class SiriusPlugin extends Plugin {

    private void bg(Runnable r) { new Thread(r).start(); }

    private static String msg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.toString() : m;
    }

    private void startAct(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
    }

    private JSObject statusObj() {
        Context ctx = getContext();
        JSObject o = new JSObject();
        o.put("sms", ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED);
        o.put("call", ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED);
        o.put("exact", DailyCore.canExact(ctx));
        boolean bat = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            bat = pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        }
        o.put("battery", bat);
        o.put("lastRunDate", DailyCore.prefs(ctx).getString("lastRunDate", ""));
        return o;
    }

    // ---------------------------------------------------------------- ma'lumot / sozlash

    @PluginMethod
    public void saveSnapshot(PluginCall call) {
        String j = call.getString("json");
        if (j == null) { call.reject("json yo'q"); return; }
        DailyCore.prefs(getContext()).edit().putString("snapshot", j).apply();
        try { DailyCore.schedule(getContext()); } catch (Exception ignored) { }
        call.resolve();
    }

    @PluginMethod
    public void getPendingLogs(PluginCall call) {
        SharedPreferences p = DailyCore.prefs(getContext());
        synchronized (DailyCore.class) {
            String v = p.getString("pending", "[]");
            p.edit().putString("pending", "[]").apply();
            JSObject r = new JSObject();
            r.put("json", v);
            call.resolve(r);
        }
    }

    @PluginMethod
    public void setup(PluginCall call) {
        if (getPermissionState("sms") != PermissionState.GRANTED) {
            requestPermissionForAlias("sms", call, "smsCb");
        } else {
            finishSetup(call);
        }
    }

    @PermissionCallback
    private void smsCb(PluginCall call) {
        finishSetup(call);
    }

    private void finishSetup(PluginCall call) {
        DailyCore.schedule(getContext());
        SyncCore.schedule(getContext());
        askNotifPermission();
        call.resolve(statusObj());
    }

    private void askNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(getContext(), "android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            try {
                ActivityCompat.requestPermissions(getActivity(), new String[]{"android.permission.POST_NOTIFICATIONS"}, 4242);
            } catch (Exception ignored) { }
        }
    }

    @PluginMethod
    public void status(PluginCall call) {
        call.resolve(statusObj());
    }

    @PluginMethod
    public void openExactAlarmSettings(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startAct(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getContext().getPackageName())));
            }
            call.resolve();
        } catch (Exception e) { call.reject(msg(e)); }
    }

    @PluginMethod
    public void requestIgnoreBattery(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startAct(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getContext().getPackageName())));
            }
            call.resolve();
        } catch (Exception e) { call.reject(msg(e)); }
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        try {
            startAct(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getContext().getPackageName())));
            call.resolve();
        } catch (Exception e) { call.reject(msg(e)); }
    }

    @PluginMethod
    public void showNotification(PluginCall call) {
        DailyCore.notifyPlain(getContext(), call.getString("title", "SIRIUS STORE"), call.getString("body", ""));
        call.resolve();
    }

    // ---------------------------------------------------------------- SMS

    @PluginMethod
    public void sendSms(final PluginCall call) {
        if (getPermissionState("sms") != PermissionState.GRANTED) {
            requestPermissionForAlias("sms", call, "singleCb");
        } else {
            runSingle(call);
        }
    }

    @PermissionCallback
    private void singleCb(PluginCall call) { runSingle(call); }

    private void runSingle(final PluginCall call) {
        final String phone = call.getString("phone");
        final String text = call.getString("text", "");
        bg(new Runnable() {
            @Override
            public void run() {
                String err = DailyCore.sendSmsChecked(getContext(), phone, text);
                JSObject r = new JSObject();
                r.put("ok", err.isEmpty());
                r.put("error", err);
                call.resolve(r);
            }
        });
    }

    /** items: [{id, phone, text}] — hammasiga ketma-ket, SIM-kartadan, ilovadan chiqmasdan. */
    @PluginMethod
    public void sendSmsBatch(PluginCall call) {
        if (getPermissionState("sms") != PermissionState.GRANTED) {
            requestPermissionForAlias("sms", call, "batchCb");
        } else {
            runBatch(call);
        }
    }

    @PermissionCallback
    private void batchCb(PluginCall call) { runBatch(call); }

    private void runBatch(final PluginCall call) {
        final JSArray items = call.getArray("items");
        final long pause = call.getLong("pauseMs", 1200L);
        bg(new Runnable() {
            @Override
            public void run() {
                JSArray results = new JSArray();
                int ok = 0, fail = 0;
                String permErr = "";
                try {
                    int n = items == null ? 0 : items.length();
                    for (int i = 0; i < n; i++) {
                        JSONObject it = items.getJSONObject(i);
                        String err = DailyCore.sendSmsChecked(getContext(), it.optString("phone"), it.optString("text"));
                        JSObject r = new JSObject();
                        r.put("id", it.optString("id"));
                        r.put("ok", err.isEmpty());
                        r.put("error", err);
                        results.put(r);
                        if (err.isEmpty()) ok++; else fail++;
                        if (DailyCore.PERM_ERR.equals(err)) { permErr = err; }
                        if (i < n - 1) Thread.sleep(pause);
                    }
                } catch (Exception e) {
                    permErr = msg(e);
                }
                JSObject out = new JSObject();
                out.put("results", results);
                out.put("ok", ok);
                out.put("failed", fail);
                out.put("error", permErr);
                call.resolve(out);
            }
        });
    }

    @PluginMethod
    public void runNow(final PluginCall call) {
        if (getPermissionState("sms") != PermissionState.GRANTED) {
            requestPermissionForAlias("sms", call, "runCb");
        } else {
            doRun(call);
        }
    }

    @PermissionCallback
    private void runCb(PluginCall call) { doRun(call); }

    private void doRun(final PluginCall call) {
        bg(new Runnable() {
            @Override
            public void run() {
                String res = DailyCore.run(getContext(), true);
                JSObject r = new JSObject();
                r.put("result", res);
                call.resolve(r);
            }
        });
    }

    // ---------------------------------------------------------------- qo'ng'iroq / havolalar

    @PluginMethod
    public void callNumber(PluginCall call) {
        if (getPermissionState("call") != PermissionState.GRANTED) {
            requestPermissionForAlias("call", call, "callCb");
        } else {
            doCall(call, true);
        }
    }

    @PermissionCallback
    private void callCb(PluginCall call) {
        doCall(call, getPermissionState("call") == PermissionState.GRANTED);
    }

    private void doCall(PluginCall call, boolean direct) {
        String phone = DailyCore.normalizePhone(call.getString("phone"));
        if (phone.isEmpty()) { call.reject("Telefon raqami noto'g'ri"); return; }
        try {
            Intent i = new Intent(direct ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null));
            startAct(i);
            JSObject r = new JSObject();
            r.put("mode", direct ? "call" : "dial");
            call.resolve(r);
        } catch (Exception e) {
            call.reject(msg(e));
        }
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url", "");
        String fallback = call.getString("fallback", "");
        try {
            startAct(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            call.resolve();
        } catch (ActivityNotFoundException e) {
            if (!fallback.isEmpty()) {
                try {
                    startAct(new Intent(Intent.ACTION_VIEW, Uri.parse(fallback)));
                    call.resolve();
                    return;
                } catch (Exception e2) { call.reject(msg(e2)); return; }
            }
            call.reject("Ilova topilmadi");
        } catch (Exception e) {
            call.reject(msg(e));
        }
    }

    @PluginMethod
    public void printPage(final PluginCall call) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    PrintManager pm = (PrintManager) getContext().getSystemService(Context.PRINT_SERVICE);
                    WebView wv = getBridge().getWebView();
                    PrintDocumentAdapter a = wv.createPrintDocumentAdapter("SIRIUS_STORE");
                    pm.print("SIRIUS STORE", a, new PrintAttributes.Builder().build());
                    call.resolve();
                } catch (Exception e) { call.reject(msg(e)); }
            }
        });
    }

    // ---------------------------------------------------------------- ovoz (TTS)

    private interface TtsJob { void run(TextToSpeech t, boolean ok); }

    private void ttsInit(final TtsJob job) {
        final TextToSpeech[] h = new TextToSpeech[1];
        h[0] = new TextToSpeech(getContext().getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                job.run(h[0], status == TextToSpeech.SUCCESS);
            }
        });
    }

    /** O'zbekcha ovoz bo'lsa "uz", aks holda ruscha "ru", ikkalasi ham yo'q bo'lsa "". */
    private String pickLang(TextToSpeech t, String pref) {
        if ("uz".equals(pref) && t.isLanguageAvailable(new Locale("uz")) >= TextToSpeech.LANG_AVAILABLE) {
            t.setLanguage(new Locale("uz"));
            return "uz";
        }
        if (t.isLanguageAvailable(new Locale("ru")) >= TextToSpeech.LANG_AVAILABLE) {
            t.setLanguage(new Locale("ru"));
            return "ru";
        }
        return "";
    }

    @PluginMethod
    public void speak(final PluginCall call) {
        final String uz = call.getString("textUz", "");
        final String ru = call.getString("textRu", uz);
        final String pref = call.getString("pref", "uz");
        ttsInit(new TtsJob() {
            @Override
            public void run(final TextToSpeech t, boolean ok) {
                if (!ok || t == null) { call.reject("Ovoz dvigateli (TTS) topilmadi"); return; }
                String lang = pickLang(t, pref);
                if (lang.isEmpty()) { t.shutdown(); call.reject("Telefonda ovoz tili yo'q (Google TTS o'rnating)"); return; }
                t.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { t.shutdown(); }
                    @Override public void onError(String id) { t.shutdown(); }
                });
                t.speak("uz".equals(lang) ? uz : ru, TextToSpeech.QUEUE_FLUSH, null, "sirius");
                JSObject r = new JSObject();
                r.put("lang", lang);
                call.resolve(r);
            }
        });
    }

    /** Matnni ovozli faylga aylantirib, WhatsApp/Telegram (yoki tanlov oynasi) orqali yuboradi. */
    @PluginMethod
    public void shareVoice(final PluginCall call) {
        final String uz = call.getString("textUz", "");
        final String ru = call.getString("textRu", uz);
        final String pref = call.getString("pref", "uz");
        final String pkg = call.getString("pkg", "");
        final String phone = DailyCore.normalizePhone(call.getString("phone", "")).replace("+", "");
        ttsInit(new TtsJob() {
            @Override
            public void run(final TextToSpeech t, boolean ok) {
                if (!ok || t == null) { call.reject("Ovoz dvigateli (TTS) topilmadi"); return; }
                final String lang = pickLang(t, pref);
                if (lang.isEmpty()) { t.shutdown(); call.reject("Telefonda ovoz tili yo'q (Google TTS o'rnating)"); return; }
                final File f = new File(getContext().getCacheDir(), "ovozli_xabar_" + System.currentTimeMillis() + ".wav");
                t.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) {
                        t.shutdown();
                        shareFile(call, f, pkg, phone, lang);
                    }
                    @Override public void onError(String id) {
                        t.shutdown();
                        call.reject("Ovoz faylini yaratib bo'lmadi");
                    }
                });
                int rc = t.synthesizeToFile("uz".equals(lang) ? uz : ru, null, f, "sirius");
                if (rc != TextToSpeech.SUCCESS) { t.shutdown(); call.reject("Ovoz faylini yaratib bo'lmadi"); }
            }
        });
    }

    private void shareFile(final PluginCall call, File f, String pkg, String phone, String lang) {
        try {
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".siriusfp", f);
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("audio/*");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            boolean direct = !pkg.isEmpty();
            if (direct) {
                it.setPackage(pkg);
                if (pkg.startsWith("com.whatsapp") && !phone.isEmpty()) it.putExtra("jid", phone + "@s.whatsapp.net");
            }
            Intent launch = direct ? it : Intent.createChooser(it, "Ovozli xabar");
            try {
                startAct(launch);
            } catch (ActivityNotFoundException e) {
                Intent it2 = new Intent(Intent.ACTION_SEND);
                it2.setType("audio/*");
                it2.putExtra(Intent.EXTRA_STREAM, uri);
                it2.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startAct(Intent.createChooser(it2, "Ovozli xabar"));
            }
            JSObject r = new JSObject();
            r.put("lang", lang);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(msg(e));
        }
    }

    // ---------------------------------------------------------------- fayl ulashish (PDF, rasm, Excel, zaxira)
    @PluginMethod
    public void shareData(final PluginCall call) {
        final String name = call.getString("name", "fayl");
        final String b64 = call.getString("b64", "");
        final String mime = call.getString("mime", "application/octet-stream");
        final String pkg = call.getString("pkg", "");
        final String text = call.getString("text", "");
        final String title = call.getString("title", "SIRIUS STORE");
        try {
            byte[] data = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            File f = new File(getContext().getCacheDir(), name.replaceAll("[^A-Za-z0-9._-]", "_"));
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(data);
            fo.close();
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".siriusfp", f);
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType(mime);
            it.putExtra(Intent.EXTRA_STREAM, uri);
            if (!text.isEmpty()) it.putExtra(Intent.EXTRA_TEXT, text);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!pkg.isEmpty()) it.setPackage(pkg);
            Intent launch = pkg.isEmpty() ? Intent.createChooser(it, title) : it;
            startAct(launch);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("Ilova topilmadi");
        } catch (Exception e) {
            call.reject(msg(e));
        }
    }

    // ---------------------------------------------------------------- barmoq izi

    @PluginMethod
    public void biometricAvailable(PluginCall call) {
        JSObject r = new JSObject();
        try {
            int rc = BiometricManager.from(getContext()).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
            r.put("available", rc == BiometricManager.BIOMETRIC_SUCCESS);
        } catch (Exception e) {
            r.put("available", false);
        }
        call.resolve(r);
    }

    @PluginMethod
    public void biometricAuth(final PluginCall call) {
        final String title = call.getString("title", "SIRIUS STORE");
        final String sub = call.getString("subtitle", "");
        final String neg = call.getString("negative", "PIN");
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Executor ex = ContextCompat.getMainExecutor(getContext());
                    BiometricPrompt bp = new BiometricPrompt((FragmentActivity) getActivity(), ex,
                            new BiometricPrompt.AuthenticationCallback() {
                                @Override
                                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                                    JSObject o = new JSObject();
                                    o.put("ok", true);
                                    call.resolve(o);
                                }

                                @Override
                                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                                    call.reject(String.valueOf(errString));
                                }
                            });
                    BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                            .setTitle(title)
                            .setSubtitle(sub)
                            .setNegativeButtonText(neg)
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                                    | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                            .build();
                    bp.authenticate(info);
                } catch (Exception e) {
                    call.reject(msg(e));
                }
            }
        });
    }

    // ---------------------------------------------------------------- telefonlar o'rtasida sinxronlash (Telegram bot orqali)

    @PluginMethod
    public void syncConfig(PluginCall call) {
        String j = call.getString("json", "{}");
        DailyCore.prefs(getContext()).edit().putString("syncCfg", j).apply();
        SyncCore.schedule(getContext());
        call.resolve();
    }

    @PluginMethod
    public void tgMe(final PluginCall call) {
        final String token = call.getString("token", "");
        bg(new Runnable() {
            @Override
            public void run() {
                try {
                    JSObject r = new JSObject();
                    r.put("username", TgNet.botUsername(token));
                    call.resolve(r);
                } catch (Exception e) { call.reject(msg(e)); }
            }
        });
    }

    @PluginMethod
    public void syncPointer(final PluginCall call) {
        final String token = call.getString("token", "");
        bg(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] p = TgNet.pointer(token);
                    JSObject r = new JSObject();
                    if (p != null) {
                        r.put("rev", p[0]);
                        r.put("fileId", p[1]);
                        r.put("dev", p[2]);
                    }
                    call.resolve(r);
                } catch (Exception e) { call.reject(msg(e)); }
            }
        });
    }

    @PluginMethod
    public void syncDownload(final PluginCall call) {
        final String token = call.getString("token", "");
        final String fileId = call.getString("fileId", "");
        bg(new Runnable() {
            @Override
            public void run() {
                try {
                    JSObject r = new JSObject();
                    r.put("json", TgNet.download(token, fileId));
                    call.resolve(r);
                } catch (Exception e) { call.reject(msg(e)); }
            }
        });
    }

    @PluginMethod
    public void syncPush(final PluginCall call) {
        final String token = call.getString("token", "");
        final String chat = call.getString("chat", "");
        final String json = call.getString("json", "{}");
        final String rev = call.getString("rev", "");
        final String dev = call.getString("dev", "");
        final String caption = call.getString("caption", "");
        bg(new Runnable() {
            @Override
            public void run() {
                try {
                    String fileId = TgNet.uploadDoc(token, chat, "SIRIUS_SYNC.json", json, caption);
                    TgNet.setPointer(token, rev, fileId, dev);
                    JSObject r = new JSObject();
                    r.put("fileId", fileId);
                    call.resolve(r);
                } catch (Exception e) { call.reject(msg(e)); }
            }
        });
    }

    @PluginMethod
    public void filterUnseenOps(PluginCall call) {
        JSArray ids = call.getArray("ids");
        boolean silent = call.getBoolean("silent", false);
        JSObject r = new JSObject();
        r.put("ids", SyncCore.filterUnseen(getContext(), ids == null ? new JSArray() : ids, silent));
        call.resolve(r);
    }

    // ---------------------------------------------------------------- fayl saqlash

    @PluginMethod
    public void saveFile(PluginCall call) {
        String name = call.getString("name", "fayl.txt");
        String content = call.getString("content", "");
        String mime = call.getString("mime", "application/octet-stream");
        try {
            String b64 = call.getString("b64", "");
            byte[] data = b64.isEmpty() ? content.getBytes("UTF-8") : android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            String where;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                OutputStream os = getContext().getContentResolver().openOutputStream(uri);
                os.write(data);
                os.close();
                where = "Yuklashlar (Downloads) / " + name;
            } else {
                File f = new File(getContext().getExternalFilesDir(null), name);
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(data);
                fo.close();
                where = f.getAbsolutePath();
            }
            JSObject r = new JSObject();
            r.put("path", where);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(msg(e));
        }
    }
}
