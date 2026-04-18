package my.utar.uccd3223.medimind.presentation.settings;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.data.local.database.MediMindDatabase;
import my.utar.uccd3223.medimind.presentation.auth.LoginActivity;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SettingsFragment extends Fragment {

    @Inject
    MediMindDatabase database;

    private TextView textName, textId, textEmail, textGender, textDob, textWeight, textBloodType, textAllergies;
    private ImageButton btnEditName, btnEditEmail, btnEditGender, btnEditDob, btnEditWeight, btnEditBloodType, btnEditAllergies, btnCopyId;
    private ImageView imgAvatar;
    private Button btnLogout;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private String currentUid;
    private String currentProfileImagePath;

    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<Void> takePictureLauncher;
    private ActivityResultLauncher<String> galleryLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        takePictureLauncher.launch(null);
                    } else {
                        Toast.makeText(requireContext(),
                                R.string.camera_permission_needed, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) {
                        saveProfileImage(bitmap);
                    }
                }
        );

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Bitmap bitmap = uriToBitmap(uri);
                        if (bitmap != null) {
                            saveProfileImage(bitmap);
                        } else {
                            Toast.makeText(requireContext(),
                                    "Failed to load image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            navigateToLogin();
            return;
        }
        currentUid = user.getUid();

        // Back button
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        // Bind views
        textName = view.findViewById(R.id.text_name);
        textId = view.findViewById(R.id.text_id);
        textEmail = view.findViewById(R.id.text_email);
        textGender = view.findViewById(R.id.text_gender);
        textDob = view.findViewById(R.id.text_dob);
        textWeight = view.findViewById(R.id.text_weight);
        textBloodType = view.findViewById(R.id.text_blood_type);
        textAllergies = view.findViewById(R.id.text_allergies);

        btnEditName = view.findViewById(R.id.btn_edit_name);
        btnCopyId = view.findViewById(R.id.btn_copy_id);
        btnEditEmail = view.findViewById(R.id.btn_edit_email);
        btnEditGender = view.findViewById(R.id.btn_edit_gender);
        btnEditDob = view.findViewById(R.id.btn_edit_dob);
        btnEditWeight = view.findViewById(R.id.btn_edit_weight);
        btnEditBloodType = view.findViewById(R.id.btn_edit_blood_type);
        btnEditAllergies = view.findViewById(R.id.btn_edit_allergies);
        btnLogout = view.findViewById(R.id.btn_logout);
        imgAvatar = view.findViewById(R.id.img_avatar);

        // Profile photo click listeners
        imgAvatar.setOnClickListener(v -> showEnlargedPhotoDialog());
        view.findViewById(R.id.btn_change_photo).setOnClickListener(v -> showPhotoPickerDialog());

        // Setup click listeners
        btnEditName.setOnClickListener(v -> showEditTextDialog(getString(R.string.edit_name), "name", textName.getText().toString()));
        btnCopyId.setOnClickListener(v -> copyIdToClipboard());
        btnEditEmail.setOnClickListener(v -> showEditEmailDialog());
        btnEditGender.setOnClickListener(v -> showEditGenderDialog());
        btnEditDob.setOnClickListener(v -> showEditDobDialog());
        btnEditWeight.setOnClickListener(v -> showEditWeightDialog());
        btnEditBloodType.setOnClickListener(v -> showEditBloodTypeDialog());
        btnEditAllergies.setOnClickListener(v -> showEditTextDialog(getString(R.string.edit_allergies), "allergies", textAllergies.getText().toString()));
        btnLogout.setOnClickListener(v -> showLogoutConfirmation());

        // Load profile data
        loadProfile();
    }

    private void loadProfile() {
        firestore.collection("users").document(currentUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        String shareableId = doc.getString("shareableId");
                        String email = doc.getString("email");
                        String gender = doc.getString("gender");
                        String dob = doc.getString("dob");
                        String weight = doc.getString("weight");
                        String bloodType = doc.getString("bloodType");
                        String allergies = doc.getString("allergies");

                        textName.setText(name != null && !name.isEmpty() ? name : getString(R.string.not_set));
                        textId.setText(shareableId != null ? getString(R.string.id_format, shareableId) : "");
                        textEmail.setText(email != null && !email.isEmpty() ? email : getString(R.string.not_set));
                        textGender.setText(gender != null && !gender.isEmpty() ? gender : getString(R.string.not_set));
                        textDob.setText(dob != null && !dob.isEmpty() ? dob : getString(R.string.not_set));
                        textWeight.setText(weight != null && !weight.isEmpty() ? getString(R.string.weight_kg_format, weight) : getString(R.string.not_set));
                        textBloodType.setText(bloodType != null && !bloodType.isEmpty() ? bloodType : getString(R.string.not_set));
                        textAllergies.setText(allergies != null && !allergies.isEmpty() ? allergies : getString(R.string.not_set));

                        String profileImagePath = doc.getString("profileImagePath");
                        loadProfileImage(profileImagePath);
                    } else {
                        // Document doesn't exist — auto-recover from Firebase Auth
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null) {
                            String email = user.getEmail();
                            String displayName = user.getDisplayName();

                            textName.setText(displayName != null && !displayName.isEmpty()
                                    ? displayName : getString(R.string.not_set));
                            textEmail.setText(email != null && !email.isEmpty()
                                    ? email : getString(R.string.not_set));
                            textGender.setText(getString(R.string.not_set));
                            textDob.setText(getString(R.string.not_set));
                            textWeight.setText(getString(R.string.not_set));
                            textBloodType.setText(getString(R.string.not_set));
                            textAllergies.setText(getString(R.string.not_set));

                            // Create a minimal profile document so future edits work
                            createRecoveryProfile(email, displayName);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        // Fallback: show Firebase Auth email even on Firestore failure
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null && user.getEmail() != null) {
                            textEmail.setText(user.getEmail());
                        }
                        Toast.makeText(requireContext(), "Failed to load profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private DocumentReference getUserDoc() {
        return firestore.collection("users").document(currentUid);
    }

    private void updateField(String field, String value, Runnable onSuccess) {
        Map<String, Object> update = new HashMap<>();
        update.put(field, value);
        getUserDoc().set(update, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show();
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showEditTextDialog(String title, String field, String currentValue) {
        if (!isAdded()) return;
        String value = currentValue.equals(getString(R.string.not_set)) ? "" : currentValue;
        int hintRes = field.equals("name") ? R.string.edit_dialog_hint_name : R.string.edit_dialog_hint_allergies;
        int subtitleRes = field.equals("name") ? R.string.edit_dialog_subtitle_name : R.string.edit_dialog_subtitle_allergies;
        showStyledInputDialog(title, getString(subtitleRes), getString(hintRes), value,
                InputType.TYPE_CLASS_TEXT, input -> {
                    String newValue = input.trim();
                    updateField(field, newValue, () -> {
                        if (field.equals("name")) {
                            textName.setText(newValue.isEmpty() ? getString(R.string.not_set) : newValue);
                        } else if (field.equals("allergies")) {
                            textAllergies.setText(newValue.isEmpty() ? getString(R.string.not_set) : newValue);
                        }
                    });
                });
    }

    private void showEditEmailDialog() {
        if (!isAdded()) return;
        String current = textEmail.getText().toString();
        String value = current.equals(getString(R.string.not_set)) ? "" : current;
        showStyledInputDialog(
                getString(R.string.edit_email),
                getString(R.string.edit_dialog_subtitle_email),
                getString(R.string.edit_dialog_hint_email),
                value,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                newEmail -> {
                    if (newEmail.isEmpty()) return;

                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user != null) {
                        // Update Firebase Auth email
                        user.verifyBeforeUpdateEmail(newEmail)
                                .addOnSuccessListener(aVoid -> {
                                    // Also update Firestore
                                    updateField("email", newEmail, () -> textEmail.setText(newEmail));
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), "Verification email sent to " + newEmail, Toast.LENGTH_LONG).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                    }
                });
    }

    private void showEditGenderDialog() {
        if (!isAdded()) return;
        String[] genders = getResources().getStringArray(R.array.gender_array);
        String current = textGender.getText().toString();
        showStyledOptionDialog(
                getString(R.string.edit_gender),
                getString(R.string.edit_dialog_subtitle_gender),
                genders,
                current,
                newGender -> updateField("gender", newGender, () -> textGender.setText(newGender))
        );
    }

    private void showEditDobDialog() {
        if (!isAdded()) return;
        Calendar cal = Calendar.getInstance();

        // Try to parse current DOB to pre-select
        String current = textDob.getText().toString();
        try {
            String[] parts = current.split("/");
            if (parts.length == 3) {
                cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[0]));
                cal.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
                cal.set(Calendar.YEAR, Integer.parseInt(parts[2]));
            }
        } catch (Exception ignored) {
        }

        DatePickerDialog dialog = new DatePickerDialog(requireContext(),
                (view, year, month, dayOfMonth) -> {
                    String dateStr = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year);
                    updateField("dob", dateStr, () -> textDob.setText(dateStr));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void showEditWeightDialog() {
        if (!isAdded()) return;
        String current = textWeight.getText().toString().replace(" KG", "");
        String value = current.equals(getString(R.string.not_set)) ? "" : current;
        showStyledInputDialog(
                getString(R.string.edit_weight),
                getString(R.string.edit_dialog_subtitle_weight),
                getString(R.string.edit_dialog_hint_weight),
                value,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
                newWeight -> {
                    updateField("weight", newWeight, () ->
                            textWeight.setText(newWeight.isEmpty() ? getString(R.string.not_set) : getString(R.string.weight_kg_format, newWeight)));
                });
    }

    private void showEditBloodTypeDialog() {
        if (!isAdded()) return;
        String[] bloodTypes = getResources().getStringArray(R.array.blood_type_array);
        String current = textBloodType.getText().toString();
        showStyledOptionDialog(
                getString(R.string.edit_blood_type),
                getString(R.string.edit_dialog_subtitle_blood_type),
                bloodTypes,
                current,
                newBloodType -> updateField("bloodType", newBloodType, () -> textBloodType.setText(newBloodType))
        );
    }

    private void showStyledInputDialog(String title, String subtitle, String hint, String currentValue,
                                       int inputType, OnValueSaved onSave) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_edit_info);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView textTitle = dialog.findViewById(R.id.text_title);
        TextView textSubtitle = dialog.findViewById(R.id.text_subtitle);
        EditText inputValue = dialog.findViewById(R.id.input_value);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_save);

        textTitle.setText(title);
        textSubtitle.setText(subtitle);
        inputValue.setHint(hint);
        inputValue.setText(currentValue);
        inputValue.setInputType(inputType);
        inputValue.setSelection(inputValue.getText() != null ? inputValue.getText().length() : 0);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            onSave.onSave(inputValue.getText() != null ? inputValue.getText().toString() : "");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showStyledOptionDialog(String title, String subtitle, String[] options,
                                        String currentValue, OnValueSaved onSave) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_option_picker);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView textTitle = dialog.findViewById(R.id.text_title);
        TextView textSubtitle = dialog.findViewById(R.id.text_subtitle);
        RadioGroup radioGroup = dialog.findViewById(R.id.radio_group_options);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_save);

        textTitle.setText(title);
        textSubtitle.setText(subtitle);

        for (String option : options) {
            RadioButton radioButton = new RadioButton(requireContext());
            radioButton.setText(option);
            radioButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            radioButton.setPadding(0, dpToPx(8), 0, dpToPx(8));
            radioGroup.addView(radioButton);
            if (option.equals(currentValue)) {
                radioButton.setChecked(true);
            }
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            if (checkedId != -1) {
                RadioButton selected = radioGroup.findViewById(checkedId);
                if (selected != null) {
                    onSave.onSave(selected.getText().toString());
                }
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private interface OnValueSaved {
        void onSave(String value);
    }

    private void createRecoveryProfile(String email, String displayName) {
        // Generate shareable ID via atomic counter (same logic as RegisterActivity)
        firestore.runTransaction(transaction -> {
            DocumentReference counterRef = firestore.collection("counters").document("shareableId");
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(counterRef);
            long currentCount;
            if (snapshot.exists() && snapshot.getLong("count") != null) {
                currentCount = snapshot.getLong("count") + 1;
            } else {
                currentCount = 1;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("count", currentCount);
            transaction.set(counterRef, data);
            return currentCount;
        }).addOnSuccessListener(counter -> {
            String shareableId = counterToShareableId(counter);

            Map<String, Object> profile = new HashMap<>();
            profile.put("name", displayName != null ? displayName : "");
            profile.put("email", email != null ? email : "");
            profile.put("shareableId", shareableId);
            profile.put("gender", "");
            profile.put("dob", "");
            profile.put("weight", "");
            profile.put("bloodType", "");
            profile.put("allergies", "");
            profile.put("createdAt", com.google.firebase.Timestamp.now());

            getUserDoc().set(profile)
                    .addOnSuccessListener(aVoid -> {
                        if (isAdded()) {
                            textId.setText(getString(R.string.id_format, shareableId));
                            textName.setText(displayName != null && !displayName.isEmpty()
                                    ? displayName : getString(R.string.not_set));
                        }
                    });
        }).addOnFailureListener(e -> {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Could not generate ID: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String counterToShareableId(long counter) {
        long letterPart = (counter - 1) / 999999;
        long numberPart = ((counter - 1) % 999999) + 1;
        char c1 = (char) ('A' + (letterPart / (26 * 26)));
        char c2 = (char) ('A' + ((letterPart / 26) % 26));
        char c3 = (char) ('A' + (letterPart % 26));
        return String.format("%c%c%c%06d", c1, c2, c3, numberPart);
    }

    private void copyIdToClipboard() {
        if (!isAdded()) return;
        String idText = textId.getText().toString();
        // Extract the ID part after "ID: "
        String shareableId = idText.startsWith("ID: ") ? idText.substring(4) : idText;
        if (shareableId.isEmpty()) return;

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("MediMind ID", shareableId);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), R.string.id_copied, Toast.LENGTH_SHORT).show();
    }

    private void showLogoutConfirmation() {
        if (!isAdded()) return;
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirmation);
        configureDialogWindow(dialog);

        ImageView imageIcon = dialog.findViewById(R.id.image_icon);
        TextView textTitle = dialog.findViewById(R.id.text_title);
        TextView textMessage = dialog.findViewById(R.id.text_message);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        imageIcon.setImageResource(R.drawable.ic_logout);
        imageIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.accent_dark)));
        textTitle.setText(R.string.confirm_logout_title);
        textMessage.setText(R.string.logout_subtitle);
        btnConfirm.setText(R.string.logout);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            new Thread(() -> {
                database.clearAllTables();
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        firebaseAuth.signOut();
                        navigateToLogin();
                    });
                }
            }).start();
        });

        dialog.show();
    }

    // ─── Profile Photo Methods ────────────────────────────────────────

    private void showPhotoPickerDialog() {
        if (!isAdded()) return;
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_photo_picker);
        configureDialogWindow(dialog);

        View optionCamera = dialog.findViewById(R.id.option_camera);
        View optionGallery = dialog.findViewById(R.id.option_gallery);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);

        optionCamera.setOnClickListener(v -> {
            dialog.dismiss();
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                takePictureLauncher.launch(null);
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        optionGallery.setOnClickListener(v -> {
            dialog.dismiss();
            galleryLauncher.launch("image/*");
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showEnlargedPhotoDialog() {
        if (!isAdded()) return;

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ImageView enlargedImage = new ImageView(requireContext());
        enlargedImage.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        enlargedImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        enlargedImage.setBackgroundColor(0xFF000000);
        enlargedImage.setOnClickListener(v -> dialog.dismiss());

        if (currentProfileImagePath != null) {
            File file = new File(currentProfileImagePath);
            if (file.exists()) {
                Glide.with(this)
                        .load(file)
                        .into(enlargedImage);
            } else {
                enlargedImage.setImageResource(R.drawable.ic_person);
            }
        } else {
            enlargedImage.setImageResource(R.drawable.ic_person);
        }

        dialog.setContentView(enlargedImage);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
        }
        dialog.show();
    }

    private void saveProfileImage(Bitmap bitmap) {
        try {
            File dir = new File(requireContext().getFilesDir(), "profile_images");
            if (!dir.exists()) dir.mkdirs();

            String fileName = "profile_" + currentUid + ".jpg";
            File destFile = new File(dir, fileName);

            FileOutputStream out = new FileOutputStream(destFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();

            String path = destFile.getAbsolutePath();
            currentProfileImagePath = path;

            // Update avatar display
            displayAvatarImage(path);

            // Save path to Firestore
            updateField("profileImagePath", path, () -> {});

        } catch (IOException e) {
            Toast.makeText(requireContext(),
                    "Failed to save photo", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadProfileImage(String path) {
        currentProfileImagePath = path;
        if (path != null && !path.isEmpty()) {
            File file = new File(path);
            if (file.exists()) {
                displayAvatarImage(path);
                return;
            }
        }
        // Default avatar
        imgAvatar.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));
        imgAvatar.setImageResource(R.drawable.ic_person);
        imgAvatar.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.on_primary)));
    }

    private void displayAvatarImage(String path) {
        imgAvatar.setPadding(0, 0, 0, 0);
        imgAvatar.setImageTintList(null);
        Glide.with(this)
                .load(new File(path))
                .apply(RequestOptions.circleCropTransform()
                        .skipMemoryCache(true)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE))
                .into(imgAvatar);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void configureDialogWindow(Dialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private Bitmap uriToBitmap(Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(
                        requireContext().getContentResolver(), uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setMutableRequired(true);
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } else {
                return MediaStore.Images.Media.getBitmap(
                        requireContext().getContentResolver(), uri);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private void navigateToLogin() {
        if (!isAdded()) return;
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
