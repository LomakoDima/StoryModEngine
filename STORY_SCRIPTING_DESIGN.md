# SME — Story Scripting Language — Design

## 0. Core principle, restated precisely

```
data/<ns>/storymodengine/stories/*.sme  (raw text, datapack resource)
     │  Lexer → Parser → AST
     ▼
Validation   (duplicate ids, undefined refs, arg types, dead jumps — collected, never throw-first)
     │
     ▼
Compiler     (AST node → existing builder-API calls — exactly what hand-written @Auto* Java produces)
     ▼
DialogueRegistry / QuestRegistry / TriggerRegistry / CutsceneRegistry / FlowRegistry
```

SME owns zero runtime state and zero execution engine. It is a second *front-end* to APIs that
already exist — Flow/Dialogue/Quest/Trigger/Cinematic still tick, persist, and network exactly as
they do without SME. A `.sme` file participates in the resource-reload lifecycle (like the existing
dialogue/quest/cutscene JSON loaders), not mod-construction-time discovery, because it is datapack
content, not annotated Java.

**The one architectural decision everything else follows from**: SME compiles directly to in-memory
builder-API calls and registry registration — never to an intermediate JSON schema. JSON dialogue/
quest/cutscene loaders exist in this engine already, but their schemas can't express choice
conditions/commands, cutscene event/action tracks, or triggers at all (no trigger JSON loader has
ever existed) — and SME's own choice-body-with-a-`set`-statement is exactly the shape JSON can't
carry. Compiling straight to Java objects sidesteps this and needs no new JSON schema anywhere.

---

## 1. Package layout

```
api/scripting/annotation/StoryCommand.java     -- the one mod-author extension point

common/scripting/
  ScriptingBootstrap.java

  lexer/       SmeLexer, Token, TokenType, LexResult, LexException
  parser/      SmeParser, ParseResult, ParseException
  ast/         ~50 sealed-hierarchy node types — SmeNode (root), StoryNode, MetadataTag,
               StmtNode/ExprNode families, dialogue/quest/trigger/sequence declaration nodes
  validation/  SmeValidator, DuplicateIdPass, ReferenceResolutionPass, VariableUsagePass,
               CommandArityPass, UnreachablePass, ValidationContext, StmtWalker, StoryValidationResult
  compiler/    SmeCompiler (orchestrator), ExprCompiler, EffectCompiler, ActionCallCompiler,
               SequenceCompiler, DialogueCompiler, SmeQuestCompiler, TriggerCompiler,
               ObjectiveDispatch, RewardDispatch, RaycastCompiler, CompileContext
  registry/    ScriptingRegistryFacade, SmeSourceRegistry, ZoneRegistry, EventNameRegistry
  command/     StoryCommandRegistry, StoryCommandDiscovery, BuiltinStoryCommands, ArgumentCoercion
  diagnostics/ SmeException, SmeDiagnostic, Severity, DiagnosticReporter
  reload/      SmeReloadListener, SmeReloadBootstrap
  persistence/ StoryVariableData, StoryWorldVariableData, SmeValue, StoryVariableStore, ScriptingCapabilities
  example/     prologue.sme, DemoCutscenes.java, DemoZones.java
  debug/       ScriptTestCommand
```

`api` holds exactly one type — `@StoryCommand` — because it's the only piece a mod author's own code
ever imports directly. Everything else is orchestration and lives under `common.scripting`.

---

## 2. Grammar (EBNF)

No semicolons, no significant newlines — every statement's leading token uniquely determines its
production. Comments: `//` and `/* */`.

