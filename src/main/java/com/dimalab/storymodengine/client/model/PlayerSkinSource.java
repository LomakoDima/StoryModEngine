package com.dimalab.storymodengine.client.model;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A real player's skin texture, by name or UUID — ported from HollowEngine's own
 * {@code MaterialSources.kt}, adapted to the actual 1.20.1 API: HE's {@code
 * SkullBlockEntity.fetchGameProfile} (returning a {@code CompletableFuture}) doesn't exist in this
 * version — verified against the decompiled sources, not assumed — the real equivalent here is the
 * callback-based {@code SkullBlockEntity.updateGameprofile(GameProfile, Consumer)}.
 *
 * <p>Same fallback order HE uses:
 * <ol>
 *   <li>The live multiplayer player list ({@code ClientPacketListener.getPlayerInfo}) — if this
 *       player is actually connected, its already-loaded skin is used immediately, synchronously,
 *       no lookup needed.</li>
 *   <li>A cached result from a previous asynchronous lookup.</li>
 *   <li>Otherwise, kick off {@code SkullBlockEntity.updateGameprofile} (the same machinery a player-
 *       head block uses to resolve an owner's skin) → {@code SkinManager.registerSkins}, and return
 *       vanilla's default (Steve/Alex-by-UUID-hash) skin for <i>this</i> call — the real texture
 *       becomes available once the lookup completes, on a later frame.</li>
 * </ol>
 *
 * <p>Cached forever once resolved, by the raw input string, deliberately never evicted — the same
 * choice HE makes; a name that stops resolving just keeps returning the default skin rather than
 * retrying every frame. {@link #PENDING} is what stops a still-unresolved name from firing a new
 * lookup on every single call before that.
 */
public final class PlayerSkinSource {

    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    /**
     * Temporary debug aid: last {@code (nameOrUuid, resolved location)} logged and when, so {@link #textureFor}
     * — called every frame a "skin" material is drawn — doesn't spam once a frame. Re-logs on an actual change
     * OR once {@link #DEBUG_RELOG_INTERVAL_MS} has passed, specifically so a second {@code /sme npc
     * skin} test later in the <em>same</em> game session (same name, same resolved location) still produces a
     * fresh log line instead of being silently suppressed by the first test's now-stale entry — this class has
     * no safe way to be told "a new command just ran" directly, since the command itself executes in common
     * (server-capable) code that must not reference a client-only class like this one. Remove all of this once
     * the face-artifact investigation is closed.
     */
    private static String lastLoggedNameOrUuid;
    private static ResourceLocation lastLoggedLocation;
    private static long lastLoggedAtMillis;
    private static final long DEBUG_RELOG_INTERVAL_MS = 3000;

    private PlayerSkinSource() {
    }

    /** @param nameOrUuid a player's name or UUID (either textual form); never null/empty — callers only reach here once a "skin" material has one configured. */
    public static ResourceLocation textureFor(String nameOrUuid) {
        UUID uuid = tryParseUuid(nameOrUuid);

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            PlayerInfo info = uuid != null ? connection.getPlayerInfo(uuid) : connection.getPlayerInfo(nameOrUuid);
            if (info != null) {
                return logResolution(nameOrUuid, info.getSkinLocation(), "live-connection");
            }
        }

        ResourceLocation cached = CACHE.get(nameOrUuid);
        if (cached != null) {
            return logResolution(nameOrUuid, cached, "async-cache");
        }

        requestAsync(nameOrUuid, uuid);
        return logResolution(nameOrUuid, DefaultPlayerSkin.getDefaultSkin(uuid != null ? uuid : offlineUuid(nameOrUuid)), "default-fallback");
    }

    /** Temporary debug aid — logs which resolution path fired and the resulting {@link ResourceLocation}; see {@link #DEBUG_RELOG_INTERVAL_MS} for why this isn't a plain "only on change" guard. Remove once the face-artifact investigation is closed. */
    private static ResourceLocation logResolution(String nameOrUuid, ResourceLocation location, String path) {
        long now = System.currentTimeMillis();
        boolean unchanged = nameOrUuid.equals(lastLoggedNameOrUuid) && location.equals(lastLoggedLocation);
        if (unchanged && now - lastLoggedAtMillis < DEBUG_RELOG_INTERVAL_MS) {
            return location;
        }
        lastLoggedNameOrUuid = nameOrUuid;
        lastLoggedLocation = location;
        lastLoggedAtMillis = now;
        EngineLog.channel("Model").info("[skin-debug] textureFor('{}') -> path={} location={}", nameOrUuid, path, location);
        return location;
    }

    private static void requestAsync(String nameOrUuid, UUID uuid) {
        if (!PENDING.add(nameOrUuid)) {
            return;
        }
        GameProfile partial = uuid != null ? new GameProfile(uuid, "") : new GameProfile(Util.NIL_UUID, nameOrUuid);
        SkullBlockEntity.updateGameprofile(partial, profile ->
                Minecraft.getInstance().getSkinManager().registerSkins(profile, (type, location, texture) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        CACHE.put(nameOrUuid, location);
                    }
                }, false));
    }

    private static UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Vanilla's own convention for a name with no real account behind it, so the default skin at least stays stable for that name. */
    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
