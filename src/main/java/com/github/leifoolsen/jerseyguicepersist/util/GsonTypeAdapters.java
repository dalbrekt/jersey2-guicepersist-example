package com.github.leifoolsen.jerseyguicepersist.util;

import com.google.common.base.Strings;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// See: https://sites.google.com/site/gson/gson-type-adapters-for-common-classes
// See: https://sites.google.com/site/gson/gson-type-adapters-for-common-classes-1
// See: http://www.javacreed.com/gson-typeadapter-example/
// See: https://sites.google.com/site/gson/gson-user-guide#TOC-Writing-a-Deserializer
// See: https://github.com/gkopff/gson-javatime-serialisers/

public class GsonTypeAdapters {

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private GsonTypeAdapters() {}

    public static JsonDeserializer<String> stringDeserializerEmptyToNull() {
        return (json, typeOfT, context) -> json == null
                ? null
                : Strings.emptyToNull(json.getAsString().trim());
    }

    public static JsonDeserializer<LocalDate> localDateDeserializer() {
        return (json, typeOfT, context) -> json == null
                ? null
                : DATE_FORMATTER.parse(json.getAsString(), LocalDate::from);
    }

    public static JsonSerializer<LocalDate> localDateSerializer() {
        return (src, typeOfSrc, context) -> src == null
                ? null
                : new JsonPrimitive(DATE_FORMATTER.format(src));
    }

    public static JsonDeserializer<LocalDateTime> localDateTimeDeserializer() {
        return (json, typeOfT, context) -> {
            try {
                return json == null
                        ? null
                        : DATE_TIME_FORMATTER.parse(json.getAsString(), LocalDateTime::from);
            }
            catch (DateTimeParseException e) {
                return DATE_FORMATTER.parse(json.getAsString(), LocalDate::from).atStartOfDay();
            }
        };
    }

    public static JsonSerializer<LocalDateTime> localDateTimeSerializer() {
        return (src, typeOfSrc, context) -> src == null
                ? null
                : new JsonPrimitive(DATE_TIME_FORMATTER.format(src));
    }

    public static JsonDeserializer<Instant> instantDeserializer() {
        return (json, typeOfT, context) -> json == null
                ? null
                : INSTANT_FORMATTER.parse(json.getAsString(), Instant::from);
    }

    public static JsonSerializer<Instant> instantSerializer() {
        return (src, typeOfSrc, context) -> src == null
                ? null
                : new JsonPrimitive(INSTANT_FORMATTER.format(src));
    }
}
