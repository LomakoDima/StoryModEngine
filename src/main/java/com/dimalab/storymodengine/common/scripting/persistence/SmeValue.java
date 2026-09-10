package com.dimalab.storymodengine.common.scripting.persistence;

import com.dimalab.storymodengine.common.scripting.ast.SmeValueType;

/**
 * A closed tagged-union runtime value — being a {@code record}, {@code SerializerRegistry} resolves
 * it automatically (NBT and network alike) with zero hand-written serializer code, the same way
 * {@code QuestProgress} already nests a plain {@code Map<String,Integer>}. Only the field matching
 * {@link #type} is meaningful; the others sit at their default value, mirroring how a tagged union
 * is commonly modeled in a language with no native sum types.
 */
public record SmeValue(SmeValueType type, boolean boolVal, long intVal, double doubleVal, String stringVal) {

    public static SmeValue ofBool(boolean v) {
        return new SmeValue(SmeValueType.BOOL, v, 0, 0, "");
    }

    public static SmeValue ofInt(long v) {
        return new SmeValue(SmeValueType.INT, false, v, 0, "");
    }

    public static SmeValue ofDouble(double v) {
        return new SmeValue(SmeValueType.DOUBLE, false, 0, v, "");
    }

    public static SmeValue ofString(String v) {
        return new SmeValue(SmeValueType.STRING, false, 0, 0, v);
    }

    public static SmeValue zeroOf(SmeValueType type) {
        return switch (type) {
            case BOOL -> ofBool(false);
            case INT -> ofInt(0);
            case DOUBLE -> ofDouble(0);
            case STRING -> ofString("");
        };
    }

    public Object asObject() {
        return switch (type) {
            case BOOL -> boolVal;
            case INT -> intVal;
            case DOUBLE -> doubleVal;
            case STRING -> stringVal;
        };
    }
}
