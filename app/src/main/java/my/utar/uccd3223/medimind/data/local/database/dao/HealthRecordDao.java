package my.utar.uccd3223.medimind.data.local.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import java.util.List;

import my.utar.uccd3223.medimind.data.local.database.entities.HealthRecord;

@Dao
/**
 * DAO for storing and reading user health records.
 */
public interface HealthRecordDao {

    @Query("SELECT * FROM health_records ORDER BY timestamp DESC")
    LiveData<List<HealthRecord>> getAllRecords();

    @Query("SELECT * FROM health_records WHERE type = :type ORDER BY timestamp DESC")
    LiveData<List<HealthRecord>> getRecordsByType(String type);

    @Query("SELECT * FROM health_records WHERE id = :id")
    LiveData<HealthRecord> getRecordById(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(HealthRecord record);

    @Update
    void update(HealthRecord record);

    @Delete
    void delete(HealthRecord record);
}
