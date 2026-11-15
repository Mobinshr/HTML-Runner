package com.mobin.htmlrunner;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.DocumentsContract;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int PICK_FILE = 1;
    private static final int PICK_FOLDER = 2;
    private static final String PROJECT_HOST = "htmlrunner.local";
    private static final String PROJECT_URL = "https://" + PROJECT_HOST + "/index.html";
    private static final String PREFS = "html_runner";
    private static final String PROJECT_URI = "project_tree_uri";

    private WebView web;
    private Uri projectTree;
    private TextView errorPanel;
    private final StringBuilder consoleErrors = new StringBuilder();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        final int ink = 0xFF182230;
        final int muted = 0xFF667085;
        final int surface = 0xFFFFFFFF;
        final int accent = 0xFF3659D9;
        final int page = 0xFFF4F6FB;

        getWindow().setStatusBarColor(0xFFF4F6FB);
        getWindow().setNavigationBarColor(0xFFF4F6FB);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(page);
        root.setPadding(dp(14), dp(8), dp(14), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(4), dp(8), dp(4), dp(12));

        TextView eyebrow = new TextView(this);
        eyebrow.setText("WEB PROJECT WORKSPACE");
        eyebrow.setTextColor(accent);
        eyebrow.setTextSize(10);
        eyebrow.setLetterSpacing(0.12f);
        eyebrow.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView title = new TextView(this);
        title.setText("HTML Runner");
        title.setTextColor(ink);
        title.setTextSize(25);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(3), 0, 0);

        TextView subtitle = new TextView(this);
        subtitle.setText("پروژه‌های وب خودت را مستقیم روی گوشی اجرا کن");
        subtitle.setTextColor(muted);
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(3), 0, 0);

        header.addView(eyebrow);
        header.addView(title);
        header.addView(subtitle);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(7), dp(7), dp(7), dp(7));
        toolbar.setBackground(roundedBackground(surface, 0xFFE5E9F2, 18));

        Button folderButton = createActionButton("پوشه پروژه", 0xFF3659D9, 0xFFFFFFFF);
        folderButton.setOnClickListener(v -> pickProjectFolder());

        Button fileButton = createActionButton("فایل HTML", 0xFFE9EDFF, 0xFF2948B8);
        fileButton.setOnClickListener(v -> pickHtmlFile());

        Button errorsButton = createActionButton("خطاها", 0xFFFFF0E8, 0xFF9A4D20);
        errorsButton.setOnClickListener(v -> {
            if (errorPanel != null) {
                errorPanel.setVisibility(errorPanel.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE);
            }
        });

        Button reloadButton = createActionButton("↻", 0xFFF0F2F7, ink);
        reloadButton.setTextSize(20);
        reloadButton.setContentDescription("بارگذاری مجدد");
        reloadButton.setOnClickListener(v -> {
            if (projectTree != null) web.loadUrl(PROJECT_URL);
            else web.reload();
        });

        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        actionParams.setMargins(dp(3), 0, dp(3), 0);
        toolbar.addView(folderButton, actionParams);
        toolbar.addView(fileButton, actionParams);
        toolbar.addView(errorsButton, actionParams);
        LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(dp(48), dp(46));
        reloadParams.setMargins(dp(3), 0, dp(3), 0);
        toolbar.addView(reloadButton, reloadParams);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        TextView previewLabel = new TextView(this);
        previewLabel.setText("پیش‌نمایش");
        previewLabel.setTextColor(ink);
        previewLabel.setTextSize(13);
        previewLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        previewLabel.setPadding(dp(4), dp(12), dp(4), dp(7));
        root.addView(previewLabel, new LinearLayout.LayoutParams(-1, -2));

        web = new WebView(this);
        web.setBackgroundColor(0xFFFFFFFF);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        // Keep native viewport sizing; avoid overview-mode downscaling that can soften canvas games.
        settings.setLoadWithOverviewMode(false);
        settings.setTextZoom(100);
        settings.setLoadsImagesAutomatically(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHorizontalScrollBarEnabled(false);
        web.setVerticalScrollBarEnabled(true);
        web.setWebViewClient(new ProjectWebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                    recordConsoleError(message.message(), message.sourceId(), message.lineNumber());
                }
                return true;
            }
        });
        web.getSettings().setSupportMultipleWindows(false);

        LinearLayout previewFrame = new LinearLayout(this);
        previewFrame.setOrientation(LinearLayout.VERTICAL);
        previewFrame.setBackground(roundedBackground(surface, 0xFFE1E6EF, 18));
        previewFrame.setClipToOutline(true);
        previewFrame.addView(web, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, 0, 1);
        previewParams.bottomMargin = dp(6);
        root.addView(previewFrame, previewParams);

        ScrollView errorScroll = new ScrollView(this);
        errorPanel = new TextView(this);
        errorPanel.setText("هنوز خطای JavaScript ثبت نشده است.");
        errorPanel.setTextSize(12);
        errorPanel.setTextColor(0xFFFFDAD4);
        errorPanel.setBackground(roundedBackground(0xFF202938, 0xFF202938, 14));
        errorPanel.setPadding(dp(14), dp(12), dp(14), dp(12));
        errorScroll.setPadding(0, dp(6), 0, 0);
        errorScroll.addView(errorPanel);
        errorScroll.setVisibility(View.GONE);
        root.addView(errorScroll, new LinearLayout.LayoutParams(-1, dp(112)));

        setContentView(root);

        Uri incoming = getIncomingHtmlUri(getIntent());
        if (incoming != null) {
            loadSingleHtmlFile(incoming);
        } else {
            restoreProject();
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private android.graphics.drawable.GradientDrawable roundedBackground(
            int fill, int stroke, int radiusDp) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private Button createActionButton(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(foreground);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(android.view.Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setBackground(roundedBackground(background, background, 13));
        return button;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri incoming = getIncomingHtmlUri(intent);
        if (incoming != null) loadSingleHtmlFile(incoming);
    }

    private void recordConsoleError(String message, String source, int line) {
        String entry = "خط " + line + " | " + message
                + (source == null || source.isEmpty() ? "" : "\\n" + source);
        consoleErrors.append(entry).append("\\n\\n");
        String[] entries = consoleErrors.toString().split("\\n\\n");
        if (entries.length > 30) {
            consoleErrors.setLength(0);
            for (int i = entries.length - 30; i < entries.length; i++) {
                consoleErrors.append(entries[i]).append("\\n\\n");
            }
        }
        runOnUiThread(() -> {
            if (errorPanel != null) errorPanel.setText(consoleErrors.toString());
        });
    }

    private Uri getIncomingHtmlUri(Intent intent) {
        if (intent == null) return null;
        Uri uri = intent.getData();
        if (uri == null) {
            Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream instanceof Uri) uri = (Uri) stream;
        }
        if (uri == null && intent.getClipData() != null
                && intent.getClipData().getItemCount() > 0) {
            uri = intent.getClipData().getItemAt(0).getUri();
        }
        if (uri != null && ("content".equalsIgnoreCase(uri.getScheme())
                || "file".equalsIgnoreCase(uri.getScheme()))) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                // Temporary URI grants are sufficient while the Activity is alive.
            }
            return uri;
        }
        return null;
    }

    private void pickProjectFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER);
    }

    private void pickHtmlFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/html");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri selected = data.getData();
        if (requestCode == PICK_FOLDER) {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(selected, flags);
                projectTree = selected;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(PROJECT_URI, selected.toString()).apply();
                web.loadUrl(PROJECT_URL);
            } catch (SecurityException e) {
                Toast.makeText(this, "دسترسی به پوشه ذخیره نشد", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == PICK_FILE) {
            try {
                getContentResolver().takePersistableUriPermission(selected,
                        data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                // Some document providers do not offer persistable permissions.
            }
            loadSingleHtmlFile(selected);
        }
    }

    private void restoreProject() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(PROJECT_URI, null);
        if (saved != null) {
            try {
                projectTree = Uri.parse(saved);
                web.loadUrl(PROJECT_URL);
                return;
            } catch (Exception ignored) {
                projectTree = null;
            }
        }
        showHome();
    }

    private void loadSingleHtmlFile(Uri uri) {
        projectTree = null;
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("Unable to open file");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            web.loadDataWithBaseURL("https://" + PROJECT_HOST + "/",
                    output.toString("UTF-8"), "text/html", "UTF-8", null);
        } catch (Exception e) {
            Toast.makeText(this, "فایل HTML باز نشد", Toast.LENGTH_LONG).show();
            showHome();
        }
    }

    private WebResourceResponse serveProjectFile(Uri requestUri) {
        if (projectTree == null) return null;

        List<String> segments = requestUri.getPathSegments();
        if (segments.isEmpty()) segments = java.util.Collections.singletonList("index.html");

        Uri current;
        String servedName = segments.get(segments.size() - 1);
        try {
            String rootId = DocumentsContract.getTreeDocumentId(projectTree);
            Uri rootDocument = DocumentsContract.buildDocumentUriUsingTree(projectTree, rootId);
            current = rootDocument;

            for (int i = 0; i < segments.size(); i++) {
                String segment = segments.get(i);
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                        || segment.contains("/") || segment.contains("\\\\")) {
                    return notFound();
                }
                current = findChildDocument(current, segment);
                if (current == null) {
                    // BrowserRouter routes have no file extension: serve the app shell.
                    if (!segments.get(segments.size() - 1).contains(".")) {
                        current = findChildDocument(rootDocument, "index.html");
                        servedName = "index.html";
                        break;
                    }
                    return notFound();
                }
            }

            InputStream stream = getContentResolver().openInputStream(current);
            if (stream == null) return notFound();

            String mime = getContentResolver().getType(current);
            if (mime == null || "application/octet-stream".equalsIgnoreCase(mime)) {
                mime = mimeFromName(servedName);
            }
            boolean text = mime.startsWith("text/") || mime.contains("javascript")
                    || mime.contains("json") || mime.contains("xml") || mime.contains("svg");
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-store");
            headers.put("Access-Control-Allow-Origin", "*");
            return new WebResourceResponse(mime, text ? "UTF-8" : null,
                    200, "OK", headers, stream);
        } catch (Exception e) {
            return notFound();
        }
    }

    private Uri findChildDocument(Uri parent, String displayName) {
        String parentId = DocumentsContract.getDocumentId(parent);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(projectTree, parentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        try (Cursor cursor = getContentResolver().query(childrenUri, projection,
                null, null, null)) {
            if (cursor == null) return null;
            int idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            while (cursor.moveToNext()) {
                if (displayName.equals(cursor.getString(nameColumn))) {
                    return DocumentsContract.buildDocumentUriUsingTree(
                            projectTree, cursor.getString(idColumn));
                }
            }
        }
        return null;
    }

    private String mimeFromName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".webmanifest")) return "application/manifest+json";
        if (lower.endsWith(".wasm")) return "application/wasm";
        if (lower.endsWith(".map")) return "application/json";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".avif")) return "image/avif";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".eot")) return "application/vnd.ms-fontobject";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }

    private WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                new HashMap<>(), new java.io.ByteArrayInputStream(
                "Project file not found".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void showHome() {
        String html = "<!doctype html><html lang='fa' dir='rtl'><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<body style='font-family:sans-serif;background:#101827;color:#f8fafc;"
                + "min-height:90vh;display:grid;place-items:center;text-align:center;padding:24px'>"
                + "<main><div style='font-size:64px'>🌐</div><h2>HTML Runner</h2>"
                + "<p>برای اجرای یک پروژه وب، پوشه‌ای را انتخاب کن که فایل index.html دارد.</p>"
                + "<p style='color:#94a3b8'>فایل‌های CSS، JavaScript و تصاویر داخل همان پوشه نیز بارگذاری می‌شوند.</p>"
                + "</main></body></html>";
        web.loadDataWithBaseURL("https://" + PROJECT_HOST + "/", html,
                "text/html", "UTF-8", null);
    }

    private class ProjectWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (PROJECT_HOST.equals(uri.getHost())) return serveProjectFile(uri);
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if ("content".equalsIgnoreCase(uri.getScheme())
                    || "file".equalsIgnoreCase(uri.getScheme())) {
                loadSingleHtmlFile(uri);
                return true;
            }
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if ("content".equalsIgnoreCase(uri.getScheme())
                    || "file".equalsIgnoreCase(uri.getScheme())) {
                loadSingleHtmlFile(uri);
                return true;
            }
            return false;
        }
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.clearHistory();
            web.removeAllViews();
            if (web.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) web.getParent()).removeView(web);
            }
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
