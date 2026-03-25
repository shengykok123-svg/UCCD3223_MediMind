package my.utar.uccd3223.medimind.presentation.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import javax.inject.Inject;

import my.utar.uccd3223.medimind.data.local.database.entities.Medication;
import my.utar.uccd3223.medimind.data.local.database.entities.MedicationScheduleItem;
import my.utar.uccd3223.medimind.data.local.database.entities.Schedule;
import my.utar.uccd3223.medimind.data.repository.MedicationRepositoryImpl;
import my.utar.uccd3223.medimind.domain.repository.MedicationRepository;

@HiltViewModel
public class HomeViewModel extends ViewModel {

    private final MedicationRepository repository;
    private final Executor executor;

    // Selected date (yyyy-MM-dd format)
    private final MutableLiveData<String> selectedDate = new MutableLiveData<>();
    // Day of week ISO (Mon=1, Sun=7) for the selected date
    private int selectedDayOfWeek;

    // Medication schedule items for the selected date
    private final MutableLiveData<List<MedicationScheduleItem>> medications = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    // Notification bar text: "X of Y medications taken"
    private final MutableLiveData<String> notificationText = new MutableLiveData<>();

    @Inject
    public HomeViewModel(MedicationRepository repository, Executor executor) {
        this.repository = repository;
        this.executor = executor;

        // Default to today
        LocalDate today = LocalDate.now();
        selectedDayOfWeek = today.getDayOfWeek().getValue(); // ISO Mon=1..Sun=7
        selectedDate.setValue(today.toString()); // yyyy-MM-dd
    }

    public LiveData<List<MedicationScheduleItem>> getMedications() {
        return medications;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<String> getNotificationText() {
        return notificationText;
    }

    public LiveData<String> getSelectedDate() {
        return selectedDate;
    }

    /**
     * Set a new date and reload medications for that date.
     * @param year  the year
     * @param month 0-based month (Calendar.MONTH)
     * @param day   day of month
     */
    public void setDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, day);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateStr = sdf.format(cal.getTime());
        selectedDate.setValue(dateStr);

        // Calendar.DAY_OF_WEEK: Sun=1, Mon=2, ... Sat=7
        // Convert to ISO: Mon=1, Tue=2, ... Sun=7
        int calDow = cal.get(Calendar.DAY_OF_WEEK);
        selectedDayOfWeek = (calDow == Calendar.SUNDAY) ? 7 : calDow - 1;

        loadMedications();
    }

    /**
     * Load (or reload) medications for the currently selected date.
     * Safe to call from any thread - uses postValue for all LiveData updates.
     */
    public void loadMedications() {
        String dateStr = selectedDate.getValue();
        if (dateStr == null) return;

        isLoading.postValue(true);

        repository.getMedicationSchedulesForDate(dateStr, selectedDayOfWeek,
                new MedicationRepository.ScheduleCallback() {
                    @Override
                    public void onResult(List<MedicationScheduleItem> items, int takenCount, int totalCount, int missedCount) {
                        medications.postValue(items);
                        isLoading.postValue(false);

                        if (totalCount == 0) {
                            notificationText.postValue("No medications scheduled for this day");
                        } else if (missedCount > 0) {
                            notificationText.postValue(takenCount + " of " + totalCount + " taken · " + missedCount + " missed");
                        } else {
                            notificationText.postValue(takenCount + " of " + totalCount + " medications taken");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        error.postValue(message);
                        isLoading.postValue(false);
                    }
                });
    }

    /**
     * Return all active (not deleted, not expired) medications regardless of selected date.
     */
    public LiveData<List<Medication>> getActiveMedications() {
        return repository.getActiveMedications(LocalDate.now().toString());
    }

    /**
     * Record that the user took a specific medication dose.
     */
    public void takeMedication(MedicationScheduleItem item) {
        if (item.isTaken()) return; // Already taken

        String dateStr = selectedDate.getValue();
        if (dateStr == null) dateStr = LocalDate.now().toString();

        // Guard: block past dates
        if (LocalDate.parse(dateStr).isBefore(LocalDate.now())) {
            error.postValue("Cannot modify past medication records");
            return;
        }

        // Guard: block future dates
        if (LocalDate.parse(dateStr).isAfter(LocalDate.now())) {
            error.postValue("Cannot take medication for a future date");
            return;
        }

        String scheduledDateTime = dateStr + " " + item.getTime();

        repository.recordTaken(item.getId(), item.getScheduleId(), scheduledDateTime,
                new MedicationRepository.TakeCallback() {
                    @Override
                    public void onSuccess() {
                        loadMedications();
                    }

                    @Override
                    public void onError(String message) {
                        error.postValue(message);
                    }
                });
    }

    /**
     * Add a new medication with schedule(s) and reminder(s).
     * Does NOT wrap in executor - the repo method already runs on background thread.
     */
    public void addMedicationWithSchedule(String name, String dosage, String frequency,
                                           List<String> times, String startDate, String endDate,
                                           String instructions, String imageUrl) {
        Medication medication = new Medication(
                name,
                dosage,
                frequency,
                startDate
        );
        if (endDate != null && !endDate.isEmpty()) {
            medication.setEndDate(endDate);
        }
        if (imageUrl != null && !imageUrl.isEmpty()) {
            medication.setImageUrl(imageUrl);
        }

        List<Schedule> schedules = new ArrayList<>();
        for (String time : times) {
            Schedule schedule = new Schedule(
                    0, // Will be set after medication is inserted
                    time,
                    "1,2,3,4,5,6,7" // Default all days - actual filtering by date range + frequency
            );
            schedule.setInstructions(instructions);
            schedules.add(schedule);
        }

        ((MedicationRepositoryImpl) repository).addMedicationWithSchedule(
                medication,
                schedules,
                new MedicationRepositoryImpl.ReminderCallback() {
                    @Override
                    public void onSuccess(long medicationId) {
                        loadMedications();
                    }

                    @Override
                    public void onError(String message) {
                        error.postValue(message);
                    }
                }
        );
    }
}
