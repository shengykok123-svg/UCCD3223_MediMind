package my.utar.uccd3223.medimind.data.local.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "schedules")
/**
 * Room entity describing when and how often a medication should be taken.
 */
public class Schedule {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String firestoreId;
    private long medicationId;
    private String medicationFirestoreId;
    private String time; // "09:00"
    private String daysOfWeek; // "1,2,3,4,5"
    private String instructions; // "with food"
    private boolean isActive;

    // Constructor
    public Schedule(long medicationId, String time, String daysOfWeek) {
        this.medicationId = medicationId;
        this.time = time;
        this.daysOfWeek = daysOfWeek;
        this.isActive = true;
    }

    // Getters
    public long getId() { return id; }
    public String getFirestoreId() { return firestoreId; }
    public long getMedicationId() { return medicationId; }
    public String getMedicationFirestoreId() { return medicationFirestoreId; }
    public String getTime() { return time; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public String getInstructions() { return instructions; }
    public boolean isActive() { return isActive; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }
    public void setMedicationId(long medicationId) { this.medicationId = medicationId; }
    public void setMedicationFirestoreId(String medicationFirestoreId) { this.medicationFirestoreId = medicationFirestoreId; }
    public void setTime(String time) { this.time = time; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public void setActive(boolean active) { isActive = active; }
}
