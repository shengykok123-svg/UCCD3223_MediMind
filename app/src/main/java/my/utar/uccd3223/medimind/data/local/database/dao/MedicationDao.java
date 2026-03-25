package my.utar.uccd3223.medimind.data.local.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import java.util.List;

import my.utar.uccd3223.medimind.data.local.database.entities.Medication;

@Dao
public interface MedicationDao {

    @Query("SELECT * FROM medications WHERE isDeleted = 0 AND (endDate IS NULL OR endDate >= :today) ORDER BY name ASC")
    LiveData<List<Medication>> getActiveMedications(String today);

    @Query("SELECT * FROM medications WHERE id = :id")
    Medication getMedicationById(long id);

    @Query("SELECT * FROM medications WHERE isDeleted = 0 " +
           "ORDER BY CASE WHEN endDate IS NOT NULL AND endDate < :today THEN 1 ELSE 0 END ASC, " +
           "name ASC, endDate ASC")
    LiveData<List<Medication>> getAllMedications(String today);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Medication medication);

    @Update
    void update(Medication medication);

    @Delete
    void delete(Medication medication);

    @Query("DELETE FROM medications WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM medications WHERE isDeleted = 0 AND name LIKE '%' || :query || '%'")
    LiveData<List<Medication>> searchMedications(String query);

    @Query("SELECT * FROM medications WHERE firestoreId = :firestoreId LIMIT 1")
    Medication getByFirestoreId(String firestoreId);

    @Query("DELETE FROM medications")
    void deleteAll();

    @Query("SELECT * FROM medications")
    List<Medication> getAllMedicationsSync();
}