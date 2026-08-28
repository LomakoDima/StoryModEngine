# Concurrency / Async System — Design

## 0. Core principle, restated precisely

StoryModEngine's gameplay stays exactly as deterministic and main-thread-oriented as it always was —
Flow, Dialogue, Quest, Trigger, Cinematic, and every touch of `Level`/`Entity`/`ServerPlayer`/
`BlockEntity` still run on the Minecraft server thread, in tick order, with no change to any of those
systems. `common.concurrent` adds a second thing entirely: a small, controlled place to run work that
*doesn't* need to be on that thread — CPU-bound computation, blocking I/O, timed/delayed work — with
exactly one door back in. It is infrastructure underneath the engine, not a second runtime beside it.

```
Minecraft Main Thread (Gameplay / Flow / Dialogue / Quest / Trigger / World State)
        │
        │  Async.cpu() / Async.io() / Async.delay() / Async.schedule()
        ▼
   ┌─────────┬─────────┬───────────┐
   │   CPU   │   IO    │ SCHEDULED │   (background executors — never touch Minecraft API)
   └─────────┴─────────┴───────────┘
        │
        │  completion (AsyncTask settles)
        ▼
Minecraft Main Thread (.thenMain / Async.main() / Flow.await's consumer)
```

## 1. Architecture

Five layers, each with one job:

| Layer | Package | Job |
|---|---|---|
| Vocabulary | `api.concurrent` | Pure types — `ExecutorKind`, `TaskState`, `AsyncResult`, `CancellationToken`, the three exceptions. Zero coupling, like `api.flow`. |
| Public surface | `common.concurrent` | `Async` (facade), `AsyncTask`/`AsyncScope` (future + handle + scope), `AsyncDebug`/`AsyncStats`/`AsyncRegistry`/`TaskInfo` (observability), `ConcurrencyBootstrap`. |
| Execution mechanism | `common.concurrent.executor` | `ExecutorFactory` (the Java 21 seam), `PlatformThreadExecutorFactory`, `AsyncExecutors` (owns the live pools), `MainThreadDispatcher`, `AsyncThreadFactory`. |
| Minecraft integration | `common.concurrent.integration`, `common.concurrent.schedule` | `AsyncLifecycle` (start/stop pools with the server), `ConcurrencyTickBridge` (the `Phase.START` drain), `FlowResumeQueue`, `TickScheduler` (Minecraft-tick scheduling). |
| Flow integration | `common.flow.node.AsyncNode` + two `Flow` factories | The one place Flow reaches into this layer. |

Everything else in the engine — Dialogue, Quest, Trigger, Cinematic — never imports `common.concurrent`
directly. If they eventually need background work, they go through `Flow.async`/`Flow.await`, exactly
like they already go through `Flow` for everything else. This is the same "one system extends the
next, nothing gets its own parallel runtime" shape the whole engine already follows.

## 2. Thread model

Four kinds of thread exist after this system starts:

1. **The Minecraft server thread** — one, pre-existing, unchanged. Runs every tick, every Flow node's
   `onTick`, every `EventBus.post`, every world/entity mutation.
2. **CPU pool threads** (`SME-Async-CPU-N`) — `max(2, cores − 1)` platform threads, daemon, for
   CPU-bound work.
3. **IO pool threads** (`SME-Async-IO-N`) — elastic, 0–64, daemon, for work that may block on
   something outside the JVM (disk, network, a database driver).
4. **One SCHEDULED thread** (`SME-Async-SCHEDULED-0`) — timekeeping only. It never runs a task body;
   it only ever hands a `Runnable` to CPU/IO/MAIN once its delay elapses. This is deliberate: a body
   that blocks would stall every other pending delay/schedule in the engine.

`MAIN` is not a thread pool — `Async.main()`/`.thenMain(...)` route through `MainThreadDispatcher`,
which is `server.isSameThread() ? task.run() : server.execute(task)`. This is the exact shape already
used by `common.logging.LogEntry#runOnServerThread` (verified by reading that file), generalized to
the whole engine rather than invented fresh.

## 3. Executors — exactly four, no fifth

