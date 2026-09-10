package com.dimalab.storymodengine.common.scripting.validation;

import com.dimalab.storymodengine.common.scripting.ast.*;

import java.util.*;

/**
 * Non-blocking ({@link com.dimalab.storymodengine.common.scripting.diagnostics.Severity#WARNING})
 * checks only — the hard "does this jump target exist at all" check already lives in {@link
 * ReferenceResolutionPass} (an {@code ERROR}, since it would misbehave at runtime, not just waste
 * content). This pass finds a dialogue node no reachable path ever jumps to, and a choice whose
 * {@code when} condition is a literal {@code false} (so it can never actually be offered).
 */
public final class UnreachablePass {

    private UnreachablePass() {
    }

    public static void run(ValidationContext ctx) {
        for (SmeNode decl : ctx.story().declarations()) {
            if (decl instanceof DialogueDeclNode d) {
                checkReachability(ctx, d);
                checkDeadChoices(ctx, d);
            }
        }
    }

    private static void checkReachability(ValidationContext ctx, DialogueDeclNode d) {
        if (d.nodes().isEmpty()) {
            return;
        }
        Map<String, DialogueNodeNode> byId = new HashMap<>();
        for (DialogueNodeNode n : d.nodes()) {
            byId.put(n.nodeId(), n);
        }

        Set<String> reachable = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        String startId = d.nodes().get(0).nodeId();
        queue.add(startId);
        reachable.add(startId);
        while (!queue.isEmpty()) {
            DialogueNodeNode node = byId.get(queue.poll());
            if (node == null) {
                continue;
            }
            for (String target : outgoingJumps(node)) {
                if (byId.containsKey(target) && reachable.add(target)) {
                    queue.add(target);
                }
            }
        }

        for (DialogueNodeNode node : d.nodes()) {
            if (!reachable.contains(node.nodeId())) {
                ctx.warn(node.pos(), "SME500", "Dialogue node '" + node.nodeId() + "' in '" + d.id() + "' is never reached by any jump");
            }
        }
    }

    private static List<String> outgoingJumps(DialogueNodeNode node) {
        List<String> targets = new ArrayList<>();
        for (DialogueEntryNode entry : node.entries()) {
            if (entry instanceof JumpEntryNode j) {
                targets.add(j.targetNodeId());
            } else if (entry instanceof ChoiceGroupNode group) {
                for (ChoiceNode choice : group.options()) {
                    trailingJumpTarget(choice.body()).ifPresent(targets::add);
                }
            }
        }
        return targets;
    }

    private static Optional<String> trailingJumpTarget(List<StmtNode> body) {
        if (!body.isEmpty() && body.get(body.size() - 1) instanceof JumpStmtNode j) {
            return Optional.of(j.targetId());
        }
        return Optional.empty();
    }

    private static void checkDeadChoices(ValidationContext ctx, DialogueDeclNode d) {
        for (DialogueNodeNode node : d.nodes()) {
            for (DialogueEntryNode entry : node.entries()) {
                if (!(entry instanceof ChoiceGroupNode group)) {
                    continue;
                }
                for (ChoiceNode choice : group.options()) {
                    if (choice.condition() instanceof LiteralExprNode lit
                            && lit.type() == SmeValueType.BOOL
                            && Boolean.FALSE.equals(lit.value())) {
                        ctx.warn(choice.pos(), "SME501", "Choice '" + choice.text() + "' can never be offered — its 'when' condition is always false");
                    }
                }
            }
        }
    }
}
