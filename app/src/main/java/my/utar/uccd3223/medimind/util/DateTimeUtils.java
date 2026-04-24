package my.utar.uccd3223.medimind.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Centralizes date and time formatting helpers used across medication schedules.
 */
public class DateTimeUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String getCurrentDateTime() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
}
