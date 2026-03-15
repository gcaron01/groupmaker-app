package fr.groupmaker.app;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WeatherActivity extends AppCompatActivity {

    private static final String TAG = "MarinaWeather";
    private static final int LOC_PERMISSION_CODE = 3001;
    private static final int COLOR_BG       = Color.parseColor("#f6f8fc");
    private static final int COLOR_CARD     = Color.WHITE;
    private static final int COLOR_PRIMARY  = Color.parseColor("#20466e");
    private static final int COLOR_TEXT     = Color.parseColor("#0f172a");
    private static final int COLOR_MUTED    = Color.parseColor("#64748b");

    private LinearLayout contentLayout;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));

        // Back
        TextView backBtn = new TextView(this);
        backBtn.setText("← Retour");
        backBtn.setTextColor(COLOR_PRIMARY);
        backBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        backBtn.setPadding(dp(8), dp(12), dp(8), dp(12));
        backBtn.setOnClickListener(v -> finish());
        root.addView(backBtn);

        // Title
        TextView title = new TextView(this);
        title.setText("⛵ Météo marine");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(COLOR_TEXT);
        title.setPadding(dp(8), dp(8), dp(8), dp(4));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Conditions actuelles à votre position");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setPadding(dp(8), 0, dp(8), dp(16));
        root.addView(subtitle);

        // Content container
        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);

        TextView loading = new TextView(this);
        loading.setText("📍 Localisation en cours…");
        loading.setTextColor(COLOR_MUTED);
        loading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        loading.setPadding(dp(8), dp(20), dp(8), dp(20));
        loading.setGravity(Gravity.CENTER);
        contentLayout.addView(loading);

        root.addView(contentLayout);
        scrollView.addView(root);
        setContentView(scrollView);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            getLocationAndFetch();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOC_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOC_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLocationAndFetch();
            } else {
                showError("Permission de localisation requise pour la météo marine.");
            }
        }
    }

    private void getLocationAndFetch() {
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    fetchWeather(location.getLatitude(), location.getLongitude());
                } else {
                    // Fallback : essayer la position du port d'attache
                    showError("Position GPS indisponible. Activez la localisation.");
                }
            }).addOnFailureListener(e -> {
                showError("Erreur GPS : " + e.getMessage());
            });
        } catch (SecurityException e) {
            showError("Permission GPS refusée.");
        }
    }

    private void fetchWeather(double lat, double lon) {
        new Thread(() -> {
            try {
                // Open-Meteo Marine API (gratuit, pas de clé)
                String apiUrl = "https://api.open-meteo.com/v1/forecast?"
                        + "latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
                        + "precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,"
                        + "pressure_msl"
                        + "&hourly=temperature_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m,"
                        + "weather_code,precipitation_probability"
                        + "&forecast_hours=24"
                        + "&wind_speed_unit=kn"
                        + "&timezone=auto";

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    runOnUiThread(() -> showError("Erreur météo (HTTP " + code + ")"));
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());

                // Cache for offline
                SharedPreferences prefs = getSharedPreferences("marina_weather", MODE_PRIVATE);
                prefs.edit()
                        .putString("last_data", json.toString())
                        .putLong("last_fetch", System.currentTimeMillis())
                        .putFloat("last_lat", (float) lat)
                        .putFloat("last_lon", (float) lon)
                        .apply();

                runOnUiThread(() -> renderWeather(json, lat, lon));

            } catch (Exception e) {
                Log.e(TAG, "Fetch weather failed", e);
                // Try cache
                runOnUiThread(() -> {
                    SharedPreferences prefs = getSharedPreferences("marina_weather", MODE_PRIVATE);
                    String cached = prefs.getString("last_data", "");
                    if (!cached.isEmpty()) {
                        try {
                            renderWeather(new JSONObject(cached),
                                    prefs.getFloat("last_lat", 0),
                                    prefs.getFloat("last_lon", 0));
                            Toast.makeText(this, "Données en cache (hors connexion)", Toast.LENGTH_SHORT).show();
                        } catch (Exception ignored) {
                            showError("Pas de connexion et aucune donnée en cache.");
                        }
                    } else {
                        showError("Erreur de connexion météo.");
                    }
                });
            }
        }).start();
    }

    private void renderWeather(JSONObject json, double lat, double lon) {
        contentLayout.removeAllViews();

        try {
            JSONObject current = json.getJSONObject("current");

            // Current conditions card
            LinearLayout currentCard = createCard();

            addLabel(currentCard, "CONDITIONS ACTUELLES", true);
            addSpacer(currentCard, 8);

            // Temperature big
            double temp = current.getDouble("temperature_2m");
            double feelsLike = current.getDouble("apparent_temperature");
            TextView tempView = new TextView(this);
            tempView.setText(weatherEmoji(current.getInt("weather_code")) + " " + formatNum(temp) + "°C");
            tempView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
            tempView.setTypeface(Typeface.DEFAULT_BOLD);
            tempView.setTextColor(COLOR_PRIMARY);
            currentCard.addView(tempView);

            addInfo(currentCard, "Ressenti " + formatNum(feelsLike) + "°C · " + weatherLabel(current.getInt("weather_code")));

            addSpacer(currentCard, 12);

            // Wind
            double windSpeed = current.getDouble("wind_speed_10m");
            double windGusts = current.getDouble("wind_gusts_10m");
            int windDir = current.getInt("wind_direction_10m");
            String beaufort = toBeaufort(windSpeed);

            addLabel(currentCard, "🌬️ VENT", false);
            addInfo(currentCard, formatNum(windSpeed) + " nœuds " + compassDir(windDir)
                    + " · Rafales " + formatNum(windGusts) + " nœuds");
            addInfo(currentCard, "Force " + beaufort + " · Direction " + windDir + "°");

            addSpacer(currentCard, 12);

            // Pressure + humidity
            double pressure = current.getDouble("pressure_msl");
            int humidity = current.getInt("relative_humidity_2m");
            addLabel(currentCard, "📊 ATMOSPHÈRE", false);
            addInfo(currentCard, "Pression " + formatNum(pressure) + " hPa · Humidité " + humidity + "%");

            addSpacer(currentCard, 8);

            // Position
            addMuted(currentCard, String.format(Locale.FRANCE, "📍 %.4f, %.4f", lat, lon));

            contentLayout.addView(currentCard);

            // Hourly forecast
            if (json.has("hourly")) {
                JSONObject hourly = json.getJSONObject("hourly");
                JSONArray times = hourly.getJSONArray("time");
                JSONArray temps = hourly.getJSONArray("temperature_2m");
                JSONArray winds = hourly.getJSONArray("wind_speed_10m");
                JSONArray gusts = hourly.getJSONArray("wind_gusts_10m");
                JSONArray codes = hourly.getJSONArray("weather_code");
                JSONArray precipProb = hourly.optJSONArray("precipitation_probability");

                LinearLayout forecastCard = createCard();
                addLabel(forecastCard, "PRÉVISIONS 24H", true);
                addSpacer(forecastCard, 8);

                SimpleDateFormat sdfIn = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
                SimpleDateFormat sdfOut = new SimpleDateFormat("HH'h'", Locale.FRANCE);

                int count = Math.min(times.length(), 24);
                for (int i = 0; i < count; i += 3) { // Every 3 hours
                    try {
                        Date d = sdfIn.parse(times.getString(i));
                        String timeStr = d != null ? sdfOut.format(d) : times.getString(i);
                        double t = temps.getDouble(i);
                        double w = winds.getDouble(i);
                        double g = gusts.getDouble(i);
                        int wc = codes.getInt(i);
                        String precip = (precipProb != null && !precipProb.isNull(i))
                                ? precipProb.getInt(i) + "%" : "";

                        String line2 = weatherEmoji(wc) + " " + formatNum(t) + "°C  "
                                + "💨 " + formatNum(w) + " nds (raf. " + formatNum(g) + ")"
                                + (precip.isEmpty() ? "" : "  🌧 " + precip);

                        TextView timeLabel = new TextView(this);
                        timeLabel.setText(timeStr + "  " + line2);
                        timeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                        timeLabel.setTextColor(COLOR_TEXT);
                        timeLabel.setPadding(0, dp(4), 0, dp(4));
                        forecastCard.addView(timeLabel);
                    } catch (Exception ignored) {}
                }

                contentLayout.addView(forecastCard);
            }

        } catch (Exception e) {
            Log.e(TAG, "Render weather failed", e);
            showError("Erreur d'affichage météo.");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

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

    private void addLabel(LinearLayout parent, String text, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(COLOR_MUTED);
        tv.setLetterSpacing(0.05f);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        parent.addView(tv);
    }

    private void addInfo(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(COLOR_TEXT);
        tv.setPadding(0, dp(2), 0, dp(2));
        parent.addView(tv);
    }

    private void addMuted(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(COLOR_MUTED);
        tv.setPadding(0, dp(4), 0, 0);
        parent.addView(tv);
    }

    private void addSpacer(LinearLayout parent, int dpHeight) {
        TextView sp = new TextView(this);
        sp.setHeight(dp(dpHeight));
        parent.addView(sp);
    }

    private void showError(String msg) {
        contentLayout.removeAllViews();
        TextView err = new TextView(this);
        err.setText(msg);
        err.setTextColor(Color.parseColor("#ef4444"));
        err.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        err.setPadding(dp(8), dp(20), dp(8), dp(20));
        err.setGravity(Gravity.CENTER);
        contentLayout.addView(err);
    }

    private String formatNum(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        return String.format(Locale.FRANCE, "%.1f", v);
    }

    private String weatherEmoji(int code) {
        if (code == 0) return "☀️";
        if (code <= 3) return "⛅";
        if (code <= 49) return "🌫️";
        if (code <= 59) return "🌦️";
        if (code <= 69) return "🌧️";
        if (code <= 79) return "🌨️";
        if (code <= 82) return "🌧️";
        if (code <= 86) return "🌨️";
        if (code <= 99) return "⛈️";
        return "🌤️";
    }

    private String weatherLabel(int code) {
        if (code == 0) return "Ciel dégagé";
        if (code <= 3) return "Partiellement nuageux";
        if (code <= 49) return "Brouillard";
        if (code <= 55) return "Bruine";
        if (code <= 59) return "Bruine verglaçante";
        if (code <= 65) return "Pluie";
        if (code <= 69) return "Pluie verglaçante";
        if (code <= 75) return "Neige";
        if (code <= 79) return "Grésil";
        if (code <= 82) return "Averses";
        if (code <= 86) return "Averses de neige";
        if (code <= 95) return "Orage";
        if (code <= 99) return "Orage avec grêle";
        return "Variable";
    }

    private String compassDir(int degrees) {
        String[] dirs = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO"};
        int idx = (int) Math.round(((double) degrees % 360) / 22.5) % 16;
        return dirs[idx];
    }

    private String toBeaufort(double knots) {
        if (knots < 1) return "0 (calme)";
        if (knots < 4) return "1 (très légère brise)";
        if (knots < 7) return "2 (légère brise)";
        if (knots < 11) return "3 (petite brise)";
        if (knots < 17) return "4 (jolie brise)";
        if (knots < 22) return "5 (bonne brise)";
        if (knots < 28) return "6 (vent frais)";
        if (knots < 34) return "7 (grand frais)";
        if (knots < 41) return "8 (coup de vent)";
        if (knots < 48) return "9 (fort coup de vent)";
        if (knots < 56) return "10 (tempête)";
        if (knots < 64) return "11 (violente tempête)";
        return "12 (ouragan)";
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
