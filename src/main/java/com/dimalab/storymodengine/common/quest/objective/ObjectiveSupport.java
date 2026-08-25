package com.dimalab.storymodengine.common.quest.objective;

import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.api.quest.ObjectiveState;
import com.dimalab.storymodengine.common.quest.persistence.QuestProgressStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

/**
 * The one shared building block every event-driven {@link Objective} compiles through — see the
 * design doc §3. "Activate, wait for a matching event (incrementing progress as a side effect of a
 * filter that keeps returning {@code false} until satisfied), mark complete" — zero polling, backed
 * entirely by {@code Flow.waitForEvent}'s push-based {@code EventWaiter} (verified from source: it
 * subscribes once on start, unsubscribes exactly once on match/timeout/cancel).
 */
final class ObjectiveSupport {

    private ObjectiveSupport() {
    }

    /**
     * @param matches        whether {@code event} is even relevant to this objective/player at all (e.g. right entity type, right player)
     * @param progressDelta  how much one matching event contributes — almost always {@code e -> 1}, exposed for e.g. a stack-size pickup counting more than one
     */
    static <E extends Event> Flow waitFor(ResourceLocation questId, Objective self, Class<E> eventType,
                                           BiPredicate<ServerPlayer, E> matches, ToIntFunction<E> progressDelta) {
        return Flow.sequence(
                Flow.action(ctx -> QuestProgressStore.setObjectiveState(ctx.player(), questId, self.id(), ObjectiveState.ACTIVE)),
                Flow.waitForEvent(eventType, (ctx, event) -> {
                    if (!matches.test(ctx.player(), event)) {
                        return false;
                    }
                    int total = QuestProgressStore.incrementObjective(
                            ctx.player(), questId, self.id(), progressDelta.applyAsInt(event), self.requiredCount());
                    return total >= self.requiredCount();
                }),
                Flow.action(ctx -> QuestProgressStore.completeObjective(ctx.player(), questId, self.id(), self.requiredCount()))
        );
    }

    /** Same as {@link #waitFor}, for an objective whose one matching event always fully satisfies it (the common {@code requiredCount() == 1} case) — {@code progressDelta} is fixed at {@code 1}. */
    static <E extends Event> Flow waitFor(ResourceLocation questId, Objective self, Class<E> eventType, BiPredicate<ServerPlayer, E> matches) {
        return waitFor(questId, self, eventType, matches, event -> 1);
    }

    /** {@code namespace:path} with the {@code :} folded to {@code _} — used to build a deterministic, content-derived objective id (e.g. {@code "kill_minecraft_zombie"}) that stays stable across JVM restarts/registration-order changes, unlike an incrementing counter. */
    static String sanitize(ResourceLocation id) {
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
