package com.omkar.breathe;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String FILE_NAME = "breath_holds.csv";
    private static final String REL_PATH = Environment.DIRECTORY_DOCUMENTS + "/Breathe/";
    private static final String HEADER =
            "timestamp,session_start,round,hold_seconds,breaths_done,breaths_target,in_seconds,out_seconds,"
            + "recovery_hold_seconds,ring_enabled,settings_changed_mid_round,app_version,voice_enabled\n";

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private final Object fileLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0B1320"));
        getWindow().setNavigationBarColor(Color.parseColor("#0B1320"));

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.setSpeechRate(0.95f);
                ttsReady = true;
            }
        });

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0B1320"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidTTS");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    // ------------------------------------------------------------------
    // CSV file in the shared Documents/Breathe folder (visible in Files app).
    // Falls back to the app's own folder if the shared folder can't be used.
    // ------------------------------------------------------------------

    private Uri findSharedCsv() {
        ContentResolver cr = getContentResolver();
        Uri coll = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String sel = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND "
                + MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        try (Cursor c = cr.query(coll, new String[]{MediaStore.MediaColumns._ID}, sel,
                new String[]{REL_PATH, FILE_NAME}, null)) {
            if (c != null && c.moveToFirst()) return ContentUris.withAppendedId(coll, c.getLong(0));
        } catch (Exception ignored) { }
        return null;
    }

    private Uri createSharedCsv() throws Exception {
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, REL_PATH);
        Uri uri = getContentResolver().insert(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        if (uri == null) throw new Exception("insert failed");
        try (OutputStream os = getContentResolver().openOutputStream(uri, "w")) {
            os.write(HEADER.getBytes(StandardCharsets.UTF_8));
        }
        return uri;
    }

    /** Older files have fewer columns: rewrite only their first line so the header lists every column. */
    private void upgradeHeader(Uri uri) throws Exception {
        String text;
        try (InputStream in = getContentResolver().openInputStream(uri)) { text = readAll(in); }
        int nl = text.indexOf('\n');
        String first = nl >= 0 ? text.substring(0, nl + 1) : text;
        if (first.equals(HEADER)) return;
        String rest = nl >= 0 ? text.substring(nl + 1) : "";
        try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
            os.write((HEADER + rest).getBytes(StandardCharsets.UTF_8));
        }
    }

    private File privateCsv() {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        return new File(dir, FILE_NAME);
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    private boolean useShared = true;

    private void append(String line) {
        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        synchronized (fileLock) {
            if (useShared) {
                try {
                    Uri uri = findSharedCsv();
                    if (uri == null) uri = createSharedCsv();
                    else upgradeHeader(uri);
                    // open read/write and seek to the end = reliable append on all Android versions
                    try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "rw");
                         FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor())) {
                        FileChannel ch = fos.getChannel();
                        ch.position(ch.size());
                        fos.write(bytes);
                    }
                    return;
                } catch (Exception e) {
                    useShared = false; // fall through to private file
                }
            }
            try {
                File f = privateCsv();
                boolean fresh = !f.exists();
                try (FileOutputStream fos = new FileOutputStream(f, true)) {
                    if (fresh) fos.write(HEADER.getBytes(StandardCharsets.UTF_8));
                    fos.write(bytes);
                }
            } catch (Exception ignored) { }
        }
    }

    private String read() {
        synchronized (fileLock) {
            if (useShared) {
                Uri uri = findSharedCsv();
                if (uri != null) {
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        return readAll(in);
                    } catch (Exception ignored) { }
                }
            }
            File f = privateCsv();
            if (f.exists()) {
                try (InputStream in = new FileInputStream(f)) {
                    return readAll(in);
                } catch (Exception ignored) { }
            }
            return HEADER;
        }
    }

    /** Replace the whole CSV (used when the user deletes rows). */
    private boolean overwrite(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        synchronized (fileLock) {
            if (useShared) {
                try {
                    Uri uri = findSharedCsv();
                    if (uri == null) uri = createSharedCsv();
                    try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                        os.write(bytes);
                    }
                    return true;
                } catch (Exception e) {
                    useShared = false;
                }
            }
            try (FileOutputStream fos = new FileOutputStream(privateCsv(), false)) {
                fos.write(bytes);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** Exposed to the page as window.AndroidTTS */
    private class Bridge {
        @JavascriptInterface
        public void speak(String text) {
            if (ttsReady && text != null && !text.trim().isEmpty()) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "breathe");
            }
        }

        @JavascriptInterface
        public void stop() {
            if (ttsReady) tts.stop();
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void appendCsv(String line) {
            if (line != null && !line.isEmpty()) append(line);
        }

        @JavascriptInterface
        public boolean writeCsv(String text) {
            return text != null && overwrite(text);
        }

        @JavascriptInterface
        public String readCsv() {
            return read();
        }

        @JavascriptInterface
        public String appVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "?";
            }
        }

        @JavascriptInterface
        public String csvLocation() {
            return useShared ? REL_PATH + FILE_NAME
                    : "Android/data/" + getPackageName() + "/files/" + FILE_NAME;
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
