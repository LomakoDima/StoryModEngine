package com.dimalab.storymodengine.client.network.context;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * Isolates the one client-only field access {@link PacketContext#level()} needs
 * ({@code Minecraft.getInstance().level}, whose declared type is {@code ClientLevel}) into its own
 * class, so {@link PacketContext} itself — loaded unconditionally on both sides, for every packet,
 * with no {@code Dist} gate — never has a direct reference to a client-only type in its own
 * bytecode. Verified live: inlining this access directly into {@code PacketContext.level()} broke
 * every packet actually processed server-side, silently — Forge's {@code RuntimeDistCleaner} scans
 * a class's bytecode for {@code @OnlyIn}-mismatched type references at the moment that class
 * *loads*, not when the specific referencing branch runs, so the guard has to be "this class is
 * never loaded on the wrong side" (the same fix already used for {@code AutoParticleRegistration}),
 * not "this branch never executes on the wrong side". {@link PacketContext#level()} only ever
 * calls {@link #get()} behind its own {@code isClient()} check, so this class is only ever loaded
 * — and only ever has its own bytecode scanned — on the client. {@code public} only because the
 * api/client/common split now puts it in a different package than {@link PacketContext} — visibility
 * has no bearing on the dist-safety property above, which comes from lazy class-loading, not access
 * modifiers.
 */
public final class ClientLevelLookup {

    private ClientLevelLookup() {
    }

    public static Level get() {
        return Minecraft.getInstance().level;
    }
}
