package fr.groupmaker.app;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.net.Uri;
import android.content.Intent;
import android.app.Activity;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends BridgeActivity {

    private static final String TAG          = "MarinaApp";
    private static final String ALLOWED_HOST = "marina.groupmaker.fr";
    private static final String REGISTER_URL = "https://marina.groupmaker.fr/api/register_device.php";

    private ValueCallback<Uri[]> fileUploadCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                fetchAndRegisterToken();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cookies: persist PHP session
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(getBridge().getWebView(), true);
        cookieManager.flush();

        // WebView settings
        WebView webView = getBridge().getWebView();
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        String defaultUA = settings.getUserAgentString();
        settings.setUserAgentString(defaultUA + " MarinaApp/1.0");

        // External links open in browser, internal stay in WebView
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                    android.webkit.WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host != null && host.equals(ALLOWED_HOST)) {
                    return false;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                startActivity(intent);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                tryRegisterToken();
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                // Only intercept main frame errors (not sub-resources)
                if (request.isForMainFrame()) {
                    view.loadUrl("file:///android_asset/offline.html");
                }
            }
        });

        // Geolocation + file upload
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    fileUploadCallback = null;
                    return false;
                }
                return true;
            }
        });

        // Native share interface callable from JS: MarinaApp.share(title, url)
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void share(String title, String url) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
                shareIntent.putExtra(Intent.EXTRA_TEXT, title + "\n" + url);
                Intent chooser = Intent.createChooser(shareIntent, "Partager via");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplicationContext().startActivity(chooser);
            }
        }, "MarinaApp");

        // Handle deep link URL passed via notification tap
        handleLaunchUrl(getIntent());

        // Request notification permission (Android 13+)
        askNotificationPermission();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleLaunchUrl(intent);
    }

    private void handleLaunchUrl(Intent intent) {
        if (intent == null) return;
        String launchUrl = intent.getStringExtra("launch_url");
        if (launchUrl != null && !launchUrl.isEmpty()) {
            getBridge().getWebView().loadUrl(launchUrl);
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                fetchAndRegisterToken();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            fetchAndRegisterToken();
        }
    }

    private void fetchAndRegisterToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "FCM token fetch failed", task.getException());
                return;
            }
            String token = task.getResult();
            SharedPreferences prefs = getSharedPreferences("marina_prefs", MODE_PRIVATE);
            String stored = prefs.getString("fcm_token", "");
            if (!token.equals(stored)) {
                prefs.edit()
                    .putString("fcm_token", token)
                    .putBoolean("fcm_token_sent", false)
                    .apply();
            }
        });
    }

    private void tryRegisterToken() {
        SharedPreferences prefs = getSharedPreferences("marina_prefs", MODE_PRIVATE);
        boolean sent  = prefs.getBoolean("fcm_token_sent", false);
        String  token = prefs.getString("fcm_token", "");
        if (sent || token.isEmpty()) return;

        // Send token to server in background thread
        String finalToken = token;
        new Thread(() -> {
            try {
                String cookies = CookieManager.getInstance().getCookie(
                    "https://marina.groupmaker.fr");
                if (cookies == null || !cookies.contains("PHPSESSID")) return; // not logged in

                URL url = new URL(REGISTER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Cookie", cookies);
                conn.setRequestProperty("User-Agent", "MarinaApp/1.0");

                String body = "fcm_token=" + Uri.encode(finalToken) + "&platform=android";
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.close();

                int code = conn.getResponseCode();
                if (code == 200) {
                    prefs.edit().putBoolean("fcm_token_sent", true).apply();
                    Log.d(TAG, "FCM token registered");
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "FCM token registration failed", e);
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileUploadCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        WebView webView = getBridge().getWebView();
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
