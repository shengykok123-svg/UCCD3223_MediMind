package my.utar.uccd3223.medimind.presentation.scan;

import android.graphics.Bitmap;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.RequestOptions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import my.utar.uccd3223.medimind.BuildConfig;

@HiltViewModel
public class ScanViewModel extends ViewModel {

    private final Executor executor;
    private final GenerativeModelFutures model;

    private final MutableLiveData<ScanResult> scanResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> flashlightOn = new MutableLiveData<>(false);
    private String pendingAiQuery;

    @Inject
    public ScanViewModel(Executor executor) {
        this.executor = executor;

        Content.Builder sysBuilder = new Content.Builder();
        sysBuilder.addText("You are a medication identification assistant. "
                + "When given an image of a medication (pill, tablet, capsule, bottle, packaging, etc.), "
                + "identify it and respond ONLY with a JSON object in this exact format:\n"
                + "{\"name\": \"medication name\", \"dosage\": \"dosage info\", \"type\": \"medication type\", \"usage\": \"brief usage instructions\"}\n"
                + "If you cannot identify the medication, still respond with the JSON format using your best guess "
                + "or \"Unknown\" for fields you cannot determine. "
                + "Do NOT include any text outside the JSON object. Do NOT use markdown code fences.");
        Content systemInstruction = sysBuilder.build();

        GenerativeModel gm = new GenerativeModel(
                "gemini-2.5-flash",
                BuildConfig.GEMINI_API_KEY,
                null,
                null,
                new RequestOptions(),
                null,
                null,
                systemInstruction
        );

        model = GenerativeModelFutures.from(gm);
    }

    public LiveData<ScanResult> getScanResult() {
        return scanResult;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getFlashlightOn() {
        return flashlightOn;
    }

    public void toggleFlashlight() {
        Boolean current = flashlightOn.getValue();
        flashlightOn.setValue(current != null && current ? false : true);
    }

    public void analyzeMedication(Bitmap bitmap) {
        isLoading.setValue(true);
        error.setValue(null);

        Content.Builder contentBuilder = new Content.Builder();
        contentBuilder.setRole("user");
        contentBuilder.addImage(bitmap);
        contentBuilder.addText("Identify this medication and provide its name, dosage, type, and usage instructions in JSON format.");
        Content content = contentBuilder.build();

        ListenableFuture<GenerateContentResponse> future = model.generateContent(content);

        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String responseText = result.getText();
                if (responseText != null && !responseText.isEmpty()) {
                    ScanResult parsed = parseGeminiResponse(responseText.trim(), bitmap);
                    scanResult.postValue(parsed);
                } else {
                    error.postValue("Could not identify the medication. Please try again.");
                }
                isLoading.postValue(false);
            }

            @Override
            public void onFailure(Throwable t) {
                String errorMsg = t.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "An unexpected error occurred";
                }
                error.postValue("Failed to analyze: " + errorMsg);
                isLoading.postValue(false);
            }
        }, executor);
    }

    private ScanResult parseGeminiResponse(String response, Bitmap bitmap) {
        // Strip markdown code fences if present
        String cleaned = response;
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        try {
            JSONObject json = new JSONObject(cleaned);
            String name = json.optString("name", "Unknown Medication");
            String dosage = json.optString("dosage", "Unknown");
            String type = json.optString("type", "Unknown");
            String usage = json.optString("usage", "Consult your healthcare provider");
            return new ScanResult(name, dosage, type, usage, bitmap);
        } catch (Exception e) {
            return new ScanResult("Unknown Medication", "Unknown", "Unknown",
                    "Could not parse details. Please try again.", bitmap);
        }
    }

    public void setPendingAiQuery(String query) {
        this.pendingAiQuery = query;
    }

    public String consumePendingAiQuery() {
        String query = pendingAiQuery;
        pendingAiQuery = null;
        return query;
    }

    public void clearResult() {
        scanResult.setValue(null);
        error.setValue(null);
        isLoading.setValue(false);
    }
}
