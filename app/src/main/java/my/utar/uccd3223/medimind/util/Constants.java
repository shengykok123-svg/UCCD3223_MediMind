package my.utar.uccd3223.medimind.util;

/**
 * Stores shared constants used by notifications, reminders, and intent actions.
 */
public class Constants {
    public static final String DATABASE_NAME = "medimind_database";
    public static final String CHANNEL_ID = "medication_reminders";

    // Adherence Status
    public static final String STATUS_TAKEN = "TAKEN";
    public static final String STATUS_MISSED = "MISSED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_SNOOZED = "SNOOZED";
    public static final String STATUS_PENDING = "PENDING";

    // Health Record Types
    public static final String RECORD_TYPE_SYMPTOM = "SYMPTOM";
    public static final String RECORD_TYPE_VITAL = "VITAL";
    public static final String RECORD_TYPE_PHOTO = "PHOTO";
    public static final String RECORD_TYPE_NOTE = "NOTE";

    // Preferences
    public static final String PREF_REMINDER_ENABLED = "reminder_enabled";
    public static final String PREF_REMINDER_SOUND = "reminder_sound";
    public static final String PREF_REMINDER_VIBRATION = "reminder_vibration";
}
