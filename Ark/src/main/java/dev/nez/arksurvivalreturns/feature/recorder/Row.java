package dev.nez.arksurvivalreturns.feature.recorder;

import java.util.Arrays;
import java.util.List;

/**
 * One captured record. The game thread copies plain values into it and hands it to the writer thread,
 * which turns it into a JSON line; nothing here keeps a reference to a live game object.
 */
public final class Row {
    final String type;
    long seq = -1;
    int tick;
    /** Nanoseconds since the recording started, or -1 when the tick record's time is precise enough. */
    long nanos = -1;
    private Object[] entries = new Object[24];
    private int size;

    public Row(String type) { this.type = type; }

    private Row add(String key, Object value) {
        if (size + 2 > entries.length) entries = Arrays.copyOf(entries, entries.length * 2);
        entries[size++] = key;
        entries[size++] = value;
        return this;
    }

    public Row put(String key, int value) { return add(key, value); }
    public Row put(String key, long value) { return add(key, value); }
    public Row put(String key, float value) { return add(key, value); }
    public Row put(String key, double value) { return add(key, value); }
    public Row put(String key, boolean value) { return add(key, value); }
    /** A null text or name is simply left out. */
    public Row put(String key, String value) { return value == null ? this : add(key, value); }
    public Row put(String key, Enum<?> value) { return value == null ? this : add(key, value.name()); }
    /** Written only when set, so the usual false costs no bytes. */
    public Row flag(String key, boolean value) { return value ? add(key, Boolean.TRUE) : this; }
    public Row xyz(String key, double x, double y, double z) { return add(key, new double[]{x, y, z}); }
    /** Single precision stays single, so a velocity prints as 0.0784 and not as its double expansion. */
    public Row xyz(String key, float x, float y, float z) { return add(key, new float[]{x, y, z}); }
    public Row block(String key, int x, int y, int z) { return add(key, new int[]{x, y, z}); }
    public Row ints(String key, int[] values) { return values == null ? this : add(key, values); }
    public Row doubles(String key, double[] values) { return values == null ? this : add(key, values); }
    public Row row(String key, Row nested) { return nested == null ? this : add(key, nested); }
    /** A list of rows, tuples ({@code Object[]}), texts or boxed numbers. */
    public Row list(String key, List<?> items) { return items == null ? this : add(key, items); }

    int size() { return size >> 1; }
    String key(int index) { return (String) entries[index * 2]; }
    Object value(int index) { return entries[index * 2 + 1]; }
}