```ebnf
file            = { storyDecl } ;
storyDecl       = { metaTag } "story" ID "{" { topDecl } "}" ;
metaTag         = "@" ("chapter"|"author"|"debug"|"test") [ "(" argList ")" ] ;
topDecl         = varDecl | dialogueDecl | questDecl | triggerDecl | sequenceDecl | includeDecl ;
includeDecl     = "include" STRING ;   (* parsed, not yet compiled — see §10 *)

varDecl         = "var" ID "=" literal ;
setStmt         = "set" varRef ( "=" | "+=" | "-=" ) expr ;
varRef          = ID ;   (* one dotted literal name; "player." prefix is the only structurally
                             special case, routing to the per-player capability *)

expr            = orExpr ;  orExpr = andExpr {"||" andExpr} ;  andExpr = notExpr {"&&" notExpr} ;
notExpr         = ["!"] cmpExpr ;
cmpExpr         = primary [ ("=="|"!="|">"|"<"|">="|"<=") primary ] ;
primary         = literal | varRef | "(" expr ")" ;
literal         = BOOL | INT | DOUBLE | STRING ;

block           = "{" { stmt } "}" ;
stmt            = setStmt | varDecl | ifStmt | waitStmt | waitEventStmt | jumpStmt | callStmt
                | returnStmt | endStmt | actionCallStmt | startQuestStmt | playCinematicStmt
                | startDialogueStmt | raycastIfStmt ;
ifStmt          = "if" "(" expr ")" block { "elseif" "(" expr ")" block } [ "else" block ] ;
waitStmt        = "wait" (INT | DURATION) ;
waitEventStmt   = "wait" "event" eventName ;
eventName       = ID ;                          (* resolved via EventNameRegistry *)
jumpStmt        = "jump" ID ;
callStmt        = "call" ID [ argList ] ;        (* args parsed, not yet threaded to the sub-flow — see §10 *)
returnStmt      = "return" ;   endStmt = "end" ;
startQuestStmt  = "start" "quest" ID ;
playCinematicStmt = "play" "cinematic" ID | "cinematic" ID ;
startDialogueStmt = "start" "dialogue" ID | "dialogue" ID ;
actionCallStmt  = ID [ "player" ] argList ;      (* a bare 'player' token right after the command
                                                     name is the implicit-self-target sentinel,
                                                     dropped by the parser, never a real argument *)
argList         = arg { arg } ;   arg = literal | ID ;   (* a bareword ID is always a literal string —
                                                             see §4g for why *)

raycastIfStmt   = "if" "raycast" "." ("entity"|"block"|"any") block [ "else" block ]
                | "raycast" "{" { raycastOpt } "}" block ;
raycastOpt      = "distance" INT | "entities" | "blocks" | "any" | "living" ;

dialogueDecl    = { metaTag } "dialogue" ID "{" { dialogueBodyItem } "}" ;
dialogueBodyItem = ("node" ID) | dialogueEntry ;   (* a bare "node <id>" is a LABEL, no braces — see §4b *)
dialogueEntry   = lineEntry | choiceGroup | actionEntry | gateEntry | jumpEntry | endEntry ;
lineEntry       = ID ":" STRING ;
actionEntry     = block ;
gateEntry       = "gate" "(" expr ")" ;
jumpEntry       = "jump" ID ;   endEntry = "end" ;
choiceGroup     = "choice" "{" { choiceOption } "}" ;
choiceOption    = STRING [ "when" "(" expr ")" ] block ;

questDecl       = { metaTag } "quest" ID "{" { questEntry } "}" ;
questEntry      = "title" STRING | "description" STRING | "prerequisite" ID
                | "objective" objectiveKind objectiveArgs | "reward" "{" { rewardEntry } "}" ;
objectiveKind   = "kill"|"collect"|"talk_to"|"interact"|"find_entity"|"dialogue"|"quest" ;
objectiveArgs   = ID [ INT ] ;   (* for find_entity, the INT is a radius, not a repeat count *)
rewardEntry     = "item" ID INT | "xp" INT | "command" STRING | "unlock_quest" ID ;

triggerDecl     = { metaTag } "trigger" ID "{" triggerWhen ["once"|"repeat"] "run" block "}" ;
triggerWhen     = "when" "player" "enters" STRING | "when" "time" INT | "when" "event" eventName ;

sequenceDecl    = { metaTag } "sequence" [ ID ] block ;

ID              = letter {letter|digit|"_"} {"." letter {letter|digit|"_"}}         (* dotted name *)
                | letter {letter|digit|"_"} ":" letter {letter|digit|"_"|":"|"/"} ;  (* namespaced id *)
STRING          = '"' { '\\' ('"'|'\\'|'n'|'t') | anyOtherChar } '"' ;
INT = digit {digit} ;  DOUBLE = digit{digit} "." digit{digit} ;
DURATION        = digit{digit} ("s"|"t"|"m") ;    (* s=20 ticks, t=raw ticks, m=1200 ticks *)
BOOL            = "true" | "false" ;
```

