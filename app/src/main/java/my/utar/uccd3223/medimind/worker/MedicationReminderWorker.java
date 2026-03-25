package my.utar.uccd3223.medimind.worker;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.Objects;
import my.utar.uccd3223.medimind.MainActivity;
import my.utar.uccd3223.medimind.MediMindApplication;
import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.receiver.MedicationActionReceiver;

public class MedicationReminderWorker extends Worker {

    public static final String KEY_MEDICATION_ID = "medication_id";
    public static final String KEY_MEDICATION_NAME = "medication_name";
    public static final String KEY_MEDICATION_DOSAGE = "medication_dosage";
    public static final String KEY_SCHEDULE_ID = "schedule_id";
    public static final String KEY_SCHEDULED_TIME = "scheduled_time";
    public static final String KEY_NOTIFICATION_ID = "notification_id";

    public MedicationReminderWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params
    ) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Get medication details from input data
        long medicationId = getInputData().getLong(KEY_MEDICATION_ID, -1);
        String medicationName = getInputData().getString(KEY_MEDICATION_NAME);
        String medicationDosage = getInputData().getString(KEY_MEDICATION_DOSAGE);
        long scheduleId = getInputData().getLong(KEY_SCHEDULE_ID, -1);
        String scheduledTime = getInputData().getString(KEY_SCHEDULED_TIME);

        if (medicationId == -1 || medicationName == null) {
            return Result.failure();
        }

        // Show notification
        showNotification(medicationId, medicationName, medicationDosage, scheduleId, scheduledTime);

        return Result.success();
    }

    private void showNotification(long medicationId, String name, String dosage,
                                   long scheduleId, String scheduledTime) {
        Context context = getApplicationContext();
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = Objects.hash(medicationId, scheduleId);

        // Intent to open app when notification is clicked
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // "Take" button intent
        Intent takeIntent = new Intent(context, MedicationActionReceiver.class);
        takeIntent.setAction(MedicationActionReceiver.ACTION_TAKE);
        takeIntent.putExtra(KEY_MEDICATION_ID, medicationId);
        takeIntent.putExtra(KEY_SCHEDULE_ID, scheduleId);
        takeIntent.putExtra(KEY_MEDICATION_NAME, name);
        takeIntent.putExtra(KEY_SCHEDULED_TIME, scheduledTime);
        takeIntent.putExtra(KEY_NOTIFICATION_ID, notificationId);
        PendingIntent takePendingIntent = PendingIntent.getBroadcast(
                context,
                Objects.hash(medicationId, scheduleId, 0),
                takeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // "Snooze" button intent
        Intent snoozeIntent = new Intent(context, MedicationActionReceiver.class);
        snoozeIntent.setAction(MedicationActionReceiver.ACTION_SNOOZE);
        snoozeIntent.putExtra(KEY_MEDICATION_ID, medicationId);
        snoozeIntent.putExtra(KEY_SCHEDULE_ID, scheduleId);
        snoozeIntent.putExtra(KEY_MEDICATION_NAME, name);
        snoozeIntent.putExtra(KEY_MEDICATION_DOSAGE, dosage);
        snoozeIntent.putExtra(KEY_SCHEDULED_TIME, scheduledTime);
        snoozeIntent.putExtra(KEY_NOTIFICATION_ID, notificationId);
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                Objects.hash(medicationId, scheduleId, 1),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // "Skip" button intent
        Intent skipIntent = new Intent(context, MedicationActionReceiver.class);
        skipIntent.setAction(MedicationActionReceiver.ACTION_SKIP);
        skipIntent.putExtra(KEY_MEDICATION_ID, medicationId);
        skipIntent.putExtra(KEY_SCHEDULE_ID, scheduleId);
        skipIntent.putExtra(KEY_MEDICATION_NAME, name);
        skipIntent.putExtra(KEY_SCHEDULED_TIME, scheduledTime);
        skipIntent.putExtra(KEY_NOTIFICATION_ID, notificationId);
        PendingIntent skipPendingIntent = PendingIntent.getBroadcast(
                context,
                Objects.hash(medicationId, scheduleId, 2),
                skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context,
                MediMindApplication.CHANNEL_ID
        )
                .setSmallIcon(R.drawable.ic_medication)
                .setContentTitle("Time for " + name)
                .setContentText(dosage != null ? dosage : "Take your medication")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent)
                .addAction(R.drawable.ic_check, "Take", takePendingIntent)
                .addAction(R.drawable.ic_snooze, "Snooze 10 min", snoozePendingIntent)
                .addAction(R.drawable.ic_close, "Skip", skipPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
        }
    }
}