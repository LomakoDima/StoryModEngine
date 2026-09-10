package com.dimalab.storymodengine.common.scripting.diagnostics;

import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.List;

/**
 * The one funnel every SME stage (lexer/parser/validator/compiler) writes diagnostics into — logs
 * each one to {@code EngineLog.channel("SME")} and keeps a running failure count so {@code
 * SmeReloadListener} can log one summary line per reload without aborting on a single bad file (the
 * same "catch, log, keep going per-resource" shape {@code DialogueJsonLoader} already uses).
 *
 * <p>Error codes: {@code SME0xx} lexer, {@code SME1xx} parser, {@code SME2xx} duplicate-id
 * validation, {@code SME3xx} reference-resolution validation, {@code SME4xx} type/arity validation,
 * {@code SME5xx} compiler-internal.
 */
public final class DiagnosticReporter {

    private DiagnosticReporter() {
    }

    /** Logs every diagnostic in {@code diagnostics}; returns how many were {@link Severity#ERROR}. */
    public static int report(String file, List<SmeDiagnostic> diagnostics) {
        int errors = 0;
        for (SmeDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == Severity.ERROR) {
                EngineLog.channel("SME").error(diagnostic.format());
                errors++;
            } else {
                EngineLog.channel("SME").warn(diagnostic.format());
            }
        }
        return errors;
    }
}
