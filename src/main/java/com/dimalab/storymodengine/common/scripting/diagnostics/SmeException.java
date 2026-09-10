package com.dimalab.storymodengine.common.scripting.diagnostics;

import java.util.List;

/**
 * Thrown only by the compiler, and only for a condition validation should already have ruled out
 * (defensive, not the normal error-reporting path) — the lexer/parser/validator all *collect*
 * diagnostics into a {@code List} and keep going rather than throwing on the first problem, per this
 * subsystem's "no silent failures, no abort-on-first-error" requirement. See {@link DiagnosticReporter}.
 */
public class SmeException extends RuntimeException {

    private final List<SmeDiagnostic> diagnostics;

    public SmeException(List<SmeDiagnostic> diagnostics) {
        super(diagnostics.size() + " diagnostic(s) — see getDiagnostics()");
        this.diagnostics = List.copyOf(diagnostics);
    }

    public SmeException(SmeDiagnostic single) {
        this(List.of(single));
    }

    public List<SmeDiagnostic> diagnostics() {
        return diagnostics;
    }
}
