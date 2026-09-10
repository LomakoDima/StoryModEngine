package com.dimalab.storymodengine.common.scripting.ast;

/** {@code when player enters "<zoneName>"} — {@code zoneName} is resolved against a Java-registered named zone at compile time (SME's grammar has no coordinate literal), see {@code TriggerCompiler}. */
public record WhenEntersNode(SourcePos pos, String zoneName) implements TriggerConditionNode {
}
