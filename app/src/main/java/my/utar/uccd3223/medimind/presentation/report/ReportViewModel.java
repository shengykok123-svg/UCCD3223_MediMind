package my.utar.uccd3223.medimind.presentation.report;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import my.utar.uccd3223.medimind.data.local.database.dao.AdherenceDao;
import my.utar.uccd3223.medimind.data.local.database.dao.MedicationDao;
import my.utar.uccd3223.medimind.data.local.database.dao.ScheduleDao;
import my.utar.uccd3223.medimind.data.local.database.entities.AdherenceLog;
import my.utar.uccd3223.medimind.data.local.database.entities.Medication;
import my.utar.uccd3223.medimind.data.local.database.entities.Schedule;

/**
 * Aggregates adherence statistics and medication data for report charts and export.
 */
@HiltViewModel
public class ReportViewModel extends ViewModel {

    public enum DateRange { DAYS_7, DAYS_30, ALL, CUSTOM }

    public static class MedicationBarData {
        public String label;
        public int takenCount;
        public int totalCount;

        public MedicationBarData(String label, int takenCount, int totalCount) {
            this.label = label;
            this.takenCount = takenCount;
            this.totalCount = totalCount;
        }
    }

    /** Holds computed report statistics. Used by both on-screen display and PDF export. */
    private static class ReportStats {
        int taken, missed, skipped, total;
        float percent;
        List<MedicationBarData> barData;
    }

    private final AdherenceDao adherenceDao;
    private final MedicationDao medicationDao;
    private final ScheduleDao scheduleDao;
    private final Executor executor;
    private final FirebaseFirestore firestore;

    private DateRange currentRange = DateRange.DAYS_7;
    private String customStartDate;
    private String customEndDate;
    private Set<Long> filteredMedicationIds = new HashSet<>();

    private final MutableLiveData<Float> adherencePercent = new MutableLiveData<>(0f);
    private final MutableLiveData<int[]> statusCounts = new MutableLiveData<>(new int[]{0, 0, 0});
    private final MutableLiveData<Integer> totalCount = new MutableLiveData<>(0);
    private final MutableLiveData<List<MedicationBarData>> barChartData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<String, String>> userProfile = new MutableLiveData<>();
    private final MutableLiveData<DateRange> selectedRange = new MutableLiveData<>(DateRange.DAYS_7);
    private final MutableLiveData<String> earliestDate = new MutableLiveData<>();
    private final MutableLiveData<String> rangeLabelText = new MutableLiveData<>("Past 7 Days");
    private final MutableLiveData<List<Medication>> allMedications = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Set<Long>> selectedMedicationIds = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<File> generatedPdf = new MutableLiveData<>();

    @Inject
    public ReportViewModel(AdherenceDao adherenceDao, MedicationDao medicationDao,
                           ScheduleDao scheduleDao, Executor executor,
                           FirebaseFirestore firestore) {
        this.adherenceDao = adherenceDao;
        this.medicationDao = medicationDao;
        this.scheduleDao = scheduleDao;
        this.executor = executor;
        this.firestore = firestore;

        loadMedications();
        loadEarliestDate();
        loadUserProfile();
        loadReportData();
    }