Two deliberate grammar additions beyond the illustrative examples in the original request, both
necessary to reach real engine features: **`when (expr)` on a choice option** (reaches
`DialogueDefinition.Builder.condition(...)`), and **`if raycast.entity { }` sugar** (desugars at
parse time into the general `raycast { entities } block` form, default `distance 20`).

**One refinement made during implementation, differing from the earlier design draft**: a dialogue
node boundary is written `node <id>` with **no braces** — a label, not `node <id> { ... }`. Entries
accumulate into whichever node was most recently labeled (or an implicit `"start"` node before the
first label). This is what actually lets one dialogue mix a short unlabeled opening with a labeled
continuation reached via `jump`, exactly the shape `prologue.sme` itself uses — the originally-drafted
braced form couldn't express that mix without redundant nesting.

---

## 3. AST

Every node carries `SourcePos pos()` (file/line/column), declared once on a sealed root interface
(`SmeNode`) so a diagnostic can always point at real text. `StmtNode` and `ExprNode` are their own
sealed sub-hierarchies; `VarDeclNode` implements `StmtNode` (not `SmeNode` directly) since a `var`
declaration is legal both as a top-level story declaration and inside a block.

Representative shapes:

```java
public sealed interface SmeNode permits StoryNode, MetadataTag, StmtNode, ExprNode, ... { SourcePos pos(); }
public record StoryNode(SourcePos pos, String id, List<MetadataTag> tags, List<SmeNode> declarations) implements SmeNode {}
public record SetStmtNode(SourcePos pos, String varName, SetOp op, ExprNode value) implements StmtNode {}
public record IfStmtNode(SourcePos pos, ExprNode cond, List<StmtNode> thenBlock, List<ElseIfBranch> elseIfs, List<StmtNode> elseBlock) implements StmtNode {}
public record ChoiceNode(SourcePos pos, String text, ExprNode condition, List<StmtNode> body) implements SmeNode {}
```

`ChoiceNode.body` being an arbitrary `List<StmtNode>` (not just a jump target) is what drives §4b's
choice-body compilation strategy.

**Java-17 note**: every tree walk in this subsystem uses `instanceof` pattern-matching chains, never
a pattern-matching `switch` — switch patterns are a preview feature until Java 21, and this project
targets Java 17 with no `--enable-preview`. Plain `instanceof` patterns have been final since Java 16
and cost nothing in clarity here.

---

## 4. Compiler — per construct

### (a) `if`/`elseif`/`else` → `Evaluator<Boolean>` / `Flow.branch`

`ExprCompiler.compileBoolean(ExprNode)` returns a real `Evaluator<Boolean>` closure tree built once
at compile time — no interpreter loop ever runs. At sequence/trigger scope, `IfStmtNode` lowers to
`Flow.branch(cond, thenFlow, elseFlow)`; `elseif` chains desugar left-to-right into nested branches.
The *same* `compileBoolean` output feeds a dialogue choice's `when (expr)` →
`.condition(Evaluator<Boolean>)` — verified by reading `DialogueDefinition.java`, a dialogue
condition is not a separate `DialogueContext`-flavored type, it's the identical `FlowContext`-based
`Evaluator`.

### (b) Dialogue choice/action bodies → `DialogueCommand`

Re-verified directly against `DialogueDefinition.java`: `.condition(...)`, `.command(...)`, and
`.gotoNode(...)` are three **independently-settable** fields on the same pending choice — no engine
change was needed for a choice body to carry both effects and a jump target, contrary to what the
original request text might suggest. Every non-control-flow statement in a choice/action body
compiles through `EffectCompiler` into a flat `Consumer<FlowContext>`, wrapped as
`DialogueCommand.of(dctx -> effect.accept(dctx.flowContext()))` — `DialogueContext.flowContext()` is
a real, verified accessor, so the dialogue-effect compiler is not a second parallel implementation of
`SequenceCompiler`'s effect logic, it *reuses* it.

