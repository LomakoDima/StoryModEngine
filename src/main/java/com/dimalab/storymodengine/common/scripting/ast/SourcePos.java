package com.dimalab.storymodengine.common.scripting.ast;

/** Where in a {@code .sme} source file a node came from — carried by every {@link SmeNode} so a diagnostic can always point at real text, never just "somewhere in this file." */
public record SourcePos(String file, int line, int column) {

    public static final SourcePos UNKNOWN = new SourcePos("<unknown>", 0, 0);

    @Override
    public String toString() {
        return file + ":" + line + ":" + column;
    }
}