    // Getters
    public LiveData<Float> getAdherencePercent() { return adherencePercent; }
    public LiveData<int[]> getStatusCounts() { return statusCounts; }
    public LiveData<Integer> getTotalCount() { return totalCount; }
    public LiveData<List<MedicationBarData>> getBarChartData() { return barChartData; }
    public LiveData<Map<String, String>> getUserProfile() { return userProfile; }
    public LiveData<DateRange> getSelectedRange() { return selectedRange; }
    public LiveData<String> getEarliestDate() { return earliestDate; }
    public LiveData<String> getRangeLabelText() { return rangeLabelText; }
    public LiveData<List<Medication>> getAllMedications() { return allMedications; }
    public LiveData<Set<Long>> getSelectedMedicationIds() { return selectedMedicationIds; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<File> getGeneratedPdf() { return generatedPdf; }
    public DateRange getCurrentRange() { return currentRange; }
    public String getCustomStartDate() { return customStartDate; }
    public String getCustomEndDate() { return customEndDate; }

    public void setDateRange(DateRange range) {
        currentRange = range;
        selectedRange.postValue(range);
        loadReportData();
    }

    public void setCustomRange(String startDate, String endDate) {
        currentRange = DateRange.CUSTOM;
        customStartDate = startDate;
        customEndDate = endDate;
        selectedRange.postValue(DateRange.CUSTOM);
        loadReportData();
    }

    public void setMedicationFilter(Set<Long> ids) {
        filteredMedicationIds = ids != null ? ids : new HashSet<>();
        selectedMedicationIds.postValue(filteredMedicationIds);
        loadReportData();
    }

    public void loadReportData() {
        isLoading.postValue(true);
        executor.execute(() -> {
            try {
                ReportStats stats = computeStats(currentRange, customStartDate,
                        customEndDate, filteredMedicationIds);
                String label = buildRangeLabel(currentRange, customStartDate, customEndDate);

                adherencePercent.postValue(stats.percent);
                statusCounts.postValue(new int[]{stats.taken, stats.missed, stats.skipped});
                totalCount.postValue(stats.total);
                barChartData.postValue(stats.barData);
                rangeLabelText.postValue(label);
                isLoading.postValue(false);
            } catch (Exception e) {
                isLoading.postValue(false);
            }
        });
    }

    public void refreshPageData() {
        loadMedications();
        loadEarliestDate();
        loadUserProfile();
        loadReportData();
    }

    /**
     * Compute report statistics by iterating over each day in the range,
     * determining expected doses from schedules, and comparing against
     * actual adherence logs.
     *
     * MISSED = schedule expected but no adherence log exists AND time has passed.
     * This mirrors the Home screen logic in MedicationRepositoryImpl.
     */
    private ReportStats computeStats(DateRange range, String customStart,
                                      String customEnd, Set<Long> medFilter) {
        ReportStats stats = new ReportStats();
        stats.barData = new ArrayList<>();

        // 1. Determine date range bounds
        LocalDate today = LocalDate.now();
        LocalDate startDate, endDate;
        switch (range) {
            case DAYS_7:
                startDate = today.minusDays(6);
                endDate = today;
                break;
            case DAYS_30:
                startDate = today.minusDays(29);
                endDate = today;
                break;
            case ALL:
                startDate = getEarliestMedicationStartDate();
                endDate = today;
                break;
            case CUSTOM:
                startDate = customStart != null ? LocalDate.parse(customStart) : today.minusDays(6);
                endDate = customEnd != null ? LocalDate.parse(customEnd) : today;
                break;
            default:
                startDate = today.minusDays(6);
                endDate = today;
        }
        if (startDate == null) startDate = today.minusDays(6);

        // 2. Load all data once
        List<Schedule> allSchedules = scheduleDao.getAllSchedulesSync();
        List<Medication> allMeds = medicationDao.getAllMedicationsSync();
        if (allSchedules == null) allSchedules = new ArrayList<>();
        if (allMeds == null) allMeds = new ArrayList<>();

        Map<Long, Medication> medMap = new HashMap<>();
        for (Medication med : allMeds) medMap.put(med.getId(), med);

        // 3. Load all adherence logs in the range and build lookup
        List<AdherenceLog> logs;
        if (range == DateRange.ALL) {
            logs = adherenceDao.getAllLogsSync();
        } else {
            logs = adherenceDao.getLogsBetweenDatesSync(
                    startDate.toString() + " 00:00", endDate.toString() + " 23:59");
        }
        if (logs == null) logs = new ArrayList<>();

        // Build lookup: "medicationId_scheduleId_yyyy-MM-dd" -> status
        Map<String, String> logLookup = new HashMap<>();
        for (AdherenceLog log : logs) {
            String dateOnly = extractDatePart(log.getScheduledDateTime());
            String key = buildDoseKey(log.getMedicationId(), log.getScheduleId(), dateOnly);
            logLookup.put(key, log.getStatus());
        }

        // 4. Iterate through each day, compute expected doses vs actual
        int taken = 0, missed = 0, skipped = 0;
        Map<Long, int[]> medCounts = new HashMap<>(); // medId -> [taken, total]
        Set<String> accountedDoseKeys = new HashSet<>();

        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            String dayStr = day.toString();
            boolean isPast = day.isBefore(today);
            boolean isToday = day.equals(today);

            for (Schedule schedule : allSchedules) {
                Medication med = medMap.get(schedule.getMedicationId());
                if (med == null) continue;

                // Check medication active on this day (startDate/endDate bounds)
                if (med.getStartDate() != null && !med.getStartDate().isEmpty()
                        && dayStr.compareTo(med.getStartDate()) < 0) continue;
                if (med.getEndDate() != null && !med.getEndDate().isEmpty()
                        && dayStr.compareTo(med.getEndDate()) > 0) continue;

                // Check frequency interval (e.g., every 3 days from startDate)
                if (!isScheduledForDate(med.getFrequency(), med.getStartDate(), dayStr)) continue;

                // Apply medication filter
                if (!medFilter.isEmpty() && !medFilter.contains(med.getId())) continue;

                // This is an expected dose — check what happened
                String key = buildDoseKey(med.getId(), schedule.getId(), dayStr);
                String status = logLookup.get(key);
                accountedDoseKeys.add(key);

                int[] counts = getOrCreateMedicationCounts(medCounts, med.getId());
                counts[1]++; // total expected

                if ("TAKEN".equalsIgnoreCase(status)) {
                    taken++;
                    counts[0]++;
                } else if ("SKIPPED".equalsIgnoreCase(status)) {
                    skipped++;
                } else {
                    // No log exists — determine if missed or still pending
                    if (isPast) {
                        missed++;
                    } else if (isToday) {
                        try {
                            LocalTime scheduledTime = LocalTime.parse(schedule.getTime());
                            if (LocalTime.now().isAfter(scheduledTime)) {
                                missed++;
                            }
                        } catch (Exception e) { /* keep as pending — don't count */ }
                    }
                    // Future days: pending, not counted in taken/missed/skipped
                }
            }
        }

        // 4b. Preserve historical adherence logs even if the live schedule has been
        // removed or remapped. This prevents report data from disappearing after
        // schedule cleanup while avoiding double-counting schedule-backed doses.
        for (AdherenceLog log : logs) {
            String dayStr = extractDatePart(log.getScheduledDateTime());
            if (dayStr == null || dayStr.isEmpty()) continue;

            LocalDate logDate;
            try {
                logDate = LocalDate.parse(dayStr);
            } catch (Exception e) {
                continue;
            }

            if (logDate.isBefore(startDate) || logDate.isAfter(endDate)) continue;
            if (!medFilter.isEmpty() && !medFilter.contains(log.getMedicationId())) continue;

            String key = buildDoseKey(log.getMedicationId(), log.getScheduleId(), dayStr);
            if (accountedDoseKeys.contains(key)) continue;

            int[] counts = getOrCreateMedicationCounts(medCounts, log.getMedicationId());
            counts[1]++;

            String status = log.getStatus();
            if ("TAKEN".equalsIgnoreCase(status)) {
                taken++;
                counts[0]++;
            } else if ("SKIPPED".equalsIgnoreCase(status)) {
                skipped++;
            } else if ("MISSED".equalsIgnoreCase(status)) {
                missed++;
            }
        }

        // 5. Build bar chart data
        List<MedicationBarData> barData = new ArrayList<>();
        for (Map.Entry<Long, int[]> entry : medCounts.entrySet()) {
            Medication med = medMap.get(entry.getKey());
            String label = med != null
                    ? med.getName() + (med.getDosage() != null && !med.getDosage().isEmpty()
                        ? " (" + med.getDosage() + ")" : "")
                    : "Unknown";
            barData.add(new MedicationBarData(label, entry.getValue()[0], entry.getValue()[1]));
        }

        stats.taken = taken;
        stats.missed = missed;
        stats.skipped = skipped;
        stats.total = taken + missed + skipped;
        stats.percent = stats.total > 0 ? (taken * 100f / stats.total) : 0f;
        stats.barData = barData;
        return stats;
    }

