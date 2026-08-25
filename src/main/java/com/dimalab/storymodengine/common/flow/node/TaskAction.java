package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.flow.Result;
import com.dimalab.storymodengine.common.flow.Task;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.function.Supplier;

/**
 * Bridges a {@link Task} into the node lifecycle — ticks it every {@link #onTick} and translates
 * {@link Result} into {@link NodeState}. A fresh {@link Task} is built per instantiation (via the
 * supplied factory), matching every other node's "definition is a factory, not a shared instance"
 * rule. Built via {@code Flow.task(Supplier)}. No real multi-tick task is implemented anywhere in
 * this engine yet — this is only the bridge future systems (animation, movement, scripted
 * interactions) can build their own {@link Task} implementations against.
 */
public final class TaskAction extends Node {

    private final Supplier<Task> factory;
    private Task task;

    public TaskAction(Supplier<Task> factory) {
        this.factory = factory;
    }

    @Override
    protected void onStart(FlowContext context) {
        task = factory.get();
        applyResult(tickTask(context));
    }

    @Override
    protected void onTick(FlowContext context) {
        applyResult(tickTask(context));
    }

    @Override
    protected void onCancel(FlowContext context) {
        if (task != null) {
            task.cancel(context);
        }
    }

    private Result tickTask(FlowContext context) {
        try {
            return task.tick(context);
        } catch (Exception e) {
            EngineLog.channel("Flow").error("Task threw, treating as failure: {}", e.toString());
            return Result.FAILURE;
        }
    }

    private void applyResult(Result result) {
        switch (result) {
            case SUCCESS -> complete();
            case FAILURE, CANCELLED -> fail();
            case RUNNING -> {
                // stay RUNNING — nothing to do this tick
            }
        }
    }
}
