# Quest System — Design

Status: approved-by-construction per the task's own instruction ("после утверждения архитектуры не
жди ответа — реализуй, если архитектура очевидно совместима"). This document is written *before*
any quest code exists, based on the verified (source-read, not assumed) APIs of `flow`, `dialogue`,
`capabilities`, `event`, `network`, and the `@AutoFlow`/`@AutoDialogue` discovery pattern.

## 0. Core principle, restated precisely

```
QuestDefinition  (immutable data: id, title, objectives, rewards, prerequisites)
      │  QuestCompiler.compile(...)
      ▼
Flow             (existing engine — sequence/branch/waitForEvent/action, nothing new added to it
      │           except what's noted in §9)
      ▼
FlowManager      (existing runtime — ticks it, persists it, resumes it on relog)
```

Quest owns **zero** runtime state of its own beyond a small persisted progress record (§6) —
no quest-specific ticking, no quest-specific scheduler, no quest-specific "is this flow done yet"
polling loop. Everything that *executes* is a `Flow`; Quest only ever *describes* one.

## 1. What already exists and is reused as-is (verified by reading source, not assumed)

| Need | Existing mechanism | Verified in |
|---|---|---|
| Execution engine | `Flow.sequence/branch/action/condition/waitForEvent/lazy`, `FlowManager.start/tick/resumeAll` | `flow/Flow.java`, `flow/FlowManager.java` |
| Objective completion without polling the world | `Flow.waitForEvent(Class<E>, BiPredicate<FlowContext,E>)` — push-based, subscribes once, unsubscribes once | `flow/node/EventWaiter.java` |
| Dialogue objectives | `DialogueSystem.start`, `dialogue.event.DialogueCompletedEvent` — exact same "start X, `Flow.waitForEvent(CompletedEvent)`" shape `CinematicManager.playSequence` already uses for cutscenes | `dialogue/DialogueSystem.java`, `dialogue/event/*` |
| Discovery/registration | `@AutoFlow`/`@AutoDialogue`'s `ModFileScanData`-based static-field scan — copied verbatim for `@AutoQuest` | `dialogue/discovery/DialogueDiscovery.java` |
| Persistence | `@Capability` + `Capabilities.get/markDirty/sync`, `Optional<T>`-for-nullable convention | `capabilities/`, `cinematic/persistence/CutscenePlaybackData.java` |
| Client sync | `@Capability(sync = true)` + `Capabilities.sync(...)` → generic `CapabilitySyncPacket` — **no bespoke sync packet needed** (see §8) | `capabilities/Capabilities.java` |
| Real-world → engine event bridge | `event/bridge/MinecraftEventBridge.java` — the *exact* "translate a Forge event into our own `Event`, post via `Events.post`" mechanism the task asks for; extended in place, not duplicated | `event/bridge/MinecraftEventBridge.java` |
| JSON loading | `SimpleJsonResourceReloadListener`, registered via `AddReloadListenerEvent` in an `XyzJsonBootstrap` | `dialogue/json/DialogueJsonLoader.java` |
| Networking | `@Packet` record + `PacketHandler`, auto-discovered — used only where genuinely needed (§8) | `network/` |

## 2. Package layout