    private String buildDoseKey(long medicationId, long scheduleId, String dateStr) {
        return medicationId + "_" + scheduleId + "_" + dateStr;
    }

    private String extractDatePart(String scheduledDateTime) {
        if (scheduledDateTime == null || scheduledDateTime.isEmpty()) return "";
        if (scheduledDateTime.contains(" ")) {
            return scheduledDateTime.substring(0, scheduledDateTime.indexOf(" "));
        }
        return scheduledDateTime;
    }

    private int[] getOrCreateMedicationCounts(Map<Long, int[]> medCounts, long medicationId) {
        int[] counts = medCounts.get(medicationId);
        if (counts == null) {
            counts = new int[]{0, 0};
            medCounts.put(medicationId, counts);
        }
        return counts;
    }

    /**
     * Check if a medication is scheduled for a given date based on frequency interval.
     * Mirrors MedicationScheduleItem.isScheduledForDate() logic.
     */
    private boolean isScheduledForDate(String frequency, String startDate, String dateStr) {
        int intervalDays = 1;
        if (frequency != null && frequency.contains("/")) {
            try {
                String[] parts = frequency.split("/");
                intervalDays = Integer.parseInt(parts[1].trim());
            } catch (Exception e) { /* default 1 */ }
        }
        if (intervalDays <= 1) return true;
        if (startDate == null || startDate.isEmpty()) return true;

        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate selected = LocalDate.parse(dateStr);
            long diffDays = ChronoUnit.DAYS.between(start, selected);
            return diffDays >= 0 && (diffDays % intervalDays) == 0;
        } catch (Exception e) {
            return true;
        }
    }

    /** Get the earliest medication startDate across all medications. */
    private LocalDate getEarliestMedicationStartDate() {
        List<Medication> meds = medicationDao.getAllMedicationsSync();
        LocalDate earliest = null;
        if (meds != null) {
            for (Medication med : meds) {
                if (med.getStartDate() != null && !med.getStartDate().isEmpty()) {
                    try {
                        LocalDate d = LocalDate.parse(med.getStartDate());
                        if (earliest == null || d.isBefore(earliest)) {
                            earliest = d;
                        }
                    } catch (Exception e) { /* skip */ }
                }
            }
        }
        return earliest != null ? earliest : LocalDate.now().minusDays(6);
    }

    private String buildRangeLabel(DateRange range, String customStart, String customEnd) {
        DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        switch (range) {
            case DAYS_7: return "Past 7 Days";
            case DAYS_30: return "Past 30 Days";
            case ALL: return "All Time";
            case CUSTOM:
                if (customStart != null && customEnd != null) {
                    try {
                        LocalDate start = LocalDate.parse(customStart);
                        LocalDate end = LocalDate.parse(customEnd);
                        return start.format(DateTimeFormatter.ofPattern("MMM d"))
                                + " - " + end.format(displayFmt);
                    } catch (Exception e) {
                        return "Custom Range";
                    }
                }
                return "Custom Range";
            default: return "";
        }
    }

    public void loadMedications() {
        executor.execute(() -> {
            String today = LocalDate.now().toString();
            List<Medication> meds = medicationDao.getReportFilterMedicationsSync(today);
            allMedications.postValue(meds != null ? meds : new ArrayList<>());
        });
    }

    private void loadEarliestDate() {
        executor.execute(() -> {
            // Use earliest medication startDate as min bound for custom date picker
            LocalDate earliest = getEarliestMedicationStartDate();
            earliestDate.postValue(earliest.toString());
        });
    }

    private void loadUserProfile() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Map<String, String> profile = new HashMap<>();
                        profile.put("name", doc.getString("name"));
                        profile.put("email", doc.getString("email"));
                        profile.put("gender", doc.getString("gender"));
                        profile.put("dob", doc.getString("dob"));
                        profile.put("weight", doc.getString("weight"));
                        profile.put("bloodType", doc.getString("bloodType"));
                        profile.put("allergies", doc.getString("allergies"));
                        userProfile.postValue(profile);
                    }
                });
    }

    public void generatePdf(Context context, DateRange range, String exportCustomStart,
                            String exportCustomEnd, Set<Long> medIds) {
        isLoading.postValue(true);
        executor.execute(() -> {
            try {
                // Compute stats for export parameters (independent from on-screen state)
                ReportStats stats = computeStats(range, exportCustomStart, exportCustomEnd,
                        medIds != null ? medIds : new HashSet<>());

                // Build range label for export
                String rangeLabel = buildRangeLabel(range, exportCustomStart, exportCustomEnd);

                // Build medication filter label
                String medFilterLabel;
                if (medIds == null || medIds.isEmpty()) {
                    medFilterLabel = "All Medications";
                } else {
                    List<String> names = new ArrayList<>();
                    for (Long id : medIds) {
                        Medication med = medicationDao.getMedicationById(id);
                        if (med != null) names.add(med.getName());
                    }
                    medFilterLabel = String.join(", ", names);
                }

                Map<String, String> profile = userProfile.getValue();

                File pdf = ReportPdfGenerator.generate(
                        context, profile, rangeLabel, medFilterLabel,
                        stats.percent, stats.taken, stats.missed, stats.skipped,
                        stats.total, stats.barData);

                generatedPdf.postValue(pdf);
                isLoading.postValue(false);
            } catch (Exception e) {
                isLoading.postValue(false);
            }
        });
    }

    /** Reset the generatedPdf LiveData after the email intent has been launched. */
    public void clearGeneratedPdf() {
        generatedPdf.setValue(null);
    }
}
