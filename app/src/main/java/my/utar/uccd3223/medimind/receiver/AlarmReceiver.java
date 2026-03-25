package my.utar.uccd3223.medimind.receiver;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import java.util.Objects;

import my.utar.uccd3223.medimind.MainActivity;
import my.utar.uccd3223.medimind.MediMindApplication;
import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.worker.MedicationReminderWorker;

/**
 * BroadcastReceiver triggered by AlarmManager at scheduled medication times.
 * Shows a notification with Take/Snooze/Skip actions.
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long medicationId = intent.getLongExtra(MedicationReminderWorker.KEY_MEDICATION_ID, -1);
        String medicationName = intent.getStringExtra(MedicationReminderWorker.KEY_MEDICATION_NAME);
        String medicationDosage = intent.getStringExtra(MedicationReminderWorker.KEY_MEDICATION_DOSAGE);
        long scheduleId = intent.getLongExtra(MedicationReminderWorker.KEY_SCHEDULE_ID, -1);
        String scheduledTime = intent.getStringExtra(MedicationReminderWorker.KEY_SCHEDULED_TIME);

        if (medicationId == -1 || medicationName == null) return;

        showNotification(context, medicationId, medicationName, medicationDosage,
                scheduleId, scheduledTime);
    }

    private void showNotification(Context context, long medicationId, String name,
                                   String dosage, long scheduleId, String scheduledTime) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = Objects.hash(medicationId, scheduleId);

        // Intent to open app
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, notificationId, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // "Take" action
        Intent takeIntent = new Intent(context, MedicationActionReceiver.class);
        takeIntent.setAction(MedicationActionReceiver.ACTION_TAKE);
        takeIntent.putExtra(MedicationReminderWorker.KEY_MEDICATION_ID, medicationId);
        takeIntent.putExtra(MedicationReminderWorker.KEY_SCHEDULE_ID, scheduleId);
        takeIntent.putExtra(MedicationReminderWorker.KEY_MEDICATION_NAME, name);
        takeIntent.putExtra(MedicationReminderWorker.KEY_SCHEDULED_TIME, scheduledTime);
        takeIntent.putExtra(MedicationReminderWorker.KEY_NOTIFICATION_ID, notificationId);
        PendingIntent takePendingIntent = PendingIntent.getBroadcast(
                context, Objects.hash(medicationId, scheduleId, 0), takeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // "Snooze" action
        Intent snoozeIntent = new Intent(context, MedicationActionReceiver.class);
        snoozeIntent.setAction(MedicationActionReceiver.ACTION_SNOOZE);
        snoozeIntent.putExtra(MedicationReminderWorker.KEY_MEDICATION_ID, medicationId);
        snoozeIntent.putExtra(MedicationReminderWorker.KEY_SCHEDULE_ID, scheduleId);
        snoozeIntent.putExtra(MedicationReminderWorker.KEY_MEDICATION_NAME, name);
        snoozeIntent.putExtra(MedicationReminderWorker.KEY_MEDICATION_DOSAGE, dosage);
        snoozeIntent.putExtra(MedicationReminderWorker.KEY_SCHEDULED_TIME, scheduledTime);
        snoozeIntent.putExtra(MedicationReminderWorker.KEY_NOTIFICATION_ID, notificationId);
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                context, Objects.hash(medicationId, scheduleId, 1), snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // "Skip" action
        Intent skipIntent = new Intent(context, MedicationActionReceiver.class);
        skipIntent.setAction(MedicationActionReceiver.ACTION_SKIP);
        skipIntent.putExtra(MedicationReminderWorker.KEY_MEDICATION_ID, medicationId);
        skipIntent.putExtra(MedicationReminderWorker.KEY_SCHEDULE_ID, scheduleId);
        skipIntent.putExtra(MedicationReminderWorker.KEY_MEDICATION_NAME, name);
        skipIntent.putExtra(MedicationReminderWorker.KEY_SCHEDULED_TIME, scheduledTime);
        skipIntent.putExtra(MedicationReminderWorker.KEY_NOTIFICATION_ID, notificationId);
        PendingIntent skipPendingIntent = PendingIntent.getBroadcast(
                context, Objects.hash(medicationId, scheduleId, 2), skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, MediMindApplication.CHANNEL_ID)
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
