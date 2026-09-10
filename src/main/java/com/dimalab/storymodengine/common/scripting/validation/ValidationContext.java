package com.dimalab.storymodengine.common.scripting.validation;

import com.dimalab.storymodengine.common.scripting.ast.SmeValueType;
import com.dimalab.storymodengine.common.scripting.ast.SourcePos;
import com.dimalab.storymodengine.common.scripting.ast.StoryNode;
import com.dimalab.storymodengine.common.scripting.diagnostics.Severity;
import com.dimalab.storymodengine.common.scripting.diagnostics.SmeDiagnostic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared, mutable state one file's worth of validation passes read and write — built once per {@link StoryNode}, discarded after {@link SmeValidator#validate} returns. */
public final class ValidationContext {

    private final String file;
    private final StoryNode story;
    private final Map<String, SmeValueType> varTypes = new HashMap<>();
    private final List<SmeDiagnostic> diagnostics = new ArrayList<>();

    public ValidationContext(String file, StoryNode story) {
        this.file = file;
        this.story = story;
    }

    public String file() {
        return file;
    }

    public StoryNode story() {
        return story;
    }

    public Map<String, SmeValueType> varTypes() {
        return varTypes;
    }

    public List<SmeDiagnostic> diagnostics() {
        return diagnostics;
    }

    public void error(SourcePos pos, String code, String message) {
        diagnostics.add(new SmeDiagnostic(Severity.ERROR, pos, code, message));
    }

    public void warn(SourcePos pos, String code, String message) {
        diagnostics.add(new SmeDiagnostic(Severity.WARNING, pos, code, message));
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }
}
