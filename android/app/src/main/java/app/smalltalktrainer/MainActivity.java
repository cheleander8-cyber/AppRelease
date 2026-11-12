package app.smalltalktrainer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String START_URL = "file:///android_asset/index.html";

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private final Handler main = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int bg = dark ? Color.parseColor("#141a1c") : Color.parseColor("#f6f1e9");
        int bar = dark ? Color.parseColor("#1d2528") : Color.parseColor("#fffdf9");

        web = new WebView(this);
        web.setBackgroundColor(bg);
        setContentView(web);
        setupSystemBars(bg, bar, dark);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setAllowFileAccess(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                    return true;
                }
                return false;
            }
        });

        web.addJavascriptInterface(new TtsBridge(), "AndroidTTS");
        initTts();

        web.loadUrl(START_URL);
    }

    /** Inhalt nicht unter Status- und Navigationsleiste legen (Android 15 erzwingt Edge-to-Edge). */
    private void setupSystemBars(int bg, int bar, boolean dark) {
        Window w = getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bar);
        View decor = w.getDecorView();
        if (!dark) {
            int flags = decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(flags);
        }
        decor.setBackgroundColor(bg);
        if (Build.VERSION.SDK_INT >= 30) {
            web.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            });
        }
    }

    private void initTts() {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS) return;
            ttsReady = true;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { js("start", id); }
                @Override public void onDone(String id) { js("end", id); }
                @Override public void onError(String id) { js("error", id); }
                @Override public void onError(String id, int code) { js("error", id); }
                @Override public void onStop(String id, boolean interrupted) { js("error", id); }
            });
            main.post(() -> web.evaluateJavascript(
                    "window.__ttsVoicesChanged&&window.__ttsVoicesChanged()", null));
        });
    }

    private void js(String type, String id) {
        if (id == null) return;
        final String code = "window.__ttsEvent&&window.__ttsEvent(" + JSONObject.quote(id) + ","
                + JSONObject.quote(type) + ")";
        main.post(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    /** Wird aus JavaScript als window.AndroidTTS aufgerufen (läuft auf einem Binder-Thread). */
    private class TtsBridge {
        @JavascriptInterface
        public String voices() {
            JSONArray arr = new JSONArray();
            if (!ttsReady) return arr.toString();
            try {
                Set<Voice> vs = tts.getVoices();
                if (vs == null) return arr.toString();
                for (Voice v : vs) {
                    Set<String> f = v.getFeatures();
                    if (f != null && f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                    JSONObject o = new JSONObject();
                    o.put("name", v.getName());
                    o.put("lang", v.getLocale().toLanguageTag());
                    o.put("net", v.isNetworkConnectionRequired());
                    arr.put(o);
                }
            } catch (Exception ignored) { }
            return arr.toString();
        }

        @JavascriptInterface
        public void speak(String id, String text, String lang, String voiceName, double rate, double pitch) {
            if (!ttsReady) { js("error", id); return; }
            try {
                Voice chosen = null;
                if (voiceName != null && !voiceName.isEmpty()) {
                    Set<Voice> vs = tts.getVoices();
                    if (vs != null) for (Voice v : vs) if (voiceName.equals(v.getName())) { chosen = v; break; }
                }
                if (chosen != null) tts.setVoice(chosen);
                else if (lang != null && !lang.isEmpty()) tts.setLanguage(Locale.forLanguageTag(lang));
                tts.setSpeechRate((float) rate);
                tts.setPitch((float) pitch);
                Bundle p = new Bundle();
                if (tts.speak(text, TextToSpeech.QUEUE_ADD, p, id) != TextToSpeech.SUCCESS) js("error", id);
            } catch (Exception e) {
                js("error", id);
            }
        }

        @JavascriptInterface
        public void stop() { if (ttsReady) tts.stop(); }

        @JavascriptInterface
        public boolean isSpeaking() { return ttsReady && tts.isSpeaking(); }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__appBack?window.__appBack():false", r -> {
            if (!"true".equals(r)) finish();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ttsReady) tts.stop();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
