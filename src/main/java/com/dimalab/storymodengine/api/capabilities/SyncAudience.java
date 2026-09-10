package com.dimalab.storymodengine.api.capabilities;

/**
 * Who a {@code @Capability(sync = true)} value reaches, layered on top of {@code Capabilities.sync}'s
 * existing owner-type inference rather than replacing it — {@link #AUTO} reproduces today's exact
 * behavior (a {@code ServerPlayer} owner gets sent only to itself, anything else broadcasts to every
 * client tracking it) so every existing {@code @Capability} declaration keeps working unchanged.
 * {@link #OWNER} and {@link #TRACKING} are explicit overrides for the one real gap that inference
 * can't express on its own: a player-owned value that should broadcast to trackers instead of staying
 * player-private, or (matching HollowEngine's own {@code Sync.OWNER}) a value that should reach only
 * the owner even when the owner isn't a player — a no-op in that case, never a broadcast.
 */
public enum SyncAudience {
    /** Infer from owner type: {@code ServerPlayer} → that player only; anything else → everyone tracking it. */
    AUTO,
    /** Only the owner itself, and only if the owner is a {@code ServerPlayer} — silently skipped otherwise. */
    OWNER,
    /** Everyone currently tracking the owner entity — including a {@code ServerPlayer} owner, unlike {@link #AUTO}. */
    TRACKING
}
