package it.watchtracker.catalog;

import com.fasterxml.jackson.databind.JsonNode;

/** Letture tolleranti dai JSON dei cataloghi. */
final class JsonSupport {

    private JsonSupport() {}

    static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }

    static Integer integer(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || !node.get(field).canConvertToInt()) {
            return null;
        }
        int value = node.get(field).asInt();
        return value <= 0 ? null : value;
    }

    /** Estrae l'anno da una data in formato ISO (es. "2019-04-06"). */
    static Integer yearOf(String isoDate) {
        if (isoDate == null || isoDate.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(isoDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
