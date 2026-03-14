package fr.groupmaker.marina;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import fr.groupmaker.app.MainActivity;
import fr.groupmaker.app.R;

public class MarinaFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID   = "marina_chat";
    private static final String CHANNEL_NAME = "Messages";

    // Called when a new FCM token is generated (install or token refresh)
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Store locally — MainActivity will send it to the server on next launch
        getSharedPreferences("marina_prefs", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .putBoolean("fcm_token_sent", false)
            .apply();
    }

    // Called when a push arrives while app is in foreground or background
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title  = remoteMessage.getData().get("title");
        String body   = remoteMessage.getData().get("body");
        String url    = remoteMessage.getData().get("url");
        String avatar = remoteMessage.getData().get("avatar_url");

        if (title == null) {
            // Fallback to notification payload if no data payload
            if (remoteMessage.getNotification() != null) {
                title = remoteMessage.getNotification().getTitle();
                body  = remoteMessage.getNotification().getBody();
            }
        }

        showNotification(title, body, url, avatar);
    }

    private void showNotification(String title, String body, String url, String avatarUrl) {
        NotificationManager manager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create channel (required Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications de chat Marina");
            manager.createNotificationChannel(channel);
        }

        // Tapping the notification opens the app at the given URL
        Intent intent = new Intent(this, MainActivity.class);
        if (url != null && !url.isEmpty()) {
            intent.putExtra("launch_url", url);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title != null ? title : "Marina")
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent);

        // Load sender avatar if available
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Bitmap avatar = fetchBitmap(avatarUrl);
            if (avatar != null) {
                builder.setLargeIcon(avatar)
                       .setStyle(new NotificationCompat.BigTextStyle().bigText(body));
            }
        }

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private Bitmap fetchBitmap(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setDoInput(true);
            conn.connect();
            InputStream input = conn.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            return null;
        }
    }
}