```
quest/
  Quest.java                    -- Quest.define(id) entry point, mirrors CutsceneDefinition.define(id)
  QuestDefinition.java           -- immutable: id, title, description, prerequisites, objectives, rewards, steps
  QuestStep.java                 -- package-private: Flow compile(ResourceLocation questId) — Objective and
                                     BranchStep both implement this; keeps §7 branching out of Objective's contract
  QuestState.java                 -- enum ACTIVE, COMPLETED, FAILED (NOT_STARTED = absent from progress map)
  ObjectiveState.java             -- enum INACTIVE, ACTIVE, COMPLETED, FAILED
  QuestProgress.java              -- immutable record: state, Map<String,Integer> objectiveProgress,
                                      Map<String,ObjectiveState> objectiveStates (whole-record replace on
                                      change — same idiom CutscenePlaybackData uses, not in-place field mutation)
  QuestCompiler.java               -- QuestDefinition -> Flow
  QuestSystem.java                 -- the public runtime facade (§10)
  QuestRegistry.java                -- ConcurrentHashMap<ResourceLocation, QuestDefinition>
  event/
    QuestStartedEvent.java / QuestObjectiveProgressedEvent.java / QuestCompletedEvent.java / QuestFailedEvent.java
  annotation/
    AutoQuest.java
  discovery/
    QuestDiscovery.java            -- byte-for-byte DialogueDiscovery's shape, targeting QuestDefinition fields
  objective/
    Objective.java                  -- the extension point: id(), description(), requiredCount(), compile(questId)
                                        + static factories (kill/collect/talkTo/interact/location/findEntity/
                                        dialogue/quest/event) — same "interface doubles as factory holder"
                                        shape dialogue.DialogueCommand already uses
    ObjectiveSupport.java            -- shared compile() boilerplate (activate/increment/complete), so each
                                        concrete Objective is ~15 lines, not a copy-pasted Flow.sequence
    KillObjective.java / CollectObjective.java / TalkToObjective.java / InteractObjective.java /
    LocationObjective.java / FindEntityObjective.java / EventObjective.java / DialogueObjective.java /
    QuestCompletionObjective.java
  reward/
    Reward.java                     -- extension point + static factories (item/experience/command/unlockQuest)
  bridgeevent/
    EntityKilledEvent.java / ItemCollectedEvent.java / EntityInteractionEvent.java / BlockInteractionEvent.java
    (posted by an *extension* to the existing event/bridge/MinecraftEventBridge.java — see §5)
  persistence/
    QuestProgressData.java (the @Capability class) / ModQuestCapabilities.java / QuestProgressStore.java
  json/
    QuestJsonLoader.java / QuestJsonBootstrap.java / QuestObjectiveJsonParsers.java / QuestRewardJsonParsers.java
  client/
    QuestToast.java (chat/actionbar feedback) / QuestTrackerOverlay.java (minimal read-only HUD line)
  example/
    QuestExamples.java (2+ `@AutoQuest` demo quests) / QuestDemoCommand.java (`/quest ...` debug commands)
  debug/
    QuestCompilerTestCommand.java  -- JVM-level assertions on QuestCompiler + QuestProgress, reported to
                                       chat/log; this project has no JUnit setup (verified: no src/test, no
                                       junit in build.gradle) — this is the same substitution
                                       `EditorRoundTripDebugCommand` already established this session
  QuestBootstrap.java
```

Naming note: the task's §16 example writes `implements ObjectiveType<...>`. This design uses
`implements Objective` instead — mirroring `DialogueCommand`, which is both the interface *and* its
own static-factory holder (`DialogueCommand.of(...)`, `.playCutscene(...)`), the established
in-repo convention for exactly this "small pluggable behavior + a few built-in factories" shape.
One interface, one file, matches `Objective.kill(...)` reading as calling a static method on the
same type a custom implementation implements. Flagged as a deliberate naming deviation, not an
oversight — the task's own preamble says not to copy the examples blindly.

## 3. Objective — the extension point (§16)

```java
public interface Objective {
    String id();                 // explicit or auto "objective_N", same pattern as DialogueChoice's id
    String description();
    int requiredCount();         // default 1 via a default method
    boolean supportsEasing... 	  // N/A, not applicable here — see ObjectiveState below instead

    /** Compiles to "mark ACTIVE, wait until satisfied, mark COMPLETED" — see ObjectiveSupport. */
    Flow compile(ResourceLocation questId);
}
```

**No giant switch anywhere.** `QuestCompiler` never inspects *which* objective type it has — it
just calls `objective.compile(questId)` polymorphically. A third-party `DefeatBossObjective
implements Objective` is a first-class objective with zero changes to `QuestCompiler`, exactly
per §16.

