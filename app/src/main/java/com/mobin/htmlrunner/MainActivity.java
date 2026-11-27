package com.mobin.htmlrunner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_FILE = 1;
    private static final int PICK_FOLDER = 2;
    private static final int PICK_WEB_FILE = 3;
    private static final int REQUEST_LOCATION = 41;
    private static final String PROJECT_HOST = "htmlrunner.local";
    private static final String PROJECT_URL = "https://" + PROJECT_HOST + "/index.html";
    private static final String PREFS = "html_runner";
    private static final String PROJECT_URI = "project_tree_uri";
    private static final int MAX_SINGLE_HTML_BYTES = 5 * 1024 * 1024;

    private WebView web;
    private FrameLayout root;
    private Uri projectTree;
    private boolean hasProjectContent;
    private boolean pickerOpen;
    private long blankTouchStart;
    private ValueCallback<Uri[]> filePathCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Uri> projectDocumentCache = new ConcurrentHashMap<>();
    private int fileLoadGeneration;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        applyImmersiveMode();

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
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
        web.setVerticalScrollBarEnabled(false);
        web.setWebViewClient(new ProjectWebViewClient());
        web.getSettings().setSupportMultipleWindows(false);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                    recordConsoleError(message.message(), message.sourceId(), message.lineNumber());
                }
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    pickerOpen = true;
                    startActivityForResult(params.createIntent(), PICK_WEB_FILE);
                    return true;
                } catch (Exception e) {
                    pickerOpen = false;
                    filePathCallback = null;
                    return false;
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                web.setVisibility(View.GONE);
                root.addView(customView, new FrameLayout.LayoutParams(-1, -1));
                applyImmersiveMode();
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                requestWebLocation(origin, callback);
            }
        });

        // No in-app controls: short tap on the empty surface picks a folder;
        // long press picks one HTML file.
        web.setOnTouchListener((view, event) -> {
            if (!hasProjectContent && !pickerOpen) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    blankTouchStart = System.currentTimeMillis();
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    if (System.currentTimeMillis() - blankTouchStart >= 550) pickHtmlFile();
                    else pickProjectFolder();
                }
            }
            return false;
        });

        Uri incoming = getIncomingHtmlUri(getIntent());
        if (incoming != null) loadSingleHtmlFile(incoming);
        else restoreProject();
    }

    private void applyImmersiveMode() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        applyImmersiveMode();
    }

    private void requestWebLocation(String origin, GeolocationPermissions.Callback callback) {
        Uri parsed = Uri.parse(origin);
        if (!"https".equalsIgnoreCase(parsed.getScheme())
                || !PROJECT_HOST.equalsIgnoreCase(parsed.getHost())) {
            callback.invoke(origin, false, false);
            return;
        }
        boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            callback.invoke(origin, true, false);
            return;
        }
        if (pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, false, false);
        }
        pendingGeoOrigin = origin;
        pendingGeoCallback = callback;
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, REQUEST_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION || pendingGeoCallback == null) return;
        boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
        pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
        pendingGeoCallback = null;
        pendingGeoOrigin = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri incoming = getIncomingHtmlUri(intent);
        if (incoming != null) loadSingleHtmlFile(incoming);
    }

    private void recordConsoleError(String message, String source, int line) {
        Log.e("HTMLRunner", "JavaScript error at line " + line + ": " + message
                + (source == null || source.isEmpty() ? "" : " [" + source + "]"));
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
        if (pickerOpen) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            pickerOpen = true;
            startActivityForResult(intent, PICK_FOLDER);
        } catch (Exception e) {
            pickerOpen = false;
        }
    }

    private void pickHtmlFile() {
        if (pickerOpen) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/html");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            pickerOpen = true;
            startActivityForResult(intent, PICK_FILE);
        } catch (Exception e) {
            pickerOpen = false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_WEB_FILE) {
            pickerOpen = false;
            if (filePathCallback != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            return;
        }

        if (requestCode == PICK_FOLDER || requestCode == PICK_FILE) pickerOpen = false;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri selected = data.getData();
        if (requestCode == PICK_FOLDER) {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(selected, flags);
                fileLoadGeneration++;
                projectTree = selected;
                projectDocumentCache.clear();
                hasProjectContent = true;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(PROJECT_URI, selected.toString()).apply();
                web.loadUrl(PROJECT_URL);
            } catch (SecurityException e) {
                projectTree = null;
                projectDocumentCache.clear();
                hasProjectContent = false;
                showBlankSurface();
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
                fileLoadGeneration++;
                projectTree = Uri.parse(saved);
                projectDocumentCache.clear();
                hasProjectContent = true;
                web.loadUrl(PROJECT_URL);
                return;
            } catch (Exception ignored) {
                projectTree = null;
                projectDocumentCache.clear();
            }
        }
        showBlankSurface();
        pickProjectFolder();
    }

    private void loadSingleHtmlFile(Uri uri) {
        projectTree = null;
        projectDocumentCache.clear();
        hasProjectContent = true;
        final int generation = ++fileLoadGeneration;
        if (web != null) web.stopLoading();

        fileExecutor.execute(() -> {
            String html;
            try (InputStream input = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) throw new IllegalStateException("Unable to open file");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_SINGLE_HTML_BYTES) {
                        throw new IllegalArgumentException("HTML file is larger than the 5 MB limit");
                    }
                    output.write(buffer, 0, count);
                }
                html = output.toString("UTF-8");
            } catch (Exception e) {
                Log.e("HTMLRunner", "Could not open HTML document", e);
                mainHandler.post(() -> {
                    if (generation != fileLoadGeneration || isFinishing() || isDestroyed()) return;
                    hasProjectContent = false;
                    showBlankSurface();
                });
                return;
            }

            mainHandler.post(() -> {
                if (generation != fileLoadGeneration || isFinishing() || isDestroyed() || web == null) return;
                hasProjectContent = true;
                web.loadDataWithBaseURL("https://" + PROJECT_HOST + "/",
                        html, "text/html", "UTF-8", null);
            });
        });
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
        String cacheKey = parent.toString() + "::" + displayName;
        Uri cached = projectDocumentCache.get(cacheKey);
        if (cached != null) return cached;

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
                    Uri child = DocumentsContract.buildDocumentUriUsingTree(
                            projectTree, cursor.getString(idColumn));
                    projectDocumentCache.put(cacheKey, child);
                    return child;
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

    private void showBlankSurface() {
        hasProjectContent = false;
        String blank = "<!doctype html><html><head><meta name='viewport' "
                + "content='width=device-width,initial-scale=1,maximum-scale=1'></head>"
                + "<body style='margin:0;background:#000'></body></html>";
        web.loadDataWithBaseURL(PROJECT_URL, blank, "text/html", "UTF-8", null);
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
        applyImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        fileLoadGeneration++;
        fileExecutor.shutdownNow();
        projectDocumentCache.clear();
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, false, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
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
        if (customView != null) hideCustomView();
        else if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
