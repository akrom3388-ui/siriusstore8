package uz.siriusstore.app;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Telegram Bot API bilan oddiy HTTP (CORS muammosisiz, fonda ham ishlaydi). */
public class TgNet {
    static final String BASE = "https://api.telegram.org/";
    static final String MARK = "SYNC#";

    static HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        return c;
    }

    static String readAll(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    /** JSON POST. Xato bo'lsa Exception(description). */
    static JSONObject api(String token, String method, JSONObject body) throws Exception {
        HttpURLConnection c = open(BASE + "bot" + token + "/" + method, "POST");
        try {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            OutputStream os = c.getOutputStream();
            os.write((body == null ? "{}" : body.toString()).getBytes("UTF-8"));
            os.close();
            String resp = readAll(c);
            JSONObject j = new JSONObject(resp);
            if (!j.optBoolean("ok")) throw new Exception(j.optString("description", "Telegram xatosi"));
            return j;
        } finally {
            c.disconnect();
        }
    }

    static void sendMessage(String token, String chats, String text) {
        if (token == null || token.isEmpty() || chats == null || chats.isEmpty()) return;
        if (text.length() > 3900) text = text.substring(0, 3900) + "\n...";
        for (String chat : chats.split("[,;\\s]+")) {
            if (chat.isEmpty()) continue;
            try {
                JSONObject b = new JSONObject();
                b.put("chat_id", chat);
                b.put("text", text);
                api(token, "sendMessage", b);
            } catch (Exception ignored) { }
        }
    }

    static String botUsername(String token) throws Exception {
        JSONObject j = api(token, "getMe", null);
        return j.getJSONObject("result").optString("username", "");
    }

    /** Bot tavsifidan oxirgi sinxron ko'rsatkichini o'qiydi: {rev, fileId, dev} yoki null. */
    static String[] pointer(String token) throws Exception {
        JSONObject j = api(token, "getMyDescription", null);
        String d = j.getJSONObject("result").optString("description", "");
        int i = d.indexOf(MARK);
        if (i < 0) return null;
        String[] p = d.substring(i + MARK.length()).trim().split("#");
        if (p.length < 2) return null;
        return new String[]{p[0].trim(), p[1].trim(), p.length > 2 ? p[2].trim() : ""};
    }

    static void setPointer(String token, String rev, String fileId, String dev) throws Exception {
        JSONObject b = new JSONObject();
        b.put("description", "SIRIUS STORE" + "\n" + MARK + rev + "#" + fileId + "#" + dev);
        api(token, "setMyDescription", b);
    }

    static String download(String token, String fileId) throws Exception {
        JSONObject b = new JSONObject();
        b.put("file_id", fileId);
        String path = api(token, "getFile", b).getJSONObject("result").getString("file_path");
        HttpURLConnection c = open(BASE + "file/bot" + token + "/" + path, "GET");
        try {
            String s = readAll(c);
            if (c.getResponseCode() >= 400) throw new Exception("Fayl yuklanmadi (" + c.getResponseCode() + ")");
            return s;
        } finally {
            c.disconnect();
        }
    }

    static void field(OutputStream os, String boundary, String name, String value) throws Exception {
        os.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes("UTF-8"));
    }

    /** JSON faylni chatga hujjat sifatida yuboradi, file_id qaytaradi. */
    static String uploadDoc(String token, String chat, String filename, String content, String caption) throws Exception {
        String boundary = "----sirius" + System.currentTimeMillis();
        HttpURLConnection c = open(BASE + "bot" + token + "/sendDocument", "POST");
        try {
            c.setDoOutput(true);
            c.setChunkedStreamingMode(0);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            OutputStream os = c.getOutputStream();
            field(os, boundary, "chat_id", chat);
            field(os, boundary, "disable_notification", "true");
            if (caption != null && !caption.isEmpty()) field(os, boundary, "caption", caption);
            os.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"document\"; filename=\"" + filename
                    + "\"\r\nContent-Type: application/json\r\n\r\n").getBytes("UTF-8"));
            os.write(content.getBytes("UTF-8"));
            os.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
            os.close();
            JSONObject j = new JSONObject(readAll(c));
            if (!j.optBoolean("ok")) throw new Exception(j.optString("description", "Yuklab bo'lmadi"));
            return j.getJSONObject("result").getJSONObject("document").getString("file_id");
        } finally {
            c.disconnect();
        }
    }
}
