package com.dimalab.storymodengine.common.scripting.lexer;

import com.dimalab.storymodengine.common.scripting.diagnostics.SmeDiagnostic;

import java.util.List;

public record LexResult(List<Token> tokens, List<SmeDiagnostic> diagnostics) {
}
