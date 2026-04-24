package my.utar.uccd3223.medimind.data.local.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "medications")
/**
 * Room entity storing the base medication profile and optional image/notes.
 */
public class Medication {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String name;
    private String dosage;
    private String frequency;
    private String startDate;
    private String endDate;
    private String imageUrl;
    private String rxcui;
    private String notes;
    private String firestoreId;
    private long createdAt;
    private long updatedAt;
    private boolean isDeleted;

    // Constructor
    public Medication(String name, String dosage, String frequency, String startDate) {
        this.name = name;
        this.dosage = dosage;
        this.frequency = frequency;
        this.startDate = startDate;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.isDeleted = false;
    }

    // Getters
    public long getId() { return id; }
    public String getName() { return name; }
    public String getDosage() { return dosage; }
    public String getFrequency() { return frequency; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public String getImageUrl() { return imageUrl; }
    public String getRxcui() { return rxcui; }
    public String getNotes() { return notes; }
    public String getFirestoreId() { return firestoreId; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public boolean isDeleted() { return isDeleted; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setRxcui(String rxcui) { this.rxcui = rxcui; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}
