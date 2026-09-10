package com.dimalab.storymodengine.common.scripting.parser;

import com.dimalab.storymodengine.common.scripting.diagnostics.SmeDiagnostic;
import com.dimalab.storymodengine.common.scripting.diagnostics.SmeException;

/** Never thrown in normal operation — {@link SmeParser} collects parse errors as diagnostics and performs panic-mode recovery instead (see its class doc), matching {@code lexer.LexException}'s identical role. */
public final class ParseException extends SmeException {

    public ParseException(SmeDiagnostic diagnostic) {
        super(diagnostic);
    }
}
