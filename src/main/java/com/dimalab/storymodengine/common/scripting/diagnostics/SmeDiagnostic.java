package com.dimalab.storymodengine.common.scripting.diagnostics;

import com.dimalab.storymodengine.common.scripting.ast.SourcePos;

/**
 * One lexer/parser/validation/compiler finding, always carrying a real file:line:column — no stage
 * in this subsystem ever reports "something went wrong" without one. {@code code} is a flat
 * {@code SME###} namespace grouped by stage (see {@link DiagnosticReporter}'s class doc).
 */
public record SmeDiagnostic(Severity severity, SourcePos pos, String code, String message) {

    public String format() {
        return pos + ": " + severity + " [" + code + "] " + message;
    }
}
