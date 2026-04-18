package my.utar.uccd3223.medimind.receiver;

import android.app.NotificationManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import my.utar.uccd3223.medimind.data.local.database.MediMindDatabase;
import my.utar.uccd3223.medimind.data.local.database.entities.AdherenceLog;
import my.utar.uccd3223.medimind.data.local.database.entities.Medication;
import my.utar.uccd3223.medimind.data.local.database.entities.MedicationScheduleItem;
import my.utar.uccd3223.medimind.data.local.database.entities.Schedule;
import my.utar.uccd3223.medimind.util.Constants;
import my.utar.uccd3223.medimind.util.DateTimeUtils;
import my.utar.uccd3223.medimind.worker.MedicationReminderWorker;

public class MedicationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_TAKE = "my.utar.uccd3223.medimind.ACTION_TAKE";
    public static final String ACTION_SNOOZE = "my.utar.uccd3223.medimind.ACTION_SNOOZE";
    public static final String ACTION_SKIP = "my.utar.uccd3223.medimind.ACTION_SKIP";

    private static final ExecutorService SHARED_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        long medicationId = intent.getLongExtra(
                MedicationReminderWorker.KEY_MEDICATION_ID, -1
        );
        long scheduleId = intent.getLongExtra(
                MedicationReminderWorker.KEY_SCHEDULE_ID, -1
        );
        String medicationName = intent.getStringExtra(
                MedicationReminderWorker.KEY_MEDICATION_NAME
        );
        String scheduledTime = intent.getStringExtra(
                MedicationReminderWorker.KEY_SCHEDULED_TIME
        );
        int notificationId = intent.getIntExtra(
                MedicationReminderWorker.KEY_NOTIFICATION_ID, (int) medicationId
        );

        // Dismiss notification using correct notification ID
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(notificationId);
        }

        switch (action) {
            case ACTION_TAKE:
                handleTake(context, medicationId, scheduleId, medicationName, scheduledTime);
                break;
            case ACTION_SNOOZE:
                handleSnooze(context, intent, medicationId);
                break;
            case ACTION_SKIP:
                handleSkip(context, medicationId, scheduleId, medicationName, scheduledTime);
                break;
        }
    }

    private void handleTake(Context context, long medicationId, long scheduleId,
                             String name, String scheduledTime) {
        logAdherence(context, medicationId, scheduleId, Constants.STATUS_TAKEN, scheduledTime);
        Toast.makeText(context, name + " marked as taken", Toast.LENGTH_SHORT).show();
    }

    private void handleSnooze(Context context, Intent intent, long medicationId) {
        String medicationName = intent.getStringExtra(
                MedicationReminderWorker.KEY_MEDICATION_NAME
        );
        String medicationDosage = intent.getStringExtra(
                MedicationReminderWorker.KEY_MEDICATION_DOSAGE
        );
        long scheduleId = intent.getLongExtra(
                MedicationReminderWorker.KEY_SCHEDULE_ID, -1
        );
        String scheduledTime = intent.getStringExtra(
                MedicationReminderWorker.KEY_SCHEDULED_TIME
        );

        // Schedule reminder again after 10 minutes via AlarmManager
        Intent alarmIntent = new Intent(context, AlarmReceiver.class);
        alarmIntent.setAction("my.utar.uccd3223.medimind.ALARM_REMINDER");
        alarmIntent.putExtra(MedicationReminderWorker.KEY_MEDICATION_ID, medicationId);
        alarmIntent.putExtra(MedicationReminderWorker.KEY_MEDICATION_NAME, medicationName);
        alarmIntent.putExtra(MedicationReminderWorker.KEY_MEDICATION_DOSAGE, medicationDosage);
        alarmIntent.putExtra(MedicationReminderWorker.KEY_SCHEDULE_ID, scheduleId);
        alarmIntent.putExtra(MedicationReminderWorker.KEY_SCHEDULED_TIME, scheduledTime);

        int requestCode = Objects.hash(medicationId, scheduleId, "snooze");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAt = System.currentTimeMillis() + 10 * 60 * 1000; // 10 minutes
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                }
            } catch (SecurityException e) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        }

        Toast.makeText(context, "Reminder snoozed for 10 minutes", Toast.LENGTH_SHORT).show();
    }

    private void handleSkip(Context context, long medicationId, long scheduleId,
                             String name, String scheduledTime) {
        logAdherence(context, medicationId, scheduleId, Constants.STATUS_SKIPPED, scheduledTime);
        Toast.makeText(context, name + " skipped", Toast.LENGTH_SHORT).show();
    }

    private void logAdherence(Context context, long medicationId, long scheduleId,
                               String status, String scheduledTime) {
        final PendingResult goAsync = goAsync();
        SHARED_EXECUTOR.execute(() -> {
            try {
                MediMindDatabase db = MediMindDatabase.getInstance(context);

                // Use the actual scheduled time from the notification, fallback to current time
                String scheduledDateTime;
                if (scheduledTime != null && !scheduledTime.isEmpty()) {
                    // scheduledTime is "yyyy-MM-dd HH:mm", append ":00" for consistency
                    scheduledDateTime = scheduledTime + ":00";
                } else {
                    scheduledDateTime = DateTimeUtils.getCurrentDateTime();
                }

                String now = DateTimeUtils.getCurrentDateTime();
                AdherenceLog log = new AdherenceLog(
                        medicationId,
                        scheduleId,
                        scheduledDateTime,
                        status
                );

                if (status.equals(Constants.STATUS_TAKEN)) {
                    log.setTakenDateTime(now);
                }

                // Look up Firestore IDs from Room cache
                Medication med = db.medicationDao().getMedicationById(medicationId);
                Schedule sch = db.scheduleDao().getScheduleById(scheduleId);

                String medFsId = med != null ? med.getFirestoreId() : null;
                String schFsId = sch != null && sch.getFirestoreId() != null ? sch.getFirestoreId() : "";

                if (medFsId != null) {
                    log.setMedicationFirestoreId(medFsId);
                    log.setScheduleFirestoreId(schFsId);
                }

                // Build deterministic doc ID for Firestore dedup
                String scheduledDate = scheduledDateTime.contains(" ")
                        ? scheduledDateTime.substring(0, scheduledDateTime.indexOf(" "))
                        : LocalDate.now().toString();
                String deterministicId = (medFsId != null ? medFsId : String.valueOf(medicationId))
                        + "_" + schFsId + "_" + scheduledDate;

                log.setFirestoreId(deterministicId);
                db.adherenceDao().insert(log);

                // Write to Firestore with deterministic ID
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null && medFsId != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("medicationFirestoreId", medFsId);
                    data.put("scheduleFirestoreId", schFsId);
                    data.put("scheduledDateTime", scheduledDateTime);
                    data.put("takenDateTime", status.equals(Constants.STATUS_TAKEN) ? now : null);
                    data.put("status", status);
                    data.put("notes", "");

                    FirebaseFirestore.getInstance()
                            .collection("users").document(user.getUid())
                            .collection("adherence_logs").document(deterministicId).set(data);

                    // Update daily_adherence summary in Firestore
                    updateDailyAdherenceSummary(context, db, user.getUid());
                }
            } finally {
                goAsync.finish();
            }
        });
    }

    private void updateDailyAdherenceSummary(Context context, MediMindDatabase db, String uid) {
        try {
            String today = LocalDate.now().toString();
            List<MedicationScheduleItem> allItems = db.scheduleDao().getMedicationSchedulesForDate(today);

            List<MedicationScheduleItem> filtered = new java.util.ArrayList<>();
            for (MedicationScheduleItem item : allItems) {
                if (item.isScheduledForDate(today)) {
                    filtered.add(item);
                }
            }

            String dateStart = today + " 00:00";
            String dateEnd = today + " 23:59";
            int takenCount = 0;
            int missedCount = 0;

            for (MedicationScheduleItem item : filtered) {
                String st = db.adherenceDao().getStatusForScheduleOnDate(
                        item.getId(), item.getScheduleId(), dateStart, dateEnd);
                if ("TAKEN".equals(st)) {
                    takenCount++;
                    item.setTodayStatus("TAKEN");
                } else if ("SKIPPED".equals(st)) {
                    item.setTodayStatus("SKIPPED");
                } else {
                    item.setTodayStatus("PENDING");
                    try {
                        java.time.LocalTime scheduledTime = java.time.LocalTime.parse(item.getTime());
                        if (java.time.LocalTime.now().isAfter(scheduledTime)) {
                            item.setTodayStatus("MISSED");
                            missedCount++;
                        }
                    } catch (Exception e) { /* keep as PENDING */ }
                }
            }

            int pendingCount = filtered.size() - takenCount - missedCount;
            if (pendingCount < 0) pendingCount = 0;

            // Fetch today's adherence logs to look up takenDateTime
            List<AdherenceLog> todayLogs = db.adherenceDao().getLogsBetweenDatesSync(dateStart, dateEnd);

            List<Map<String, Object>> medications = new java.util.ArrayList<>();
            for (MedicationScheduleItem item : filtered) {
                Map<String, Object> med = new HashMap<>();
                // New field names
                med.put("medicationName", item.getName());
                med.put("scheduledTime", item.getTime() != null ? item.getTime() : "");
                // Backwards compatibility field names
                med.put("name", item.getName());
                med.put("time", item.getTime() != null ? item.getTime() : "");
                // Common fields
                med.put("dosage", item.getDosage() != null ? item.getDosage() : "");
                med.put("status", item.getTodayStatus() != null ? item.getTodayStatus() : "PENDING");
                med.put("instructions", item.getInstructions() != null ? item.getInstructions() : "");
                med.put("medicationFirestoreId", item.getFirestoreId() != null ? item.getFirestoreId() : "");
                med.put("scheduleFirestoreId", item.getScheduleFirestoreId() != null ? item.getScheduleFirestoreId() : "");
                med.put("finalized", false);
                med.put("lastUpdatedAt", com.google.firebase.Timestamp.now());
                med.put("updatedBy", "client");

                // Look up takenTime from adherence logs
                if ("TAKEN".equals(item.getTodayStatus())) {
                    String takenTime = findTakenTime(todayLogs, item.getId(), item.getScheduleId());
                    if (takenTime != null) {
                        med.put("takenTime", takenTime);
                    }
                }

                medications.add(med);
            }

            double adherenceRate = filtered.size() > 0
                    ? Math.round((double) takenCount / filtered.size() * 100.0) / 100.0 : 0;

            Map<String, Object> data = new HashMap<>();
            data.put("date", today);
            data.put("takenCount", takenCount);
            data.put("missedCount", missedCount);
            data.put("pendingCount", pendingCount);
            data.put("totalScheduled", filtered.size());
            data.put("totalCount", filtered.size());
            data.put("medications", medications);
            data.put("adherenceRate", adherenceRate);
            data.put("finalized", false);
            data.put("lastComputedAt", com.google.firebase.Timestamp.now());
            data.put("lastUpdated", com.google.firebase.Timestamp.now());
            data.put("computedBy", "client");

            FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("daily_adherence").document(today)
                    .set(data);
        } catch (Exception e) {
            android.util.Log.e("MedicationActionReceiver", "updateDailyAdherenceSummary error", e);
        }
    }

    private String findTakenTime(List<AdherenceLog> logs, long medicationId, long scheduleId) {
        for (AdherenceLog log : logs) {
            if (log.getMedicationId() == medicationId && log.getScheduleId() == scheduleId
                    && "TAKEN".equals(log.getStatus()) && log.getTakenDateTime() != null) {
                return log.getTakenDateTime();
            }
        }
        return null;
    }
}
