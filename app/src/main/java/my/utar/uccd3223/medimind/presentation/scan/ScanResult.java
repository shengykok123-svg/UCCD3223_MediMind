package my.utar.uccd3223.medimind.presentation.scan;

import android.graphics.Bitmap;

public class ScanResult {
    private final String medicationName;
    private final String dosage;
    private final String frequencyDoses;
    private final String frequencyDays;
    private final String mealInstructions;
    private final String type;
    private final String usage;
    private final Bitmap capturedImage;

    public ScanResult(String medicationName, String dosage, String frequencyDoses,
                      String frequencyDays, String mealInstructions,
                      String type, String usage, Bitmap capturedImage) {
        this.medicationName = medicationName;
        this.dosage = dosage;
        this.frequencyDoses = frequencyDoses;
        this.frequencyDays = frequencyDays;
        this.mealInstructions = mealInstructions;
        this.type = type;
        this.usage = usage;
        this.capturedImage = capturedImage;
    }

    public String getMedicationName() {
        return medicationName;
    }

    public String getDosage() {
        return dosage;
    }

    public String getFrequencyDoses() {
        return frequencyDoses;
    }

    public String getFrequencyDays() {
        return frequencyDays;
    }

    public String getMealInstructions() {
        return mealInstructions;
    }

    public String getType() {
        return type;
    }

    public String getUsage() {
        return usage;
    }

    public Bitmap getCapturedImage() {
        return capturedImage;
    }

    public boolean hasMeaningfulPrefillData() {
        return hasValue(medicationName)
                || hasValue(dosage)
                || hasValue(frequencyDoses)
                || hasValue(frequencyDays)
                || hasValue(mealInstructions);
    }

    private boolean hasValue(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
