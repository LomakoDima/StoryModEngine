package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** A {@code @chapter(...)}/{@code @author(...)}/{@code @debug}/{@code @test} tag attached to the declaration that immediately follows it — informational only, never affects compilation. */
public record MetadataTag(SourcePos pos, String name, List<Object> args) implements SmeNode {
}
