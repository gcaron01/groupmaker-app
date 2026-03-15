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

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
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
    private static final int QR_SCANNER_REQUEST   = 1002;

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
                // Capture user name for biometric prompt display
                view.evaluateJavascript(
                    "(function(){var el=document.querySelector('.header-photo img')||document.querySelector('[data-user-name]');"
                    + "return el?el.getAttribute('alt')||el.getAttribute('data-user-name')||'':''})()",
                    value -> {
                        if (value != null) {
                            String name = value.replace("\"", "").trim();
                            if (!name.isEmpty() && !name.equals("null")) {
                                getSharedPreferences("marina_prefs", MODE_PRIVATE)
                                    .edit().putString("user_name", name).apply();
                            }
                        }
                    });
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

        // Native interface callable from JS
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

            @JavascriptInterface
            public void openWeather() {
                Intent intent = new Intent(MainActivity.this, WeatherActivity.class);
                startActivity(intent);
            }

            @JavascriptInterface
            public void scanQR() {
                Intent intent = new Intent(MainActivity.this, QrScannerActivity.class);
                startActivityForResult(intent, QR_SCANNER_REQUEST);
            }

            @JavascriptInterface
            public void openNotificationSettings() {
                Intent intent = new Intent(MainActivity.this, NotificationSettingsActivity.class);
                startActivity(intent);
            }

            @JavascriptInterface
            public boolean hasBiometric() {
                BiometricManager bm = BiometricManager.from(MainActivity.this);
                return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        == BiometricManager.BIOMETRIC_SUCCESS;
            }

            @JavascriptInterface
            public boolean isBiometricEnabled() {
                return BiometricHelper.hasToken(MainActivity.this);
            }

            @JavascriptInterface
            public void enableBiometric() {
                // Demande un prompt biométrique puis enregistre le token
                runOnUiThread(() -> promptBiometricForRegistration());
            }

            @JavascriptInterface
            public void disableBiometric() {
                String token = BiometricHelper.getToken(MainActivity.this);
                BiometricHelper.clearToken(MainActivity.this);
                // Révoquer côté serveur en background
                new Thread(() -> {
                    try {
                        String cookies = CookieManager.getInstance().getCookie("https://marina.groupmaker.fr");
                        if (cookies != null) {
                            java.net.URL url = new java.net.URL("https://marina.groupmaker.fr/api/biometric_auth.php");
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setDoOutput(true);
                            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                            conn.setRequestProperty("Cookie", cookies);
                            String body = "action=revoke&token=" + token;
                            conn.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            conn.getResponseCode();
                            conn.disconnect();
                        }
                    } catch (Exception ignored) {}
                }).start();
                WebView wv = getBridge().getWebView();
                wv.post(() -> wv.evaluateJavascript(
                    "typeof _onBiometricDisabled==='function'&&_onBiometricDisabled()", null));
            }
        }, "MarinaApp");

        // Handle deep link URL passed via notification tap
        handleLaunchUrl(getIntent());

        // Request notification permission (Android 13+)
        askNotificationPermission();

        // Try biometric auto-login if token is stored
        tryBiometricLogin();
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
        if (requestCode == QR_SCANNER_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                String qrResult = data.getStringExtra("qr_result");
                if (qrResult != null && !qrResult.isEmpty()) {
                    handleQrResult(qrResult);
                }
            }
            return;
        }
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

    // ── QR code result handling ────────────────────────────────────────
    private void handleQrResult(String value) {
        WebView webView = getBridge().getWebView();

        // If it's a marina URL, navigate directly
        if (value.startsWith("https://marina.groupmaker.fr/") || value.startsWith("https://groupmaker.fr/")) {
            webView.loadUrl(value);
            return;
        }

        // If it's a marina:// deep link, convert to HTTPS
        if (value.startsWith("marina://")) {
            String path = value.substring("marina://".length());
            webView.loadUrl("https://marina.groupmaker.fr/" + path);
            return;
        }

        // Otherwise, pass to JS for custom handling
        String escaped = value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
        webView.evaluateJavascript(
                "typeof _onQrScanned==='function'?_onQrScanned('" + escaped + "'):alert('QR: " + escaped + "')",
                null);
    }

    // ── Biometric: registration (user already logged in) ──────────────
    private void promptBiometricForRegistration() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Activer la connexion biométrique")
                .setSubtitle("Confirmez votre identité")
                .setNegativeButtonText("Annuler")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        // Register token in background
                        new Thread(() -> {
                            String cookies = CookieManager.getInstance().getCookie("https://marina.groupmaker.fr");
                            if (cookies == null || !cookies.contains("PHPSESSID")) {
                                runOnUiThread(() -> {
                                    WebView wv = getBridge().getWebView();
                                    wv.evaluateJavascript(
                                        "typeof _onBiometricError==='function'&&_onBiometricError('Non connecté')", null);
                                });
                                return;
                            }
                            String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
                            String token = BiometricHelper.registerToken(cookies, deviceName);
                            if (token != null) {
                                String userName = (String) getSharedPreferences("marina_prefs", MODE_PRIVATE)
                                        .getString("user_name", "");
                                BiometricHelper.saveToken(MainActivity.this, token, userName);
                                runOnUiThread(() -> {
                                    WebView wv = getBridge().getWebView();
                                    wv.evaluateJavascript(
                                        "typeof _onBiometricEnabled==='function'&&_onBiometricEnabled()", null);
                                });
                            }
                        }).start();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        Log.w(TAG, "Biometric auth failed");
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        Log.e(TAG, "Biometric error: " + errString);
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    // ── Biometric: auto-login at app launch ─────────────────────────────
    private void tryBiometricLogin() {
        if (!BiometricHelper.hasToken(this)) return;

        // Check if already logged in (has PHPSESSID)
        String cookies = CookieManager.getInstance().getCookie("https://marina.groupmaker.fr");
        if (cookies != null && cookies.contains("PHPSESSID")) return;

        String userName = BiometricHelper.getUserName(this);
        String subtitle = userName.isEmpty() ? "Connectez-vous avec votre empreinte"
                : "Se connecter en tant que " + userName;

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Marina")
                .setSubtitle(subtitle)
                .setNegativeButtonText("Utiliser le mot de passe")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        new Thread(() -> {
                            String token = BiometricHelper.getToken(MainActivity.this);
                            org.json.JSONObject resp = BiometricHelper.loginWithToken(token);
                            if (resp != null && resp.optBoolean("ok")) {
                                String redirect = resp.optString("redirect",
                                        "https://marina.groupmaker.fr/index.php");
                                runOnUiThread(() -> getBridge().getWebView().loadUrl(redirect));
                            } else if (resp != null && resp.optBoolean("revoked")) {
                                // Token révoqué côté serveur
                                BiometricHelper.clearToken(MainActivity.this);
                            }
                        }).start();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // User can retry
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        // User chose "mot de passe" or cancelled — continue with WebView login
                        Log.d(TAG, "Bio login cancelled: " + errString);
                    }
                });

        biometricPrompt.authenticate(promptInfo);
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
