package my.utar.uccd3223.medimind.data.local.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import my.utar.uccd3223.medimind.data.local.database.dao.*;
import my.utar.uccd3223.medimind.data.local.database.entities.*;

@Database(
        entities = {
                Medication.class,
                Schedule.class,
                AdherenceLog.class,
                HealthRecord.class
        },
        version = 3,
        exportSchema = false
)
public abstract class MediMindDatabase extends RoomDatabase {

    private static volatile MediMindDatabase INSTANCE;

    public abstract MedicationDao medicationDao();
    public abstract ScheduleDao scheduleDao();
    public abstract AdherenceDao adherenceDao();
    public abstract HealthRecordDao healthRecordDao();

    public static MediMindDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MediMindDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            MediMindDatabase.class,
                            "medimind_database"
                    ).fallbackToDestructiveMigration()
                     .build();
                }
            }
        }
        return INSTANCE;
    }
}