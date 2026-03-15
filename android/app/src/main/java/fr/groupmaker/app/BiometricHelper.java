package fr.groupmaker.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.CookieManager;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Gère le stockage sécurisé du token biométrique et les appels API.
 */
public class BiometricHelper {

    private static final String TAG = "BiometricHelper";
    private static final String API_URL = "https://marina.groupmaker.fr/api/biometric_auth.php";
    private static final String PREFS_FILE = "marina_bio_prefs";
    private static final String KEY_TOKEN = "bio_token";
    private static final String KEY_USER = "bio_user_name";

    /**
     * Retourne les EncryptedSharedPreferences (Android Keystore-backed).
     */
    public static SharedPreferences getSecurePrefs(Context ctx) {
        try {
            MasterKey masterKey = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    ctx, PREFS_FILE, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences", e);
            return null;
        }
    }

    /** Vérifie si un token biométrique est stocké. */
    public static boolean hasToken(Context ctx) {
        SharedPreferences prefs = getSecurePrefs(ctx);
        if (prefs == null) return false;
        String token = prefs.getString(KEY_TOKEN, "");
        return token.length() == 64;
    }

    /** Récupère le token stocké. */
    public static String getToken(Context ctx) {
        SharedPreferences prefs = getSecurePrefs(ctx);
        if (prefs == null) return "";
        return prefs.getString(KEY_TOKEN, "");
    }

    /** Récupère le nom d'utilisateur stocké. */
    public static String getUserName(Context ctx) {
        SharedPreferences prefs = getSecurePrefs(ctx);
        if (prefs == null) return "";
        return prefs.getString(KEY_USER, "");
    }

    /** Stocke un token biométrique. */
    public static void saveToken(Context ctx, String token, String userName) {
        SharedPreferences prefs = getSecurePrefs(ctx);
        if (prefs == null) return;
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER, userName)
                .apply();
    }

    /** Supprime le token stocké. */
    public static void clearToken(Context ctx) {
        SharedPreferences prefs = getSecurePrefs(ctx);
        if (prefs == null) return;
        prefs.edit().clear().apply();
    }

    /**
     * Appelle l'API register pour obtenir un token (appelé quand user est connecté).
     * Retourne le token ou null en cas d'erreur.
     */
    public static String registerToken(String cookies, String deviceName) {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Cookie", cookies);
            conn.setRequestProperty("User-Agent", "MarinaApp/1.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            String body = "action=register&platform=android&device_name="
                    + java.net.URLEncoder.encode(deviceName, "UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.e(TAG, "Register failed: HTTP " + code);
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();

            JSONObject json = new JSONObject(sb.toString());
            if (json.optBoolean("ok")) {
                return json.getString("token");
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Register token failed", e);
            return null;
        }
    }

    /**
     * Appelle l'API login avec le token biométrique.
     * Retourne le JSON de réponse ou null.
     */
    public static JSONObject loginWithToken(String token) {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "MarinaApp/1.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(false);

            String body = "action=login&token=" + token;
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.close();

            // Capturer les cookies de session retournés
            int code = conn.getResponseCode();
            String setCookie = conn.getHeaderField("Set-Cookie");

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();

            JSONObject json = new JSONObject(sb.toString());

            // Stocker le cookie PHPSESSID dans le CookieManager WebView
            if (code == 200 && setCookie != null) {
                CookieManager cm = CookieManager.getInstance();
                cm.setCookie("https://marina.groupmaker.fr", setCookie);
                cm.flush();
                json.put("session_cookie", setCookie);
            }

            json.put("http_code", code);
            return json;

        } catch (Exception e) {
            Log.e(TAG, "Login with token failed", e);
            return null;
        }
    }
}
