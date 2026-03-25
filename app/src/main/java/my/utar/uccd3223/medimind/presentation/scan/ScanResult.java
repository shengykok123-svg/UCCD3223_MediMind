package my.utar.uccd3223.medimind.presentation.scan;

import android.graphics.Bitmap;

public class ScanResult {
    private final String medicationName;
    private final String dosage;
    private final String type;
    private final String usage;
    private final Bitmap capturedImage;

    public ScanResult(String medicationName, String dosage, String type, String usage, Bitmap capturedImage) {
        this.medicationName = medicationName;
        this.dosage = dosage;
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

    public String getType() {
        return type;
    }

    public String getUsage() {
        return usage;
    }

    public Bitmap getCapturedImage() {
        return capturedImage;
    }
}
