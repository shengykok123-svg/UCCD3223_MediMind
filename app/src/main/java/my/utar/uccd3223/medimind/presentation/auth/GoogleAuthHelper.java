package my.utar.uccd3223.medimind.presentation.auth;

import android.app.Activity;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import my.utar.uccd3223.medimind.R;

public class GoogleAuthHelper {

    public interface Callback {
        void onLoading(boolean loading);
        void onFirebaseSuccess(AuthResult authResult, GoogleSignInAccount account);
        void onError(String message);
    }

    private final Activity activity;
    private final FirebaseAuth firebaseAuth;
    private final GoogleSignInClient googleSignInClient;
    private final Callback callback;

    public GoogleAuthHelper(Activity activity, FirebaseAuth firebaseAuth, Callback callback) {
        this.activity = activity;
        this.firebaseAuth = firebaseAuth;
        this.callback = callback;

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(activity.getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(activity, gso);
    }

    public void start(ActivityResultLauncher<Intent> launcher) {
        callback.onLoading(true);
        googleSignInClient.signOut().addOnCompleteListener(activity, task ->
                launcher.launch(googleSignInClient.getSignInIntent()));
    }

    public void handleResult(Intent data) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                callback.onLoading(false);
                callback.onError("Google sign-in failed");
                return;
            }

            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            firebaseAuth.signInWithCredential(credential)
                    .addOnSuccessListener(activity, authResult ->
                            callback.onFirebaseSuccess(authResult, account))
                    .addOnFailureListener(activity, e -> {
                        callback.onLoading(false);
                        callback.onError(e.getMessage() != null ? e.getMessage() : "Google sign-in failed");
                    });
        } catch (ApiException e) {
            callback.onLoading(false);
            callback.onError(e.getMessage() != null ? e.getMessage() : "Google sign-in canceled");
        }
    }
}
