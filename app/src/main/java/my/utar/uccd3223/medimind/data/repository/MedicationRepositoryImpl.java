package my.utar.uccd3223.medimind.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import my.utar.uccd3223.medimind.data.local.database.dao.AdherenceDao;
import my.utar.uccd3223.medimind.data.local.database.dao.MedicationDao;
import my.utar.uccd3223.medimind.data.local.database.dao.ScheduleDao;
import my.utar.uccd3223.medimind.data.local.database.entities.AdherenceLog;
import my.utar.uccd3223.medimind.data.local.database.entities.Medication;
import my.utar.uccd3223.medimind.data.local.database.entities.MedicationScheduleItem;
import my.utar.uccd3223.medimind.data.local.database.entities.Schedule;
import my.utar.uccd3223.medimind.domain.repository.MedicationRepository;
import my.utar.uccd3223.medimind.util.DateTimeUtils;
import my.utar.uccd3223.medimind.util.ReminderScheduler;

public class MedicationRepositoryImpl implements MedicationRepository {

    private static final String TAG = "MedicationRepo";

    private final MedicationDao medicationDao;
    private final ScheduleDao scheduleDao;
    private final AdherenceDao adherenceDao;
    private final Executor executor;
    private final ReminderScheduler reminderScheduler;
    private final FirebaseFirestore firestore;

    @Inject
    public MedicationRepositoryImpl(
            MedicationDao medicationDao,
            ScheduleDao scheduleDao,
            AdherenceDao adherenceDao,
            Executor executor,
            Context context,
            FirebaseFirestore firestore
    ) {
        this.medicationDao = medicationDao;
        this.scheduleDao = scheduleDao;
        this.adherenceDao = adherenceDao;
        this.executor = executor;
        this.reminderScheduler = new ReminderScheduler(context);
        this.firestore = firestore;
    }

    private String getUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // ─── Read methods (Room-based, unchanged interface) ─────────────

    @Override
    public LiveData<List<Medication>> getActiveMedications(String today) {
        return medicationDao.getActiveMedications(today);
    }

    @Override
    public Medication getMedicationById(long id) {
        return medicationDao.getMedicationById(id);
    }

    @Override
    public LiveData<List<Medication>> getAllMedications(String today) {
        return medicationDao.getAllMedications(today);
    }

    @Override
    public long addMedication(Medication medication) {
        final long[] id = new long[1];
        executor.execute(() -> {
            id[0] = medicationDao.insert(medication);
        });
        return id[0];
    }

    @Override
    public void updateMedication(Medication medication) {
        executor.execute(() -> medicationDao.update(medication));
    }

    @Override
    public void deleteMedication(Medication medication) {
        executor.execute(() -> medicationDao.delete(medication));
    }

    @Override
    public LiveData<List<Medication>> searchMedications(String query) {
        return medicationDao.searchMedications(query);
    }

