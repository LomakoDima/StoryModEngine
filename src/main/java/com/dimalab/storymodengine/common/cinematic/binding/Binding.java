package com.dimalab.storymodengine.common.cinematic.binding;

import com.dimalab.storymodengine.common.cinematic.CutsceneContext;

/**
 * Resolves to a live value through a {@link CutsceneContext} — the generalized shape {@code
 * cinematic.actor.ActorBinding} already follows in spirit (its own {@code resolve(Level,
 * CutsceneContext)} predates this interface and keeps that two-argument shape for compatibility,
 * since {@code Level} is reachable off {@code CutsceneContext} too). New binding kinds (see {@link
 * LookAt}) implement this one directly. A {@code CutsceneDefinition} never stores a resolved
 * Minecraft object directly — only a {@code Binding}, resolved fresh every time a cutscene plays,
 * safe to resolve to {@code null} if whatever it points at is gone (see {@link LookAt}'s Javadoc).
 */
@FunctionalInterface
public interface Binding<T> {

    T resolve(CutsceneContext context);
}
