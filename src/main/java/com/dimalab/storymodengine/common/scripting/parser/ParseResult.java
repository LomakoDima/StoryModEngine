package com.dimalab.storymodengine.common.scripting.parser;

import com.dimalab.storymodengine.common.scripting.ast.StoryNode;
import com.dimalab.storymodengine.common.scripting.diagnostics.SmeDiagnostic;

import java.util.List;

public record ParseResult(List<StoryNode> stories, List<SmeDiagnostic> diagnostics) {
}
