package uz.siriusstore.app;

import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SiriusPlugin.class);
        super.onCreate(savedInstanceState);

        // Telefonning "Orqaga" tugmasi / imo-ishorasi: avval ilova ichida (modal, sahifa, tab) orqaga qaytadi,
        // bosh sahifada esa ilovadan chiqadi.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView wv = null;
                try { if (getBridge() != null) wv = getBridge().getWebView(); } catch (Exception ignored) { }
                if (wv == null) { finish(); return; }
                wv.evaluateJavascript(
                        "(function(){try{return window.__sBack?window.__sBack():false}catch(e){return false}})()",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String v) {
                                if (!"true".equals(v)) finish();
                            }
                        });
            }
        });
    }
}
