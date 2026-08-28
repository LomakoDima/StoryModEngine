package com.dimalab.storymodengine.api.concurrent;

/** An {@code AsyncTask}'s lifecycle — mirrors {@code api.flow.FlowRunState}'s shape for the same kind of thing one level up. */
public enum TaskState {
    /** Created, not yet running (still queued, or waiting on a delay/timer). */
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
