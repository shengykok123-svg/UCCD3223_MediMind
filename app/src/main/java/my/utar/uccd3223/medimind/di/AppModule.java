package my.utar.uccd3223.medimind.di;

import android.content.Context;
import my.utar.uccd3223.medimind.data.local.database.MediMindDatabase;
import my.utar.uccd3223.medimind.data.local.database.dao.*;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

    @Provides
    @Singleton
    public MediMindDatabase provideDatabase(@ApplicationContext Context context) {
        return MediMindDatabase.getInstance(context);
    }

    @Provides
    @Singleton
    public MedicationDao provideMedicationDao(MediMindDatabase database) {
        return database.medicationDao();
    }

    @Provides
    @Singleton
    public ScheduleDao provideScheduleDao(MediMindDatabase database) {
        return database.scheduleDao();
    }

    @Provides
    @Singleton
    public AdherenceDao provideAdherenceDao(MediMindDatabase database) {
        return database.adherenceDao();
    }

    @Provides
    @Singleton
    public HealthRecordDao provideHealthRecordDao(MediMindDatabase database) {
        return database.healthRecordDao();
    }

    @Provides
    @Singleton
    public Executor provideExecutor() {
        return Executors.newSingleThreadExecutor();
    }

    @Provides
    @Singleton
    public Context provideContext(@ApplicationContext Context context) {
        return context;
    }

    @Provides
    @Singleton
    public FirebaseFirestore provideFirestore() {
        return FirebaseFirestore.getInstance();
    }
}