package my.utar.uccd3223.medimind.presentation.auth;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import my.utar.uccd3223.medimind.MainActivity;
import my.utar.uccd3223.medimind.R;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class RegisterActivity extends AppCompatActivity {

    private EditText inputName, inputEmail, inputPassword, inputConfirmPassword;
    private EditText inputDob, inputWeight, inputAllergies;
    private Spinner spinnerGender, spinnerBloodType;
    private CheckBox checkboxTerms;
    private Button btnCreate, btnGoogleRegister;
    private ProgressBar progressBar;
    private boolean passwordVisible = false;
    private boolean confirmPasswordVisible = false;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private GoogleAuthHelper googleAuthHelper;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Bind views
        inputName = findViewById(R.id.input_name);
        inputEmail = findViewById(R.id.input_email);
        inputPassword = findViewById(R.id.input_password);
        inputConfirmPassword = findViewById(R.id.input_confirm_password);
        inputDob = findViewById(R.id.input_dob);
        inputWeight = findViewById(R.id.input_weight);
        inputAllergies = findViewById(R.id.input_allergies);
        spinnerGender = findViewById(R.id.spinner_gender);
        spinnerBloodType = findViewById(R.id.spinner_blood_type);
        checkboxTerms = findViewById(R.id.checkbox_terms);
        btnCreate = findViewById(R.id.btn_create);
        btnGoogleRegister = findViewById(R.id.btn_google_register);
        progressBar = findViewById(R.id.progress_bar);
        googleAuthHelper = new GoogleAuthHelper(this, firebaseAuth, new GoogleAuthHelper.Callback() {
            @Override
            public void onLoading(boolean loading) {
                setLoading(loading);
            }

            @Override
            public void onFirebaseSuccess(AuthResult authResult, GoogleSignInAccount account) {
                if (firebaseAuth.getCurrentUser() == null) {
                    setLoading(false);
                    Toast.makeText(RegisterActivity.this, "Google sign-in failed", Toast.LENGTH_LONG).show();
                    return;
                }
                String uid = firebaseAuth.getCurrentUser().getUid();
                firestore.collection("users").document(uid)
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            if (snapshot.exists()) {
                                navigateToMain();
                            } else {
                                String name = account.getDisplayName() != null ? account.getDisplayName() : "";
                                String email = account.getEmail() != null ? account.getEmail() : "";
                                generateShareableIdAndCreateProfile(uid, name, email, "", "", "", "", "");
                            }
                        })
                        .addOnFailureListener(e -> {
                            setLoading(false);
                            Toast.makeText(RegisterActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        googleAuthHelper.handleResult(result.getData());
                    } else {
                        setLoading(false);
                    }
                }
        );

        setupSpinners();
        setupDatePicker();
        setupPasswordToggles();

        btnCreate.setOnClickListener(v -> handleRegister());
        btnGoogleRegister.setOnClickListener(v -> googleAuthHelper.start(googleSignInLauncher));

        // Sign In link
        TextView textSignInLink = findViewById(R.id.text_sign_in_link);
        String full = getString(R.string.already_have_account_inline) + getString(R.string.sign_in);
        SpannableString spannable = new SpannableString(full);
        int start = getString(R.string.already_have_account_inline).length();
        spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.primary)),
                start, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                start, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textSignInLink.setText(spannable);
        textSignInLink.setOnClickListener(v -> finish());
    }

    private void setupSpinners() {
        // Gender spinner with prompt
        String[] genderItems = new String[]{"Select Gender", "Male", "Female", "Other"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, genderItems);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);

        // Blood type spinner with prompt
        String[] bloodTypeItems = new String[]{"Select Blood Type (Optional)", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
        ArrayAdapter<String> bloodTypeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, bloodTypeItems);
        bloodTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBloodType.setAdapter(bloodTypeAdapter);
    }

    private void setupPasswordToggles() {
        ImageButton btnTogglePassword = findViewById(R.id.btn_toggle_password);
        ImageButton btnToggleConfirmPassword = findViewById(R.id.btn_toggle_confirm_password);

        btnTogglePassword.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            inputPassword.setInputType(passwordVisible
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            inputPassword.setSelection(inputPassword.length());
            btnTogglePassword.setImageResource(passwordVisible
                    ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
        });

        btnToggleConfirmPassword.setOnClickListener(v -> {
            confirmPasswordVisible = !confirmPasswordVisible;
            inputConfirmPassword.setInputType(confirmPasswordVisible
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            inputConfirmPassword.setSelection(inputConfirmPassword.length());
            btnToggleConfirmPassword.setImageResource(confirmPasswordVisible
                    ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
        });
    }

    private void setupDatePicker() {
        inputDob.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            DatePickerDialog dialog = new DatePickerDialog(this,
                    (view, year, month, dayOfMonth) -> {
                        String dateStr = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year);
                        inputDob.setText(dateStr);
                    },
                    cal.get(Calendar.YEAR) - 20,
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH));
            dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            dialog.show();
        });
    }

    private void handleRegister() {
        String name = inputName.getText().toString().trim();
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();
        String confirmPassword = inputConfirmPassword.getText().toString().trim();
        String dob = inputDob.getText().toString().trim();
        String weight = inputWeight.getText().toString().trim();
        String allergies = inputAllergies.getText().toString().trim();

        // Gender: position 0 is the prompt "Select Gender"
        int genderPos = spinnerGender.getSelectedItemPosition();
        String gender = genderPos > 0 ? spinnerGender.getSelectedItem().toString() : "";

        // Blood type: position 0 is the prompt
        int bloodTypePos = spinnerBloodType.getSelectedItemPosition();
        String bloodType = bloodTypePos > 0 ? spinnerBloodType.getSelectedItem().toString() : "";

        // Validation
        if (name.isEmpty()) {
            inputName.setError("Name is required");
            return;
        }
        if (email.isEmpty()) {
            inputEmail.setError("Email is required");
            return;
        }
        if (password.isEmpty()) {
            inputPassword.setError("Password is required");
            return;
        }
        if (password.length() < 6) {
            inputPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            inputConfirmPassword.setError("Passwords do not match");
            return;
        }
        if (gender.isEmpty()) {
            Toast.makeText(this, R.string.gender_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (dob.isEmpty()) {
            Toast.makeText(this, R.string.dob_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!checkboxTerms.isChecked()) {
            Toast.makeText(this, "Please agree to Terms & Privacy", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress
        setLoading(true);

        // Create Firebase Auth user
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                        String uid = firebaseAuth.getCurrentUser().getUid();
                        generateShareableIdAndCreateProfile(uid, name, email, gender, dob, weight, bloodType, allergies);
                    } else {
                        setLoading(false);
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(this, getString(R.string.registration_failed, error), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void generateShareableIdAndCreateProfile(String uid, String name, String email,
                                                      String gender, String dob, String weight,
                                                      String bloodType, String allergies) {
        // Atomically increment the shareable ID counter
        firestore.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentReference counterRef =
                    firestore.collection("counters").document("shareableId");
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
            createUserProfile(uid, name, email, gender, dob, weight, bloodType, allergies, shareableId);
        }).addOnFailureListener(e -> {
            setLoading(false);
            Toast.makeText(this, getString(R.string.registration_failed, e.getMessage()), Toast.LENGTH_LONG).show();
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

    private void createUserProfile(String uid, String name, String email, String gender,
                                    String dob, String weight, String bloodType,
                                    String allergies, String shareableId) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("email", email);
        profile.put("gender", gender);
        profile.put("dob", dob);
        profile.put("weight", weight);
        profile.put("bloodType", bloodType);
        profile.put("allergies", allergies);
        profile.put("shareableId", shareableId);
        profile.put("createdAt", com.google.firebase.Timestamp.now());

        firestore.collection("users").document(uid)
                .set(profile)
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                    navigateToMain();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, getString(R.string.registration_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnCreate.setEnabled(!loading);
        if (btnGoogleRegister != null) {
            btnGoogleRegister.setEnabled(!loading);
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
