package dev.adrian.goral.localhiveagent.master.dto;

import java.time.LocalDateTime;
import java.util.List;

final class MasterTimestampParser {

    private MasterTimestampParser() {
    }

    static LocalDateTime parse(Object value, String fieldName) {
        if (value instanceof String text) {
            return LocalDateTime.parse(text);
        }

        if (value instanceof List<?> values && values.size() >= 5) {
            int year = numberAt(values, 0, fieldName);
            int month = numberAt(values, 1, fieldName);
            int day = numberAt(values, 2, fieldName);
            int hour = numberAt(values, 3, fieldName);
            int minute = numberAt(values, 4, fieldName);
            int second = values.size() > 5 ? numberAt(values, 5, fieldName) : 0;
            int nano = values.size() > 6 ? numberAt(values, 6, fieldName) : 0;

            return LocalDateTime.of(year, month, day, hour, minute, second, nano);
        }

        throw new IllegalArgumentException(fieldName + " must be an ISO timestamp or LocalDateTime array.");
    }

    private static int numberAt(List<?> values, int index, String fieldName) {
        Object value = values.get(index);
        if (value instanceof Number number) {
            return number.intValue();
        }

        throw new IllegalArgumentException(fieldName + " contains a non-numeric timestamp component.");
    }
}
