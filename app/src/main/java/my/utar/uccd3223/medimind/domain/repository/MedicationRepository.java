package my.utar.uccd3223.medimind.domain.repository;

import androidx.lifecycle.LiveData;
import my.utar.uccd3223.medimind.data.local.database.entities.Medication;
import my.utar.uccd3223.medimind.data.local.database.entities.MedicationScheduleItem;
import java.util.List;

/**
 * Repository contract for medication, schedule, adherence, and report operations.
 */
public interface MedicationRepository {
    LiveData<List<Medication>> getActiveMedications(String today);
    Medication getMedicationById(long id);
    LiveData<List<Medication>> getAllMedications(String today);
    long addMedication(Medication medication);
    void updateMedication(Medication medication);
    void deleteMedication(Medication medication);
    LiveData<List<Medication>> searchMedications(String query);

    /**
     * Get medications with schedules for a given date, enriched with adherence status.
     * Runs on background thread and returns result via callback.
     */
    void getMedicationSchedulesForDate(String dateStr, int dayOfWeekISO, ScheduleCallback callback);

    /**
     * Record that the user took a medication dose.
     */
    void recordTaken(long medicationId, long scheduleId, String scheduledDateTime, TakeCallback callback);

    interface ScheduleCallback {
        void onResult(List<MedicationScheduleItem> items, int takenCount, int totalCount, int missedCount);
        void onError(String message);
    }

    interface TakeCallback {
        void onSuccess();
        void onError(String message);
    }
}
