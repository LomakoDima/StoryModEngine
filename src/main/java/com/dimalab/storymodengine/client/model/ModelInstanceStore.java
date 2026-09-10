package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.common.model.ModelDefinition;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-entity {@link ModelInstance}s for a renderer, holding them only as long as the entities
 * themselves live and keeping them in step with the currently loaded model.
 *
 * <p><b>Fixes a real leak.</b> Both renderers previously kept a plain {@code HashMap<Integer,
 * ModelInstance>} keyed by entity id and never removed anything from it, so every entity that had
 * ever been rendered left its whole runtime node tree behind for the rest of the session. A {@link
 * WeakHashMap} keyed by the {@link Entity} itself drops the entry as soon as the entity is collected;
 * a {@link ModelInstance} holds no reference back to the entity, so nothing keeps the key alive.
 *
 * <p>({@code Entity} overrides {@code equals}/{@code hashCode} to compare by network id — verified in
 * its source. That is fine here: lookups still behave, and two objects sharing an id are the same
 * entity as far as rendering is concerned.)
 *
 * <p><b>And a staleness bug.</b> An instance is rebuilt whenever the definition it was built from is
 * no longer the one that resolves now — which is what happens after a resource reload replaces a
 * model. Previously a live instance kept its original definition forever, so an edited model only
 * appeared on entities that spawned afterwards.
 */
public final class ModelInstanceStore {

    private final Map<Entity, Entry> entries = new WeakHashMap<>();

    /** The instance for {@code entity}, rebuilt if {@code definition} is not what it was built from. Null when there is no model to build from. */
    public ModelInstance instanceFor(Entity entity, ModelDefinition definition) {
        if (definition == null) {
            discard(entries.remove(entity));
            return null;
        }
        Entry entry = entries.get(entity);
        if (entry == null || entry.builtFor != definition) {
            discard(entry);
            entry = new Entry(new ModelInstance(definition), definition);
            entries.put(entity, entry);
        }
        return entry.instance;
    }

    /**
     * Frees a discarded instance's GPU skinning buffers. Only reached when this store itself
     * replaces an entry (model reload, or the entity's model changed) — an entry dropped by the
     * {@link WeakHashMap} because the entity was garbage collected leaks its GL objects instead.
     * Worth knowing, not solved here: there is no hook this store can act on when a key silently
     * vanishes from a {@code WeakHashMap}, so that path still needs a real fix (e.g. a reference
     * queue) if it turns out to matter in practice.
     */
    private static void discard(Entry entry) {
        if (entry != null) {
            entry.instance.destroyGpuResources();
        }
    }

    /**
     * Seconds of animation to advance by, for advancing this frame — derived from {@code ageInTicks}
     * ({@code entity.tickCount + partialTick}, the same quantity vanilla's own {@code
     * LivingEntityRenderer.getBob} computes and every {@code RenderLayer} already receives), not wall
     * clock time. A tick only advances while the simulation itself does, so this freezes exactly when
     * a paused game does — the previous {@code System.nanoTime()}-based version kept accumulating real
     * time while paused, animating right through a pause menu — and reproduces identically given the
     * same tick history, independent of real frame timing/machine speed.
     *
     * <p>Still clamped, for the same reason as before: an entity that left and re-entered view (or a
     * lag spike) shouldn't jump its animation forward by however many ticks it missed in one step.
     */
    public float deltaSeconds(Entity entity, float ageInTicks) {
        Entry entry = entries.get(entity);
        if (entry == null) {
            return 0f;
        }
        float previous = entry.lastAgeInTicks;
        boolean hadPrevious = entry.hasLastAge;
        entry.lastAgeInTicks = ageInTicks;
        entry.hasLastAge = true;
        if (!hadPrevious) {
            return 0f;
        }
        float deltaTicks = Math.max(0f, ageInTicks - previous);
        return Math.min(deltaTicks / TICKS_PER_SECOND, MAX_STEP_SECONDS);
    }

    private static final float TICKS_PER_SECOND = 20f;
    private static final float MAX_STEP_SECONDS = 0.25f;

    private static final class Entry {
        private final ModelInstance instance;
        private final ModelDefinition builtFor;
        private float lastAgeInTicks;
        private boolean hasLastAge;

        private Entry(ModelInstance instance, ModelDefinition builtFor) {
            this.instance = instance;
            this.builtFor = builtFor;
        }
    }
}
