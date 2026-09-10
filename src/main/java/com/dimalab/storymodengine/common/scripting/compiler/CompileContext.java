package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.common.scripting.ast.SmeValueType;
import com.dimalab.storymodengine.common.scripting.ast.StoryNode;

import java.util.Map;

/** Compile-time state shared across one story's sub-compilers — the compiler-side counterpart of {@code validation.ValidationContext}, carrying the same variable-type symbol table {@code VariableUsagePass} already resolved (see {@code validation.StoryValidationResult}), so compilation never re-derives it and can never disagree with what validation already checked. */
public final class CompileContext {

    private final String file;
    private final StoryNode story;
    private final Map<String, SmeValueType> varTypes;

    public CompileContext(String file, StoryNode story, Map<String, SmeValueType> varTypes) {
        this.file = file;
        this.story = story;
        this.varTypes = varTypes;
    }

    public String file() {
        return file;
    }

    public StoryNode story() {
        return story;
    }

    /** Defaults to {@code STRING} for a name with no known declaration — should not happen for a story that passed validation, but never null. */
    public SmeValueType typeOf(String varName) {
        return varTypes.getOrDefault(varName, SmeValueType.STRING);
    }
}
