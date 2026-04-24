package my.utar.uccd3223.medimind.data.local.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import java.util.List;

import my.utar.uccd3223.medimind.data.local.database.entities.MedicationScheduleItem;
import my.utar.uccd3223.medimind.data.local.database.entities.Schedule;

@Dao
/**
 * DAO for medication schedule queries and reminder status updates.
 */
public interface ScheduleDao {

    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId")
    LiveData<List<Schedule>> getSchedulesForMedication(long medicationId);

    @Query("SELECT * FROM schedules WHERE isActive = 1")
    LiveData<List<Schedule>> getAllActiveSchedules();

    @Query("SELECT * FROM schedules WHERE isActive = 1")
    List<Schedule> getAllActiveSchedulesSync();

    @Query("SELECT * FROM schedules WHERE id = :id")
    Schedule getScheduleById(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Schedule schedule);

    @Update
    void update(Schedule schedule);

    @Delete
    void delete(Schedule schedule);

    @Query("DELETE FROM schedules WHERE medicationId = :medicationId")
    void deleteByMedicationId(long medicationId);

    @Query("UPDATE schedules SET isActive = 0 WHERE medicationId = :medicationId")
    void deactivateByMedicationId(long medicationId);

    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId")
    List<Schedule> getSchedulesForMedicationSync(long medicationId);

    /**
     * Get all medications joined with their active schedules, ordered by time.
     * Returns all - filtering by day of week is done in Java code.
     */
    @Query("SELECT m.id, m.firestoreId, m.name, m.dosage, m.frequency, m.imageUrl, m.notes, " +
           "m.startDate, m.endDate, " +
           "s.id AS scheduleId, s.firestoreId AS scheduleFirestoreId, " +
           "s.time, s.daysOfWeek, s.instructions " +
           "FROM medications m INNER JOIN schedules s ON m.id = s.medicationId " +
           "WHERE s.isActive = 1 AND m.isDeleted = 0 " +
           "AND (m.startDate IS NULL OR m.startDate <= :today) " +
           "AND (m.endDate IS NULL OR m.endDate >= :today) " +
           "ORDER BY s.time ASC")
    List<MedicationScheduleItem> getMedicationSchedulesForDate(String today);

    /**
     * Get deleted medications whose active date range covers the given date.
     * endDate is set to the deletion date, so this naturally excludes future dates.
     */
    @Query("SELECT m.id, m.firestoreId, m.name, m.dosage, m.frequency, m.imageUrl, m.notes, " +
           "m.startDate, m.endDate, " +
           "s.id AS scheduleId, s.firestoreId AS scheduleFirestoreId, " +
           "s.time, s.daysOfWeek, s.instructions " +
           "FROM medications m INNER JOIN schedules s ON m.id = s.medicationId " +
           "WHERE m.isDeleted = 1 " +
           "AND (m.startDate IS NULL OR m.startDate <= :today) " +
           "AND (m.endDate IS NULL OR m.endDate >= :today) " +
           "ORDER BY s.time ASC")
    List<MedicationScheduleItem> getDeletedMedicationSchedulesForDate(String today);

    @Query("SELECT * FROM schedules WHERE firestoreId = :firestoreId LIMIT 1")
    Schedule getByFirestoreId(String firestoreId);

    @Query("DELETE FROM schedules")
    void deleteAll();

    @Query("SELECT * FROM schedules WHERE medicationFirestoreId = :medicationFirestoreId")
    List<Schedule> getSchedulesByMedicationFirestoreId(String medicationFirestoreId);

    @Query("SELECT * FROM schedules")
    List<Schedule> getAllSchedulesSync();
}
