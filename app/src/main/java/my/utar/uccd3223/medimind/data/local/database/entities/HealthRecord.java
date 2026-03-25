package my.utar.uccd3223.medimind.data.local.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "health_records")
public class HealthRecord {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String timestamp;
    private String type; // SYMPTOM, VITAL, PHOTO, NOTE
    private String data; // JSON-encoded data
    private String relatedMedicationIds; // Comma-separated IDs

    // Constructor
    public HealthRecord(String timestamp, String type, String data) {
        this.timestamp = timestamp;
        this.type = type;
        this.data = data;
    }

    // Getters
    public long getId() { return id; }
    public String getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getData() { return data; }
    public String getRelatedMedicationIds() { return relatedMedicationIds; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public void setType(String type) { this.type = type; }
    public void setData(String data) { this.data = data; }
    public void setRelatedMedicationIds(String relatedMedicationIds) {
        this.relatedMedicationIds = relatedMedicationIds;
    }
}