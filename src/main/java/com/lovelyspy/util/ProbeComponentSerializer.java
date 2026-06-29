package com.lovelyspy.util;

public final class ProbeComponentSerializer {
    public static final int SAFE_SIGN_COMPONENT_LENGTH = 96;

    private ProbeComponentSerializer() {
    }

    public static String serialize(String value) {
        if (value == null || value.isEmpty()) {
            return "{\"text\":\"\"}";
        }
        if (value.equals("key.forward") || value.equals("gui.yes")) {
            return "{\"keybind\":" + quote(value) + "}";
        }
        return "{\"translate\":" + quote(value) + "}";
    }

    public static boolean fitsDefaultSignLimiter(String value) {
        return serialize(value).length() <= SAFE_SIGN_COMPONENT_LENGTH;
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }
}
