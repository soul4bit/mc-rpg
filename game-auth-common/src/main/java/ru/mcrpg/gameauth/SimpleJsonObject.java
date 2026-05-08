package ru.mcrpg.gameauth;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class SimpleJsonObject {

    private final Map<String, JsonValue> values;

    private SimpleJsonObject(Map<String, JsonValue> values) {
        this.values = values;
    }

    static SimpleJsonObject parse(String json) throws IOException {
        return new Parser(json).parse();
    }

    JsonValue get(String field) {
        return values.get(field);
    }

    String requiredString(String field) throws IOException {
        JsonValue value = values.get(field);
        if (value == null || value.type != JsonType.STRING || value.text.trim().isEmpty()) {
            throw new IOException("Missing required JSON string field: " + field);
        }
        return value.text.trim();
    }

    String optionalString(String field) {
        JsonValue value = values.get(field);
        if (value == null || value.type == JsonType.NULL) {
            return "";
        }
        if (value.type == JsonType.STRING || value.type == JsonType.NUMBER || value.type == JsonType.BOOLEAN) {
            return value.text == null ? "" : value.text.trim();
        }
        return "";
    }

    boolean optionalBoolean(String field, boolean fallback) {
        JsonValue value = values.get(field);
        if (value == null || value.type == JsonType.NULL) {
            return fallback;
        }
        if (value.type == JsonType.BOOLEAN) {
            return value.booleanValue;
        }
        if (value.type == JsonType.STRING) {
            String normalized = value.text.trim();
            if ("true".equalsIgnoreCase(normalized)) {
                return true;
            }
            if ("false".equalsIgnoreCase(normalized)) {
                return false;
            }
        }
        return fallback;
    }

    enum JsonType {
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    static final class JsonValue {
        private final JsonType type;
        private final String text;
        private final boolean booleanValue;

        private JsonValue(JsonType type, String text, boolean booleanValue) {
            this.type = type;
            this.text = text == null ? "" : text;
            this.booleanValue = booleanValue;
        }

        JsonType getType() {
            return type;
        }

        String getText() {
            return text;
        }

        boolean getBooleanValue() {
            return booleanValue;
        }
    }

    private static final class Parser {
        private final String json;
        private int index;

        private Parser(String json) {
            this.json = json == null ? "" : json;
        }

        private SimpleJsonObject parse() throws IOException {
            skipWhitespace();
            expect('{');
            skipWhitespace();

            Map<String, JsonValue> values = new LinkedHashMap<String, JsonValue>();
            if (peek('}')) {
                index++;
                skipWhitespace();
                ensureFinished();
                return new SimpleJsonObject(values);
            }

            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                values.put(key, parseValue());
                skipWhitespace();

                if (peek(',')) {
                    index++;
                    skipWhitespace();
                    continue;
                }
                if (peek('}')) {
                    index++;
                    skipWhitespace();
                    ensureFinished();
                    return new SimpleJsonObject(values);
                }
                throw error("Expected ',' or '}'");
            }
        }

        private JsonValue parseValue() throws IOException {
            if (index >= json.length()) {
                throw error("Unexpected end of JSON");
            }

            char current = json.charAt(index);
            if (current == '"') {
                return new JsonValue(JsonType.STRING, parseString(), false);
            }
            if (current == '-' || (current >= '0' && current <= '9')) {
                return new JsonValue(JsonType.NUMBER, parseNumber(), false);
            }
            if (startsWith("true")) {
                index += 4;
                return new JsonValue(JsonType.BOOLEAN, "true", true);
            }
            if (startsWith("false")) {
                index += 5;
                return new JsonValue(JsonType.BOOLEAN, "false", false);
            }
            if (startsWith("null")) {
                index += 4;
                return new JsonValue(JsonType.NULL, "", false);
            }
            throw error("Unsupported JSON value");
        }

        private String parseString() throws IOException {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < json.length()) {
                char current = json.charAt(index++);
                if (current == '"') {
                    return builder.toString();
                }
                if (current != '\\') {
                    builder.append(current);
                    continue;
                }
                if (index >= json.length()) {
                    throw error("Invalid escape sequence");
                }
                char escaped = json.charAt(index++);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        builder.append(escaped);
                        break;
                    case 'b':
                        builder.append('\b');
                        break;
                    case 'f':
                        builder.append('\f');
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case 'u':
                        builder.append(parseUnicode());
                        break;
                    default:
                        throw error("Unsupported escape sequence");
                }
            }
            throw error("Unterminated string literal");
        }

        private char parseUnicode() throws IOException {
            if (index + 4 > json.length()) {
                throw error("Invalid unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                char digit = json.charAt(index++);
                int hex = Character.digit(digit, 16);
                if (hex < 0) {
                    throw error("Invalid unicode escape");
                }
                value = (value << 4) + hex;
            }
            return (char) value;
        }

        private String parseNumber() {
            int start = index;
            if (json.charAt(index) == '-') {
                index++;
            }
            while (index < json.length() && Character.isDigit(json.charAt(index))) {
                index++;
            }
            if (index < json.length() && json.charAt(index) == '.') {
                index++;
                while (index < json.length() && Character.isDigit(json.charAt(index))) {
                    index++;
                }
            }
            if (index < json.length() && (json.charAt(index) == 'e' || json.charAt(index) == 'E')) {
                index++;
                if (index < json.length() && (json.charAt(index) == '+' || json.charAt(index) == '-')) {
                    index++;
                }
                while (index < json.length() && Character.isDigit(json.charAt(index))) {
                    index++;
                }
            }
            return json.substring(start, index);
        }

        private void skipWhitespace() {
            while (index < json.length()) {
                char current = json.charAt(index);
                if (current == ' ' || current == '\n' || current == '\r' || current == '\t') {
                    index++;
                    continue;
                }
                break;
            }
        }

        private void expect(char expected) throws IOException {
            if (index >= json.length() || json.charAt(index) != expected) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < json.length() && json.charAt(index) == expected;
        }

        private boolean startsWith(String prefix) {
            return json.regionMatches(index, prefix, 0, prefix.length());
        }

        private void ensureFinished() throws IOException {
            if (index != json.length()) {
                throw error("Trailing characters after JSON object");
            }
        }

        private IOException error(String message) {
            return new IOException(message + " at index " + index);
        }
    }
}
