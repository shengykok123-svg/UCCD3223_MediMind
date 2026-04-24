package my.utar.uccd3223.medimind.data.local.database.entities;

import androidx.room.Ignore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * POJO for joined Medication + Schedule query result.
 * Used to display medication timeline on the Home screen.
 */
/**
 * Projection model combining medication and schedule fields for home/report lists.
 */
public class MedicationScheduleItem {

    // Medication fields
    private long id;              // medication id
    private String firestoreId;   // medication's firestoreId
    private String name;
    private String dosage;
    private String frequency;     // "1/1" (daily), "2/1" (twice daily), "1/3" (every 3 days)
    private String imageUrl;
    private String notes;
    private String startDate;     // yyyy-MM-dd
    private String endDate;       // yyyy-MM-dd or null

    // Schedule fields
    private long scheduleId;
    private String scheduleFirestoreId;  // schedule's firestoreId
    private String time;          // "09:00"
    private String daysOfWeek;    // "1,2,3,4,5,6,7"
    private String instructions;  // "After Meal", "Before Sleep"

    // Adherence status for today (set in code, not from query)
    @Ignore
    private String todayStatus;   // TAKEN, MISSED, SKIPPED, PENDING, or null
    @Ignore
    private String takenDateTime; // actual recorded taken time for the selected date

    // Getters
    public long getId() { return id; }
    public String getFirestoreId() { return firestoreId; }
    public String getName() { return name; }
    public String getDosage() { return dosage; }
    public String getFrequency() { return frequency; }
    public String getImageUrl() { return imageUrl; }
    public String getNotes() { return notes; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public long getScheduleId() { return scheduleId; }
    public String getScheduleFirestoreId() { return scheduleFirestoreId; }
    public String getTime() { return time; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public String getInstructions() { return instructions; }
    public String getTodayStatus() { return todayStatus; }
    public String getTakenDateTime() { return takenDateTime; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }
    public void setName(String name) { this.name = name; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public void setScheduleId(long scheduleId) { this.scheduleId = scheduleId; }
    public void setScheduleFirestoreId(String scheduleFirestoreId) { this.scheduleFirestoreId = scheduleFirestoreId; }
    public void setTime(String time) { this.time = time; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public void setTodayStatus(String todayStatus) { this.todayStatus = todayStatus; }
    public void setTakenDateTime(String takenDateTime) { this.takenDateTime = takenDateTime; }

    /**
     * Get formatted time display (e.g., "8:00 AM")
     */
    public String getFormattedTime() {
        if (time == null || time.isEmpty()) return "";
        try {
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            String amPm = hour >= 12 ? "PM" : "AM";
            int displayHour = hour % 12;
            if (displayHour == 0) displayHour = 12;
            return String.format("%d:%02d %s", displayHour, minute, amPm);
        } catch (Exception e) {
            return time;
        }
    }

    /**
     * Check if this medication should be taken on a given date,
     * based on the frequency interval (e.g., "1/3" means every 3 days from startDate).
     *
     * @param selectedDateStr the selected date in yyyy-MM-dd format
     * @return true if the medication should be taken on this date
     */
    public boolean isScheduledForDate(String selectedDateStr) {
        // Parse frequency interval (Y in "X/Y")
        int intervalDays = getIntervalDays();

        // If daily (interval=1), always scheduled
        if (intervalDays <= 1) return true;

        // For multi-day intervals, check if (daysBetween(startDate, selectedDate) % interval) == 0
        if (startDate == null || startDate.isEmpty()) return true;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date start = sdf.parse(startDate);
            Date selected = sdf.parse(selectedDateStr);
            if (start == null || selected == null) return true;

            long diffMs = selected.getTime() - start.getTime();
            long diffDays = TimeUnit.MILLISECONDS.toDays(diffMs);
            return diffDays >= 0 && (diffDays % intervalDays) == 0;
        } catch (Exception e) {
            return true; // Default to showing if can't parse
        }
    }

    /**
     * Get the interval in days from frequency string.
     * Frequency format: "X/Y" where X = doses per period, Y = period in days.
     * e.g., "1/1" = daily, "2/1" = twice daily, "1/3" = every 3 days
     */
    public int getIntervalDays() {
        if (frequency == null || !frequency.contains("/")) return 1;
        try {
            String[] parts = frequency.split("/");
            return Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * Get a human-readable frequency label.
     */
    public String getFrequencyLabel() {
        if (frequency == null || !frequency.contains("/")) {
            return frequency != null ? frequency : "Daily";
        }
        try {
            String[] parts = frequency.split("/");
            int doses = Integer.parseInt(parts[0].trim());
            int days = Integer.parseInt(parts[1].trim());

            if (doses == 1 && days == 1) return "Daily";
            if (doses == 2 && days == 1) return "Twice Daily";
            if (doses == 3 && days == 1) return "3 Times Daily";
            if (doses == 1 && days == 2) return "Every 2 Days";
            if (doses == 1 && days == 3) return "Every 3 Days";
            if (doses == 1 && days == 7) return "Weekly";

            return doses + " time(s) per " + days + " day(s)";
        } catch (Exception e) {
            return frequency;
        }
    }

    /**
     * Check if medication has been taken today
     */
    public boolean isTaken() {
        return "TAKEN".equals(todayStatus);
    }

    public boolean isMissed() {
        return "MISSED".equals(todayStatus);
    }

    public boolean isUpcoming() {
        return "UPCOMING".equals(todayStatus);
    }

    public boolean isSkipped() {
        return "SKIPPED".equals(todayStatus);
    }
}
