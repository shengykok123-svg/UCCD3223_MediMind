package my.utar.uccd3223.medimind;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import dagger.hilt.android.HiltAndroidApp;
import my.utar.uccd3223.medimind.util.ThemeManager;

@HiltAndroidApp
public class MediMindApplication extends Application {

    public static final String CHANNEL_ID = "medication_reminders";
    public static final String COMMUNITY_CHANNEL_ID = "community_notifications";

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applyStoredTheme(this);
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Medication Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders for medication times");
            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationChannel communityChannel = new NotificationChannel(
                    COMMUNITY_CHANNEL_ID,
                    "Community Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            communityChannel.setDescription("Friend requests and community updates");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                notificationManager.createNotificationChannel(communityChannel);
            }
        }
    }
}
