package dev.nez.arksurvivalreturns.feature.recorder;

import java.util.List;

/**
 * JSON text of a {@link Row}: one object per line, {@code t} the record type, {@code s} its sequence
 * number, {@code k} the server tick and {@code ns} the nanoseconds since the recording started when the
 * row carries its own time. Numbers keep the shortest text that reads back to the same value.
 */
final class RowJson {
    static void line(StringBuilder out, Row row) {
        out.append("{\"t\":");
        text(out, row.type);
        if (row.seq >= 0) out.append(",\"s\":").append(row.seq);
        out.append(",\"k\":").append(row.tick);
        if (row.nanos >= 0) out.append(",\"ns\":").append(row.nanos);
        fields(out, row, true);
        out.append('}');
    }

    private static void fields(StringBuilder out, Row row, boolean following) {
        for (int i = 0; i < row.size(); i++) {
            if (following || i > 0) out.append(',');
            text(out, row.key(i));
            out.append(':');
            value(out, row.value(i));
        }
    }

    private static void value(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case String text -> text(out, text);
            case Integer number -> out.append(number.intValue());
            case Long number -> out.append(number.longValue());
            case Float number -> decimal(out, number, Float.toString(number));
            case Double number -> decimal(out, number, Double.toString(number));
            case Boolean flag -> out.append(flag.booleanValue());
            case double[] numbers -> {
                out.append('[');
                for (int i = 0; i < numbers.length; i++) {
                    if (i > 0) out.append(',');
                    decimal(out, numbers[i], Double.toString(numbers[i]));
                }
                out.append(']');
            }
            case float[] numbers -> {
                out.append('[');
                for (int i = 0; i < numbers.length; i++) {
                    if (i > 0) out.append(',');
                    decimal(out, numbers[i], Float.toString(numbers[i]));
                }
                out.append(']');
            }
            case int[] numbers -> {
                out.append('[');
                for (int i = 0; i < numbers.length; i++) {
                    if (i > 0) out.append(',');
                    out.append(numbers[i]);
                }
                out.append(']');
            }
            case Object[] items -> {
                out.append('[');
                for (int i = 0; i < items.length; i++) {
                    if (i > 0) out.append(',');
                    value(out, items[i]);
                }
                out.append(']');
            }
            case List<?> items -> {
                out.append('[');
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) out.append(',');
                    value(out, items.get(i));
                }
                out.append(']');
            }
            case Row nested -> {
                out.append('{');
                fields(out, nested, false);
                out.append('}');
            }
            default -> text(out, value.toString());
        }
    }

    /** JSON has no NaN or infinity; whole values drop their ".0". */
    private static void decimal(StringBuilder out, double number, String shortest) {
        if (Double.isNaN(number) || Double.isInfinite(number)) out.append("null");
        else if (number == Math.rint(number) && Math.abs(number) < 1.0E15) out.append((long) number);
        else out.append(shortest);
    }

    private static void text(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private RowJson() {}
}