Because a `DialogueCommand` runs synchronously inside one `execute()` call, it cannot suspend — so
`wait`, `wait event`, and `call` are **rejected at validation time** (`SME305`) anywhere inside a
dialogue body. `if` and `raycast` stay legal there (both are instant, synchronous computations).

A choice body ending in `end` (rather than `jump <id>`) needs a real node id to `.gotoNode(...)`,
since that call takes a node id, not an end sentinel — the compiler emits a synthetic `__sme_end_N`
node containing only `.end()`. Compiler-internal bookkeeping, not a builder API change.

### (c) `sequence { }` → `Flow.sequence(...)`

`SequenceCompiler` maps each statement to one `Flow`, joined with `Flow.sequence(Flow...)`:

| Statement | Flow factory |
|---|---|
| `set` | `Flow.action` via `EffectCompiler` |
| `if`/`elseif`/`else` | `Flow.branch` (§a) |
| `wait N` / `wait 2s` | `Flow.wait(ticks)` |
| `wait event x.y` | `Flow.waitForEvent(eventClass, matcher)` |
| `dialogue X` / `start dialogue X` | `Flow.action` → `DialogueSystem.start` |
| `play cinematic Y` / `cinematic Y` | `Flow.action` → `CinematicManager.play` |
| `start quest Z` | `Flow.action` → `QuestSystem.start` |
| `command_name args` | `Flow.action` → `StoryCommandRegistry.invoke` |
| `call other` | `Flow.subFlow(Flow.lazy(() -> ...))` — handles same-file forward references |
| `return` | `Flow.actionResult(ctx -> false)` — a failing step already stops a `Flow.sequence` |
| `end` | `Flow.action(ctx -> {})` — documented no-op, kept for symmetry with dialogue's `end` |
| `jump <id>` (sequence/trigger scope) | resolves dialogue → quest → cutscene → sequence, first match, pinned at compile time |

