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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import my.utar.uccd3223.medimind.BuildConfig;

/**
 * Sends captured medication images to Gemini and sanitizes extracted form fields.
 */
@HiltViewModel
public class ScanViewModel extends ViewModel {

    private static final List<String> MODEL_NAMES = Arrays.asList(
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.5-flash"
    );

    private final Executor executor;
    private final List<GenerativeModelFutures> models = new ArrayList<>();

    private final MutableLiveData<ScanResult> scanResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> flashlightOn = new MutableLiveData<>(false);
    private String pendingAiQuery;
    private String pendingAiDisplayText;

    /**
     * Builds the scan extraction prompt and prepares Gemini clients for each fallback model.
     * The prompt forces JSON-only output so parsing stays predictable.
     */
    @Inject
    public ScanViewModel(Executor executor) {
        this.executor = executor;

        Content.Builder sysBuilder = new Content.Builder();
        sysBuilder.addText("You are a medication identification assistant. "
                + "Extract only information that is clearly visible on the medication image or packaging. "
                + "Do not guess, infer brand details from weak evidence, or invent missing values. "
                + "Return ONLY a single JSON object with exactly these keys:\n"
                + "{\"medicationName\":string|null,"
                + "\"dosage\":string|null,"
                + "\"frequencyDoses\":number|null,"
                + "\"frequencyDays\":number|null,"
                + "\"mealInstructions\":string|null,"
                + "\"type\":string|null,"
                + "\"usage\":string|null}\n"
                + "Field rules:\n"
                + "- medicationName: short medicine/product name only.\n"
                + "- dosage: medicine strength or concentration only, e.g. \"500 mg\", \"10 mg\", \"250 mg/5 mL\", \"1%\".\n"
                + "- dosage must NOT be package quantity/count. Values like \"30 tablets\", \"60 tablets\", \"100 capsules\", \"120 mL bottle\", or \"60 pcs\" are package size, not dosage; use null unless a real strength/concentration is visible.\n"
                + "- frequencyDoses and frequencyDays: only when explicitly shown or clearly stated on the packaging.\n"
                + "- mealInstructions: only one of \"Before Meal\", \"During Meal\", \"After Meal\", \"Before Sleep\", \"As Needed\" when clearly supported.\n"
                + "- type: short category only, e.g. \"Tablet\", \"Capsule\", \"Syrup\".\n"
                + "- usage: one short line only if clearly visible; otherwise null.\n"
                + "- If a field is absent or unclear, use null.\n"
                + "- Do not output markdown, prose, explanations, placeholders, or values like \"Unknown\".");
        Content systemInstruction = sysBuilder.build();

        for (String modelName : MODEL_NAMES) {
            models.add(GenerativeModelFutures.from(createGenerativeModel(modelName, systemInstruction)));
        }
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

    /**
     * Toggles flashlight state; the Fragment observes this value and applies it to CameraX.
     */
    public void toggleFlashlight() {
        Boolean current = flashlightOn.getValue();
        flashlightOn.setValue(current != null && current ? false : true);
    }

    /**
     * Sends a captured or gallery medication image to Gemini for structured extraction.
     * The original bitmap is kept in the result so it can be previewed and saved later.
     */
    public void analyzeMedication(Bitmap bitmap) {
        isLoading.setValue(true);
        error.setValue(null);

        Content.Builder contentBuilder = new Content.Builder();
        contentBuilder.setRole("user");
        contentBuilder.addImage(bitmap);
        contentBuilder.addText("Extract medication fields for the existing Add Medication form. "
                + "Use null for anything absent or unclear. Keep every returned value concise.");
        Content content = contentBuilder.build();

        analyzeMedicationWithFallback(content, bitmap, 0);
    }

    /**
     * Attempts image extraction with the current model and recursively retries the next model
     * if the API call fails.
     */
    private void analyzeMedicationWithFallback(Content content, Bitmap bitmap, int modelIndex) {
        ListenableFuture<GenerateContentResponse> future = models.get(modelIndex).generateContent(content);
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
                int nextModelIndex = modelIndex + 1;
                if (nextModelIndex < models.size()) {
                    analyzeMedicationWithFallback(content, bitmap, nextModelIndex);
                    return;
                }

                String errorMsg = t.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "An unexpected error occurred";
                }
                error.postValue("Failed to analyze: " + errorMsg);
                isLoading.postValue(false);
            }
        }, executor);
    }

    /**
     * Creates one Gemini model instance with the scan-specific system instruction.
     */
    private GenerativeModel createGenerativeModel(String modelName, Content systemInstruction) {
        return new GenerativeModel(
                modelName,
                BuildConfig.GEMINI_API_KEY,
                null,
                null,
                new RequestOptions(),
                null,
                null,
                systemInstruction
        );
    }

    /**
     * Parses Gemini's JSON response and sanitizes every field before exposing it to the UI.
     * Invalid or unclear fields become null rather than unreliable prefill values.
     */
    private ScanResult parseGeminiResponse(String response, Bitmap bitmap) {
        String cleaned = extractJsonObject(response);

        try {
            JSONObject json = new JSONObject(cleaned);
            String name = sanitizeShortText(json.opt("medicationName"), 80);
            String dosage = sanitizeDosage(json.opt("dosage"));
            String frequencyDoses = sanitizePositiveIntegerString(json.opt("frequencyDoses"));
            String frequencyDays = sanitizePositiveIntegerString(json.opt("frequencyDays"));
            String mealInstructions = sanitizeMealInstruction(json.opt("mealInstructions"));
            String type = sanitizeShortText(json.opt("type"), 40);
            String usage = sanitizeShortText(json.opt("usage"), 100);

            return new ScanResult(
                    name,
                    dosage,
                    frequencyDoses,
                    frequencyDays,
                    mealInstructions,
                    type,
                    usage,
                    bitmap
            );
        } catch (Exception e) {
            return new ScanResult(null, null, null, null, null, null, null, bitmap);
        }
    }

    /**
     * Extracts the first JSON object from a response and strips accidental Markdown fences.
     */
    private String extractJsonObject(String response) {
        if (response == null) return "{}";

        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return "{}";
    }

    /**
     * Normalizes text fields, rejects placeholder values such as "unknown",
     * and caps the field length for form safety.
     */
    private String sanitizeShortText(Object rawValue, int maxLength) {
        if (rawValue == null || rawValue == JSONObject.NULL) return null;

        String value = String.valueOf(rawValue).trim();
        if (value.isEmpty()) return null;

        String normalized = value.toLowerCase();
        if (normalized.equals("null")
                || normalized.equals("unknown")
                || normalized.equals("n/a")
                || normalized.equals("na")
                || normalized.equals("not visible")
                || normalized.equals("not provided")
                || normalized.equals("not sure")
                || normalized.equals("unclear")) {
            return null;
        }

        value = value.replaceAll("\\s+", " ").trim();
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength).trim();
        }
        return value.isEmpty() ? null : value;
    }

    /**
     * Keeps only logical strength/concentration values and rejects package quantities.
     * Example: "500 mg" is valid, while "60 tablets" is treated as bottle count and removed.
     */
    private String sanitizeDosage(Object rawValue) {
        String value = sanitizeShortText(rawValue, 60);
        if (value == null) return null;

        String normalized = value.toLowerCase();

        if (normalized.matches("^\\d+\\s*(tablets?|tabs?|capsules?|caps?|pills?|softgels?|lozenges?|sachets?|pcs?|pieces?|count|ct)\\b.*")) {
            return null;
        }

        if (normalized.matches("^\\d+\\s*(ml|m\\s*l|milliliters?)\\s*(bottle|pack|container)?$")) {
            return null;
        }

        boolean hasStrengthUnit = normalized.matches(".*\\d+(\\.\\d+)?\\s*(mcg|µg|ug|mg|g|kg|iu|units?|mmol|meq|%)\\b.*")
                || normalized.matches(".*\\d+(\\.\\d+)?\\s*(mg|mcg|µg|ug|g|iu|units?)\\s*/\\s*\\d+(\\.\\d+)?\\s*(ml|m\\s*l|l|tablet|tab|capsule|cap)\\b.*");

        return hasStrengthUnit ? value : null;
    }

    /**
     * Converts numeric JSON fields into positive integer strings for the form inputs.
     */
    private String sanitizePositiveIntegerString(Object rawValue) {
        if (rawValue == null || rawValue == JSONObject.NULL) return null;

        try {
            if (rawValue instanceof Number) {
                int value = ((Number) rawValue).intValue();
                return value > 0 ? String.valueOf(value) : null;
            }

            String text = String.valueOf(rawValue).trim();
            if (text.isEmpty()) return null;
            int value = Integer.parseInt(text);
            return value > 0 ? String.valueOf(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Maps free-text meal instructions to the fixed dropdown values used by the app.
     */
    private String sanitizeMealInstruction(Object rawValue) {
        String value = sanitizeShortText(rawValue, 30);
        if (value == null) return null;

        switch (value.toLowerCase()) {
            case "before meal":
                return "Before Meal";
            case "during meal":
                return "During Meal";
            case "after meal":
                return "After Meal";
            case "before sleep":
                return "Before Sleep";
            case "as needed":
                return "As Needed";
            default:
                return null;
        }
    }

    /**
     * Stores a scan-generated AI query without a custom display label.
     */
    public void setPendingAiQuery(String query) {
        this.pendingAiQuery = query;
        this.pendingAiDisplayText = null;
    }

    /**
     * Stores a detailed hidden AI query plus a shorter message shown in the chat bubble.
     */
    public void setPendingAiQuery(String query, String displayText) {
        this.pendingAiQuery = query;
        this.pendingAiDisplayText = displayText;
    }

    /**
     * Returns and clears the pending scan-to-chat prompt so it is sent once.
     */
    public String consumePendingAiQuery() {
        String query = pendingAiQuery;
        pendingAiQuery = null;
        return query;
    }

    /**
     * Returns and clears the short display text paired with the hidden scan prompt.
     */
    public String consumePendingAiDisplayText() {
        String displayText = pendingAiDisplayText;
        pendingAiDisplayText = null;
        return displayText;
    }

    /**
     * Resets scan output and loading state so the user can retake or choose another image.
     */
    public void clearResult() {
        scanResult.setValue(null);
        error.setValue(null);
        isLoading.setValue(false);
    }
}
