package com.dimalab.storymodengine.api.concurrent;

/**
 * Which of the four logical pools a piece of async work runs on — see {@code
 * common.concurrent.Async}'s own doc for what each one is actually for. Deliberately just these
 * four; a mod author never picks a raw {@code Executor} or thread count directly.
 */
public enum ExecutorKind {
    /** CPU-bound work — the default for {@code Async.run}/{@code Async.supply}. */
    CPU,
    /** Potentially blocking I/O — file access, HTTP, database calls. */
    IO,
    /** Delay/scheduling bookkeeping only — never runs the actual task body. */
    SCHEDULED,
    /** The Minecraft server thread — the only place Minecraft/Forge API may be touched. */
    MAIN
}