Reused verbatim for a top-level `sequence <id> {}`, a `trigger { run {} }` body, and (via §b's bridge)
a dialogue body's non-suspending statements.

### (d) `trigger { when ... run {} }` → `Trigger.Builder`

`when player enters "zone"` resolves a named zone via `ZoneRegistry` — the grammar has no coordinate
literal, so zones are registered from Java (`ZoneRegistry.register(name, center, radius)`), the same
extensibility shape as `@StoryCommand`. Compiled `Trigger`s register into `TriggerRegistry` **and**
get armed via `TriggerSystem.arm(...)` — mirroring exactly what `@AutoTrigger`'s discovery already
does; no trigger JSON loader has ever existed, so this is the only non-annotation path this subsystem
has ever had.

### (e) `objective <kind> <target> [count]` → `Objective.kill/collect/...`

A closed dispatch table (`ObjectiveDispatch`), not a switch on Java type — matches `Objective`'s own
polymorphic-dispatch discipline. `kill`/`collect`/`talk_to`/`interact` default to the `minecraft`
namespace when unqualified (`village_trial`'s `objective kill zombie 3` means
`minecraft:zombie`); `dialogue`/`quest` default to `storymodengine` (they reference story content).
`location` is not supported — same coordinate-literal boundary as trigger zones. `RewardDispatch`
mirrors this for `item`/`xp`/`command`/`unlock_quest`.

### (f) Variables → `StoryVariableData` / `StoryWorldVariableData`

No existing capability could hold an open-ended `Map<String,Object>` (`SerializerRegistry` has no
leaf serializer for `Object.class`). Two new `@Capability`-annotated types follow the existing
`StoryPlayerData` pattern exactly (public mutable field, public no-arg constructor — zero hand-written
serialize code): `StoryVariableData implements EntityCapability` for every `"player."`-prefixed name,
`StoryWorldVariableData implements LevelCapability` for everything else. `SmeValue` is a closed
tagged-union `record` (`type`, `boolVal`, `intVal`, `doubleVal`, `stringVal`) — being a record,
`SerializerRegistry` resolves `Map<String,SmeValue>` automatically, the same way `QuestProgress`
already nests `Map<String,Integer>`. Both capabilities are `sync = false`: re-verified directly
against `Capabilities.java`, syncing a `LEVEL`-owner capability is a documented no-op today, and
nothing in this MVP needs client-visible story variables.

**Known, documented simplification**: a `var x = <initial>` declaration establishes `x`'s *type* for
validation only — it does not seed the capability with that initial value at reload time. An unset
variable always reads as its type's zero value (`0`/`false`/`0.0`/`""`), regardless of the authored
initial literal. Every declaration in `prologue.sme` happens to use a zero initial, so this is never
observable there; matching an arbitrary non-zero initial (`var x = true`) is a clean, scoped
fast-follow, not attempted in this pass — it would need a "seed on first touch" mechanism (hooked to
player-join for `player.*` vars, and to some notion of "current level" at reload time for world vars)
that adds real complexity for a case this demo never exercises.

### (g) `@StoryCommand` invocation

`ActionCallCompiler` resolves `StoryCommandRegistry.get(name)` and coerces every argument once, at
compile time — every argument in this grammar is a compile-time constant (a literal, or a bareword
`RefExprNode`, which is **always treated as a literal string**, never a variable read). This is a
deliberate simplification from an earlier draft that considered resolving a bareword against declared
variables — `CommandArityPass`'s own dry-run check already assumed `RefExprNode` → `STRING`
uniformly, so making the compiler agree by construction removes an entire disagreement surface for
free, and reads naturally besides (`give player diamond 5` — `diamond` obviously means the string
`"diamond"`, the same way an unquoted word in a Minecraft command already does).

### (h) Cross-references and events

`ScriptingRegistryFacade` is the one lookup path shared by validation and compilation —
`DialogueRegistry.get`/`QuestRegistry.get`/`CutsceneRegistry.get`/`TriggerRegistry.get`/
`FlowRegistry.get`, all verified to return `null` (never throw) on a miss. `EventNameRegistry` is a
small, extensible `String → Class<? extends Event>` table (`"player.join"`, `"entity.kill"`,
`"quest.completed"`, ...) with an optional player-extractor function, since the engine has no generic
namespaced-event convention. **Known limitation**: matching is by event *type* only —
`"quest.completed"` matches every `QuestCompletedEvent`, not one specific quest. `prologue.sme` has
only one quest, so this never manifests there; a story with several quests should prefer `objective
quest <id>` (which already targets one specific quest) over a trigger for per-quest completion.
Raycast: `RaycastCompiler` wraps `Raycast.from(ctx.player()).distance(d)....cast().isHit()` as an
`Evaluator<Boolean>`, flowing through the same `if`-compilation path as §a.

---

## 5. Validation

One pass class per concern, all diagnostics collected — never throw-first:

1. **`DuplicateIdPass`** — same-file repeats (a local `Set`) and cross-file collisions (against
   `SmeSourceRegistry`, which records the *first* file to claim each id across the whole reload batch).
2. **`ReferenceResolutionPass`** — every quest/dialogue/cutscene/sequence/event/zone reference,
   resolved against `SmeSourceRegistry` (same-batch declarations, any file, any order) union the live
   registries. Also enforces §4b's dialogue-body statement restriction (`SME305`) and routes a
   dialogue-scope `jump` to local-node-target checking rather than cross-kind resolution — the one
   place `JumpStmtNode`'s meaning depends on where it appears in the tree.
3. **`VariableUsagePass`** — undeclared read/write, redeclaration, and `+=`/`-=` on a `bool`/`string`
   variable, all against one same-file symbol table (collected in a pre-pass so forward references
   within a file resolve).
4. **`CommandArityPass`** — unknown command, wrong argument count, or a literal whose type can't
   coerce to the declared Java parameter type — using the exact same `ArgumentCoercion.isCoercible`
   check the compiler itself uses, so the two can never disagree.
5. **`UnreachablePass`** (warnings only) — a dialogue node no `jump`/choice-trailing-jump ever
   reaches, and a choice whose `when` condition is a literal `false`.

**Cross-file ordering**: `SmeReloadListener` runs the whole reload batch in three passes — lex+parse
every file; claim every successfully-parsed declaration's id into `SmeSourceRegistry` (regardless of
that file's own eventual validation outcome — a deliberate, documented relaxation from an earlier,
stricter two-sweep draft, chosen for substantially simpler and more robust code at the cost of a rare
edge case: a reference into a file that parses but fails its *own* validation resolves at validation
time but would still fail at runtime, since that file's content was never registered); then validate
and compile each story independently — a story that fails validation is reported but simply never
registered, and never blocks a different, valid story in the same file or a different file.

---

## 6. Diagnostics

```java
public record SmeDiagnostic(Severity severity, SourcePos pos, String code, String message) {
    public String format() { return pos + ": " + severity + " [" + code + "] " + message; }
}
```

The lexer resyncs at the next whitespace after a malformed token; the parser performs panic-mode
recovery (skip to the next `}` at the current brace depth, or the next top-level/statement keyword) —
both verified live against deliberately-broken input: zero infinite loops, real `file:line:column`
diagnostics for every independent problem in a file, and every *other* declaration in that file still
parses and reports independently. `DiagnosticReporter` funnels everything to `EngineLog.channel("SME")`,
mirroring the existing dialogue/quest/cutscene JSON loaders' "catch, log, keep going" shape so one bad
`.sme` file never aborts a whole reload.

Codes: `SME0xx` lexer, `1xx` parser, `2xx` duplicate-id, `3xx` reference/scope, `4xx` type/arity,
`5xx` compiler-internal (defensive only — should be unreachable given validation passed).

---

## 7. `@StoryCommand`

```java
@Retention(RUNTIME) @Target(METHOD)
public @interface StoryCommand { String value(); }
```

`StoryCommandDiscovery` mirrors `EventListenerDiscovery`'s exact shape (`ModFileScanData` scan for
`METHOD`-level annotations, then per-owner-class reflective re-validation). Required method shape:
`public static`, first parameter exactly `ServerPlayer` (auto-supplied from the compiled call site's
`FlowContext.player()`, never authored in `.sme`), remaining parameters restricted to
`String`/`int`/`double`/`boolean`/`long`, `void` return. `StoryCommandRegistry` binds each via
`MethodHandles.lookup().unreflect(method)` — the same idiom `EventBus` already uses.

