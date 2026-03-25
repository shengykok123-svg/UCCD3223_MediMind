package my.utar.uccd3223.medimind.di;

import android.content.Context;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

import com.google.firebase.firestore.FirebaseFirestore;

import my.utar.uccd3223.medimind.data.local.database.dao.AdherenceDao;
import my.utar.uccd3223.medimind.data.local.database.dao.MedicationDao;
import my.utar.uccd3223.medimind.data.local.database.dao.ScheduleDao;
import my.utar.uccd3223.medimind.data.repository.MedicationRepositoryImpl;
import my.utar.uccd3223.medimind.domain.repository.MedicationRepository;

import java.util.concurrent.Executor;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class RepositoryModule {

    @Provides
    @Singleton
    public MedicationRepository provideMedicationRepository(
            MedicationDao medicationDao,
            ScheduleDao scheduleDao,
            AdherenceDao adherenceDao,
            Executor executor,
            @ApplicationContext Context context,
            FirebaseFirestore firestore
    ) {
        return new MedicationRepositoryImpl(
                medicationDao,
                scheduleDao,
                adherenceDao,
                executor,
                context,
                firestore
        );
    }
}
