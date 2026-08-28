package com.dimalab.storymodengine.common.concurrent.executor;

import com.dimalab.storymodengine.api.concurrent.AsyncShutdownException;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.Executor;

/**
 * Marshals work onto the Minecraft server thread — generalizes {@code logging.LogEntry
 * #runOnServerThread} (verified byte-for-byte: {@code server.isSameThread() ? task.run() :
 * server.execute(task)}) into a reusable {@link Executor} usable from anywhere, not just chat
 * dispatch. Deliberately <b>not</b> built on {@code network.context.PacketContext#enqueue} — that
 * delegates to Forge's {@code NetworkEvent.Context#enqueueWork}, which needs a live packet context
 * and is unreachable from an arbitrary background thread. {@code MinecraftServer} itself already
 * {@code implements Executor} (via {@code ReentrantBlockableEventLoop}), so no packet is needed at
 * all — this is the more general primitive the codebase already had access to, just never named.
 */
public final class MainThreadDispatcher implements Executor {

    @Override
    public void execute(Runnable task) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new AsyncShutdownException("No server is running — cannot dispatch to the main thread");
        }
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }

    /** Whether the calling thread is the Minecraft server thread right now — {@code false} (not "unknown") if no server is running. */
    public boolean isMainThread() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread();
    }
}
