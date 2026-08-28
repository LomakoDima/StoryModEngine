package com.dimalab.storymodengine.common.concurrent;

import com.dimalab.storymodengine.api.concurrent.ExecutorKind;

import java.util.Map;

/** A point-in-time snapshot of everything {@code /storymodengine async stats} prints — see {@code AsyncDebug#stats()}. */
public record AsyncStats(
        Map<ExecutorKind, KindStats> byKind,
        int liveTasks,
        int resumeQueueDepth,
        int tickScheduleDepth,
        boolean debugHistoryEnabled,
        int debugHistorySize
) {
    /** Pool gauges are {@code 0} for {@code SCHEDULED}/{@code MAIN}, which have no {@code ThreadPoolExecutor} to inspect. */
    public record KindStats(
            long submitted, long completed, long failed, long cancelled, long rejected,
            int poolSize, int activeCount, int queuedCount
    ) {
    }
}
