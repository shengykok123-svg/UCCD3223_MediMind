package my.utar.uccd3223.medimind.presentation.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Window;
import android.view.View;
import android.widget.Button;
import android.app.Dialog;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
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

import javax.inject.Inject;

import my.utar.uccd3223.medimind.MainActivity;
import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.data.repository.MedicationRepositoryImpl;
import my.utar.uccd3223.medimind.domain.repository.MedicationRepository;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LoginActivity extends AppCompatActivity {

    @Inject
    MedicationRepository repository;

    private EditText inputEmail, inputPassword;
    private Button btnLogin, btnGoogleLogin;
    private ImageButton btnTogglePassword;
    private TextView textForgotPassword, textCreateAccount;
    private ProgressBar progressBar;
    private boolean passwordVisible = false;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private GoogleAuthHelper googleAuthHelper;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize views BEFORE auth check so setLoading() won't NPE on auto-login
        setContentView(R.layout.activity_login);

        inputEmail = findViewById(R.id.input_email);
        inputPassword = findViewById(R.id.input_password);
        btnLogin = findViewById(R.id.btn_login);
        btnGoogleLogin = findViewById(R.id.btn_google_login);
        btnTogglePassword = findViewById(R.id.btn_toggle_password);
        textForgotPassword = findViewById(R.id.text_forgot_password);
        textCreateAccount = findViewById(R.id.text_create_account);
        progressBar = findViewById(R.id.progress_bar);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        googleAuthHelper = new GoogleAuthHelper(this, firebaseAuth, new GoogleAuthHelper.Callback() {
            @Override
            public void onLoading(boolean loading) {
                setLoading(loading);
            }

            @Override
            public void onFirebaseSuccess(AuthResult authResult, GoogleSignInAccount account) {
                ensureGoogleProfileThenSync(account);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
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

        // Password visibility toggle
        btnTogglePassword.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            inputPassword.setInputType(passwordVisible
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            inputPassword.setSelection(inputPassword.length());
            btnTogglePassword.setImageResource(passwordVisible
                    ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
        });

        // Spannable "Don't have an account? Create Account"
        setupCreateAccountLink();

        // Auto-login: if user is already signed in, sync Firestore → Room then navigate
        if (firebaseAuth.getCurrentUser() != null) {
            syncAndNavigate();
            return;
        }

        btnLogin.setOnClickListener(v -> handleLogin());
        btnGoogleLogin.setOnClickListener(v -> googleAuthHelper.start(googleSignInLauncher));

        textCreateAccount.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        textForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void setupCreateAccountLink() {
        String full = getString(R.string.dont_have_account_inline) + getString(R.string.create_account);
        SpannableString spannable = new SpannableString(full);
        int start = getString(R.string.dont_have_account_inline).length();
        spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.primary)),
                start, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                start, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textCreateAccount.setText(spannable);
    }

    private void handleLogin() {
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();

        if (email.isEmpty()) {
            inputEmail.setError("Email is required");
            return;
        }
        if (password.isEmpty()) {
            inputPassword.setError("Password is required");
            return;
        }

        setLoading(true);

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sync Firestore data to Room cache before navigating
                        syncAndNavigate();
                    } else {
                        setLoading(false);
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(this, getString(R.string.login_failed, error), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showForgotPasswordDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_forgot_password);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        EditText emailInput = dialog.findViewById(R.id.input_email);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSend = dialog.findViewById(R.id.btn_send);

        String currentEmail = inputEmail.getText().toString().trim();
        if (!currentEmail.isEmpty()) {
            emailInput.setText(currentEmail);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSend.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            if (email.isEmpty()) {
                emailInput.setError(getString(R.string.email));
                return;
            }
            firebaseAuth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(aVoid -> {
                        dialog.dismiss();
                        Toast.makeText(this, R.string.reset_email_sent, Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
        });

        dialog.show();
    }

    private void syncAndNavigate() {
        setLoading(true);
        if (repository instanceof MedicationRepositoryImpl) {
            ((MedicationRepositoryImpl) repository).syncFromFirestore(
                    () -> runOnUiThread(() -> {
                        setLoading(false);
                        navigateToMain();
                    }),
                    () -> runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(this, "Failed to sync data. Please check your connection and try again.", Toast.LENGTH_LONG).show();
                    })
            );
        } else {
            setLoading(false);
            navigateToMain();
        }
    }

    private void ensureGoogleProfileThenSync(GoogleSignInAccount account) {
        if (firebaseAuth.getCurrentUser() == null) {
            setLoading(false);
            Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_LONG).show();
            return;
        }

        String uid = firebaseAuth.getCurrentUser().getUid();
        firestore.collection("users").document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        syncAndNavigate();
                    } else {
                        String name = account.getDisplayName() != null ? account.getDisplayName() : "";
                        String email = account.getEmail() != null ? account.getEmail() : "";
                        generateShareableIdAndCreateProfile(uid, name, email);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void generateShareableIdAndCreateProfile(String uid, String name, String email) {
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

            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("count", currentCount);
            transaction.set(counterRef, data);
            return currentCount;
        }).addOnSuccessListener(counter -> {
            String shareableId = counterToShareableId(counter);
            createUserProfile(uid, name, email, shareableId);
        }).addOnFailureListener(e -> {
            setLoading(false);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
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

    private void createUserProfile(String uid, String name, String email, String shareableId) {
        java.util.Map<String, Object> profile = new java.util.HashMap<>();
        profile.put("name", name);
        profile.put("email", email);
        profile.put("gender", "");
        profile.put("dob", "");
        profile.put("weight", "");
        profile.put("bloodType", "");
        profile.put("allergies", "");
        profile.put("shareableId", shareableId);
        profile.put("createdAt", com.google.firebase.Timestamp.now());

        firestore.collection("users").document(uid)
                .set(profile)
                .addOnSuccessListener(aVoid -> syncAndNavigate())
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnLogin != null) {
            btnLogin.setEnabled(!loading);
        }
        if (btnGoogleLogin != null) {
            btnGoogleLogin.setEnabled(!loading);
        }
    }
}
