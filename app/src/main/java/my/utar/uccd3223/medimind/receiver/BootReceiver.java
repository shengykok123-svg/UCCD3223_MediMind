package my.utar.uccd3223.medimind.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executors;

import my.utar.uccd3223.medimind.data.local.database.MediMindDatabase;
import my.utar.uccd3223.medimind.data.local.database.entities.Medication;
import my.utar.uccd3223.medimind.data.local.database.entities.Schedule;
import my.utar.uccd3223.medimind.util.ReminderScheduler;

/**
 * Re-schedules all active medication reminders after device reboot.
 * AlarmManager alarms are lost on reboot, so we need to recreate them.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        Log.d(TAG, "Device booted — rescheduling medication reminders");

        final PendingResult pendingResult = goAsync();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                MediMindDatabase db = MediMindDatabase.getInstance(context);
                ReminderScheduler scheduler = new ReminderScheduler(context);

                List<Schedule> activeSchedules = db.scheduleDao().getAllActiveSchedulesSync();
                if (activeSchedules == null) return;

                for (Schedule schedule : activeSchedules) {
                    Medication medication = db.medicationDao()
                            .getMedicationById(schedule.getMedicationId());
                    if (medication != null && !medication.isDeleted()) {
                        scheduler.scheduleReminder(medication, schedule);
                    }
                }

                Log.d(TAG, "Rescheduled reminders for " + activeSchedules.size() + " schedules");
            } catch (Exception e) {
                Log.e(TAG, "Error rescheduling reminders on boot", e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
