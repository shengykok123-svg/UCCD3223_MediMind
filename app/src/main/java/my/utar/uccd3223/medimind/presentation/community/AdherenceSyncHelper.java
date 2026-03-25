package my.utar.uccd3223.medimind.presentation.community;

import java.time.LocalDate;
import java.util.List;

import my.utar.uccd3223.medimind.data.local.database.entities.MedicationScheduleItem;
import my.utar.uccd3223.medimind.domain.repository.MedicationRepository;

/**
 * Simplified helper: delegates to MedicationRepository which now auto-updates
 * the Firestore daily_adherence summary on every getMedicationSchedulesForDate() call.
 */
public class AdherenceSyncHelper {

    private final MedicationRepository repository;

    public interface SyncCallback {
        void onSynced(int takenCount, int pendingCount, int missedCount, int totalCount);
        void onError(String message);
    }

    public AdherenceSyncHelper(MedicationRepository repository) {
        this.repository = repository;
    }

    public void syncTodayAdherence(SyncCallback callback) {
        LocalDate today = LocalDate.now();
        String dateStr = today.toString();
        int dayOfWeek = today.getDayOfWeek().getValue();

        repository.getMedicationSchedulesForDate(dateStr, dayOfWeek,
                new MedicationRepository.ScheduleCallback() {
                    @Override
                    public void onResult(List<MedicationScheduleItem> items, int takenCount, int totalCount, int missedCount) {
                        int pendingCount = totalCount - takenCount - missedCount;
                        if (pendingCount < 0) pendingCount = 0;
                        if (callback != null) {
                            callback.onSynced(takenCount, pendingCount, missedCount, totalCount);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (callback != null) {
                            callback.onError(message);
                        }
                    }
                });
    }
}
