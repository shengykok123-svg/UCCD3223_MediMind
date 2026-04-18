package my.utar.uccd3223.medimind.util;

import android.content.Context;
import android.net.Uri;
import android.widget.ImageView;

import my.utar.uccd3223.medimind.R;

public final class MedicationImageUtils {

    private MedicationImageUtils() {}

    public static String getDefaultMedicationImageUri(Context context) {
        return "android.resource://" + context.getPackageName() + "/" + R.drawable.ic_default_medication;
    }

    public static boolean isDefaultMedicationImageUri(Context context, String imageUrl) {
        return imageUrl != null && imageUrl.equals(getDefaultMedicationImageUri(context));
    }

    public static void loadMedicationImage(ImageView imageView, String imageUrl) {
        float density = imageView.getContext().getResources().getDisplayMetrics().density;
        int pad = (int) (8 * density);

        if (imageUrl == null || imageUrl.isEmpty() || isDefaultMedicationImageUri(imageView.getContext(), imageUrl)) {
            imageView.setPadding(pad, pad, pad, pad);
            imageView.setImageResource(R.drawable.ic_default_medication);
            return;
        }

        try {
            imageView.setPadding(0, 0, 0, 0);
            imageView.setImageURI(Uri.parse(imageUrl));
            if (imageView.getDrawable() == null) {
                imageView.setPadding(pad, pad, pad, pad);
                imageView.setImageResource(R.drawable.ic_default_medication);
            }
        } catch (Exception e) {
            imageView.setPadding(pad, pad, pad, pad);
            imageView.setImageResource(R.drawable.ic_default_medication);
        }
    }
}
