package com.dimalab.storymodengine.common.concurrent;

import com.dimalab.storymodengine.api.concurrent.ExecutorKind;
import com.dimalab.storymodengine.api.concurrent.TaskState;

/** An immutable debug snapshot of one {@code AsyncTask} — mirrors {@code flow.node.NodeSnapshot}'s role, one layer up. Timestamps are {@code null} until reached; {@code null} is a real, meaningful value here (not yet started / not yet finished), not an omission. */
public record TaskInfo(
        long id,
        String name,
        ExecutorKind kind,
        TaskState state,
        Long parentId,
        long createdAtNanos,
        Long startedAtNanos,
        Long finishedAtNanos,
        String errorSummary
) {
    /** Elapsed milliseconds since this task started running — {@code -1} if it hasn't started yet. Uses "now" if it hasn't finished. */
    public long durationMillis() {
        if (startedAtNanos == null) {
            return -1;
        }
        long end = finishedAtNanos != null ? finishedAtNanos : System.nanoTime();
        return (end - startedAtNanos) / 1_000_000;
    }
}