    @Override
    public void getMedicationSchedulesForDate(String dateStr, int dayOfWeekISO, ScheduleCallback callback) {
        executor.execute(() -> {
            try {
                List<MedicationScheduleItem> allItems = scheduleDao.getMedicationSchedulesForDate(dateStr);

                // Also include deleted medications that were active on this date
                List<MedicationScheduleItem> deletedItems = scheduleDao.getDeletedMedicationSchedulesForDate(dateStr);
                if (deletedItems != null && !deletedItems.isEmpty()) {
                    Set<String> existingKeys = new HashSet<>();
                    for (MedicationScheduleItem item : allItems) {
                        existingKeys.add(item.getId() + "_" + item.getScheduleId());
                    }
                    for (MedicationScheduleItem item : deletedItems) {
                        String key = item.getId() + "_" + item.getScheduleId();
                        if (!existingKeys.contains(key)) {
                            allItems.add(item);
                        }
                    }
                }

                List<MedicationScheduleItem> filtered = new ArrayList<>();
                for (MedicationScheduleItem item : allItems) {
                    if (item.isScheduledForDate(dateStr)) {
                        filtered.add(item);
                    }
                }

                String dateStart = dateStr + " 00:00";
                String dateEnd = dateStr + " 23:59";
                int takenCount = 0;
                int missedCount = 0;

                boolean isToday = dateStr.equals(LocalDate.now().toString());
                boolean isPast = LocalDate.parse(dateStr).isBefore(LocalDate.now());

                for (MedicationScheduleItem item : filtered) {
                    String status = adherenceDao.getStatusForScheduleOnDate(
                            item.getId(), item.getScheduleId(), dateStart, dateEnd);
                    if (status != null) {
                        item.setTodayStatus(status);
                        if ("TAKEN".equals(status)) {
                            takenCount++;
                        }
                    } else {
                        item.setTodayStatus("PENDING");
                    }

                    if ("PENDING".equals(item.getTodayStatus())) {
                        if (isPast) {
                            item.setTodayStatus("MISSED");
                        } else if (isToday) {
                            try {
                                LocalTime scheduledTime = LocalTime.parse(item.getTime());
                                if (LocalTime.now().isAfter(scheduledTime)) {
                                    item.setTodayStatus("MISSED");
                                }
                                // Today's future-time items stay PENDING (user can take early)
                            } catch (Exception e) { /* keep as PENDING */ }
                        } else {
                            // Future date
                            item.setTodayStatus("UPCOMING");
                        }
                    }

                    if ("MISSED".equals(item.getTodayStatus())) {
                        missedCount++;
                    }
                }

                // Auto-update daily_adherence summary in Firestore if this is today
                if (isToday) {
                    updateDailyAdherenceSummary(dateStr, filtered, takenCount, filtered.size(), missedCount);
                }

                callback.onResult(filtered, takenCount, filtered.size(), missedCount);
            } catch (Exception e) {
                callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
            }
        });
    }

    // ─── Firestore-first write: recordTaken ─────────────────────────

