package com.campx.admin.institute.model.preference;

import com.campx.admin.institute.exception.MalformedPayloadException;

import java.util.*;

/**
 * Lightweight, zero-dependency, standard RFC 8259 JSON parser tailored for
 * parsing preference payloads and complex nested JSON structures in the Java backend.
 */
public class PreferenceJsonParser {

    private final String src;
    private final int len;
    private int pos = 0;

    public PreferenceJsonParser(String src) {
        if (src == null) {
            throw new MalformedPayloadException("JSON input string cannot be null");
        }
        this.src = src;
        this.len = src.length();
    }

    public static Object parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new MalformedPayloadException("Request body cannot be null or empty");
        }
        PreferenceJsonParser parser = new PreferenceJsonParser(json.trim());
        Object result = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < parser.len) {
            throw new MalformedPayloadException("Unexpected trailing characters after JSON payload at offset " + parser.pos);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object val = parseJson(json);
        if (!(val instanceof Map)) {
            throw new MalformedPayloadException("Expected JSON object but received: " + (val != null ? val.getClass().getSimpleName() : "null"));
        }
        return (Map<String, Object>) val;
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= len) {
            throw new MalformedPayloadException("Unexpected end of JSON input");
        }
        char c = src.charAt(pos);
        if (c == '{') {
            return parseObject();
        } else if (c == '[') {
            return parseArray();
        } else if (c == '"') {
            return parseString();
        } else if (c == 't' || c == 'f') {
            return parseBoolean();
        } else if (c == 'n') {
            return parseNull();
        } else if (c == '-' || Character.isDigit(c)) {
            return parseNumber();
        } else {
            throw new MalformedPayloadException("Unexpected character '" + c + "' at position " + pos);
        }
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (pos < len && src.charAt(pos) == '}') {
            pos++;
            return map;
        }

        while (pos < len) {
            skipWhitespace();
            if (pos >= len || src.charAt(pos) != '"') {
                throw new MalformedPayloadException("Expected string key in JSON object at position " + pos);
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);

            skipWhitespace();
            if (pos < len && src.charAt(pos) == ',') {
                pos++;
            } else if (pos < len && src.charAt(pos) == '}') {
                pos++;
                return map;
            } else {
                throw new MalformedPayloadException("Expected ',' or '}' in JSON object at position " + pos);
            }
        }
        throw new MalformedPayloadException("Unterminated JSON object");
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (pos < len && src.charAt(pos) == ']') {
            pos++;
            return list;
        }

        while (pos < len) {
            Object value = parseValue();
            list.add(value);
            skipWhitespace();
            if (pos < len && src.charAt(pos) == ',') {
                pos++;
            } else if (pos < len && src.charAt(pos) == ']') {
                pos++;
                return list;
            } else {
                throw new MalformedPayloadException("Expected ',' or ']' in JSON array at position " + pos);
            }
        }
        throw new MalformedPayloadException("Unterminated JSON array");
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < len) {
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            } else if (c == '\\') {
                if (pos >= len) {
                    throw new MalformedPayloadException("Unfinished escape sequence at end of JSON input");
                }
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > len) {
                            throw new MalformedPayloadException("Invalid unicode escape sequence");
                        }
                        String hex = src.substring(pos, pos + 4);
                        pos += 4;
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw new MalformedPayloadException("Invalid unicode escape hex: \\u" + hex);
                        }
                        break;
                    default:
                        throw new MalformedPayloadException("Invalid escape sequence '\\" + esc + "' at position " + (pos - 1));
                }
            } else {
                sb.append(c);
            }
        }
        throw new MalformedPayloadException("Unterminated JSON string literal");
    }

    private Boolean parseBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        } else if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new MalformedPayloadException("Invalid boolean literal at position " + pos);
    }

    private Object parseNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new MalformedPayloadException("Invalid literal at position " + pos);
    }

    private Number parseNumber() {
        int start = pos;
        if (src.charAt(pos) == '-') {
            pos++;
        }
        while (pos < len && Character.isDigit(src.charAt(pos))) {
            pos++;
        }
        boolean isFloating = false;
        if (pos < len && src.charAt(pos) == '.') {
            isFloating = true;
            pos++;
            while (pos < len && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        if (pos < len && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            isFloating = true;
            pos++;
            if (pos < len && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < len && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }

        String numStr = src.substring(start, pos);
        try {
            if (isFloating) {
                return Double.parseDouble(numStr);
            }
            long l = Long.parseLong(numStr);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return (int) l;
            }
            return l;
        } catch (NumberFormatException e) {
            throw new MalformedPayloadException("Invalid numeric literal '" + numStr + "' at position " + start);
        }
    }

    private void expect(char expected) {
        if (pos >= len || src.charAt(pos) != expected) {
            char actual = pos < len ? src.charAt(pos) : ' ';
            throw new MalformedPayloadException("Expected '" + expected + "' but found '" + actual + "' at position " + pos);
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < len) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                pos++;
            } else {
                break;
            }
        }
    }
}
