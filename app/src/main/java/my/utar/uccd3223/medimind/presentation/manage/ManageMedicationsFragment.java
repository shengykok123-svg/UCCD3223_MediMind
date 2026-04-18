package my.utar.uccd3223.medimind.presentation.manage;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dagger.hilt.android.AndroidEntryPoint;
import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.data.local.database.entities.Medication;
import my.utar.uccd3223.medimind.data.local.database.entities.Schedule;
import my.utar.uccd3223.medimind.databinding.FragmentManageMedicationsBinding;
import my.utar.uccd3223.medimind.util.MedicationImageUtils;

@AndroidEntryPoint
public class ManageMedicationsFragment extends Fragment {

    private FragmentManageMedicationsBinding binding;
    private ManageMedicationsViewModel viewModel;
    private ManageMedicationAdapter adapter;

    private ActivityResultLauncher<String> imagePickerLauncher;
    private Uri selectedImageUri;
    private ImageView dialogImagePreview;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && dialogImagePreview != null) {
                        String localPath = copyImageToInternalStorage(uri);
                        if (localPath != null) {
                            selectedImageUri = Uri.fromFile(new File(localPath));
                        } else {
                            selectedImageUri = uri;
                        }
                        dialogImagePreview.setPadding(0, 0, 0, 0);
                        dialogImagePreview.setImageURI(selectedImageUri);
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentManageMedicationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ManageMedicationsViewModel.class);

        setupRecyclerView();
        setupObservers();
        setupButtons();

        // Check for pre-fill data from scan
        if (getArguments() != null) {
            String scannedName = getArguments().getString("scanned_med_name");
            String scannedDosage = getArguments().getString("scanned_med_dosage");
            String scannedFrequencyDoses = getArguments().getString("scanned_med_frequency_doses");
            String scannedFrequencyDays = getArguments().getString("scanned_med_frequency_days");
            String scannedMealInstructions = getArguments().getString("scanned_med_meal_instructions");
            String scannedImage = getArguments().getString("scanned_med_image");
            if (scannedName != null || scannedDosage != null
                    || scannedFrequencyDoses != null || scannedFrequencyDays != null
                    || scannedMealInstructions != null || scannedImage != null) {
                showAddMedicationDialog(scannedName, scannedDosage,
                        scannedFrequencyDoses, scannedFrequencyDays,
                        scannedMealInstructions, scannedImage);
                setArguments(null);
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new ManageMedicationAdapter(
                medication -> showEditMedicationDialog(medication),
                medication -> showDeleteConfirmation(medication)
        );
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupObservers() {
        viewModel.getAllMedications().observe(getViewLifecycleOwner(), medications -> {
            if (medications != null) {
                adapter.submitList(medications);
                binding.emptyView.setVisibility(medications.isEmpty() ? View.VISIBLE : View.GONE);
                binding.recyclerView.setVisibility(medications.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getSuccessMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupButtons() {
        binding.btnBack.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        binding.btnAddNew.setOnClickListener(v -> showAddMedicationDialog());
    }

    // ─── Add Medication Dialog ──────────────────────────────────────────
    private void showAddMedicationDialog() {
        showAddMedicationDialog(null, null, null, null, null, null);
    }

    private void showAddMedicationDialog(String preFillName, String preFillDosage,
                                         String preFillFrequencyDoses, String preFillFrequencyDays,
                                         String preFillMealInstructions, String preFillImagePath) {
        selectedImageUri = null;

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_medication);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        EditText nameInput = dialog.findViewById(R.id.input_name);
        EditText dosageInput = dialog.findViewById(R.id.input_dosage);
        EditText freqDosesInput = dialog.findViewById(R.id.input_freq_doses);
        EditText freqDaysInput = dialog.findViewById(R.id.input_freq_days);
        TextView freqLabel = dialog.findViewById(R.id.text_freq_label);
        EditText timeInput1 = dialog.findViewById(R.id.input_time_1);
        EditText timeInput2 = dialog.findViewById(R.id.input_time_2);
        EditText timeInput3 = dialog.findViewById(R.id.input_time_3);
        EditText startDateInput = dialog.findViewById(R.id.input_start_date);
        EditText endDateInput = dialog.findViewById(R.id.input_end_date);
        Spinner mealSpinner = dialog.findViewById(R.id.spinner_meal_instructions);
        ImageView imagePreview = dialog.findViewById(R.id.img_medicine_preview);
        Button pickImageBtn = dialog.findViewById(R.id.btn_pick_image);
        ImageButton closeButton = dialog.findViewById(R.id.btn_close);
        Button addButton = dialog.findViewById(R.id.btn_add);

        dialogImagePreview = imagePreview;

        // Pre-fill from scan if provided
        if (preFillName != null) {
            nameInput.setText(preFillName);
        }
        if (preFillDosage != null) {
            dosageInput.setText(preFillDosage);
        }
        if (preFillFrequencyDoses != null && !preFillFrequencyDoses.trim().isEmpty()) {
            freqDosesInput.setText(preFillFrequencyDoses.trim());
        }
        if (preFillFrequencyDays != null && !preFillFrequencyDays.trim().isEmpty()) {
            freqDaysInput.setText(preFillFrequencyDays.trim());
        }
        applyMealInstructionPrefill(mealSpinner, preFillMealInstructions);
        if (preFillImagePath != null) {
            selectedImageUri = Uri.fromFile(new File(preFillImagePath));
            MedicationImageUtils.loadMedicationImage(imagePreview, selectedImageUri.toString());
        } else {
            MedicationImageUtils.loadMedicationImage(
                    imagePreview,
                    MedicationImageUtils.getDefaultMedicationImageUri(requireContext())
            );
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        startDateInput.setText(sdf.format(Calendar.getInstance().getTime()));

        // Frequency label auto-update + dynamic time fields
        TextWatcher freqWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateFrequencyLabel(freqDosesInput, freqDaysInput, freqLabel);
                updateTimeFieldsVisibility(freqDosesInput, timeInput2, timeInput3);
            }
        };
        freqDosesInput.addTextChangedListener(freqWatcher);
        freqDaysInput.addTextChangedListener(freqWatcher);
        updateFrequencyLabel(freqDosesInput, freqDaysInput, freqLabel);
        updateTimeFieldsVisibility(freqDosesInput, timeInput2, timeInput3);

        // Time pickers for all 3 time fields
        setupTimePickerClickListener(timeInput1);
        setupTimePickerClickListener(timeInput2);
        setupTimePickerClickListener(timeInput3);

        // Date pickers
        startDateInput.setOnClickListener(v -> showDatePicker(startDateInput, sdf));
        endDateInput.setOnClickListener(v -> showDatePicker(endDateInput, sdf));

        pickImageBtn.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        closeButton.setOnClickListener(v -> dialog.dismiss());

        // Scan Medication button — navigate to scan page
        View scanButton = dialog.findViewById(R.id.btn_scan_medication);
        scanButton.setOnClickListener(v -> {
            dialog.dismiss();
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_manage_to_scan);
        });

        addButton.setOnClickListener(v -> {
            List<EditText> visibleTimeInputs = getVisibleTimeInputs(timeInput1, timeInput2, timeInput3);
            if (!validateInputs(nameInput, dosageInput, visibleTimeInputs, startDateInput, endDateInput,
                    freqDosesInput, freqDaysInput)) return;

            String name = nameInput.getText().toString().trim();
            String dosage = dosageInput.getText().toString().trim();
            String freqDosesStr = freqDosesInput.getText().toString().trim();
            String freqDaysStr = freqDaysInput.getText().toString().trim();
            String startDate = startDateInput.getText().toString().trim();
            String endDate = endDateInput.getText().toString().trim();
            String mealInstructions = mealSpinner.getSelectedItem().toString();

            int freqDoses = freqDosesStr.isEmpty() ? 1 : Integer.parseInt(freqDosesStr);
            int freqDays = freqDaysStr.isEmpty() ? 1 : Integer.parseInt(freqDaysStr);
            String frequency = freqDoses + "/" + freqDays;

            List<String> times = collectTimes(visibleTimeInputs);

            String imageUrl = (selectedImageUri != null)
                    ? selectedImageUri.toString()
                    : MedicationImageUtils.getDefaultMedicationImageUri(requireContext());

            viewModel.addMedicationWithSchedule(
                    name, dosage, frequency, times,
                    startDate, endDate,
                    mealInstructions, imageUrl
            );
            dialog.dismiss();
        });

        dialog.show();
    }

    // ─── Edit Medication Dialog ─────────────────────────────────────────
    private void showEditMedicationDialog(Medication medication) {
        selectedImageUri = null;

        viewModel.loadMedicationWithSchedule(medication.getId(), (med, schedules) -> {
            if (med == null) return;

            requireActivity().runOnUiThread(() -> {
                Dialog dialog = new Dialog(requireContext());
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(R.layout.dialog_edit_medication);

                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    dialog.getWindow().setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                }

                EditText nameInput = dialog.findViewById(R.id.input_name);
                EditText dosageInput = dialog.findViewById(R.id.input_dosage);
                EditText freqDosesInput = dialog.findViewById(R.id.input_freq_doses);
                EditText freqDaysInput = dialog.findViewById(R.id.input_freq_days);
                TextView freqLabel = dialog.findViewById(R.id.text_freq_label);
                EditText timeInput1 = dialog.findViewById(R.id.input_time_1);
                EditText timeInput2 = dialog.findViewById(R.id.input_time_2);
                EditText timeInput3 = dialog.findViewById(R.id.input_time_3);
                EditText startDateInput = dialog.findViewById(R.id.input_start_date);
                EditText endDateInput = dialog.findViewById(R.id.input_end_date);
                Spinner mealSpinner = dialog.findViewById(R.id.spinner_meal_instructions);
                ImageView imagePreview = dialog.findViewById(R.id.img_medicine_preview);
                Button pickImageBtn = dialog.findViewById(R.id.btn_pick_image);
                ImageButton closeButton = dialog.findViewById(R.id.btn_close);
                Button saveButton = dialog.findViewById(R.id.btn_save);

                dialogImagePreview = imagePreview;

                // Pre-fill fields
                nameInput.setText(med.getName());
                dosageInput.setText(med.getDosage());

                if (med.getFrequency() != null && med.getFrequency().contains("/")) {
                    String[] freqParts = med.getFrequency().split("/");
                    freqDosesInput.setText(freqParts[0].trim());
                    freqDaysInput.setText(freqParts[1].trim());
                }

                if (med.getStartDate() != null) startDateInput.setText(med.getStartDate());
                if (med.getEndDate() != null) endDateInput.setText(med.getEndDate());

                // Pre-fill time fields from existing schedules
                if (schedules != null && schedules.size() > 0) {
                    timeInput1.setText(schedules.get(0).getTime());
                }
                if (schedules != null && schedules.size() > 1) {
                    timeInput2.setText(schedules.get(1).getTime());
                }
                if (schedules != null && schedules.size() > 2) {
                    timeInput3.setText(schedules.get(2).getTime());
                }

                // Show/hide time fields based on frequency
                updateTimeFieldsVisibility(freqDosesInput, timeInput2, timeInput3);

                // Pre-select meal instruction in spinner (from first schedule)
                Schedule firstSchedule = (schedules != null && !schedules.isEmpty()) ? schedules.get(0) : null;
                if (firstSchedule != null && firstSchedule.getInstructions() != null) {
                    String[] options = getResources().getStringArray(R.array.meal_instructions_array);
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].equals(firstSchedule.getInstructions())) {
                            mealSpinner.setSelection(i);
                            break;
                        }
                    }
                }

                // Show existing image
                if (med.getImageUrl() != null && !med.getImageUrl().isEmpty()) {
                    MedicationImageUtils.loadMedicationImage(imagePreview, med.getImageUrl());
                } else {
                    MedicationImageUtils.loadMedicationImage(
                            imagePreview,
                            MedicationImageUtils.getDefaultMedicationImageUri(requireContext())
                    );
                }

                // Update frequency label
                updateFrequencyLabel(freqDosesInput, freqDaysInput, freqLabel);

                // Frequency label auto-update + dynamic time fields
                TextWatcher freqWatcher = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(Editable s) {
                        updateFrequencyLabel(freqDosesInput, freqDaysInput, freqLabel);
                        updateTimeFieldsVisibility(freqDosesInput, timeInput2, timeInput3);
                    }
                };
                freqDosesInput.addTextChangedListener(freqWatcher);
                freqDaysInput.addTextChangedListener(freqWatcher);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                // Time pickers for all 3 time fields
                setupTimePickerClickListener(timeInput1);
                setupTimePickerClickListener(timeInput2);
                setupTimePickerClickListener(timeInput3);

                // Date pickers
                startDateInput.setOnClickListener(v -> showDatePicker(startDateInput, sdf));
                endDateInput.setOnClickListener(v -> showDatePicker(endDateInput, sdf));

                pickImageBtn.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
                closeButton.setOnClickListener(v -> dialog.dismiss());

                saveButton.setOnClickListener(v -> {
                    List<EditText> visibleTimeInputs = getVisibleTimeInputs(timeInput1, timeInput2, timeInput3);
                    if (!validateInputs(nameInput, dosageInput, visibleTimeInputs, startDateInput, endDateInput,
                            freqDosesInput, freqDaysInput)) return;

                    String name = nameInput.getText().toString().trim();
                    String dosage = dosageInput.getText().toString().trim();
                    String freqDosesStr = freqDosesInput.getText().toString().trim();
                    String freqDaysStr = freqDaysInput.getText().toString().trim();
                    String startDate = startDateInput.getText().toString().trim();
                    String endDate = endDateInput.getText().toString().trim();
                    String mealInstructions = mealSpinner.getSelectedItem().toString();

                    int freqDoses = freqDosesStr.isEmpty() ? 1 : Integer.parseInt(freqDosesStr);
                    int freqDays = freqDaysStr.isEmpty() ? 1 : Integer.parseInt(freqDaysStr);
                    String frequency = freqDoses + "/" + freqDays;

                    List<String> times = collectTimes(visibleTimeInputs);

                    // Update medication object
                    med.setName(name);
                    med.setDosage(dosage);
                    med.setFrequency(frequency);
                    med.setStartDate(startDate);
                    med.setEndDate(endDate.isEmpty() ? null : endDate);

                    String imageUrl = (selectedImageUri != null)
                            ? selectedImageUri.toString()
                            : (med.getImageUrl() != null && !med.getImageUrl().isEmpty()
                                ? med.getImageUrl()
                                : MedicationImageUtils.getDefaultMedicationImageUri(requireContext()));
                    med.setImageUrl(imageUrl);

                    // Create new schedule objects for each time
                    List<Schedule> updatedSchedules = new ArrayList<>();
                    for (String time : times) {
                        Schedule s = new Schedule(med.getId(), time, "1,2,3,4,5,6,7");
                        s.setInstructions(mealInstructions);
                        updatedSchedules.add(s);
                    }

                    viewModel.updateMedicationWithSchedule(med, updatedSchedules);
                    dialog.dismiss();
                });

                dialog.show();
            });
        });
    }

    // ─── Delete Confirmation ────────────────────────────────────────────
    private void showDeleteConfirmation(Medication medication) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete)
                .setPositiveButton("Delete", (dialogInterface, i) -> viewModel.deleteMedication(medication))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private void setupTimePickerClickListener(EditText timeInput) {
        timeInput.setOnClickListener(v -> {
            int hour = 9, minute = 0;
            String currentTime = timeInput.getText().toString().trim();
            if (currentTime.matches("\\d{2}:\\d{2}")) {
                String[] parts = currentTime.split(":");
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            }
            new TimePickerDialog(requireContext(),
                    (view, h, m) -> {
                        String time = String.format(Locale.getDefault(), "%02d:%02d", h, m);
                        timeInput.setText(time);
                    }, hour, minute, true
            ).show();
        });
    }

    private void updateTimeFieldsVisibility(EditText freqDosesInput, EditText timeInput2, EditText timeInput3) {
        String dosesStr = freqDosesInput.getText().toString().trim();
        int doses = 1;
        try {
            if (!dosesStr.isEmpty()) doses = Integer.parseInt(dosesStr);
        } catch (NumberFormatException e) {
            doses = 1;
        }

        timeInput2.setVisibility(doses >= 2 ? View.VISIBLE : View.GONE);
        timeInput3.setVisibility(doses >= 3 ? View.VISIBLE : View.GONE);

        // Clear hidden fields
        if (doses < 2) timeInput2.setText("");
        if (doses < 3) timeInput3.setText("");
    }

    private List<EditText> getVisibleTimeInputs(EditText timeInput1, EditText timeInput2, EditText timeInput3) {
        List<EditText> visible = new ArrayList<>();
        visible.add(timeInput1); // Always visible
        if (timeInput2.getVisibility() == View.VISIBLE) visible.add(timeInput2);
        if (timeInput3.getVisibility() == View.VISIBLE) visible.add(timeInput3);
        return visible;
    }

    private List<String> collectTimes(List<EditText> visibleTimeInputs) {
        List<String> times = new ArrayList<>();
        for (EditText input : visibleTimeInputs) {
            String time = input.getText().toString().trim();
            if (time.isEmpty()) time = "09:00";
            times.add(time);
        }
        return times;
    }

    private void showDatePicker(EditText target, SimpleDateFormat sdf) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog picker = new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, dayOfMonth);
            target.setText(sdf.format(picked.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        // Restrict to today and future dates only
        picker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        picker.show();
    }

    private boolean validateInputs(EditText nameInput, EditText dosageInput,
                                    List<EditText> timeInputs, EditText startDateInput,
                                    EditText endDateInput, EditText freqDosesInput,
                                    EditText freqDaysInput) {
        String name = nameInput.getText().toString().trim();
        String dosage = dosageInput.getText().toString().trim();
        String startDate = startDateInput.getText().toString().trim();
        String endDate = endDateInput.getText().toString().trim();
        String freqDosesStr = freqDosesInput.getText().toString().trim();
        String freqDaysStr = freqDaysInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Medicine name is required");
            nameInput.requestFocus();
            return false;
        }
        if (name.length() < 2) {
            nameInput.setError("Name must be at least 2 characters");
            nameInput.requestFocus();
            return false;
        }
        if (dosage.isEmpty()) {
            dosageInput.setError("Dosage is required");
            dosageInput.requestFocus();
            return false;
        }

        // Validate all visible time inputs
        for (int i = 0; i < timeInputs.size(); i++) {
            String time = timeInputs.get(i).getText().toString().trim();
            if (!time.isEmpty() && !time.matches("\\d{2}:\\d{2}")) {
                timeInputs.get(i).setError("Invalid time format");
                timeInputs.get(i).requestFocus();
                return false;
            }
        }

        if (startDate.isEmpty()) {
            startDateInput.setError("Start date is required");
            startDateInput.requestFocus();
            return false;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
        if (startDate.compareTo(today) < 0) {
            startDateInput.setError("Start date cannot be in the past");
            startDateInput.requestFocus();
            return false;
        }
        if (!endDate.isEmpty() && endDate.compareTo(today) < 0) {
            endDateInput.setError("End date cannot be in the past");
            endDateInput.requestFocus();
            return false;
        }
        if (!endDate.isEmpty() && endDate.compareTo(startDate) < 0) {
            endDateInput.setError("End date must be after start date");
            endDateInput.requestFocus();
            return false;
        }

        try {
            int doses = freqDosesStr.isEmpty() ? 1 : Integer.parseInt(freqDosesStr);
            int days = freqDaysStr.isEmpty() ? 1 : Integer.parseInt(freqDaysStr);
            if (doses < 1) {
                freqDosesInput.setError("Must be at least 1");
                return false;
            }
            if (days < 1) {
                freqDaysInput.setError("Must be at least 1");
                return false;
            }
        } catch (NumberFormatException e) {
            freqDosesInput.setError("Enter a number");
            return false;
        }

        return true;
    }

    private void updateFrequencyLabel(EditText dosesInput, EditText daysInput, TextView label) {
        String dosesStr = dosesInput.getText().toString().trim();
        String daysStr = daysInput.getText().toString().trim();

        int doses = 1, days = 1;
        try {
            if (!dosesStr.isEmpty()) doses = Integer.parseInt(dosesStr);
            if (!daysStr.isEmpty()) days = Integer.parseInt(daysStr);
        } catch (NumberFormatException e) {
            label.setText("= Invalid");
            return;
        }

        if (doses < 1 || days < 1) {
            label.setText("= Invalid");
            return;
        }

        String text;
        if (doses == 1 && days == 1) text = "= Daily";
        else if (doses == 2 && days == 1) text = "= Twice Daily";
        else if (doses == 3 && days == 1) text = "= 3 Times Daily";
        else if (doses == 1 && days == 2) text = "= Every 2 Days";
        else if (doses == 1 && days == 3) text = "= Every 3 Days";
        else if (doses == 1 && days == 7) text = "= Weekly";
        else text = "= " + doses + " time(s) per " + days + " day(s)";

        label.setText(text);
    }

    private void applyMealInstructionPrefill(Spinner spinner, String mealInstruction) {
        if (spinner == null || mealInstruction == null || mealInstruction.trim().isEmpty()) return;

        String normalized = mealInstruction.trim();
        String[] options = getResources().getStringArray(R.array.meal_instructions_array);
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(normalized)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private String copyImageToInternalStorage(Uri sourceUri) {
        try {
            File dir = new File(requireContext().getFilesDir(), "medication_images");
            if (!dir.exists()) dir.mkdirs();

            String fileName = "med_" + UUID.randomUUID().toString() + ".jpg";
            File destFile = new File(dir, fileName);

            InputStream in = requireContext().getContentResolver().openInputStream(sourceUri);
            if (in == null) return null;

            OutputStream out = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.close();
            in.close();

            return destFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        dialogImagePreview = null;
    }
}
