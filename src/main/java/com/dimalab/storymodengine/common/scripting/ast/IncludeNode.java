package com.dimalab.storymodengine.common.scripting.ast;

/** {@code include "file.sme"} — spliced by the parser as a raw token-stream insertion (see {@code SmeParser}), resolved only relative to {@code storymodengine/stories/}; never reaches the compiler as a standalone node. */
public record IncludeNode(SourcePos pos, String relativeFilePath) implements SmeNode {
}
