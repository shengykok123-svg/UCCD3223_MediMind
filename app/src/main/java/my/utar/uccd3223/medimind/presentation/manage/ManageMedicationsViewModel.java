package my.utar.uccd3223.medimind.presentation.manage;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import my.utar.uccd3223.medimind.data.local.database.entities.Medication;
import my.utar.uccd3223.medimind.data.local.database.entities.Schedule;
import my.utar.uccd3223.medimind.data.repository.MedicationRepositoryImpl;
import my.utar.uccd3223.medimind.domain.repository.MedicationRepository;

/**
 * Coordinates medication CRUD operations and schedule persistence for the manage screen.
 */
@HiltViewModel
public class ManageMedicationsViewModel extends ViewModel {

    private final MedicationRepository repository;
    private final Executor executor;

    private final LiveData<List<Medication>> allMedications;
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();

    @Inject
    public ManageMedicationsViewModel(MedicationRepository repository, Executor executor) {
        this.repository = repository;
        this.executor = executor;
        this.allMedications = repository.getAllMedications(LocalDate.now().toString());
    }

    public LiveData<List<Medication>> getAllMedications() {
        return allMedications;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<String> getSuccessMessage() {
        return successMessage;
    }

    /**
     * Load medication by ID on background thread and return via callback.
     * Returns all schedules so the edit dialog can pre-fill multiple time fields.
     */
    public void loadMedicationWithSchedule(long medicationId, MedicationScheduleCallback callback) {
        executor.execute(() -> {
            try {
                Medication medication = repository.getMedicationById(medicationId);
                List<Schedule> schedules = ((MedicationRepositoryImpl) repository)
                        .getScheduleDao().getSchedulesForMedicationSync(medicationId);
                if (schedules == null) schedules = new ArrayList<>();
                callback.onResult(medication, schedules);
            } catch (Exception e) {
                error.postValue(e.getMessage());
            }
        });
    }

    /**
     * Add a new medication with multiple schedules (one per time).
     */
    public void addMedicationWithSchedule(String name, String dosage, String frequency,
                                           List<String> times, String startDate, String endDate,
                                           String instructions, String imageUrl) {
        Medication medication = new Medication(name, dosage, frequency, startDate);
        if (endDate != null && !endDate.isEmpty()) {
            medication.setEndDate(endDate);
        }
        if (imageUrl != null && !imageUrl.isEmpty()) {
            medication.setImageUrl(imageUrl);
        }

        List<Schedule> schedules = new ArrayList<>();
        for (String time : times) {
            Schedule schedule = new Schedule(0, time, "1,2,3,4,5,6,7");
            schedule.setInstructions(instructions);
            schedules.add(schedule);
        }

        ((MedicationRepositoryImpl) repository).addMedicationWithSchedule(
                medication, schedules,
                new MedicationRepositoryImpl.ReminderCallback() {
                    @Override
                    public void onSuccess(long medicationId) {
                        successMessage.postValue(name + " added successfully");
                    }

                    @Override
                    public void onError(String message) {
                        error.postValue(message);
                    }
                }
        );
    }

    /**
     * Update an existing medication with multiple schedules.
     */
    public void updateMedicationWithSchedule(Medication medication, List<Schedule> schedules) {
        ((MedicationRepositoryImpl) repository).updateMedicationWithSchedule(
                medication, schedules,
                new MedicationRepositoryImpl.ReminderCallback() {
                    @Override
                    public void onSuccess(long medicationId) {
                        successMessage.postValue(medication.getName() + " updated successfully");
                    }

                    @Override
                    public void onError(String message) {
                        error.postValue(message);
                    }
                }
        );
    }

    /**
     * Delete a medication and its reminders.
     */
    public void deleteMedication(Medication medication) {
        ((MedicationRepositoryImpl) repository).deleteMedicationWithReminders(
                medication,
                new MedicationRepositoryImpl.ReminderCallback() {
                    @Override
                    public void onSuccess(long medicationId) {
                        successMessage.postValue(medication.getName() + " deleted");
                    }

                    @Override
                    public void onError(String message) {
                        error.postValue(message);
                    }
                }
        );
    }

    public interface MedicationScheduleCallback {
        void onResult(Medication medication, List<Schedule> schedules);
    }
}