`ObjectiveSupport.waitFor(ResourceLocation questId, Objective self, Class<E> eventType,
BiPredicate<ServerPlayer,E> matches, ToIntFunction<E> progressDelta)` is the one shared helper every
event-driven objective calls from its own `compile()` — it builds:
```java
Flow.sequence(
    Flow.action(ctx -> QuestProgressStore.setObjectiveState(ctx.player(), questId, self.id(), ACTIVE)),
    Flow.waitForEvent(eventType, (ctx, event) -> {
        if (!matches.test(ctx.player(), event)) return false;                 // not for this objective/player
        int total = QuestProgressStore.incrementObjective(ctx.player(), questId, self.id(),
                progressDelta.applyAsInt(event), self.requiredCount());
        return total >= self.requiredCount();                                  // false = keep listening
    }),
    Flow.action(ctx -> QuestProgressStore.setObjectiveState(ctx.player(), questId, self.id(), COMPLETED))
);
```
This is the *entire* mechanism for kill/collect/interact/talkTo/dialogue/quest-completion
objectives — each concrete class just supplies `eventType`/`matches`/(usually)`progressDelta = e ->
1`. This is why `EventWaiter`'s "false keeps listening, true unsubscribes" contract (verified from
source, §1) is load-bearing: progress increments as a side effect of a filter that keeps returning
`false` until the count is reached, with **zero polling**.

