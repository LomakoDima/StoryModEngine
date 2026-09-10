package com.dimalab.storymodengine.common.scripting.ast;

/**
 * {@code objective <kind> <targetId> [count]} — {@code kind} is one of {@code
 * kill/collect/talk_to/interact/dialogue/quest/find_entity} (a closed set, dispatched by {@code
 * ObjectiveDispatch}, never a switch on a Java type). {@code count} is nullable (unused by
 * count-less kinds); for {@code find_entity} it carries the search radius rather than a repeat
 * count. {@code location} is not supported in this MVP grammar — it needs a coordinate literal this
 * language deliberately doesn't have (see {@code TriggerCompiler}'s identical zone-literal boundary).
 */
public record ObjectiveNode(SourcePos pos, String kind, String targetId, Integer count) implements SmeNode {
}
