package com.dimalab.storymodengine.common.flow;

import com.dimalab.storymodengine.api.flow.FlowRunState;
import com.dimalab.storymodengine.common.flow.node.NodeSnapshot;
import com.dimalab.storymodengine.common.flow.node.NodeState;
import com.dimalab.storymodengine.common.network.serialization.Serializer;
import com.dimalab.storymodengine.common.network.serialization.SerializerRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The serializable snapshot of one {@link FlowInstance} — deliberately just enough to restore
 * execution, never the live {@code Node} graph itself (a runtime's own tree is discarded and
 * rebuilt fresh from {@link Flow#instantiate()} on restore, then replayed via {@link
 * #snapshots} — see {@code FlowRuntime#resume}). A plain mutable POJO, the same shape {@code
 * StoryPlayerData} already uses, so it flows through the existing {@code SerializerRegistry}/
 * {@code PojoSerializer} path with zero new serialization code: {@link ResourceLocation} and
 * {@code enum} already have direct serializers, and {@code List<Integer>}/{@code List<NodeSnapshot>}
 * already resolve through the existing generic-collection support — {@link NodeSnapshot} itself
 * deliberately has no {@code NodeSnapshot}-typed field (see its own Javadoc for why a truly
 * self-referential POJO can't be resolved by {@code PojoSerializer} as it stands).
 *
 * <p>{@link #rootState} (the root {@code Node}'s own {@code NodeState}) and {@link #runState} (the
 * owning {@link FlowInstance}'s {@link FlowRunState}) are deliberately two separate fields — the
 * task's own requirement that node-level and flow-level state stay independent, made concrete: a
 * {@code Flow} can be {@code PAUSED} while its root node is still internally {@code RUNNING}.
 *
 * <p><b>Persistence format &amp; versioning</b>: {@link #registerSerializer()} installs a
 * hand-written {@code Serializer<FlowState>} (in place of the automatic {@code PojoSerializer}
 * reflection every other capability POJO uses) whose very first written value is
 * {@link #CURRENT_VERSION} — see that method's Javadoc for why a version marker needs a custom
 * serializer rather than just another field, and for exactly what this does and doesn't fix. In
 * short: this shape (snapshots + runState) already once replaced an earlier, simpler single-path
 * field, and that transition reset any flow saved under the old layout rather than restoring it —
 * {@code CapabilityStorage}'s per-descriptor try/catch caught the decode failure, so the game never
 * crashed, but the reset itself was incidental (whichever field happened to misread first). From
 * this version marker onward, a future shape change is instead caught immediately and deliberately,
 * with a clear log line naming the mismatch — still a reset, not a migration (a positional, untagged
 * binary format can't recover an old shape's data without knowing it in advance), but a diagnosable
 * one instead of an accidental one.
 */
public final class FlowState {

    /** Bumped whenever this class's serialized shape changes — see {@link #registerSerializer()}. */
    public static final int CURRENT_VERSION = 1;

    public ResourceLocation flowId;
    public FlowRunState runState = FlowRunState.NOT_STARTED;
    public NodeState rootState = NodeState.NOT_STARTED;
    public List<NodeSnapshot> snapshots = new ArrayList<>();

    public FlowState() {
    }

    public FlowState(ResourceLocation flowId, FlowRunState runState, NodeState rootState, List<NodeSnapshot> snapshots) {
        this.flowId = flowId;
        this.runState = runState;
        this.rootState = rootState;
        this.snapshots = snapshots;
    }

    /**
     * Registers this class's own {@code Serializer} directly with {@link SerializerRegistry},
     * bypassing the reflective {@code PojoSerializer} path — that's what makes it possible to write
     * {@link #CURRENT_VERSION} as a version-gate ahead of the real fields, and to reject a mismatch
     * outright on read instead of only ever discovering incompatibility by accident partway through
     * decoding some field.
     *
     * <p>Must run before {@code CapabilityBootstrap} resolves {@code StoryFlowData}'s
     * {@code Map<String, FlowState>} field — a {@code CapabilityDescriptor}'s serializer chain is
     * built once and never re-resolved, so registering a replacement afterwards would be too late.
     * {@code EngineBootstrap} calls this first, explicitly ahead of {@code CapabilityBootstrap.init},
     * for exactly that reason — see its Javadoc.
     */
    public static void registerSerializer() {
        Serializer<Object> flowIdSerializer = SerializerRegistry.resolve(ResourceLocation.class);
        Serializer<Object> runStateSerializer = SerializerRegistry.resolve(FlowRunState.class);
        Serializer<Object> rootStateSerializer = SerializerRegistry.resolve(NodeState.class);
        Serializer<Object> snapshotSerializer = SerializerRegistry.resolve(NodeSnapshot.class);

        SerializerRegistry.register(FlowState.class, Serializer.of(
                (FlowState value, FriendlyByteBuf buf) -> {
                    buf.writeVarInt(CURRENT_VERSION);
                    flowIdSerializer.write(value.flowId, buf);
                    runStateSerializer.write(value.runState, buf);
                    rootStateSerializer.write(value.rootState, buf);
                    buf.writeVarInt(value.snapshots.size());
                    for (NodeSnapshot snapshot : value.snapshots) {
                        snapshotSerializer.write(snapshot, buf);
                    }
                },
                buf -> {
                    int version = buf.readVarInt();
                    if (version != CURRENT_VERSION) {
                        throw new IllegalStateException("FlowState format version mismatch: found "
                                + version + ", engine expects " + CURRENT_VERSION + " — discarding");
                    }
                    FlowState state = new FlowState();
                    state.flowId = (ResourceLocation) flowIdSerializer.read(buf);
                    state.runState = (FlowRunState) runStateSerializer.read(buf);
                    state.rootState = (NodeState) rootStateSerializer.read(buf);
                    int size = buf.readVarInt();
                    if (size < 0 || size > SerializerRegistry.MAX_COLLECTION_SIZE) {
                        throw new IllegalArgumentException("Refusing to decode " + size
                                + " FlowState snapshot(s) (max " + SerializerRegistry.MAX_COLLECTION_SIZE + ")");
                    }
                    List<NodeSnapshot> snapshots = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        snapshots.add((NodeSnapshot) snapshotSerializer.read(buf));
                    }
                    state.snapshots = snapshots;
                    return state;
                }));
    }
}