`LocationObjective`/`FindEntityObjective` have no natural Minecraft event to hook (there is no
"player moved" event worth posting globally — see §5's own reasoning for why that was rejected).
They instead use a *self-contained, per-objective* poll — via `Flow.lazy` recursion, not a new Flow
primitive:
```java
private static Flow pollUntil(ResourceLocation questId, Objective self, BooleanSupplier reached) {
    return Flow.lazy(() -> Flow.sequence(
        Flow.wait(CHECK_INTERVAL_TICKS),   // 20 ticks = 1s, existing primitive
        Flow.branch(ctx -> reached.getAsBoolean(),
                markComplete(questId, self),
                pollUntil(questId, self, reached))   // recurse — Flow.lazy defers construction, so this
    ));                                                // doesn't blow the stack building an infinite Flow
}
```
This only exists while *that specific objective on that specific quest instance* is active — not a
global per-tick scan of every player, which is the actual thing "не сканируй мир без необходимости"
warns against. Documented in `LocationObjective`'s own Javadoc as a deliberate choice over a global
`PlayerLocationEvent` broadcast.

`EventObjective` (§2's "trigger event" / custom escape hatch) is a thin direct wrapper over
`ObjectiveSupport.waitFor` exposing `Class<E>`/`BiPredicate<ServerPlayer,E>` to the mod author
directly — for anything the 8 built-ins don't cover, without needing a whole new `Objective` class.

## 4. QuestStep and branching (§8)

```java
interface QuestStep { Flow compile(ResourceLocation questId); }   // package-private, Objective implements it too
```
`QuestDefinition.Builder.branch(Evaluator<Boolean> condition, Objective ifTrue, Objective ifFalse)`
appends a `BranchStep(condition, ifTrue, ifFalse)` whose `compile()` is exactly
`Flow.branch(condition, ifTrue.compile(questId), ifFalse.compile(questId))` — `Evaluator<Boolean>`
is the *same* `flow.Evaluator` type `Dialogue`'s `.condition(...)`/`.gate(...)` already use, so a
mod author who's used Dialogue's branching already knows this API. `QuestDefinition.objectives()`
(the public getter, for UI/tracker display) stays a flat `List<Objective>` — `BranchStep`'s two
objectives are still discoverable there (both branches are real objectives that might complete),
but the *step* sequencing (which one, dictated by `condition`) is compiler-internal. No new
"Quest branching engine" — this is `Flow.branch` with a two-line adapter.

## 5. Event bridging (§5) — extending, not duplicating

`event/bridge/MinecraftEventBridge.java` already exists for exactly this purpose (Forge event →
this engine's own `Event`, posted via `Events.post`) — used today for player connect/disconnect.
This design adds four `@SubscribeEvent` methods to that **same class** (not a new bridge, not a
second `EventBus`):

| Forge event | New engine event | Objective(s) it feeds |
|---|---|---|
| `LivingDeathEvent` (killer is `ServerPlayer`) | `EntityKilledEvent(ServerPlayer, ResourceLocation entityTypeId)` | `Objective.kill` |
| `EntityItemPickupEvent` | `ItemCollectedEvent(ServerPlayer, ResourceLocation itemId, int count)` | `Objective.collect` |
| `PlayerInteractEvent.EntityInteract` | `EntityInteractionEvent(ServerPlayer, ResourceLocation entityTypeId)` | `Objective.talkTo` |
| `PlayerInteractEvent.RightClickBlock` | `BlockInteractionEvent(ServerPlayer, BlockPos, ResourceLocation blockId)` | `Objective.interact` |

Documented, deliberate scope limits (not silently missing): `Objective.collect` only counts items
picked up off the ground (`EntityItemPickupEvent`) — crafting/trading are not wired in this pass;
extending this is one more bridge method whenever needed. No `PlayerLocationEvent` bridge (see §3).

## 6. Persistence (§10)

```java
public record QuestProgress(QuestState state,
        Map<String, Integer> objectiveProgress, Map<String, ObjectiveState> objectiveStates) {}

public final class QuestProgressData implements EntityCapability {
    public Map<ResourceLocation, QuestProgress> quests = new HashMap<>();
}
```
`QuestProgress` is a record (immutable) nested inside a mutable capability field, replaced wholesale
on every change (`quests.put(id, new QuestProgress(...))`) — the *exact* pattern
`CutscenePlaybackData`/`PersistedCutscene` already establishes (record-holding-Maps nested in a
mutable POJO field, proven to round-trip through `SerializerRegistry` — verified by reading
`PersistedCutscene.java`, which nests `Map<String,Integer>` inside a record the same way). No new
serializer needed — `Map<K,V>`, records, and enums are all already generically supported (verified
in `SerializerRegistry`). `@Capability(sync = true)` (unlike `CutscenePlaybackData`, which
deliberately leaves it `false` — quest progress genuinely needs to reach the client for the tracker
HUD in §11, cutscene playback state doesn't).

`QuestProgressStore` is the small static facade (`get`/`setState`/`setObjectiveState`/
`incrementObjective`/`isCompleted`/`isActive`) every compiled `Flow.action` calls into — same shape
as `Capabilities` itself, just quest-specific and building new `QuestProgress` records rather than
exposing the raw map.

## 7. Quest chains and prerequisites (§7)

`QuestDefinition.Builder.prerequisite(ResourceLocation questId)` — consulted **only** by
`QuestSystem.start` (not compiled into the `Flow` at all): `start` checks
`QuestProgressStore.isCompleted(player, prereqId)` for every declared prerequisite and refuses
(logs + returns `null`, mirroring `FlowManager.start`'s own "null on failure" convention) if any is
unmet. This keeps a rejected start cheap (no `Flow` instance ever created) rather than compiling a
guard into the graph.

Forward-chaining ("on completing A, unlock B") is `Reward.unlockQuest(ResourceLocation)` — a reward
whose `compile()` is `Flow.action(ctx -> QuestSystem.start(ctx.player(), questId))`. This reuses the
already-required Reward mechanism (§9) instead of inventing a second "quest chain" concept —
a chain is just "the last reward starts the next quest."

## 8. Networking — what's actually needed (§11's own instruction: "проверь, что реально необходимо")

**Zero new packets for state propagation.** `@Capability(sync = true)` + `Capabilities.sync(player,
...)` already round-trips full `QuestProgressData` to the client via the existing generic
`CapabilitySyncPacket` (verified: `Capabilities.sync` resolves the descriptor's serializer and sends
via `Network.sendToPlayer` today, for any `@Capability` type — quest progress needs nothing bespoke
here). `QuestStateSyncPacket`/`QuestProgressPacket` from the task's own illustrative list are
therefore **not built** — they'd duplicate an existing generic mechanism.

**One narrow exception, discovered during implementation**: {@code QuestToast} (§11) needs to know
*when* something happened (to show a message at that moment), not just the current state —
capability sync only ever delivers "here is the full picture right now," which can't express a
one-shot notification, and {@code event.Events.post(...)} is an in-process pub/sub (verified: no
cross-side transport), so a server-posted `QuestObjectiveProgressedEvent` never reaches a client-side
listener on a real (non-singleplayer) server. `quest.network.QuestToastPacket(String message)`
(clientbound) is the one genuinely new packet this system adds — a small server-side listener
(`QuestNotificationBridge`) translates the four quest events into short player-facing text and sends
it; the packet's `handle()` only displays it, no state logic. Still zero *state-sync* packets, and
zero client-initiated ones.

**No client-initiated packet either**, for this pass: every quest start path in §12 (dialogue
command, server command, event listener, another quest's reward, automatic) is server-initiated —
nothing in the required demo scope needs a player to press a UI button that starts a quest over the
network. If a future "accept quest from an NPC" UI needs one, it's a two-line `@Packet record
RequestQuestStartPacket(ResourceLocation questId) implements ServerboundPacket, PacketHandler`
added the same way `DialogueChoicePacket` was — deliberately deferred, not silently missing.

## 9. Flow extension — none needed

Every primitive `flow/Flow.java` already exposes (`sequence`, `branch`, `action`, `waitForEvent`,
`lazy`, `wait`) is sufficient for every quest shape in this task, including the recursive polling
loop in §3. **No new `Flow` factory method is added.** This satisfies the task's own escalation
rule ("если Flow primitives недостаточно — сначала оцени расширение, а не второй runtime") by
concluding, after verifying the actual API, that no extension is needed at all.

## 10. `QuestSystem` — the public facade (§15, pruned to what's actually used)

```java
public static QuestHandle start(ServerPlayer player, ResourceLocation questId)   // null on failure (unregistered id, unmet prerequisite, already active) — mirrors FlowManager.start's own convention
public static void stop(ServerPlayer player, ResourceLocation questId)            // cancels the underlying Flow, marks FAILED
public static boolean isActive(ServerPlayer player, ResourceLocation questId)
public static boolean isCompleted(ServerPlayer player, ResourceLocation questId)
public static QuestProgress progress(ServerPlayer player, ResourceLocation questId)   // null if never started
```
**Not added**: `QuestSystem.complete(...)`/`QuestSystem.fail(...)` as *externally callable* methods
— per §11's own "клиент не может сам завершить quest" and this codebase's own server-authoritative
discipline, a quest completes/fails only as a natural consequence of its compiled `Flow` reaching
that point (objectives satisfied, or a `Flow.branch`/failure path taken) — exposing a bare
`complete(player, questId)` that skips straight to rewards would let *any* server-side caller (a
buggy command, a malicious mixin from another mod) short-circuit objectives entirely. The debug
command in §14 gets there a different way: it *fails* an active quest's underlying `FlowManager`
instance (via `FlowManager`'s existing cancel path, already safe/idempotent), it does not fabricate
completion.

## 11. Client UI (§17) — kept genuinely minimal

- **`QuestToast`** — chat/actionbar feedback on start/objective-progress/complete/fail, same
  `EngineLog.channel(...).toChat(player)` idiom used everywhere else in this engine. Real, ~40 lines.
- **`QuestTrackerOverlay`** — a read-only HUD line/list of active quest(s) + objective progress,
  reading the client-side synced `QuestProgressData` capability — mirrors the existing
  `cinematic.client.SubtitleOverlay`/`TitleCardOverlay` shape (a `RenderGuiEvent.Post` listener,
  no interactivity, no server calls). Real, ~60-80 lines.
- **`QuestScreen`** (a browsable, clickable quest log) — **deliberately not built this pass**. It's
  a full interactive `Screen` on the scale of the just-frozen cutscene editor, and the task
  explicitly says "не создавай огромную UI систему" — a toast + a read-only tracker line satisfies
  "minimal integration, state comes from network, UI decides nothing" without that scope. Documented
  here the same way `cinematic`'s own deferred features are documented elsewhere in this project —
  explicitly deferred, not silently dropped, trivial to add later by reading `QuestProgressData`
  the tracker overlay already syncs.

## 12. Quest start paths (§14) — all funnel through one method

Player action (a `DialogueCommand` calling `QuestSystem.start`), event (a quest-package listener
posting on some engine event), command (`/quest start <id>`), prerequisite completion
(`Reward.unlockQuest`), another quest (same), automatic (an `@AutoQuest` quest a mod author starts
from their own `PlayerConnectedEvent` listener, using existing infrastructure, not a Quest-specific
"auto start" flag) — every path is just "some server-side code calls `QuestSystem.start(player,
questId)`." No per-trigger-kind runtime.

## 13. JSON schema (§12)

```json
{
  "title": "Find the Rhino",
  "description": "Find the lost rhino.",
  "prerequisites": ["modid:intro_quest"],
  "objectives": [
    { "type": "talkTo", "entity": "modid:hunter" },
    { "type": "dialogue", "dialogue": "modid:hunter_intro" },
    { "type": "kill", "entity": "modid:rhino", "count": 1 },
    { "type": "collect", "item": "minecraft:diamond", "count": 3 }
  ],
  "rewards": [
    { "type": "item", "item": "minecraft:diamond", "count": 5 },
    { "type": "experience", "amount": 100 }
  ]
}
```
`type` is a string-keyed lookup into a `Map<String, ObjectiveJsonParser>` (`kill`/`collect`/
`talkTo`/`interact`/`location`/`findEntity`/`dialogue`/`quest`/`event`), **not a switch inside the
compiler** — `QuestObjectiveJsonParsers.register(String type, ObjectiveJsonParser parser)` lets a
mod author register a JSON shape for their own custom `Objective` too, same extensibility guarantee
as the Java DSL. Loaded via `QuestJsonLoader extends SimpleJsonResourceReloadListener` from
`data/<namespace>/storymodengine/quests/*.json`, registered in `QuestJsonBootstrap` via
`AddReloadListenerEvent` — byte-for-byte `DialogueJsonLoader`'s own shape.

## 14. Debug commands (§18)

`/storymodengine quest list|start <id>|stop <id>|progress <id>|test` — `test` runs the JVM-level
QuestCompiler/QuestProgress assertions described in §16 below and reports pass/fail to chat/log,
mirroring `/storymodengine cinematic editordebug roundtrip`'s own role as this project's stand-in
for real unit tests (confirmed: no `src/test`, no JUnit dependency in `build.gradle`).

## 15. Bootstrap ordering

```java
DialogueBootstrap.init();
QuestBootstrap.init();   // needs Flow (compilation), Dialogue (objectives may start one), Capability
                          // (persistence), Network, Event — all already up by this point
if (FMLEnvironment.dist == Dist.CLIENT) { ... }
```
Exactly where the research confirmed it must go — last common-side bootstrap call, after every
subsystem it depends on.

## 16. Verification plan

1. `./gradlew compileJava` at each implementation milestone (data model → compiler → events/bridge →
   persistence → dialogue integration → JSON → client UI → demos), same incremental discipline as
   the cutscene editor build.
2. `/storymodengine quest test` — JVM-level: `QuestCompiler.compile(...)` on a hand-built
   `QuestDefinition` produces a non-null `Flow` that `.instantiate()`s without throwing; a
   `QuestProgress` record's increment/state-transition helpers behave correctly in isolation
   (0/3 → 1/3 → ... → 3/3 → COMPLETED, never exceeding `requiredCount`).
3. `runClient`/`runServer` boot checks (no new crash reports) after every milestone, same discipline
   as every other subsystem built this session.
4. Live, in-game: start a demo quest, kill/collect/talk to satisfy objectives, confirm the toast +
   tracker line update, confirm rewards land, confirm progress survives a relog (persistence),
   confirm the tracker line is correct on a second client-side observer (sync).
