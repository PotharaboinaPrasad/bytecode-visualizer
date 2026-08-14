package com.prasad.bcviz;

import java.util.List;
import java.util.Map;

/** Tiny JSON serializer - just enough to avoid pulling in an external dependency. */
public class Json {

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String str(String s) { return "\"" + escape(s) + "\""; }

    public static String arr(List<String> jsonElements) {
        return "[" + String.join(",", jsonElements) + "]";
    }

    public static String obj(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!first) sb.append(",");
            sb.append(str(e.getKey())).append(":").append(e.getValue());
            first = false;
        }
        return sb.append("}").toString();
    }
}
