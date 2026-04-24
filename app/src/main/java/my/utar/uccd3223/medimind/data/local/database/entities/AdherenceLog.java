package my.utar.uccd3223.medimind.data.local.database.entities;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "adherence_logs",
    indices = {@Index(value = {"medicationId", "scheduleId", "scheduledDateTime"}, unique = true)})
/**
 * Room entity recording whether a scheduled dose was taken, missed, or pending.
 */
public class AdherenceLog {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String firestoreId;
    private long medicationId;
    private String medicationFirestoreId;
    private long scheduleId;
    private String scheduleFirestoreId;
    private String scheduledDateTime;
    private String takenDateTime;
    private String status; // TAKEN, MISSED, SKIPPED, SNOOZED
    private String notes;

    // Constructor
    public AdherenceLog(long medicationId, long scheduleId, String scheduledDateTime, String status) {
        this.medicationId = medicationId;
        this.scheduleId = scheduleId;
        this.scheduledDateTime = scheduledDateTime;
        this.status = status;
    }

    // Getters
    public long getId() { return id; }
    public String getFirestoreId() { return firestoreId; }
    public long getMedicationId() { return medicationId; }
    public String getMedicationFirestoreId() { return medicationFirestoreId; }
    public long getScheduleId() { return scheduleId; }
    public String getScheduleFirestoreId() { return scheduleFirestoreId; }
    public String getScheduledDateTime() { return scheduledDateTime; }
    public String getTakenDateTime() { return takenDateTime; }
    public String getStatus() { return status; }
    public String getNotes() { return notes; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }
    public void setMedicationId(long medicationId) { this.medicationId = medicationId; }
    public void setMedicationFirestoreId(String medicationFirestoreId) { this.medicationFirestoreId = medicationFirestoreId; }
    public void setScheduleId(long scheduleId) { this.scheduleId = scheduleId; }
    public void setScheduleFirestoreId(String scheduleFirestoreId) { this.scheduleFirestoreId = scheduleFirestoreId; }
    public void setScheduledDateTime(String scheduledDateTime) { this.scheduledDateTime = scheduledDateTime; }
    public void setTakenDateTime(String takenDateTime) { this.takenDateTime = takenDateTime; }
    public void setStatus(String status) { this.status = status; }
    public void setNotes(String notes) { this.notes = notes; }
}