**Built-ins are the same mechanism, not a special case**: `give`/`teleport`/`play_sound`/`spawn`/
`set_block` are ordinary `@StoryCommand`-annotated static methods in `BuiltinStoryCommands`,
discovered exactly the way a mod author's own class would be. `give player diamond 5` — the parser
drops the bare `player` token as a self-target sentinel; only the invoking player is a valid implicit
target in this pass, an explicit-target syntax is a clean, scoped future extension.

---

## 8. Lifecycle / bootstrap

Verified against `EngineBootstrap.java`'s actual call order: `ConcurrencyBootstrap → ContentDiscovery
→ ResourcePacks → NetworkBootstrap → FlowState.registerSerializer → CapabilityBootstrap →
EventBootstrap → FlowBootstrap → CinematicBootstrap → DialogueBootstrap → QuestBootstrap →
TriggerBootstrap`, all synchronous inside the mod constructor. `ScriptingBootstrap.init()` is
appended as the new last line — it needs every registry above to already exist. It runs
`StoryCommandDiscovery.run(modId)` (mod-construction time, same timing as every other `@Auto*`
discovery) and the engine's own demo-only `ZoneRegistry` seeding for `prologue.sme`.

`@Auto*` discovery (including `@Capability` — `StoryVariableData`/`StoryWorldVariableData` need no
separate registration call, they're found by the same pre-existing `CapabilityDiscovery` scan) always
completes before Forge ever fires `AddReloadListenerEvent` (posted during `ReloadableResourceManager`
construction, well after mod construction). So by the time `SmeReloadListener.apply(...)` first runs,
every hand-written or JSON-authored definition is already registered.

`SmeReloadListener` extends vanilla's `SimplePreparableReloadListener<Map<ResourceLocation,String>>`,
**not** `SimpleJsonResourceReloadListener` — `.sme` is not JSON, and that class is hard-wired to
`Gson`. Same `AddReloadListenerEvent` registration timing as the dialogue/quest/cutscene loaders
(`SmeReloadBootstrap` mirrors `DialogueJsonBootstrap` exactly), different vanilla base class.

**Verified live**: a full dedicated-server boot with `prologue.sme` present produced
`[SME] [storymodengine] @StoryCommand discovered 5 command(s)` and
`[SME] [reload] 1 file(s) processed, 1 story compiled, 0 error(s)`, followed by a clean `Done`. Two
real bugs surfaced only at this stage (not by `compileJava`): `CutsceneDefinition.Builder.build()`
requires at least one `.shot(...)`, and a shot requires at least one `.rotation(...)` keyframe — both
fixed in `DemoCutscenes.java`. This is exactly the value of a live boot check beyond a green compile.

---

## 9. Testing

No JUnit setup exists in this project — `/storymodengine script test` is the same in-game,
assert-and-report substitute every other subsystem's `test`/`selftest` command already uses. Two
independent tiers:

1. **Pure JVM** (`testPipeline`) — hand-authored `.sme` snippets run through the real lexer → parser
   → validator pipeline in-process: a valid story produces zero errors; deliberately broken snippets
   (duplicate id, undeclared variable, unknown reference, unresolvable dialogue jump, a bad argument
   type to a built-in command) each produce their expected diagnostic code; the actual bundled
   `prologue.sme` resource is read via the classloader and round-trips with zero errors.
2. **Live integration** (`testIntegration`, needs the invoking player) — asserts every piece of
   `prologue.sme` actually registered (`guard_intro`, `guard_welcome`, `village_trial`,
   `village_entry`, `trial_complete`, `village_welcome` cutscene), starts `guard_intro` for the
   player, selects its first choice, and asserts `village.reputation` became `1` and `village_trial`
   is active — proving SME wired the existing systems together correctly, without re-testing
   Dialogue/Quest/Flow's own internals.

---

## 10. Explicit non-goals

No IDE/visual editor/debugger UI. No hot reload beyond the natural `/reload` mechanism. No complex
type system/macros/generics — four value types, a closed five-type `@StoryCommand` coercion table.
No general-purpose language — no user-defined functions with return values beyond `call`/`return`'s
Flow-sequence-abort mapping, no loops, no arrays, no arithmetic beyond `+=`/`-=`. No new async/
persistence/networking runtime. **Full `cinematic <id> { ... }` authoring is out of scope** — only
`play cinematic <id>` (referencing an already-defined cutscene, Java- or JSON-authored) ships.
**`include "file.sme"` is parsed but not compiled** — encountering one emits a clear `WARNING` (not a
silent drop) and the declaration is otherwise ignored; building real cross-file splicing was the
lowest-confidence "build now" call in the original design and was deferred once the compiler's actual
shape made the cost of building it properly clearer. **`call <sequence> <args>` parses its argument
list but does not thread it to the sub-flow** — `Flow.subFlow` has no built-in parameter-passing
mechanism; use shared story variables to communicate between a caller and a called sequence instead.

---

## 11. Verification

1. `./gradlew compileJava` after every stage during implementation — every stage compiled clean on
   the first or second attempt.
2. A full `./gradlew clean compileJava` — succeeds, only pre-existing deprecation warnings (verified
   present in files this pass never touched).
3. A real dedicated-server boot with `prologue.sme` bundled — reached `Done`, `0 error(s)` reported by
   the reload listener, `5` built-in commands discovered.
4. Lexer/parser smoke-tested directly against `prologue.sme`'s literal text (136 tokens, 0 lexer
   diagnostics, 0 parser diagnostics, all 7 declarations correctly shaped) and against a deliberately
   broken file (8 diagnostics, correct file:line:column on each, no infinite loop, other declarations
   in the same file still parsed independently).
5. `/storymodengine script test` — both tiers implemented and compiling; running it live needs a
   connected player (a dedicated server console has none), so final in-game confirmation is the one
   remaining step for a human with a client to run, the same way this project's live testing has
   always worked for a freshly-built subsystem.