    @Override
    public void recordTaken(long medicationId, long scheduleId, String scheduledDateTime, TakeCallback callback) {
        executor.execute(() -> {
            try {
                String now = DateTimeUtils.getCurrentDateTime();
                AdherenceLog log = new AdherenceLog(medicationId, scheduleId, scheduledDateTime, "TAKEN");
                log.setTakenDateTime(now);

                String uid = getUid();
                Medication med = medicationDao.getMedicationById(medicationId);
                Schedule sch = scheduleDao.getScheduleById(scheduleId);

                String medFsId = med != null ? med.getFirestoreId() : null;
                String schFsId = sch != null && sch.getFirestoreId() != null ? sch.getFirestoreId() : "";

                if (uid != null && medFsId != null) {
                    // Build deterministic doc ID to prevent duplicates
                    String scheduledDate = scheduledDateTime != null && scheduledDateTime.contains(" ")
                            ? scheduledDateTime.substring(0, scheduledDateTime.indexOf(" "))
                            : scheduledDateTime;
                    String deterministicId = medFsId + "_" + schFsId + "_" + scheduledDate;

                    // Write to Firestore first
                    Map<String, Object> data = new HashMap<>();
                    data.put("medicationFirestoreId", medFsId);
                    data.put("scheduleFirestoreId", schFsId);
                    data.put("scheduledDateTime", scheduledDateTime);
                    data.put("takenDateTime", now);
                    data.put("status", "TAKEN");
                    data.put("notes", "");
                    data.put("createdAt", Timestamp.now());

                    Tasks.await(
                            firestore.collection("users").document(uid)
                                    .collection("adherence_logs").document(deterministicId).set(data)
                    );

                    log.setFirestoreId(deterministicId);
                    log.setMedicationFirestoreId(medFsId);
                    log.setScheduleFirestoreId(schFsId);
                }

                // Then cache in Room
                adherenceDao.insertSync(log);

                // Update daily adherence summary
                updateDailyAdherenceSummaryAfterAction();

                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "recordTaken error", e);
                // Still try to save locally even if Firestore fails
                try {
                    AdherenceLog log = new AdherenceLog(medicationId, scheduleId, scheduledDateTime, "TAKEN");
                    log.setTakenDateTime(DateTimeUtils.getCurrentDateTime());
                    adherenceDao.insertSync(log);
                    if (callback != null) callback.onSuccess();
                } catch (Exception localErr) {
                    if (callback != null) callback.onError(localErr.getMessage());
                }
            }
        });
    }

    // ─── Firestore-first write: addMedicationWithSchedule ───────────

    public void addMedicationWithSchedule(
            Medication medication,
            Schedule schedule,
            ReminderCallback callback
    ) {
        List<Schedule> schedules = new ArrayList<>();
        schedules.add(schedule);
        addMedicationWithSchedule(medication, schedules, callback);
    }

    public void addMedicationWithSchedule(
            Medication medication,
            List<Schedule> schedules,
            ReminderCallback callback
    ) {
        executor.execute(() -> {
            try {
                String uid = getUid();

                if (uid != null) {
                    // 1. Write medication to Firestore first
                    Map<String, Object> medData = buildMedicationMap(medication);
                    DocumentReference medDocRef = Tasks.await(
                            firestore.collection("users").document(uid)
                                    .collection("medications").add(medData)
                    );
                    medication.setFirestoreId(medDocRef.getId());
                }

                // 2. Insert into Room cache
                long medicationId = medicationDao.insert(medication);
                medication.setId(medicationId);

                // 3. Process each schedule
                for (Schedule schedule : schedules) {
                    schedule.setMedicationId(medicationId);

                    if (uid != null && medication.getFirestoreId() != null) {
                        // Write schedule to Firestore
                        Map<String, Object> schData = buildScheduleMap(schedule, medication.getFirestoreId());
                        DocumentReference schDocRef = Tasks.await(
                                firestore.collection("users").document(uid)
                                        .collection("schedules").add(schData)
                        );
                        schedule.setFirestoreId(schDocRef.getId());
                        schedule.setMedicationFirestoreId(medication.getFirestoreId());
                    }

                    // Insert schedule into Room cache
                    long scheduleId = scheduleDao.insert(schedule);
                    schedule.setId(scheduleId);

                    // Schedule local reminders
                    reminderScheduler.scheduleReminder(medication, schedule);
                }

                if (callback != null) {
                    callback.onSuccess(medicationId);
                }
            } catch (Exception e) {
                Log.e(TAG, "addMedicationWithSchedule error", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    // ─── Firestore-first write: updateMedicationWithSchedule ────────

    public void updateMedicationWithSchedule(Medication medication, Schedule schedule, ReminderCallback callback) {
        List<Schedule> schedules = new ArrayList<>();
        schedules.add(schedule);
        updateMedicationWithSchedule(medication, schedules, callback);
    }

    public void updateMedicationWithSchedule(Medication medication, List<Schedule> schedules, ReminderCallback callback) {
        executor.execute(() -> {
            try {
                String uid = getUid();
                medication.setUpdatedAt(System.currentTimeMillis());

                if (uid != null && medication.getFirestoreId() != null) {
                    // 1. Update medication in Firestore
                    Map<String, Object> medData = buildMedicationMap(medication);
                    Tasks.await(
                            firestore.collection("users").document(uid)
                                    .collection("medications").document(medication.getFirestoreId())
                                    .set(medData)
                    );

                    // 2. Soft-delete old schedules in Firestore (set isActive=false)
                    QuerySnapshot oldSchedules = Tasks.await(
                            firestore.collection("users").document(uid)
                                    .collection("schedules")
                                    .whereEqualTo("medicationFirestoreId", medication.getFirestoreId())
                                    .get()
                    );
                    WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot doc : oldSchedules.getDocuments()) {
                        batch.update(doc.getReference(), "isActive", false);
                    }
                    Tasks.await(batch.commit());
                }

                // 3. Update Room cache
                medicationDao.update(medication);
                reminderScheduler.cancelReminders(medication.getId());
                scheduleDao.deactivateByMedicationId(medication.getId());

                // 4. Insert new schedules
                for (Schedule schedule : schedules) {
                    schedule.setMedicationId(medication.getId());

                    if (uid != null && medication.getFirestoreId() != null) {
                        Map<String, Object> schData = buildScheduleMap(schedule, medication.getFirestoreId());
                        DocumentReference schDocRef = Tasks.await(
                                firestore.collection("users").document(uid)
                                        .collection("schedules").add(schData)
                        );
                        schedule.setFirestoreId(schDocRef.getId());
                        schedule.setMedicationFirestoreId(medication.getFirestoreId());
                    }

                    long scheduleId = scheduleDao.insert(schedule);
                    schedule.setId(scheduleId);
                    reminderScheduler.scheduleReminder(medication, schedule);
                }

                if (callback != null) callback.onSuccess(medication.getId());
            } catch (Exception e) {
                Log.e(TAG, "updateMedicationWithSchedule error", e);
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    // ─── Firestore-first write: deleteMedicationWithReminders ───────

    public void deleteMedicationWithReminders(Medication medication) {
        deleteMedicationWithReminders(medication, null);
    }

    public void deleteMedicationWithReminders(Medication medication, ReminderCallback callback) {
        executor.execute(() -> {
            try {
                String uid = getUid();
                String today = LocalDate.now().toString();

                if (uid != null && medication.getFirestoreId() != null) {
                    // Soft-delete medication in Firestore (set isDeleted + endDate)
                    Map<String, Object> medUpdate = new HashMap<>();
                    medUpdate.put("isDeleted", true);
                    medUpdate.put("endDate", today);
                    Tasks.await(
                            firestore.collection("users").document(uid)
                                    .collection("medications").document(medication.getFirestoreId())
                                    .update(medUpdate)
                    );

                    // Deactivate its schedules in Firestore (do NOT delete)
                    QuerySnapshot scheduleSnap = Tasks.await(
                            firestore.collection("users").document(uid)
                                    .collection("schedules")
                                    .whereEqualTo("medicationFirestoreId", medication.getFirestoreId())
                                    .get()
                    );
                    WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot doc : scheduleSnap.getDocuments()) {
                        batch.update(doc.getReference(), "isActive", false);
                    }
                    Tasks.await(batch.commit());

                    // Do NOT touch adherence_logs — preserve historical data
                }

                // Soft-delete in Room cache
                medication.setDeleted(true);
                medication.setEndDate(today);
                medicationDao.update(medication);

                // Deactivate schedules in Room
                List<Schedule> schedules = scheduleDao.getSchedulesForMedicationSync(medication.getId());
                if (schedules != null) {
                    for (Schedule sch : schedules) {
                        sch.setActive(false);
                        scheduleDao.update(sch);
                    }
                }

                // Cancel WorkManager reminders
                reminderScheduler.cancelReminders(medication.getId());

                if (callback != null) callback.onSuccess(medication.getId());
            } catch (Exception e) {
                Log.e(TAG, "deleteMedicationWithReminders error", e);
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    // ─── Sync from Firestore to Room (called on login) ──────────────

    /** Backward-compatible overload — treats errors as success (old behavior). */
    public void syncFromFirestore(Runnable onComplete) {
        syncFromFirestore(onComplete, onComplete);
    }

    /**
     * Fetch all data from Firestore into temp lists, then clear Room and insert.
     * If any Firestore fetch fails, Room is left untouched and onError fires.
     */
    public void syncFromFirestore(Runnable onComplete, Runnable onError) {
        executor.execute(() -> {
            try {
                String uid = getUid();
                if (uid == null) {
                    if (onComplete != null) onComplete.run();
                    return;
                }

                // ── Step 1: Fetch ALL collections from Firestore first ──
                QuerySnapshot medSnap = Tasks.await(
                        firestore.collection("users").document(uid)
                                .collection("medications").get()
                );
                QuerySnapshot schSnap = Tasks.await(
                        firestore.collection("users").document(uid)
                                .collection("schedules").get()
                );
                QuerySnapshot adhSnap = Tasks.await(
                        firestore.collection("users").document(uid)
                                .collection("adherence_logs").get()
                );

                // ── Step 2: All fetches succeeded — now clear Room and insert ──
                medicationDao.deleteAll();
                scheduleDao.deleteAll();
                adherenceDao.deleteAll();

                // Insert medications
                Map<String, Long> firestoreIdToLocalId = new HashMap<>();
                for (QueryDocumentSnapshot doc : medSnap) {
                    Medication med = new Medication(
                            doc.getString("name") != null ? doc.getString("name") : "",
                            doc.getString("dosage") != null ? doc.getString("dosage") : "",
                            doc.getString("frequency") != null ? doc.getString("frequency") : "",
                            doc.getString("startDate") != null ? doc.getString("startDate") : ""
                    );
                    med.setFirestoreId(doc.getId());
                    med.setEndDate(doc.getString("endDate"));
                    med.setImageUrl(doc.getString("imageUrl"));
                    med.setRxcui(doc.getString("rxcui"));
                    med.setNotes(doc.getString("notes"));
                    med.setDeleted(doc.getBoolean("isDeleted") == Boolean.TRUE);
                    Long createdAt = doc.getLong("createdAt");
                    Long updatedAt = doc.getLong("updatedAt");
                    if (createdAt != null) med.setCreatedAt(createdAt);
                    if (updatedAt != null) med.setUpdatedAt(updatedAt);

                    long localId = medicationDao.insert(med);
                    firestoreIdToLocalId.put(doc.getId(), localId);
                    med.setId(localId);
                }

                // Insert schedules
                Map<String, Long> scheduleFirestoreIdToLocalId = new HashMap<>();
                for (QueryDocumentSnapshot doc : schSnap) {
                    String medFirestoreId = doc.getString("medicationFirestoreId");
                    Long localMedId = medFirestoreId != null ? firestoreIdToLocalId.get(medFirestoreId) : null;
                    if (localMedId == null) continue; // orphan schedule, skip

                    Schedule sch = new Schedule(
                            localMedId,
                            doc.getString("time") != null ? doc.getString("time") : "08:00",
                            doc.getString("daysOfWeek") != null ? doc.getString("daysOfWeek") : ""
                    );
                    sch.setFirestoreId(doc.getId());
                    sch.setMedicationFirestoreId(medFirestoreId);
                    sch.setInstructions(doc.getString("instructions"));
                    Boolean isActive = doc.getBoolean("isActive");
                    sch.setActive(isActive == null || isActive);

                    long localSchId = scheduleDao.insert(sch);
                    sch.setId(localSchId);
                    scheduleFirestoreIdToLocalId.put(doc.getId(), localSchId);

                    // Reschedule reminders
                    if (sch.isActive()) {
                        Medication med = medicationDao.getMedicationById(localMedId);
                        if (med != null) {
                            reminderScheduler.scheduleReminder(med, sch);
                        }
                    }
                }

                // Insert adherence logs
                for (QueryDocumentSnapshot doc : adhSnap) {
                    String medFsId = doc.getString("medicationFirestoreId");
                    String schFsId = doc.getString("scheduleFirestoreId");
                    Long localMedId = medFsId != null ? firestoreIdToLocalId.get(medFsId) : null;
                    Long localSchId = schFsId != null ? scheduleFirestoreIdToLocalId.get(schFsId) : null;

                    if (localMedId == null) continue;

                    AdherenceLog log = new AdherenceLog(
                            localMedId,
                            localSchId != null ? localSchId : 0,
                            doc.getString("scheduledDateTime") != null ? doc.getString("scheduledDateTime") : "",
                            doc.getString("status") != null ? doc.getString("status") : "PENDING"
                    );
                    log.setFirestoreId(doc.getId());
                    log.setMedicationFirestoreId(medFsId);
                    log.setScheduleFirestoreId(schFsId != null ? schFsId : "");
                    log.setTakenDateTime(doc.getString("takenDateTime"));
                    log.setNotes(doc.getString("notes"));

                    adherenceDao.insert(log);
                }

                Log.d(TAG, "Firestore sync complete: " + firestoreIdToLocalId.size() + " medications");

                if (onComplete != null) onComplete.run();
            } catch (Exception e) {
                Log.e(TAG, "syncFromFirestore error", e);
                if (onError != null) onError.run();
            }
        });
    }

    // ─── Helper: build Firestore maps ───────────────────────────────

    private Map<String, Object> buildMedicationMap(Medication med) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", med.getName());
        data.put("dosage", med.getDosage());
        data.put("frequency", med.getFrequency());
        data.put("startDate", med.getStartDate());
        data.put("endDate", med.getEndDate());
        data.put("imageUrl", med.getImageUrl());
        data.put("rxcui", med.getRxcui());
        data.put("notes", med.getNotes());
        data.put("createdAt", med.getCreatedAt());
        data.put("updatedAt", med.getUpdatedAt());
        data.put("isDeleted", med.isDeleted());
        return data;
    }

    private Map<String, Object> buildScheduleMap(Schedule sch, String medicationFirestoreId) {
        Map<String, Object> data = new HashMap<>();
        data.put("medicationFirestoreId", medicationFirestoreId);
        data.put("time", sch.getTime());
        data.put("daysOfWeek", sch.getDaysOfWeek());
        data.put("instructions", sch.getInstructions());
        data.put("isActive", sch.isActive());
        return data;
    }

    // ─── Helper: update daily adherence summary ─────────────────────

    public void updateDailyAdherenceSummaryAfterAction() {
        try {
            String today = LocalDate.now().toString();
            List<MedicationScheduleItem> allItems = scheduleDao.getMedicationSchedulesForDate(today);

            List<MedicationScheduleItem> filtered = new ArrayList<>();
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
                String status = adherenceDao.getStatusForScheduleOnDate(
                        item.getId(), item.getScheduleId(), dateStart, dateEnd);
                if ("TAKEN".equals(status)) {
                    takenCount++;
                    item.setTodayStatus("TAKEN");
                } else if ("SKIPPED".equals(status)) {
                    item.setTodayStatus("SKIPPED");
                } else {
                    try {
                        LocalTime scheduledTime = LocalTime.parse(item.getTime());
                        if (LocalTime.now().isAfter(scheduledTime)) {
                            item.setTodayStatus("MISSED");
                            missedCount++;
                        } else {
                            item.setTodayStatus("PENDING");
                        }
                    } catch (Exception e) {
                        item.setTodayStatus("PENDING");
                    }
                }
            }

            updateDailyAdherenceSummary(today, filtered, takenCount, filtered.size(), missedCount);
        } catch (Exception e) {
            Log.e(TAG, "updateDailyAdherenceSummaryAfterAction error", e);
        }
    }

    private void updateDailyAdherenceSummary(String dateStr, List<MedicationScheduleItem> items,
                                              int takenCount, int totalCount, int missedCount) {
        String uid = getUid();
        if (uid == null) return;

        int pendingCount = totalCount - takenCount - missedCount;
        if (pendingCount < 0) pendingCount = 0;

        List<Map<String, Object>> medications = new ArrayList<>();
        for (MedicationScheduleItem item : items) {
            Map<String, Object> med = new HashMap<>();
            med.put("name", item.getName());
            med.put("dosage", item.getDosage() != null ? item.getDosage() : "");
            med.put("time", item.getTime() != null ? item.getTime() : "");
            med.put("status", item.getTodayStatus() != null ? item.getTodayStatus() : "PENDING");
            med.put("instructions", item.getInstructions() != null ? item.getInstructions() : "");
            medications.add(med);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("takenCount", takenCount);
        data.put("missedCount", missedCount);
        data.put("pendingCount", pendingCount);
        data.put("totalCount", totalCount);
        data.put("medications", medications);
        data.put("lastUpdated", Timestamp.now());

        firestore.collection("users").document(uid)
                .collection("daily_adherence").document(dateStr)
                .set(data)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update daily_adherence", e));
    }

    // ─── Accessors ──────────────────────────────────────────────────

    public ScheduleDao getScheduleDao() {
        return scheduleDao;
    }

    public interface ReminderCallback {
        void onSuccess(long medicationId);
        void onError(String message);
    }
}