`ExecutorKind` is `CPU | IO | SCHEDULED | MAIN`. `Async.cpu()`/`.io()`/`.scheduled()`/`.main()` each
return an `AsyncScope` bound to one kind. Nothing in this engine creates a fifth pool, a
per-subsystem pool, or reaches for `ForkJoinPool.commonPool()` — every internal `CompletableFuture`
continuation in this codebase names its executor explicitly (the "continuation-executor rule", §7 of
`ARCHITECTURE.md`'s concurrent section), specifically so nothing ever lands on that uncontrolled,
JVM-wide default pool.

`AsyncExecutors` owns the live pools for one server lifetime. It's instantiable, not purely static,
for two reasons: `AsyncLifecycle` creates a fresh one on every `ServerStartingEvent` (so an
integrated/single-player world reload gets real, live pools rather than reused dead ones — both
`ServerStartingEvent` and `ServerStoppingEvent` fire on the integrated server too), and
`AsyncExecutors.createForTest(factory)` lets `AsyncSelfTestCommand` exercise a real start/shutdown
cycle without ever touching the pools other code is actively using.

## 4. Cancellation

Cooperative only — this system never calls `Thread.interrupt()` on a worker thread. `CancellationToken`
is a pure interface (`isCancelled()`, `throwIfCancelled()`, `onCancel(Runnable)`, a `NONE` constant);
`CancellationSource` is the owner a caller creates and holds. Every `AsyncTask` has its own
`CancellationSource`; a `.thenApply`/`.thenAccept`/etc. stage's source is a *child* of its upstream's
(`createChild()`), so:

- Cancelling an upstream task cascades to every derived stage.
- Cancelling a derived stage never cancels its upstream (an upstream may be shared — `Async.parallel`/
  `Async.race` both hold references to the same input tasks).

`CancellationSource` uses a `synchronized` monitor around cancellation/registration, not the
originally-sketched lock-free `AtomicBoolean` + `CopyOnWriteArrayList` pair — a lock-free design has a
real race window where a callback registered the instant after `cancel()`'s CAS succeeds, but before
it finishes draining the callback list, is silently never invoked. This mirrors `EventBus`'s own
`structuralLock`: a monitor around the cold, infrequent structural path (register/cancel), while the
hot, frequent path (`isCancelled()`) stays a lock-free `volatile` read.

`AsyncTask.cancel()` calls `future.cancel(false)` on its own `CompletableFuture` — native JDK
cancellation semantics, so `isDone()`/`isCancelled()` work for free, and a background body that
finishes anyway after being cancelled finds `complete(...)` is a silent no-op (the future is already
resolved). A body observes cancellation itself only if it explicitly holds and checks a
`CancellationToken` (`Async.run(token, () -> { while (...) token.throwIfCancelled(); ... })`) — nothing
forces it to stop.

A derived stage (`.thenApply`/etc.) that observes its *upstream* being cancelled never gets a direct
`future.cancel(false)` call of its own — it settles through ordinary exceptional propagation, with a
`CancellationException` as the completing cause. `AsyncTask` treats that exactly like a direct cancel
(`isEffectivelyCancelled` checks both `future.isCancelled()` and "the (unwrapped) completing exception
is a `CancellationException`"), so `TaskState`/`onCancel`/`onComplete` are consistent regardless of
which of the two ways a task ended up cancelled. Found via `AsyncSelfTestCommand` actually failing
this exact check before the fix — worth remembering if this area is touched again.

`AsyncTask#timeout(Duration)` is the one place internal to this package that calls `cancel()` on
itself as a *side effect* of something else settling — which is exactly why its returned task does
**not** use `cancellation.createChild()` like every other derived stage does. A child would cascade
that internal self-cancel straight back onto the timeout's own result future, racing (and usually
beating) the `completeExceptionally(TaskTimeoutException)` call meant to resolve it — reporting a
plain cancellation instead of a timeout. It uses an independent, one-way-linked `CancellationSource`
instead (see that method's own Javadoc). Also found via the self-test actually failing.

## 5. Scheduling — two axes, never mixed

**Wall-clock** (`java.time.Duration`, genuinely new to this codebase — everywhere else uses `int`
ticks, e.g. `Timeout.ticks(int)`): `Async.delay(Duration, Runnable)`, `Async.schedule(Duration,
Callable)`, `Async.scheduleAtFixedRate(...)`, `Async.scheduleWithFixedDelay(...)`. All are timed by the
single SCHEDULED thread; the body always actually runs on CPU.

**Minecraft-tick** (`TickScheduler`): `Async.nextTick(...)`, `Async.afterTicks(int, Runnable)`,
`Async.everyTick(int, Runnable)`. Advanced by `ConcurrencyTickBridge` once per server tick, at
`Phase.START` — pauses exactly when the server does, and the body runs directly on the calling
(server) thread, no `MainThreadDispatcher` hand-off needed. For gameplay/story logic, this axis — not
wall-clock time — stays authoritative, matching how `Wait`/`Timeout` already count ticks, not seconds.

## 6. Main-thread rules — the one door back in

Background lambdas passed to `Async.cpu()`/`.io()`/`Async.run`/`Async.supply` receive no
`FlowContext`/`Level`/`Entity`/`ServerPlayer` — there is nothing Minecraft-shaped for them to
accidentally touch. The only ways back onto the main thread:

- `Async.main(Runnable)` / `Async.main().supply(...)`
- `AsyncTask#thenMain(Consumer)`
- `Flow.await(...)`'s `consumer`, which the engine itself guarantees runs on main (§7)

`AsyncTask#blockingGet(Duration)` — the one escape hatch that can genuinely deadlock a caller — throws
`IllegalStateException` immediately if called while `AsyncExecutors.current().main().isMainThread()`
is true. This is an enforced runtime invariant, not a Javadoc warning; `AsyncSelfTestCommand` asserts
it directly.

## 7. Flow integration

```java
public static <T> Flow async(Function<FlowContext, AsyncTask<T>> starter);
public static <T> Flow await(Function<FlowContext, AsyncTask<T>> starter, BiConsumer<FlowContext, T> consumer);
```

Both build an `AsyncNode<T>` — mirrors `EventWaiter`'s subscribe-in-`onStart`/release-in-`onCancel`
shape, but never calls `Node#complete()`/`fail()` from the thread the background task actually
completes on. `Node.state` is a plain, non-volatile field (verified by reading `Node.java`) —
`EventWaiter` gets away with writing it from inside an event callback only because every current
`Events.post(...)` caller happens to already be on the main thread; a genuine background thread must
not copy that pattern.

Instead, `AsyncNode` hands its completion to `FlowResumeQueue` (`offer(Runnable)`, callable from any
thread), which `ConcurrencyTickBridge` drains at `TickEvent.ServerTickEvent(Phase.START)` — strictly
*before* `FlowTickBridge`'s existing `Phase.END` handler runs `FlowManager.tick()`'s before/after-state
comparison. That ordering is load-bearing: it guarantees a background completion is always observed
within the same tick it's drained, with zero changes to `Flow`, `FlowManager`, `FlowRuntime`, or
`Node`. Every composite's `onTick` already unconditionally re-reads `current.state()` rather than
"did this tick's own call just change something," so an off-stack child completion (the same shape
`EventWaiter` already produces) is correctly noticed when `Flow.async`/`Flow.await` is nested inside a
`Sequence`/`Parallel`/etc., the normal case.

`Flow.async`'s `consumer` is `null`; `Flow.await`'s runs on the main thread, with the settled value,
immediately before the node completes — the one sanctioned place to write a background result into a
`Blackboard`/capability/anything Minecraft-shaped. Cancellation is a straight line, verified
end-to-end with zero changes to any existing Flow file: `FlowHandle.cancel()` → `FlowManager.cancel` →
`FlowInstance.cancel()` → `FlowRuntime.cancel()` → `root.cancel(ctx)` → composite `onCancel`
propagation (already correct today) → `AsyncNode.onCancel` → `AsyncTask.cancel()` →
`CancellationSource.cancel()`.

`AsyncNode` has no `onTick` and no `Timeout` field — a per-operation deadline belongs to the
`AsyncTask` itself (`.timeout(Duration)`), not the node. `Timeout` instances are mutable, and a `Flow`
definition is a single shared recipe instantiated per player (see `Flow`'s own Javadoc) — capturing
one `Timeout` in a `Flow.async` closure would share and corrupt it across every player's instance.

**Never register `Flow.async(...)`/`Flow.await(...)` as a bare, top-level Flow — always wrap it in
`Flow.sequence(...)`, even a sequence of one.** This was found the hard way, by `AsyncSelfTestCommand`
actually failing its own "flow completes" check: `FlowManager.tick()` re-reads a Flow's root state
fresh at the start of every tick rather than carrying the previous tick's observed state forward (a
real, pre-existing limitation, deliberately not touched by this pass — see "Explicitly out of scope"
in the original plan). A composite root correctly notices its child completing off-stack (every
composite's `onTick` unconditionally re-reads `current.state()`, and the composite's own state change
then happens *inside* `FlowManager.tick()`'s own call, where the before/after comparison can see it).
A bare-root `AsyncNode` has no composite watching it — its completion, drained at `Phase.START`,
happens *before* `FlowManager.tick()` even captures `before` for that tick, so `before` is already the
new (terminal) state and no transition is ever observed. In practice this costs nothing: a real
`Flow.await` step is virtually always one step among several anyway.

Restoring a flow parked on an `AsyncNode` simply re-`start()`s (the leaf default), relaunching the
background operation from scratch — an accepted limitation, the same shape `Action`/`Wait` already
carry for a node that was mid-flight when the world was saved.

## 8. Error handling

A background body's exception never reaches an executor's own uncaught-exception path silently:
`CompletableFuture.supplyAsync`-style completion already catches whatever the body throws and routes
it into the future's exceptional-completion path, which this engine always unwraps back to the
original exception instance (never a `CompletionException` wrapper) before handing it to
`onFailure`/`onComplete`. If nothing downstream ever attaches an observer, the *last* stage in a chain
logs the failure once, at `ERROR`, through `EngineLog.channel("Async")` — tracked per-task via a
`failureObserved` flag set the instant any `.onFailure`/`.onComplete`/`.thenX` is attached, so a
handled chain never double-logs and an unhandled one is never silently lost. A recurring
(`scheduleAtFixedRate`/`everyTick`) firing that throws is logged the same way and does not stop later
firings — one bad tick shouldn't end a repeating job. Nothing here can crash the Minecraft server: no
code path in this package lets an exception propagate out of a Forge event handler uncaught.

## 9. Lifecycle

`ConcurrencyBootstrap.init()` runs first in `EngineBootstrap.init(...)` — it only registers two Forge
listeners (`AsyncLifecycle`, `ConcurrencyTickBridge`) and creates nothing, so every other subsystem is
free to reference `Async` during its own bootstrap without caring about ordering.

The real pools are created on `ServerStartingEvent` and torn down on `ServerStoppingEvent` — both fire
on the integrated (single-player) server too, which is why pool creation isn't done once at
mod-construction time (that would leave a single-player world reload with dead executors). Shutdown:
stop accepting new work (`state → SHUTTING_DOWN`) → `scheduled.shutdownNow()` (it never holds user
work, so discarding it outright is correct and instant) → `cpu.shutdown()`/`io.shutdown()` → a bounded
`awaitTermination` → `shutdownNow()` for stragglers → clear `FlowResumeQueue`/`TickScheduler` → one
summary log line (`WARN` if anything had to be force-stopped — the thread-leak signal) → `TERMINATED`.
Registered via the same `AtomicBoolean REGISTERED` + `MinecraftForge.EVENT_BUS.register(Class)` idiom
already used identically by `FlowTickBridge`/`CinematicTickBridge`/`MinecraftEventBridge`/
`CapabilityLifecycle`/`TriggerTickBridge` — not `@Mod.EventBusSubscriber`, since engine code isn't tied
to one modid (the debug/example commands in `concurrent.debug`/`concurrent.example` *do* use
`@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)`, matching every other subsystem's own
demo/self-test commands, since those genuinely are this mod's own commands).

## 10. Java 17 implementation

`ExecutorFactory` (`createCpu()`/`createIo()`/`createScheduled()`/`describe()`) is the entire
abstraction between "what `Async` calls" and "what actually runs the work." `PlatformThreadExecutorFactory`
is the only implementation: a fixed `ThreadPoolExecutor` for CPU, an elastic 0–64 one for IO
(`SynchronousQueue`-backed, matching the classic cached-pool shape but with a real ceiling), and a
single-thread `ScheduledThreadPoolExecutor` for SCHEDULED. No Java 21 API is referenced anywhere in
this package — `AsyncExecutors` stores `ExecutorService`, not the more specific `ThreadPoolExecutor`,
specifically so a future backend that isn't one (see §11) still type-checks; `AsyncDebug` downcasts
defensively (`instanceof ThreadPoolExecutor`) only where it wants pool gauges for `/async stats`.

## 11. Future Java 21 / Virtual Thread backend

Adding Virtual Threads later means writing one new class, `VirtualThreadExecutorFactory implements
ExecutorFactory`, whose `createCpu()`/`createIo()` return `Executors.newVirtualThreadPerTaskExecutor()`
(or similar) — and changing which factory `AsyncLifecycle` constructs on `ServerStartingEvent`. Nothing
in `Async`, `AsyncTask`, `AsyncScope`, `Flow.async`/`Flow.await`, or any mod-author call site changes at
all. This is deliberately *only* a seam, not a half-built backend — no Java 21 type is imported or
referenced anywhere in this pass.

Worth restating for whoever picks this up: Virtual Threads are not "faster threads." They exist to let
a program hold open enormous numbers of threads that spend most of their life *blocked* (classic
example: one thread per network connection) cheaply. They would not speed up CPU-bound work (the CPU
pool gains nothing from them — a CPU-bound task is CPU-bound regardless of the thread underneath it),
and they must never back MAIN — the Minecraft server thread stays exactly what it always was, single
and platform.

## 12. Examples

See `common.concurrent.example.AsyncExamples` for the request's own usage patterns as real, compiled
call sites (basic run/supply, chaining, delay, background→main, main→background→main, observers,
cooperative cancellation, parallel/race/timeout, and a `Flow.await`-based quest-objective sketch).

## 13. Thread-safety rules

| Structure | Mechanism | Why |
|---|---|---|
| `AsyncRegistry`'s live-task map | `ConcurrentHashMap` | Matches the house default (`EventBus`, `FlowManager`'s own maps). |
| `AsyncRegistry`'s per-kind counters | `LongAdder` | Write-heavy, read-rarely — the standard JDK answer for exactly this. |
| `AsyncRegistry`'s bounded history | `ConcurrentLinkedDeque` + `AtomicInteger` size | Opt-in, bounded to 128; a plain deque under concurrent add/pollFirst is safe, no external lock needed. |
| `CancellationSource` cancel/register | `synchronized` monitor + `volatile boolean` fast path | See §4 — a monitor around the cold structural path, matching `EventBus.structuralLock`; the hot `isCancelled()` read stays lock-free. |
| `AsyncTask` state/timestamps | `volatile` fields | Single-writer-at-a-time (only `onSettled`/`runBody` ever write), many readers (debug, `toInfo()`). |
| `AsyncExecutors.state`/pool references | `volatile` | Set once on `start()`, read from any thread submitting work. |
| `FlowResumeQueue` | `ConcurrentLinkedQueue` | The one multi-producer/single-consumer handoff this design needs — lets `Node.state` stay a plain field (§7). |
| `TickScheduler`'s entries | `ConcurrentLinkedQueue` (weakly-consistent iteration + safe concurrent `remove`) | Entries are added from any thread, iterated/removed only from the server thread inside `tick()`. |
| Anything touching `Node`/`FlowContext`/`ServerPlayer`/`Level` | Main-thread-only, by construction | Background lambdas are never handed one of these — there's nothing to lock because there's nothing reachable to race on. |

## 14. Verification plan

1. `./gradlew compileJava` after each implementation stage.
2. First `runServer` boot check right after `ConcurrencyBootstrap`/`ConcurrencyTickBridge`/
   `AsyncLifecycle` exist and are wired into `EngineBootstrap` — confirms `Done`, an "Executors
   started" log line, and no new crash report, before any Flow coupling exists.
3. Second `runServer` boot check after `AsyncNode`/`Flow.async`/`Flow.await` land.
4. `/storymodengine async selftest` — every check in `AsyncSelfTestCommand` passes (requires a
   connected player, since Brigadier commands here resolve `context.getSource().getPlayerOrException()`
   the same way every other subsystem's self-test command does).
5. `/storymodengine async stresstest 1000` and `10000`, run manually — confirm throughput, no
   exceptions, and `list`/`stats` empty again afterward (no thread/task leak).
6. Final `./gradlew clean compileJava`, crash-report count unchanged from baseline.
