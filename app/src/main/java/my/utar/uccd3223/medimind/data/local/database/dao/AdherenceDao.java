package my.utar.uccd3223.medimind.data.local.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import java.util.List;

import my.utar.uccd3223.medimind.data.local.database.entities.AdherenceLog;

@Dao
/**
 * DAO for adherence history used by reports and community sync.
 */
public interface AdherenceDao {

    @Query("SELECT * FROM adherence_logs WHERE medicationId = :medicationId ORDER BY scheduledDateTime DESC")
    LiveData<List<AdherenceLog>> getLogsForMedication(long medicationId);

    @Query("SELECT * FROM adherence_logs WHERE scheduledDateTime BETWEEN :startDate AND :endDate")
    LiveData<List<AdherenceLog>> getLogsBetweenDates(String startDate, String endDate);

    @Query("SELECT * FROM adherence_logs WHERE status = :status")
    LiveData<List<AdherenceLog>> getLogsByStatus(String status);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AdherenceLog log);

    @Update
    void update(AdherenceLog log);

    @Delete
    void delete(AdherenceLog log);

    @Query("SELECT COUNT(*) FROM adherence_logs WHERE status = 'TAKEN' AND scheduledDateTime >= :startDate")
    int getTakenCountSince(String startDate);

    @Query("SELECT COUNT(*) FROM adherence_logs WHERE scheduledDateTime >= :startDate")
    int getTotalCountSince(String startDate);

    /**
     * Get adherence logs for a specific date range (sync version for background threads).
     */
    @Query("SELECT * FROM adherence_logs WHERE scheduledDateTime BETWEEN :startDate AND :endDate")
    List<AdherenceLog> getLogsBetweenDatesSync(String startDate, String endDate);

    /**
     * Get the adherence status for a specific medication+schedule on a specific date.
     */
    @Query("SELECT status FROM adherence_logs " +
           "WHERE medicationId = :medicationId AND scheduleId = :scheduleId " +
           "AND scheduledDateTime BETWEEN :dateStart AND :dateEnd " +
           "LIMIT 1")
    String getStatusForScheduleOnDate(long medicationId, long scheduleId,
                                      String dateStart, String dateEnd);

    /**
     * Insert and return the inserted log (for Take Now action from UI).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertSync(AdherenceLog log);

    @Query("SELECT * FROM adherence_logs WHERE firestoreId = :firestoreId LIMIT 1")
    AdherenceLog getByFirestoreId(String firestoreId);

    @Query("DELETE FROM adherence_logs")
    void deleteAll();

    @Query("SELECT * FROM adherence_logs")
    List<AdherenceLog> getAllLogsSync();

    @Query("SELECT MIN(scheduledDateTime) FROM adherence_logs")
    String getEarliestLogDateSync();
}
