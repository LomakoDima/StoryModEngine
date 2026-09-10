package com.dimalab.storymodengine.common.scripting.lexer;

import com.dimalab.storymodengine.common.scripting.diagnostics.SmeException;

/** Never actually thrown in normal operation — {@link SmeLexer} collects lex errors as diagnostics and resyncs instead (see its class doc); reserved for a genuinely unrecoverable input (kept distinct from a bare {@code SmeException} for callers that want to catch lexer-stage failures specifically). */
public final class LexException extends SmeException {

    public LexException(com.dimalab.storymodengine.common.scripting.diagnostics.SmeDiagnostic diagnostic) {
        super(diagnostic);
    }
}
