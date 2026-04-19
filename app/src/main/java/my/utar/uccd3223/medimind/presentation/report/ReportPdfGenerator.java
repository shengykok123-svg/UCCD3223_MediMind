package my.utar.uccd3223.medimind.presentation.report;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ReportPdfGenerator {

    private static final int PAGE_WIDTH = 595;  // A4 in points
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;
    private static final int CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final int BITMAP_SCALE = 2; // 144 DPI for crisp raster output

    // Colors
    private static final int COLOR_PRIMARY = 0xFF00897B;
    private static final int COLOR_SUCCESS = 0xFF43A047;
    private static final int COLOR_ERROR = 0xFFE53935;
    private static final int COLOR_WARNING = 0xFFFFB300;
    private static final int COLOR_TEXT = 0xFF212121;
    private static final int COLOR_TEXT_SECONDARY = 0xFF757575;
    private static final int COLOR_DIVIDER = 0xFFE0E0E0;
    private static final int COLOR_LIGHT_BG = 0xFFF5F5F5;

    public static File generate(Context context, Map<String, String> profile,
                                String rangeLabel, String medFilterLabel,
                                float adherencePercent, int taken, int missed, int skipped,
                                int total, List<ReportViewModel.MedicationBarData> barData)
            throws IOException {

        PdfDocument document = new PdfDocument();
        int pageNum = 1;

        Bitmap pageBitmap = Bitmap.createBitmap(
                PAGE_WIDTH * BITMAP_SCALE, PAGE_HEIGHT * BITMAP_SCALE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(pageBitmap);
        canvas.scale(BITMAP_SCALE, BITMAP_SCALE);
        canvas.drawColor(Color.WHITE);

        float y = MARGIN;

        // --- Header ---
        Paint titlePaint = new Paint();
        titlePaint.setColor(COLOR_PRIMARY);
        titlePaint.setTextSize(22);
        titlePaint.setFakeBoldText(true);
        titlePaint.setAntiAlias(true);
        canvas.drawText("MediMind Health Report", MARGIN, y + 22, titlePaint);
        y += 30;

        Paint subtitlePaint = new Paint();
        subtitlePaint.setColor(COLOR_TEXT_SECONDARY);
        subtitlePaint.setTextSize(10);
        subtitlePaint.setAntiAlias(true);
        String genDate = "Generated on " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        canvas.drawText(genDate, MARGIN, y + 10, subtitlePaint);
        y += 20;

        // Divider
        y = drawDivider(canvas, y);

        // --- Report Parameters ---
        Paint sectionPaint = new Paint();
        sectionPaint.setColor(COLOR_TEXT);
        sectionPaint.setTextSize(14);
        sectionPaint.setFakeBoldText(true);
        sectionPaint.setAntiAlias(true);

        Paint bodyPaint = new Paint();
        bodyPaint.setColor(COLOR_TEXT);
        bodyPaint.setTextSize(11);
        bodyPaint.setAntiAlias(true);

        Paint labelPaint = new Paint();
        labelPaint.setColor(COLOR_TEXT_SECONDARY);
        labelPaint.setTextSize(11);
        labelPaint.setAntiAlias(true);

        canvas.drawText("Report Parameters", MARGIN, y + 14, sectionPaint);
        y += 24;
        canvas.drawText("Date Range: " + rangeLabel, MARGIN, y + 11, bodyPaint);
        y += 18;
        canvas.drawText("Medications: " + medFilterLabel, MARGIN, y + 11, bodyPaint);
        y += 24;

        y = drawDivider(canvas, y);

        // --- Patient Info ---
        canvas.drawText("Patient Information", MARGIN, y + 14, sectionPaint);
        y += 26;

        if (profile != null) {
            y = drawProfileRow(canvas, "Name", safeGet(profile, "name"), labelPaint, bodyPaint, y);
            y = drawProfileRow(canvas, "Gender", safeGet(profile, "gender"), labelPaint, bodyPaint, y);
            y = drawProfileRow(canvas, "Date of Birth", safeGet(profile, "dob"), labelPaint, bodyPaint, y);
            y = drawProfileRow(canvas, "Weight", safeGet(profile, "weight").isEmpty() ? "" : safeGet(profile, "weight") + " KG", labelPaint, bodyPaint, y);
            y = drawProfileRow(canvas, "Blood Type", safeGet(profile, "bloodType"), labelPaint, bodyPaint, y);
            y = drawProfileRow(canvas, "Allergies", safeGet(profile, "allergies"), labelPaint, bodyPaint, y);
        } else {
            canvas.drawText("Profile information not available", MARGIN, y + 11, labelPaint);
            y += 18;
        }
        y += 6;

        y = drawDivider(canvas, y);

        // --- Adherence Summary ---
        canvas.drawText("Adherence Summary", MARGIN, y + 14, sectionPaint);
        y += 28;

        // Draw pie chart
        float pieSize = 120;
        float pieCenterX = MARGIN + pieSize / 2;
        float pieCenterY = y + pieSize / 2;
        drawPieChart(canvas, pieCenterX, pieCenterY, pieSize / 2 - 5, taken, missed, skipped, adherencePercent);

        // Stats next to pie chart
        float statsX = MARGIN + pieSize + 30;
        float statsY = y + 10;

        Paint statLabelPaint = new Paint();
        statLabelPaint.setColor(COLOR_TEXT);
        statLabelPaint.setTextSize(12);
        statLabelPaint.setAntiAlias(true);

        Paint statValuePaint = new Paint();
        statValuePaint.setTextSize(12);
        statValuePaint.setFakeBoldText(true);
        statValuePaint.setAntiAlias(true);

        canvas.drawText("Overall Adherence:", statsX, statsY, statLabelPaint);
        statValuePaint.setColor(COLOR_PRIMARY);
        canvas.drawText(String.format("%.0f%%", adherencePercent), statsX + 130, statsY, statValuePaint);
        statsY += 22;

        canvas.drawText("Total Doses:", statsX, statsY, statLabelPaint);
        statValuePaint.setColor(COLOR_TEXT);
        canvas.drawText(String.valueOf(total), statsX + 130, statsY, statValuePaint);
        statsY += 22;

        canvas.drawText("Taken:", statsX, statsY, statLabelPaint);
        statValuePaint.setColor(COLOR_SUCCESS);
        canvas.drawText(String.valueOf(taken), statsX + 130, statsY, statValuePaint);
        statsY += 22;

        canvas.drawText("Missed:", statsX, statsY, statLabelPaint);
        statValuePaint.setColor(COLOR_ERROR);
        canvas.drawText(String.valueOf(missed), statsX + 130, statsY, statValuePaint);
        statsY += 22;

        canvas.drawText("Skipped:", statsX, statsY, statLabelPaint);
        statValuePaint.setColor(COLOR_WARNING);
        canvas.drawText(String.valueOf(skipped), statsX + 130, statsY, statValuePaint);

        y += pieSize + 16;

        y = drawDivider(canvas, y);

        // --- Per-Medication Breakdown ---
        canvas.drawText("Per-Medication Breakdown", MARGIN, y + 14, sectionPaint);
        y += 28;

        if (barData != null && !barData.isEmpty()) {
            // Table header
            Paint headerPaint = new Paint();
            headerPaint.setColor(Color.WHITE);
            headerPaint.setTextSize(11);
            headerPaint.setFakeBoldText(true);
            headerPaint.setAntiAlias(true);

            Paint headerBgPaint = new Paint();
            headerBgPaint.setColor(COLOR_PRIMARY);

            canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 22, headerBgPaint);
            canvas.drawText("Medication", MARGIN + 8, y + 15, headerPaint);
            canvas.drawText("Taken", MARGIN + CONTENT_WIDTH - 170, y + 15, headerPaint);
            canvas.drawText("Total", MARGIN + CONTENT_WIDTH - 110, y + 15, headerPaint);
            canvas.drawText("Rate", MARGIN + CONTENT_WIDTH - 50, y + 15, headerPaint);
            y += 22;

            Paint rowBgPaint = new Paint();
            Paint rowTextPaint = new Paint();
            rowTextPaint.setColor(COLOR_TEXT);
            rowTextPaint.setTextSize(10);
            rowTextPaint.setAntiAlias(true);

            Paint barBgPaint = new Paint();
            barBgPaint.setColor(COLOR_LIGHT_BG);

            Paint barFillPaint = new Paint();
            barFillPaint.setColor(COLOR_PRIMARY);

            for (int i = 0; i < barData.size(); i++) {
                ReportViewModel.MedicationBarData item = barData.get(i);

                // Check if we need a new page
                if (y + 40 > PAGE_HEIGHT - MARGIN) {
                    finishBitmapPage(document, pageBitmap, pageNum++);
                    pageBitmap = Bitmap.createBitmap(
                            PAGE_WIDTH * BITMAP_SCALE, PAGE_HEIGHT * BITMAP_SCALE, Bitmap.Config.ARGB_8888);
                    canvas = new Canvas(pageBitmap);
                    canvas.scale(BITMAP_SCALE, BITMAP_SCALE);
                    canvas.drawColor(Color.WHITE);
                    y = MARGIN;
                }

                // Alternating row background
                if (i % 2 == 0) {
                    rowBgPaint.setColor(0xFFF8F8F8);
                    canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 36, rowBgPaint);
                }

                // Medication name (truncate if too long)
                String name = item.label;
                if (name.length() > 30) name = name.substring(0, 27) + "...";
                canvas.drawText(name, MARGIN + 8, y + 14, rowTextPaint);

                // Mini bar
                float barX = MARGIN + 8;
                float barY2 = y + 20;
                float barMaxWidth = CONTENT_WIDTH - 180;
                float barHeight = 10;

                canvas.drawRect(barX, barY2, barX + barMaxWidth, barY2 + barHeight, barBgPaint);
                float fillRatio = item.totalCount > 0 ? (float) item.takenCount / item.totalCount : 0;
                canvas.drawRect(barX, barY2, barX + barMaxWidth * fillRatio, barY2 + barHeight, barFillPaint);

                // Stats columns
                canvas.drawText(String.valueOf(item.takenCount), MARGIN + CONTENT_WIDTH - 170, y + 14, rowTextPaint);
                canvas.drawText(String.valueOf(item.totalCount), MARGIN + CONTENT_WIDTH - 110, y + 14, rowTextPaint);
                String rate = item.totalCount > 0
                        ? String.format("%.0f%%", (item.takenCount * 100f / item.totalCount))
                        : "0%";
                canvas.drawText(rate, MARGIN + CONTENT_WIDTH - 50, y + 14, rowTextPaint);

                y += 36;
            }
        } else {
            canvas.drawText("No medication data available", MARGIN, y + 11, labelPaint);
            y += 18;
        }

        // --- Legend ---
        y += 16;
        Paint legendPaint = new Paint();
        legendPaint.setTextSize(9);
        legendPaint.setColor(COLOR_TEXT_SECONDARY);
        legendPaint.setAntiAlias(true);

        Paint dotPaint = new Paint();
        dotPaint.setAntiAlias(true);

        dotPaint.setColor(COLOR_SUCCESS);
        canvas.drawCircle(MARGIN + 5, y + 4, 4, dotPaint);
        canvas.drawText("Taken", MARGIN + 14, y + 8, legendPaint);

        dotPaint.setColor(COLOR_ERROR);
        canvas.drawCircle(MARGIN + 65, y + 4, 4, dotPaint);
        canvas.drawText("Missed", MARGIN + 74, y + 8, legendPaint);

        dotPaint.setColor(COLOR_WARNING);
        canvas.drawCircle(MARGIN + 130, y + 4, 4, dotPaint);
        canvas.drawText("Skipped", MARGIN + 139, y + 8, legendPaint);

        finishBitmapPage(document, pageBitmap, pageNum);

        // Save to cache
        String fileName = "MediMind_Report_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";
        File baseCacheDir = context.getExternalCacheDir() != null
                ? context.getExternalCacheDir()
                : context.getCacheDir();
        File reportsDir = new File(baseCacheDir, "reports");
        if (!reportsDir.exists()) reportsDir.mkdirs();
        File file = new File(reportsDir, fileName);
        FileOutputStream fos = new FileOutputStream(file);
        document.writeTo(fos);
        fos.flush();
        fos.getFD().sync();   // force data to physical storage before sharing
        fos.close();
        document.close();

        return file;
    }

    private static void finishBitmapPage(PdfDocument document, Bitmap bitmap, int pageNum) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                PAGE_WIDTH, PAGE_HEIGHT, pageNum).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        page.getCanvas().drawBitmap(bitmap, null,
                new RectF(0, 0, PAGE_WIDTH, PAGE_HEIGHT), null);
        document.finishPage(page);
        bitmap.recycle();
    }

    private static float drawDivider(Canvas canvas, float y) {
        Paint dividerPaint = new Paint();
        dividerPaint.setColor(COLOR_DIVIDER);
        dividerPaint.setStrokeWidth(1);
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint);
        return y + 12;
    }

    private static float drawProfileRow(Canvas canvas, String label, String value,
                                         Paint labelPaint, Paint bodyPaint, float y) {
        if (value == null || value.isEmpty()) value = "Not set";
        canvas.drawText(label + ":", MARGIN, y + 11, labelPaint);
        canvas.drawText(value, MARGIN + 120, y + 11, bodyPaint);
        return y + 18;
    }

    private static void drawPieChart(Canvas canvas, float cx, float cy, float radius,
                                      int taken, int missed, int skipped, float percent) {
        int total = taken + missed + skipped;
        if (total == 0) {
            // Empty circle
            Paint emptyPaint = new Paint();
            emptyPaint.setColor(COLOR_DIVIDER);
            emptyPaint.setStyle(Paint.Style.STROKE);
            emptyPaint.setStrokeWidth(18);
            emptyPaint.setAntiAlias(true);
            canvas.drawCircle(cx, cy, radius - 9, emptyPaint);
            return;
        }

        RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        float startAngle = -90; // Start from top

        Paint arcPaint = new Paint();
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(18);
        arcPaint.setAntiAlias(true);
        arcPaint.setStrokeCap(Paint.Cap.BUTT);

        // Taken arc (green)
        if (taken > 0) {
            float sweep = (taken * 360f / total);
            arcPaint.setColor(COLOR_SUCCESS);
            canvas.drawArc(new RectF(cx - radius + 9, cy - radius + 9, cx + radius - 9, cy + radius - 9),
                    startAngle, sweep, false, arcPaint);
            startAngle += sweep;
        }

        // Missed arc (red)
        if (missed > 0) {
            float sweep = (missed * 360f / total);
            arcPaint.setColor(COLOR_ERROR);
            canvas.drawArc(new RectF(cx - radius + 9, cy - radius + 9, cx + radius - 9, cy + radius - 9),
                    startAngle, sweep, false, arcPaint);
            startAngle += sweep;
        }

        // Skipped arc (yellow)
        if (skipped > 0) {
            float sweep = (skipped * 360f / total);
            arcPaint.setColor(COLOR_WARNING);
            canvas.drawArc(new RectF(cx - radius + 9, cy - radius + 9, cx + radius - 9, cy + radius - 9),
                    startAngle, sweep, false, arcPaint);
        }

        // Center text
        Paint centerPaint = new Paint();
        centerPaint.setColor(COLOR_PRIMARY);
        centerPaint.setTextSize(18);
        centerPaint.setFakeBoldText(true);
        centerPaint.setAntiAlias(true);
        centerPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.format("%.0f%%", percent), cx, cy + 7, centerPaint);
    }

    private static String safeGet(Map<String, String> map, String key) {
        String val = map.get(key);
        return val != null ? val : "";
    }
}
