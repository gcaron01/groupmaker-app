package fr.groupmaker.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NotificationSettingsActivity extends AppCompatActivity {

    private static final String TAG = "MarinaNotifSettings";
    private static final String API_URL = "https://marina.groupmaker.fr/api/push_preferences.php";
    private static final int COLOR_BG      = Color.parseColor("#f6f8fc");
    private static final int COLOR_CARD    = Color.WHITE;
    private static final int COLOR_PRIMARY = Color.parseColor("#20466e");
    private static final int COLOR_TEXT    = Color.parseColor("#0f172a");
    private static final int COLOR_MUTED   = Color.parseColor("#64748b");
    private static final int COLOR_BORDER  = Color.parseColor("#e6ebf5");
    private static final int COLOR_OK      = Color.parseColor("#16a34a");
    private static final int COLOR_WARN    = Color.parseColor("#ef4444");

    private LinearLayout toggleContainer;
    private TextView statusText;
    private final List<PrefItem> prefItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));

        // Back button
        TextView backBtn = new TextView(this);
        backBtn.setText("← Retour");
        backBtn.setTextColor(COLOR_PRIMARY);
        backBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        backBtn.setPadding(dp(8), dp(12), dp(8), dp(12));
        backBtn.setOnClickListener(v -> finish());
        root.addView(backBtn);

        // Title
        TextView title = new TextView(this);
        title.setText("Notifications");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(COLOR_TEXT);
        title.setPadding(dp(8), dp(8), dp(8), dp(4));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Gérez les notifications push que vous souhaitez recevoir.");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setPadding(dp(8), 0, dp(8), dp(16));
        root.addView(subtitle);

        // System permission status
        LinearLayout systemCard = createCard();
        TextView sysTitle = new TextView(this);
        sysTitle.setText("Permission système");
        sysTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sysTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sysTitle.setTextColor(COLOR_TEXT);
        systemCard.addView(sysTitle);

        statusText = new TextView(this);
        updateSystemStatus();
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusText.setPadding(0, dp(6), 0, dp(8));
        systemCard.addView(statusText);

        TextView openSettingsBtn = new TextView(this);
        openSettingsBtn.setText("Ouvrir les paramètres système");
        openSettingsBtn.setTextColor(COLOR_PRIMARY);
        openSettingsBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        openSettingsBtn.setTypeface(Typeface.DEFAULT_BOLD);
        openSettingsBtn.setPadding(0, dp(4), 0, 0);
        openSettingsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        });
        systemCard.addView(openSettingsBtn);
        root.addView(systemCard);

        // Notification types
        LinearLayout typesCard = createCard();
        TextView typesTitle = new TextView(this);
        typesTitle.setText("Types de notifications");
        typesTitle.setTypeface(Typeface.DEFAULT_BOLD);
        typesTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        typesTitle.setTextColor(COLOR_TEXT);
        typesTitle.setPadding(0, 0, 0, dp(8));
        typesCard.addView(typesTitle);

        toggleContainer = new LinearLayout(this);
        toggleContainer.setOrientation(LinearLayout.VERTICAL);

        // Placeholder while loading
        TextView loading = new TextView(this);
        loading.setText("Chargement…");
        loading.setTextColor(COLOR_MUTED);
        loading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        loading.setPadding(0, dp(8), 0, dp(8));
        toggleContainer.addView(loading);

        typesCard.addView(toggleContainer);
        root.addView(typesCard);

        scrollView.addView(root);
        setContentView(scrollView);

        // Load preferences from API
        loadPreferences();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSystemStatus();
    }

    private void updateSystemStatus() {
        boolean granted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            granted = true; // Pre-Android 13 always allowed
        }

        if (granted) {
            statusText.setText("✅ Les notifications sont autorisées");
            statusText.setTextColor(COLOR_OK);
        } else {
            statusText.setText("❌ Les notifications sont bloquées au niveau système");
            statusText.setTextColor(COLOR_WARN);
        }
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(COLOR_CARD);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);
        card.setElevation(dp(2));
        return card;
    }

    private void loadPreferences() {
        new Thread(() -> {
            try {
                String cookies = CookieManager.getInstance().getCookie("https://marina.groupmaker.fr");
                if (cookies == null || !cookies.contains("PHPSESSID")) {
                    runOnUiThread(() -> showError("Connectez-vous d'abord sur l'app."));
                    return;
                }

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Cookie", cookies);
                conn.setRequestProperty("User-Agent", "MarinaApp/1.0");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    runOnUiThread(() -> showError("Erreur serveur (" + code + ")"));
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                if (!json.optBoolean("ok")) {
                    runOnUiThread(() -> showError(json.optString("error", "Erreur inconnue")));
                    return;
                }

                JSONArray prefs = json.getJSONArray("preferences");
                prefItems.clear();
                for (int i = 0; i < prefs.length(); i++) {
                    JSONObject p = prefs.getJSONObject(i);
                    prefItems.add(new PrefItem(
                            p.getString("type"),
                            p.getString("label"),
                            p.getInt("enabled") == 1
                    ));
                }

                runOnUiThread(this::renderToggles);

            } catch (Exception e) {
                Log.e(TAG, "Load prefs failed", e);
                runOnUiThread(() -> showError("Impossible de charger les préférences."));
            }
        }).start();
    }

    private void renderToggles() {
        toggleContainer.removeAllViews();

        String[] icons = {"💬", "📅", "🔄", "👥", "✅", "🔔", "📢"};

        for (int i = 0; i < prefItems.size(); i++) {
            PrefItem item = prefItems.get(i);
            String icon = i < icons.length ? icons[i] : "🔔";

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));

            // Separator
            if (i > 0) {
                View sep = new View(this);
                sep.setBackgroundColor(COLOR_BORDER);
                LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                sepLp.setMargins(0, 0, 0, 0);
                toggleContainer.addView(sep, sepLp);
            }

            // Icon + Label
            LinearLayout labelCol = new LinearLayout(this);
            labelCol.setOrientation(LinearLayout.VERTICAL);
            labelCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView label = new TextView(this);
            label.setText(icon + "  " + item.label);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            label.setTextColor(COLOR_TEXT);
            labelCol.addView(label);

            row.addView(labelCol);

            // Toggle
            Switch toggle = new Switch(this);
            toggle.setChecked(item.enabled);
            toggle.setOnCheckedChangeListener((btn, isChecked) -> {
                item.enabled = isChecked;
                savePreferences();
            });
            row.addView(toggle);

            toggleContainer.addView(row);
        }
    }

    private void savePreferences() {
        new Thread(() -> {
            try {
                String cookies = CookieManager.getInstance().getCookie("https://marina.groupmaker.fr");
                if (cookies == null) return;

                JSONObject payload = new JSONObject();
                JSONArray prefsArr = new JSONArray();
                for (PrefItem item : prefItems) {
                    JSONObject p = new JSONObject();
                    p.put("type", item.type);
                    p.put("enabled", item.enabled ? 1 : 0);
                    prefsArr.put(p);
                }
                payload.put("preferences", prefsArr);

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Cookie", cookies);
                conn.setRequestProperty("User-Agent", "MarinaApp/1.0");
                conn.setConnectTimeout(8000);

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                os.close();

                int code = conn.getResponseCode();
                conn.disconnect();

                if (code == 200) {
                    Log.d(TAG, "Preferences saved");
                } else {
                    Log.e(TAG, "Save prefs failed: HTTP " + code);
                }
            } catch (Exception e) {
                Log.e(TAG, "Save prefs failed", e);
            }
        }).start();
    }

    private void showError(String msg) {
        toggleContainer.removeAllViews();
        TextView err = new TextView(this);
        err.setText(msg);
        err.setTextColor(COLOR_WARN);
        err.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        err.setPadding(0, dp(8), 0, dp(8));
        toggleContainer.addView(err);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private static class PrefItem {
        String type;
        String label;
        boolean enabled;

        PrefItem(String type, String label, boolean enabled) {
            this.type = type;
            this.label = label;
            this.enabled = enabled;
        }
    }
}
