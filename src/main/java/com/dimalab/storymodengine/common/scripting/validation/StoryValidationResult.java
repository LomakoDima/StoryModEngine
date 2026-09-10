package com.dimalab.storymodengine.common.scripting.validation;

import com.dimalab.storymodengine.common.scripting.ast.SmeValueType;
import com.dimalab.storymodengine.common.scripting.ast.StoryNode;
import com.dimalab.storymodengine.common.scripting.diagnostics.Severity;
import com.dimalab.storymodengine.common.scripting.diagnostics.SmeDiagnostic;

import java.util.List;
import java.util.Map;

/**
 * One {@code story} block's validation outcome — {@code SmeReloadListener} compiles a story only if
 * {@link #hasErrors()} is {@code false}, but still reports every diagnostic regardless, and a
 * failing story never blocks a different, valid story in the same file. {@code varTypes} is {@link
 * VariableUsagePass}'s own resolved symbol table, carried forward so the compiler never has to
 * re-derive it — validation and compilation can never disagree about a variable's type.
 */
public record StoryValidationResult(StoryNode story, List<SmeDiagnostic> diagnostics, Map<String, SmeValueType> varTypes) {

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }
}
