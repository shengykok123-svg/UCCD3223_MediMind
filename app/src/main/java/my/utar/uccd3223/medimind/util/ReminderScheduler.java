package my.utar.uccd3223.medimind.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import my.utar.uccd3223.medimind.data.local.database.entities.Medication;
import my.utar.uccd3223.medimind.data.local.database.entities.Schedule;
import my.utar.uccd3223.medimind.receiver.AlarmReceiver;
import my.utar.uccd3223.medimind.worker.MedicationReminderWorker;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Schedules and cancels Android alarm reminders for each medication schedule.
 */
public class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";
    private final Context context;
    private final AlarmManager alarmManager;

    /**
     * Stores the application context and AlarmManager used for reminder scheduling.
     */
    public ReminderScheduler(Context context) {
        this.context = context;
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    /**
     * Schedule reminder for a medication based on its schedule
     */
    /**
     * Parses a schedule time and delegates creation of future alarms.
     */
    public void scheduleReminder(Medication medication, Schedule schedule) {
        if (!schedule.isActive()) {
            return;
        }

        // Parse time from schedule (format: "09:00")
        LocalTime reminderTime = LocalTime.parse(schedule.getTime(),
                DateTimeFormatter.ofPattern("HH:mm"));

        // Schedule for today + next 6 days (7 days total)
        scheduleWeeklyReminders(medication, schedule, reminderTime);
    }

    /**
     * Schedule reminders for today + next 6 days
     */
    /**
     * Expands the selected days of week into individual alarm occurrences.
     */
    private void scheduleWeeklyReminders(Medication medication, Schedule schedule, LocalTime time) {
        String daysOfWeek = schedule.getDaysOfWeek();
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            // Daily medication - schedule for today + next 6 days
            for (int i = 0; i <= 6; i++) {
                LocalDateTime reminderDateTime = LocalDateTime.of(
                        LocalDate.now().plusDays(i),
                        time
                );
                scheduleAlarm(medication, schedule, reminderDateTime);
            }
        } else {
            // Specific days - check today + next 6 days for matching days
            String[] days = daysOfWeek.split(",");
            Set<Integer> targetDays = new HashSet<>();
            for (String dayStr : days) {
                targetDays.add(Integer.parseInt(dayStr.trim()));
            }
            for (int i = 0; i <= 6; i++) {
                LocalDate day = LocalDate.now().plusDays(i);
                int dayISO = day.getDayOfWeek().getValue();
                if (targetDays.contains(dayISO)) {
                    scheduleAlarm(medication, schedule, LocalDateTime.of(day, time));
                }
            }
        }
    }

    /**
     * Schedule an exact alarm for a specific reminder time
     */
    /**
     * Creates the exact AlarmManager entry and embeds medication/schedule details in the Intent.
     */
    private void scheduleAlarm(Medication medication, Schedule schedule, LocalDateTime reminderDateTime) {
        long triggerAtMillis = reminderDateTime.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        if (triggerAtMillis <= System.currentTimeMillis()) {
            return; // Time has passed
        }

        String scheduledTimeStr = reminderDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("my.utar.uccd3223.medimind.ALARM_REMINDER");
        intent.putExtra(MedicationReminderWorker.KEY_MEDICATION_ID, medication.getId());
        intent.putExtra(MedicationReminderWorker.KEY_MEDICATION_NAME, medication.getName());
        intent.putExtra(MedicationReminderWorker.KEY_MEDICATION_DOSAGE, medication.getDosage());
        intent.putExtra(MedicationReminderWorker.KEY_SCHEDULE_ID, schedule.getId());
        intent.putExtra(MedicationReminderWorker.KEY_SCHEDULED_TIME, scheduledTimeStr);

        int requestCode = Objects.hash(medication.getId(), schedule.getId(),
                reminderDateTime.toLocalDate().toString());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    // Fallback: use inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot schedule exact alarm, using inexact", e);
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    /**
     * Cancel all reminders for a medication by cancelling alarms for all possible schedules/dates
     */
    /**
     * Cancels all generated alarms for a medication across possible schedule/day combinations.
     */
    public void cancelReminders(long medicationId) {
        // Cancel alarms for today + next 6 days for reasonable schedule IDs
        // Since we can't enumerate all, cancel the known PendingIntents
        // The AlarmManager will simply ignore cancel calls for non-existent alarms
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("my.utar.uccd3223.medimind.ALARM_REMINDER");

        for (int scheduleId = 0; scheduleId < 100; scheduleId++) {
            for (int i = 0; i <= 6; i++) {
                String dateStr = LocalDate.now().plusDays(i).toString();
                int requestCode = Objects.hash(medicationId, (long) scheduleId, dateStr);
                PendingIntent pi = PendingIntent.getBroadcast(
                        context, requestCode, intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) {
                    alarmManager.cancel(pi);
                    pi.cancel();
                }
            }
        }
    }

    /**
     * Cancel specific reminder
     */
    /**
     * Cancels alarms for one medication schedule while leaving other schedules intact.
     */
    public void cancelReminder(long medicationId, long scheduleId) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("my.utar.uccd3223.medimind.ALARM_REMINDER");

        for (int i = 0; i <= 6; i++) {
            String dateStr = LocalDate.now().plusDays(i).toString();
            int requestCode = Objects.hash(medicationId, scheduleId, dateStr);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) {
                alarmManager.cancel(pi);
                pi.cancel();
            }
        }
    }
}
