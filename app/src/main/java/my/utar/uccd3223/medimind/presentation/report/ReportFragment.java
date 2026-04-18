package my.utar.uccd3223.medimind.presentation.report;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.data.local.database.entities.Medication;
import my.utar.uccd3223.medimind.data.repository.MedicationRepositoryImpl;
import my.utar.uccd3223.medimind.databinding.FragmentReportBinding;
import my.utar.uccd3223.medimind.domain.repository.MedicationRepository;

@AndroidEntryPoint
public class ReportFragment extends Fragment {

    private FragmentReportBinding binding;
    private ReportViewModel viewModel;
    @Inject MedicationRepository repository;

    private List<Medication> currentMedications = new ArrayList<>();
    private boolean[] currentFilterChecked = new boolean[0];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ReportViewModel.class);

        setupDateRangeToggle();
        setupExportButton();
        setupPieChart();
        observeViewModel();
    }

    private void setupDateRangeToggle() {
        binding.toggleDateRange.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_7_days) {
                viewModel.setDateRange(ReportViewModel.DateRange.DAYS_7);
            } else if (checkedId == R.id.btn_30_days) {
                viewModel.setDateRange(ReportViewModel.DateRange.DAYS_30);
            } else if (checkedId == R.id.btn_all) {
                viewModel.setDateRange(ReportViewModel.DateRange.ALL);
            } else if (checkedId == R.id.btn_custom) {
                showDateRangePicker(false, null);
            }
        });
    }

    private void showDateRangePicker(boolean isForExport, ExportDialogState exportState) {
        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();

        // Set max to today
        long today = MaterialDatePicker.todayInUtcMilliseconds();
        constraintsBuilder.setEnd(today);

        List<CalendarConstraints.DateValidator> validators = new ArrayList<>();
        validators.add(DateValidatorPointBackward.now());

        // Set min to earliest log date if available
        String earliest = viewModel.getEarliestDate().getValue();
        if (earliest != null) {
            try {
                LocalDate earliestLocal = LocalDate.parse(earliest);
                long earliestMs = earliestLocal.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
                constraintsBuilder.setStart(earliestMs);
                validators.add(DateValidatorPointForward.from(earliestMs));
            } catch (Exception ignored) {}
        }

        constraintsBuilder.setValidator(CompositeDateValidator.allOf(validators));

        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(getString(R.string.select_date_range))
                .setCalendarConstraints(constraintsBuilder.build())
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            LocalDate start = Instant.ofEpochMilli(selection.first)
                    .atZone(ZoneId.of("UTC")).toLocalDate();
            LocalDate end = Instant.ofEpochMilli(selection.second)
                    .atZone(ZoneId.of("UTC")).toLocalDate();

            String startStr = start.toString();
            String endStr = end.toString();

            if (isForExport && exportState != null) {
                exportState.customStart = startStr;
                exportState.customEnd = endStr;
                exportState.range = ReportViewModel.DateRange.CUSTOM;
                // Continue export flow: show medication filter, then export
                showMedicationFilterDialog(true, exportState);
            } else {
                viewModel.setCustomRange(startStr, endStr);
            }
        });

        picker.addOnNegativeButtonClickListener(v -> {
            // If user cancels custom picker for on-screen, revert to previous selection
            if (!isForExport) {
                ReportViewModel.DateRange current = viewModel.getCurrentRange();
                if (current != ReportViewModel.DateRange.CUSTOM) {
                    updateToggleButton(current);
                }
            }
        });

        picker.show(getParentFragmentManager(), "date_range_picker");
    }

    private void updateToggleButton(ReportViewModel.DateRange range) {
        switch (range) {
            case DAYS_7:
                binding.toggleDateRange.check(R.id.btn_7_days);
                break;
            case DAYS_30:
                binding.toggleDateRange.check(R.id.btn_30_days);
                break;
            case ALL:
                binding.toggleDateRange.check(R.id.btn_all);
                break;
            case CUSTOM:
                binding.toggleDateRange.check(R.id.btn_custom);
                break;
        }
    }

    private void showMedicationFilterDialog(boolean isForExport, ExportDialogState exportState) {
        if (currentMedications.isEmpty()) {
            Toast.makeText(requireContext(), "No medications available", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[currentMedications.size()];
        boolean[] checked;

        if (isForExport && exportState != null) {
            checked = new boolean[currentMedications.size()];
            for (int i = 0; i < currentMedications.size(); i++) {
                names[i] = currentMedications.get(i).getName();
                checked[i] = exportState.medicationIds.isEmpty()
                        || exportState.medicationIds.contains(currentMedications.get(i).getId());
            }
        } else {
            checked = Arrays.copyOf(currentFilterChecked, currentFilterChecked.length);
            for (int i = 0; i < currentMedications.size(); i++) {
                names[i] = currentMedications.get(i).getName();
            }
        }

        // Build custom title with Select All / Deselect All links
        LinearLayout headerLayout = new LinearLayout(requireContext());
        headerLayout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        headerLayout.setPadding(pad, pad, pad, 0);

        TextView titleView = new TextView(requireContext());
        titleView.setText(R.string.medication_filter);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTextColor(getResources().getColor(R.color.text_primary, null));
        headerLayout.addView(titleView);

        LinearLayout linkRow = new LinearLayout(requireContext());
        linkRow.setOrientation(LinearLayout.HORIZONTAL);
        int topMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = topMargin;
        linkRow.setLayoutParams(rowParams);

        TextView selectAll = new TextView(requireContext());
        selectAll.setText(R.string.select_all);
        selectAll.setTextColor(getResources().getColor(R.color.primary, null));
        selectAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        selectAll.setPadding(0, 0, pad, 0);
        linkRow.addView(selectAll);

        TextView deselectAll = new TextView(requireContext());
        deselectAll.setText(R.string.deselect_all);
        deselectAll.setTextColor(getResources().getColor(R.color.primary, null));
        deselectAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        linkRow.addView(deselectAll);

        headerLayout.addView(linkRow);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setCustomTitle(headerLayout)
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    Set<Long> selected = new HashSet<>();
                    boolean allSelected = true;
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            selected.add(currentMedications.get(i).getId());
                        } else {
                            allSelected = false;
                        }
                    }
                    // If all selected, use empty set to mean "all"
                    if (allSelected) selected.clear();

                    if (isForExport && exportState != null) {
                        exportState.medicationIds = selected;
                        doExport(exportState);
                    } else {
                        viewModel.setMedicationFilter(selected);
                        currentFilterChecked = checked;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        selectAll.setOnClickListener(v -> {
            ListView listView = dialog.getListView();
            for (int i = 0; i < checked.length; i++) {
                checked[i] = true;
                listView.setItemChecked(i, true);
            }
        });

        deselectAll.setOnClickListener(v -> {
            ListView listView = dialog.getListView();
            for (int i = 0; i < checked.length; i++) {
                checked[i] = false;
                listView.setItemChecked(i, false);
            }
        });

        dialog.show();
    }

    private void setupExportButton() {
        binding.btnExport.setOnClickListener(v -> showExportDialog());
    }

    private void showExportDialog() {
        Integer total = viewModel.getTotalCount().getValue();
        if (total == null || total == 0) {
            Toast.makeText(requireContext(), R.string.no_adherence_data, Toast.LENGTH_SHORT).show();
            return;
        }

        // Pre-fill export state with current on-screen settings
        ExportDialogState state = new ExportDialogState();
        state.range = viewModel.getCurrentRange();
        state.customStart = viewModel.getCustomStartDate();
        state.customEnd = viewModel.getCustomEndDate();
        Set<Long> currentFilter = viewModel.getSelectedMedicationIds().getValue();
        state.medicationIds = currentFilter != null ? new HashSet<>(currentFilter) : new HashSet<>();

        // Show export settings dialog
        String[] rangeOptions = {"7 Days", "30 Days", "All", "Custom"};
        int currentIdx;
        switch (state.range) {
            case DAYS_30: currentIdx = 1; break;
            case ALL: currentIdx = 2; break;
            case CUSTOM: currentIdx = 3; break;
            default: currentIdx = 0; break;
        }

        final int[] selectedIdx = {currentIdx};

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.export_settings)
                .setSingleChoiceItems(rangeOptions, currentIdx, (dialog, which) -> {
                    selectedIdx[0] = which;
                })
                .setPositiveButton(R.string.export_text, (dialog, which) -> {
                    switch (selectedIdx[0]) {
                        case 0: state.range = ReportViewModel.DateRange.DAYS_7; break;
                        case 1: state.range = ReportViewModel.DateRange.DAYS_30; break;
                        case 2: state.range = ReportViewModel.DateRange.ALL; break;
                        case 3:
                            state.range = ReportViewModel.DateRange.CUSTOM;
                            // Show date picker then continue export
                            showDateRangePicker(true, state);
                            return;
                    }
                    // Show medication filter for export
                    showMedicationFilterDialog(true, state);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doExport(ExportDialogState state) {
        viewModel.generatePdf(requireContext(), state.range,
                state.customStart, state.customEnd, state.medicationIds);
    }

    private void setupPieChart() {
        PieChart chart = binding.pieChart;
        chart.setUsePercentValues(false);
        chart.getDescription().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.WHITE);
        chart.setHoleRadius(65f);
        chart.setTransparentCircleRadius(68f);
        chart.setDrawEntryLabels(false);
        chart.getLegend().setEnabled(false);
        chart.setRotationEnabled(false);
    }

    private void observeViewModel() {
        viewModel.getStatusCounts().observe(getViewLifecycleOwner(), counts -> {
            int taken = counts[0], missed = counts[1], skipped = counts[2];
            binding.textTakenCount.setText(String.valueOf(taken));
            binding.textMissedCount.setText(String.valueOf(missed));
            binding.textSkippedCount.setText(String.valueOf(skipped));
            binding.textTotalScheduled.setText(String.valueOf(taken + missed + skipped));
            updatePieChart(taken, missed, skipped);
        });

        viewModel.getAdherencePercent().observe(getViewLifecycleOwner(), percent -> {
            binding.pieChart.setCenterText(String.format("%.0f%%", percent));
            binding.pieChart.setCenterTextSize(20f);
            binding.pieChart.setCenterTextColor(getResources().getColor(R.color.primary, null));
        });

        viewModel.getBarChartData().observe(getViewLifecycleOwner(), this::updateBarChart);

        viewModel.getRangeLabelText().observe(getViewLifecycleOwner(), label -> {
            binding.textBarChartTitle.setText(getString(R.string.medications_in_range, label));
        });

        viewModel.getAllMedications().observe(getViewLifecycleOwner(), meds -> {
            currentMedications = meds != null ? meds : new ArrayList<>();
            syncFilterCheckedState();
            populateFilterChips(currentMedications);
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        });

        viewModel.getTotalCount().observe(getViewLifecycleOwner(), total -> {
            boolean empty = (total == null || total == 0);
            binding.textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.scrollContent.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.getGeneratedPdf().observe(getViewLifecycleOwner(), file -> {
            if (file != null) {
                launchEmailIntent(file);
                viewModel.clearGeneratedPdf();
            }
        });

        viewModel.getSelectedMedicationIds().observe(getViewLifecycleOwner(), ids -> {
            syncFilterCheckedState();
            populateFilterChips(currentMedications);
        });
    }

    private void syncFilterCheckedState() {
        currentFilterChecked = new boolean[currentMedications.size()];
        Set<Long> selectedIds = viewModel.getSelectedMedicationIds().getValue();
        boolean allSelected = (selectedIds == null || selectedIds.isEmpty());

        for (int i = 0; i < currentMedications.size(); i++) {
            currentFilterChecked[i] = allSelected || selectedIds.contains(currentMedications.get(i).getId());
        }
    }

    private void populateFilterChips(List<Medication> medications) {
        if (binding == null) return;
        LinearLayout container = binding.chipContainer;
        container.removeAllViews();

        if (medications == null || medications.isEmpty()) return;

        int dp4 = dpToPx(4);
        int dp8 = dpToPx(8);
        int dp14 = dpToPx(14);
        int dp32 = dpToPx(32);

        Set<Long> selectedIds = viewModel.getSelectedMedicationIds().getValue();
        boolean allSelected = (selectedIds == null || selectedIds.isEmpty());

        int[] dotColors = {
                getResources().getColor(R.color.chip_dot_1, null),
                getResources().getColor(R.color.chip_dot_2, null),
                getResources().getColor(R.color.chip_dot_3, null)
        };

        // --- "All" chip ---
        LinearLayout allChip = new LinearLayout(requireContext());
        allChip.setOrientation(LinearLayout.HORIZONTAL);
        allChip.setGravity(Gravity.CENTER_VERTICAL);
        allChip.setBackground(getResources().getDrawable(
                allSelected ? R.drawable.bg_chip_all_active : R.drawable.bg_chip_all_inactive, null));
        allChip.setMinimumHeight(dp32);
        allChip.setPadding(dp14, dp4, dp14, dp4);

        ImageView layersIcon = new ImageView(requireContext());
        layersIcon.setImageResource(R.drawable.ic_layers);
        layersIcon.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(12), dpToPx(12)));
        if (!allSelected) {
            layersIcon.setColorFilter(getResources().getColor(R.color.toggle_inactive_text, null));
        }
        allChip.addView(layersIcon);

        TextView allText = new TextView(requireContext());
        allText.setText(R.string.all);
        allText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        allText.setTextColor(allSelected
                ? getResources().getColor(R.color.on_primary, null)
                : getResources().getColor(R.color.toggle_inactive_text, null));
        LinearLayout.LayoutParams allTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        allTextParams.setMarginStart(dpToPx(6));
        allText.setLayoutParams(allTextParams);
        allChip.addView(allText);

        allChip.setOnClickListener(v -> {
            viewModel.setMedicationFilter(new HashSet<>());
            currentFilterChecked = new boolean[currentMedications.size()];
            Arrays.fill(currentFilterChecked, true);
        });
        container.addView(allChip);

        // --- Individual medication chips (up to 2 visible) ---
        int visibleCount = Math.min(medications.size(), 2);
        for (int i = 0; i < visibleCount; i++) {
            Medication med = medications.get(i);
            boolean isSelected = !allSelected && selectedIds.contains(med.getId());

            LinearLayout chip = new LinearLayout(requireContext());
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setBackground(getResources().getDrawable(
                    isSelected ? R.drawable.bg_chip_medication_active : R.drawable.bg_chip_medication, null));
            chip.setMinimumHeight(dp32);
            chip.setPadding(dp14, dp4, dp14, dp4);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipParams.setMarginStart(dp8);
            chip.setLayoutParams(chipParams);

            // Colored dot
            View dot = new View(requireContext());
            int dotSize = dpToPx(7);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
            dot.setLayoutParams(dotParams);
            GradientDrawable dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(isSelected ? Color.WHITE : dotColors[i % dotColors.length]);
            dot.setBackground(dotDrawable);
            chip.addView(dot);

            // Name
            TextView nameText = new TextView(requireContext());
            nameText.setText(med.getName());
            nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            nameText.setTextColor(isSelected
                    ? getResources().getColor(R.color.on_primary, null)
                    : Color.parseColor("#1A1918"));
            nameText.setMaxLines(1);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            nameParams.setMarginStart(dpToPx(6));
            nameText.setLayoutParams(nameParams);
            chip.addView(nameText);

            chip.setOnClickListener(v -> {
                Set<Long> current = viewModel.getSelectedMedicationIds().getValue();
                Set<Long> newSet;
                if (current == null || current.isEmpty()) {
                    // Was "all" — select only this medication
                    newSet = new HashSet<>();
                    newSet.add(med.getId());
                } else {
                    newSet = new HashSet<>(current);
                    if (newSet.contains(med.getId())) {
                        newSet.remove(med.getId());
                    } else {
                        newSet.add(med.getId());
                    }
                }
                // If all are now selected, reset to empty (meaning "all")
                if (newSet.size() == currentMedications.size()) newSet.clear();
                // If none selected, revert to all
                if (newSet.isEmpty()) {
                    // empty set means all
                }
                viewModel.setMedicationFilter(newSet);
                // Sync currentFilterChecked
                currentFilterChecked = new boolean[currentMedications.size()];
                for (int j = 0; j < currentMedications.size(); j++) {
                    currentFilterChecked[j] = newSet.isEmpty()
                            || newSet.contains(currentMedications.get(j).getId());
                }
            });

            container.addView(chip);
        }

        // --- "+N more" chip ---
        if (medications.size() > 2) {
            int remaining = medications.size() - 2;

            LinearLayout moreChip = new LinearLayout(requireContext());
            moreChip.setOrientation(LinearLayout.HORIZONTAL);
            moreChip.setGravity(Gravity.CENTER_VERTICAL);
            moreChip.setBackground(getResources().getDrawable(R.drawable.bg_chip_more, null));
            moreChip.setMinimumHeight(dp32);
            moreChip.setPadding(dp14, dp4, dp14, dp4);
            LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            moreParams.setMarginStart(dp8);
            moreChip.setLayoutParams(moreParams);

            TextView moreText = new TextView(requireContext());
            moreText.setText(getString(R.string.chip_more_format, remaining));
            moreText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            moreText.setTextColor(getResources().getColor(R.color.toggle_inactive_text, null));
            moreChip.addView(moreText);

            ImageView chevron = new ImageView(requireContext());
            chevron.setImageResource(R.drawable.ic_chevron_down_small);
            LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dpToPx(12), dpToPx(12));
            chevronParams.setMarginStart(dp4);
            chevron.setLayoutParams(chevronParams);
            moreChip.addView(chevron);

            moreChip.setOnClickListener(v -> showMedicationFilterDialog(false, null));
            container.addView(moreChip);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void updatePieChart(int taken, int missed, int skipped) {
        PieChart chart = binding.pieChart;
        List<PieEntry> entries = new ArrayList<>();

        if (taken > 0) entries.add(new PieEntry(taken, "Taken"));
        if (missed > 0) entries.add(new PieEntry(missed, "Missed"));
        if (skipped > 0) entries.add(new PieEntry(skipped, "Skipped"));

        if (entries.isEmpty()) {
            entries.add(new PieEntry(1, "No Data"));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        List<Integer> colors = new ArrayList<>();
        for (PieEntry entry : entries) {
            switch (entry.getLabel()) {
                case "Taken": colors.add(getResources().getColor(R.color.success, null)); break;
                case "Missed": colors.add(getResources().getColor(R.color.error, null)); break;
                case "Skipped": colors.add(getResources().getColor(R.color.warning, null)); break;
                default: colors.add(getResources().getColor(R.color.divider, null)); break;
            }
        }
        dataSet.setColors(colors);
        dataSet.setDrawValues(false);

        PieData data = new PieData(dataSet);
        chart.setData(data);
        chart.invalidate();
    }

    private void updateBarChart(List<ReportViewModel.MedicationBarData> barData) {
        HorizontalBarChart chart = binding.barChart;

        if (barData == null || barData.isEmpty()) {
            chart.clear();
            return;
        }

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < barData.size(); i++) {
            entries.add(new BarEntry(i, barData.get(i).takenCount));
            labels.add(barData.get(i).label);
        }

        BarDataSet dataSet = new BarDataSet(entries, "Medicine Taken");
        dataSet.setColor(getResources().getColor(R.color.chart_bar, null));
        dataSet.setValueTextSize(12f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setFitBars(true);
        chart.setDrawValueAboveBar(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);

        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setGranularity(1f);
        chart.getAxisRight().setEnabled(false);

        // Dynamic height: ~50dp per medication + padding
        int heightDp = Math.max(200, barData.size() * 50 + 40);
        int heightPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, heightDp, getResources().getDisplayMetrics());
        ViewGroup.LayoutParams params = chart.getLayoutParams();
        params.height = heightPx;
        chart.setLayoutParams(params);

        chart.invalidate();
    }

    private void launchEmailIntent(File pdfFile) {
        Uri uri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", pdfFile);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newRawUri("", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Pre-fill email from user profile
        Map<String, String> profile = viewModel.getUserProfile().getValue();
        if (profile != null && profile.get("email") != null) {
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{profile.get("email")});
        }

        intent.putExtra(Intent.EXTRA_SUBJECT,
                "MediMind Health Report - " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy")));

        try {
            Intent chooser = Intent.createChooser(intent, getString(R.string.share_report));
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.no_app_to_share, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (repository instanceof MedicationRepositoryImpl) {
            ((MedicationRepositoryImpl) repository).syncFromFirestore(
                    viewModel::refreshPageData,
                    viewModel::refreshPageData
            );
        } else {
            viewModel.refreshPageData();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** Helper class for export dialog state */
    private static class ExportDialogState {
        ReportViewModel.DateRange range;
        String customStart;
        String customEnd;
        Set<Long> medicationIds = new HashSet<>();
    }
}
