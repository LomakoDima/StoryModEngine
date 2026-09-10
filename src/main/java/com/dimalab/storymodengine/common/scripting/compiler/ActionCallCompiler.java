package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.scripting.ast.ActionCallStmtNode;
import com.dimalab.storymodengine.common.scripting.ast.ExprNode;
import com.dimalab.storymodengine.common.scripting.ast.LiteralExprNode;
import com.dimalab.storymodengine.common.scripting.ast.RefExprNode;
import com.dimalab.storymodengine.common.scripting.command.ArgumentCoercion;
import com.dimalab.storymodengine.common.scripting.command.StoryCommandRegistry;
import com.dimalab.storymodengine.common.scripting.command.StoryCommandRegistry.CommandHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code command_name arg...} → an invocation of {@code StoryCommandRegistry.invoke}. Every
 * argument in this grammar is a compile-time constant (a literal, or a bareword {@link RefExprNode}
 * — always treated as a literal string, never a variable read; see the grammar's own {@code arg}
 * production), so coercion happens once here at compile time, not per-call.
 */
public final class ActionCallCompiler {

    private ActionCallCompiler() {
    }

    public static Consumer<FlowContext> compile(ActionCallStmtNode call) {
        var handle = StoryCommandRegistry.get(call.commandName());
        if (handle == null) {
            EngineLog.channel("SME").error("ActionCallCompiler: unknown command '{}' (should have failed validation)", call.commandName());
            return fc -> {
            };
        }
        List<Object> coerced = coerceArgs(call, handle);
        String name = call.commandName();
        return fc -> StoryCommandRegistry.invoke(name, fc.player(), coerced);
    }

    /** Split out of {@link #compile} so {@code SequenceCompiler} can build the same coerced argument list for a command it compiles specially (see {@code compileAwaitableMoveTo}) instead of duplicating this loop. */
    public static List<Object> coerceArgs(ActionCallStmtNode call, CommandHandle handle) {
        List<Object> coerced = new ArrayList<>(call.args().size());
        for (int i = 0; i < call.args().size(); i++) {
            Object raw = rawValue(call.args().get(i));
            coerced.add(ArgumentCoercion.coerce(raw, handle.paramTypes().get(i)));
        }
        return coerced;
    }

    private static Object rawValue(ExprNode expr) {
        if (expr instanceof LiteralExprNode lit) {
            return lit.value();
        }
        if (expr instanceof RefExprNode ref) {
            return ref.id();
        }
        throw new IllegalStateException("Unexpected action-call argument expression: " + expr);
    }
}
