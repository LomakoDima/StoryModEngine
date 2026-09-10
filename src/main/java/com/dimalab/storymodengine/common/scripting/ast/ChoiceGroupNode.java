package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** {@code choice { "..." {...} "..." {...} }} — one or more {@link ChoiceNode}s offered together at this point in a dialogue node. */
public record ChoiceGroupNode(SourcePos pos, List<ChoiceNode> options) implements DialogueEntryNode {
}
