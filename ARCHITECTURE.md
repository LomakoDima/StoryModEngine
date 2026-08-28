# StoryModEngine — Architecture

StoryModEngine is a mod-development engine built on top of Forge 1.20.1. Forge already gives
mod authors a real, capable API — registries, events, datagen, networking, rendering hooks,
client/server separation. The engine's job is not to replace any of that; it's to remove the
boilerplate and ceremony around using it correctly, so building actual game content stays the
focus. Every engine subsystem should be a thin, opinionated layer *on top of* a specific piece of
Forge/Minecraft, never a reimplementation of it.

**Guiding principle: the developer describes content, the engine infers everything else.**
`@AutoContent` is the first, and intentionally the *only*, marker the mod author writes:

```java
@AutoContent
public static final Supplier<Item> RUBY = ContentHolder.of(() -> new Item(new Item.Properties()));

@AutoContent
public static final Supplier<Block> RUBY_BLOCK = ContentHolder.of(() -> new Block(BlockBehaviour.Properties.of()));
```

No id, no registry, no namespace, no per-content-type annotation (`@AutoItem`, `@AutoBlock`, ...).
Everything else is inferred:

```
RUBY  (field name)          ─▶  ruby                 (ContentIds)
Supplier<Item>  (field type) ─▶ Item ─▶ ForgeRegistries.ITEMS  (ContentTypeRegistry / ContentRegistryBinding)
active @Mod container         ─▶ storymodengine     (ModLoadingContext, at discovery time)
                                        ─▶ storymodengine:ruby
```

(`ContentHolder.of(...)` — see "Why fields can be `public static final`" below — is what lets the
field stay `Supplier<T>`-typed and genuinely `final`, matching Forge's own convention, while still
being something `ContentDiscovery` can register safely.)

The mod author's whole side of the deal is: write fields like the above, put a texture at the
conventional path if the content needs one, and call `EngineBootstrap.init(modEventBus)` once, in
their `@Mod` constructor. Nothing else — no `runData`, no hand-written blockstate/model JSON, no
`BlockItem` registration. Launching Minecraft is enough:

```
Launch Minecraft
      ↓
Engine discovers @AutoContent           (ContentDiscovery, via Forge's ModFileScanData)
      ↓
Engine registers content + companions   (BlockItem for a Block, etc.)
      ↓
Engine supplies generated resources     (ResourcePacks, via AddPackFindersEvent — no files, no runData)
      ↓
Item / Block exist and render correctly
```

New capability should extend *what the engine infers*, not grow `@AutoContent` into a
configuration object or spawn sibling annotations per content type. The extension points are
internal: `ContentTypeRegistry` (routing by type, and companion content like `BlockItem`),
`ContentRegistryBinding` (registration strategy), and `ContentDescriptor` (the record that feeds
resource generation) — see below.

## Package layout: `api` / `client` / `common` / `mixin`

Every class under `com.dimalab.storymodengine` lives in exactly one of four top-level packages —
this is a physical reorganization, not just a naming convention, and it sits *above* every
subsystem this document describes (`quest`, `flow`, `dialogue`, `cinematic`, `raycast`, ...): a
subsystem's own package now nests one level deeper, e.g. `flow.Flow` lives at
`com.dimalab.storymodengine.common.flow.Flow`, `cinematic.client.CameraCollision` at
`com.dimalab.storymodengine.client.cinematic.CameraCollision`. Read every subsystem section below
with that prefix mentally applied — the prose still says `flow.Flow`/`dialogue.client.DialogueScreen`
for readability, matching how the section was written, not because the literal path is still that
short.

- **`api`** — the small, stable vocabulary a mod author's own code imports directly: annotations
  (`@AutoQuest`, `@AutoFlow`, `@AutoDialogue`, `@AutoCutscene`, `@AutoContent`, `@AutoFluidType`,
  `@Packet`, `@Capability`, `@SubscribeEvent`), extension-point interfaces with no coupling to their
  own subsystem's internals (`Reward`, `PatternMatcher`, `Evaluator`, `DialogueCommand`, `QuestStep`
  — see the exclusion note below for why `Objective` itself is *not* one of these), marker interfaces
  (`ServerboundPacket`/`ClientboundPacket`/`PacketHandler`, `Event`/`CancellableEvent`,
  `EntityCapability`/`BlockEntityCapability`/`LevelCapability`), state enums
  (`QuestState`/`ObjectiveState`, `DialogueMode`/`TextRevealMode`, `SkipPolicy`/`PlaybackState`,
  `FlowRunState`, `OwnerKind`, `LogLevel`, `PacketDirection`, `EventPriority`), and the whole `math`
  utility library (`Easing`, `Interpolator(s)`, `Geometry`, `Angles`, `Transform`, the `curve`
  package) — all of it pure, dependency-free, and safe for a mod author to depend on without pulling
  in engine internals.

  **Deliberately excluded from `api`, even though a mod author calls them directly**: every
  orchestration/facade class — `Flow`, `FlowManager`, `Quest`, `QuestSystem`, `DialogueSystem`,
  `Raycast`, `CinematicManager`, `Capabilities`, `Network`, `Events`, `EngineLog`, `EngineBootstrap`
  — and, notably, `Objective`/most of `quest.objective`: `Objective`'s own static factories
  (`Objective.kill(...)`, `.collect(...)`, ...) construct package-private sibling classes
  (`KillObjective`, `CollectObjective`, ...), so `Objective` has to stay in the same actual package
  as those siblings — moving just the interface to `api` would have broken that access entirely.
  This is a hard Java constraint, not a style choice: package-private coupling only works within one
  real package, and splitting a cohesive subsystem's public entry point away from its own
  package-private implementation is exactly what breaks it. `Reward` (its factories return anonymous
  classes, not named siblings) and `PatternMatcher` (its implementations are already `public`) don't
  have this problem, so they *are* in `api`. Every such call is a verified case, not a guess — see
  the compile log from this reorganization for the exhaustive list of what broke and why when first
  attempted naively.

- **`client`** — every class that touches a client-only Minecraft type (`ClientLevel`, `Minecraft`,
  `Screen`, `KeyMapping`, particle rendering, ...). This absorbs what used to be scattered `.client`
  subpackages inside each subsystem (`cinematic.client`, `dialogue.client`, `quest.client`, ...) —
  those files kept their relative structure, just moved up under one `client` root instead of a
  same-named subfolder of their subsystem's `common` location. Two classes needed a visibility bump
  from package-private to `public` purely because of this split (`ClientLevelLookup#get()`,
  `RaycastClientDebug#spawn(...)`) — the dist-safety property that made them package-private-until-
  invoked in the first place comes from *lazy class-loading* (Forge's `RuntimeDistCleaner` scans a
  class's bytecode for `@OnlyIn`-mismatched references at load time, not per branch executed — see
  each class's own doc), not from access modifiers, so widening them is safe and doesn't reopen the
  dedicated-server crash they were written to prevent. Verified live: a `runServer` boot reached
  `Done` and a `runClient` boot reached `Setting user: Dev` → particle registration → the main menu
  phase, both with zero new crash reports, confirming the split didn't reintroduce a dist-safety bug.

- **`common`** — everything else: every subsystem's actual runtime (`FlowManager`, `FlowRuntime`,
  `QuestCompiler`, `DialogueRunner`, `CinematicManager`, ...), discovery/JSON/persistence/network
  plumbing, and all demo/example/debug content. This is the overwhelming majority of the codebase by
  file count and is where a subsystem's own internal package-private coupling (the normal, deliberate
  kind — e.g. `FlowManager`/`FlowInstance`/`FlowHandle` cooperating on a `FlowRunState` transition)
  continues to work exactly as documented in each section below, since those classes all still share
  one real package.

- **`mixin`** — empty. No mixin exists in this codebase yet; the package exists because
  `storymodengine.mixins.json` already declares `"package": "com.dimalab.storymodengine.mixin"` (has
  since before this reorganization), so this is where the first one lands, not new scaffolding.

## Forge/Minecraft vs. Engine — who owns what

| Concern | Owned by Forge/Minecraft | Simplified by the Engine |
|---|---|---|
| Registries, `RegistryObject`, `RegisterEvent` | Yes — this is the real registration mechanism | Hides `DeferredRegister` bookkeeping behind `@AutoContent` |
| Mod lifecycle events, mod/event bus split | Yes | Future subsystems subscribe internally; mod authors don't wire buses by hand |
| Client/server separation (`Dist`, `DistExecutor`) | Yes | Future client-only subsystems (rendering, UI, VFX) isolate `Dist.CLIENT` code internally |
| Datagen (`GatherDataEvent`, providers), resource pack loading (`PackResources`, `AddPackFindersEvent`) | Yes | `resource` derives generated blockstates/models from the same `ContentDescriptor`s used for registration — computed at runtime, not via `GatherDataEvent` (see below) |
| World/entity/network primitives | Yes | Future subsystems provide task-shaped APIs (e.g. "cast a ray", "spawn a cutscene") over the primitives |

The rule of thumb for every future addition: **if Forge already exposes it cleanly, call it — don't
wrap it for its own sake.** The engine only earns a layer where the raw Forge API requires
ceremony (registry classes, event wiring, dist-splitting, generated-resource boilerplate) that
doesn't add value for a mod author.

## Current foundation

```
com.dimalab.storymodengine
├── core/
│   └── EngineBootstrap.java     — the one call a mod author makes: init(modEventBus)
├── content/
│   ├── AutoContent.java            — pure marker annotation, zero parameters
│   ├── ContentHolder.java          — the field's recipe, then its resolved registered instance
│   ├── ContentDiscovery.java       — scans the mod's own jar for @AutoContent, registers it
│   ├── ContentIds.java             — field name → registry path (its own component; see below)
│   ├── ContentTypeRegistry.java    — extensible Class<?> → ContentRegistryBinding map, + expanders
│   ├── ContentRegistryBinding.java — "how do I register a T" (Forge registry / vanilla registry)
│   ├── ContentExpander.java        — "what else does a T imply" (BlockItem, a fluid's other 4 pieces, ...)
│   ├── ExpansionContext.java       — what an expander gets: primary content, plus register(...)
│   ├── FluidContentExpander.java   — FluidType → still/flowing Fluid + LiquidBlock + BucketItem
│   ├── AutoFluidType.java          — FluidType base class with convention-based client textures
│   └── ContentDescriptor.java      — metadata captured per field, feeds resource generation
├── resource/
│   ├── ResourcePacks.java        — arms a generated resource pack via AddPackFindersEvent
│   ├── AssetGenerator.java       — ContentDescriptor list → generated blockstate/model JSON bytes
│   └── GeneratedResourcePack.java — in-memory PackResources serving those bytes to the game
├── math/                           — vectors, interpolation, curves, transforms (see below)
│   ├── Angles.java / Numbers.java / Geometry.java
│   ├── interp/   — Interpolator, Easing, Interpolators, TickValue
│   ├── curve/    — Curve, BezierCurve, CatmullRomSpline, ArcLengthTable
│   ├── transform/ — Transform
│   └── example/  — CameraPathCommand/Animator, EasingDemoScreen, MathUiCommand
└── logging/                        — EngineLog/EngineLogger, console + chat (see below)
    ├── LogLevel.java / LoggingConfig.java / ChatTemplate.java
    ├── EngineLogger.java / LogEntry.java / EngineLog.java
    └── example/ — LogTestCommand
```

`content` + `resource` are the first vertical slice: mark a field, get it registered *and*
rendering correctly in-game, with zero manual `DeferredRegister` classes, ids, package lists,
`BlockItem` registration, or hand-authored JSON. `math` is the second: a type-agnostic
interpolation/curve/transform layer everything spatial or time-based in the engine will eventually
sit on. See the Javadoc on each class for its exact contract; the mechanism and the reasoning
behind each design choice are summarized here.

### Discovery mechanism

Discovery is driven by Forge's own annotation scan data (`ModFileScanData`, obtained via
`ModList.get().getModFileById(modId).getFile().getScanResult()`) — the same mechanism Forge uses
internally to locate `@Mod` classes and to power `@AutoRegisterCapability`
(`net.minecraftforge.common.capabilities.CapabilityManager.injectCapabilities`). It's available
before the mod constructor runs, which is why a single `ContentDiscovery.run(modEventBus)` call in
the constructor can populate and wire up `DeferredRegister`s synchronously, ahead of any
`RegisterEvent` — exactly like a hand-written `DeferredRegister` setup needs to. This was
re-verified against ForgeSPI's actual 1.20.1 source rather than assumed from other versions or
loaders; no APT/Gradle changes are needed because `ModFileScanData` is already on every mod's
compile classpath.

### Why fields can be `public static final` — and why a bare `Supplier<T>` is unsafe

Forge's own convention is `public static final RegistryObject<T> FOO = ...`. Two earlier designs
were tried and rejected before landing on the current one:

1. **A plain `Supplier<T>` field, overwritten in place with the resulting `RegistryObject<T>`
   after registration.** This required the field to be non-`final` so reflection could reassign
   it. Rejected: reassigning a field behind the mod author's back is a fragile, invisible side
   effect, and reliably reassigning a genuinely `static final` field via reflection isn't
   something modern JDKs support anyway.
2. **A plain `Supplier<T>` field, read once and never written back to**, treating it purely as a
   one-time construction recipe. This was tried and shipped briefly — and then broke in actual
   play. Confirmed by running the mod: opening the creative inventory (which calls the field's
   `.get()` a second time, to add the item to a tab) crashed with
   `IllegalStateException: Registry is already frozen`, thrown from inside `Item`'s own
   constructor (`NamespacedWrapper.createIntrusiveHolder`). In 1.20.1, `Item` and `Block`
   constructors register an intrusive holder with their registry, which only works while that
   registry is unfrozen — i.e. during `RegisterEvent`. Any `Supplier<T>` that can be invoked more
   than once for these types isn't a style problem, it's a correctness bug: the *first* extra call
   anywhere in the mod (a creative tab, a recipe referencing the item, anything) crashes the game.

The current design, `ContentHolder<T>` (`content/ContentHolder.java`), avoids both problems at
once. A field is declared `public static final Supplier<Item> RUBY =
ContentHolder.of(() -> new Item(...));` — genuinely `final`, matching Forge's convention exactly.
`ContentDiscovery` never touches the *field*; it reads the holder object out of it (a plain read,
legal on a `final` field) and calls two ordinary package-private methods on that *object* —
`factory()` to get the recipe for `DeferredRegister.register(id, ...)`, then `resolve(...)` to
hand the resulting `RegistryObject<T>` back to the same holder. No reflection write, anywhere, at
any point. Every `RUBY.get()` call, from any code, at any time, now safely returns the same
registered instance — which is what fixed the crash. `@AutoContent`'s own contract (a marker
annotation with zero parameters) is unaffected by any of this.

### Registry categories — why there's a `ContentRegistryBinding`, not just a type map

Minecraft/Forge 1.20.1 has more than one kind of registry, and they don't all register the same
way:

- **Forge registries** — created via `DeferredRegister.create(IForgeRegistry<T>, modid)`, e.g.
  `ForgeRegistries.ITEMS`, `ForgeRegistries.BLOCKS`.
- **Vanilla registries Forge still lets mods contribute to via code** — created via the *other*
  `DeferredRegister.create(ResourceKey<? extends Registry<T>>, modid)` overload (both overloads are
  real, current Forge 1.20.1 API — confirmed against `DeferredRegister`'s actual signatures). The
  stock `CREATIVE_MODE_TABS` register in `StoryModEngine.java` already uses this overload via
  `Registries.CREATIVE_MODE_TAB`.
- **Dynamic/datapack registries** (biomes, worldgen features, and other data-driven registries) —
  these are populated from JSON data files via data generation (`RegistrySetBuilder` /
  `BootstrapContext`), not code registration at all. `DeferredRegister` doesn't apply to them.

`ContentTypeRegistry` therefore doesn't map `Class<?> → IForgeRegistry<?>` — it maps
`Class<?> → ContentRegistryBinding<?>`, where `ContentRegistryBinding` is a tiny strategy interface
with two factories today, `forgeRegistry(...)` and `vanillaRegistry(...)`, both producing a
`DeferredRegister<T>` (which already unifies the actual registration and event-bus wiring for both
categories). Dynamic registries are deliberately **not** forced through this interface — they need
a structurally different, data-generation-based pipeline that a future subsystem can add on its own
terms. `ContentTypeRegistry.register(Class<T>, ContentRegistryBinding<T>)` is the extension point:
any future subsystem that introduces a new registrable type plugs in by registering its binding
once — `ContentDiscovery` itself never needs to change.

### `ContentExpander` — what a piece of content implies beyond itself

Some content types need more than one registry entry to actually work — Forge doesn't create a
`BlockItem` for a `Block` (verified against Forge's own 1.20.x docs: *"When a block is registered,
only a block is registered. The block does not automatically have a `BlockItem`."*), and a usable
fluid needs five distinct registered objects, not one (see below). `@AutoContent` still stays a
single field with zero parameters either way — the extra registrations are inferred and driven
internally by `ContentTypeRegistry.registerExpander(Class<T>, ContentExpander<T>)`.

A `ContentExpander<T>` runs once, immediately after `T`'s primary registration, and gets an
`ExpansionContext<T>` carrying the modId, the derived id, the already-registered primary
`Supplier<T>`, and a `register(Class<C>, id, Supplier<C>)` method that resolves the right registry
for `C` the same way primary content does (walking up `C`'s superclass chain in
`ContentTypeRegistry`) and records a `ContentDescriptor` for it. An expander's own registrations
are not themselves re-expanded — expansion is one level deep by design, so e.g. the `LiquidBlock`
a fluid expander registers doesn't also get a spurious `BlockItem` from the `Block` expander.

**`Block → BlockItem`** (the original case): registers a `BlockItem` under the *same* id, so
`RUBY_BLOCK` yields **two** registry entries from one field — `storymodengine:ruby_block` in
`BLOCKS` and `storymodengine:ruby_block` in `ITEMS` — independent namespaces, no collision, exactly
as Forge's registry model expects.

**`FluidType → { Source, Flowing, LiquidBlock, BucketItem }`** (`FluidContentExpander`): Forge 1.20.1
has no single-object fluid registration — verified against `ForgeFlowingFluid`'s actual
constructors and `ForgeFlowingFluid.Properties`. A `@AutoContent Supplier<FluidType>` field named
`RUBY_FLUID` expands into:

| id | type | registry |
|---|---|---|
| `ruby_fluid` | `FluidType` | `ForgeRegistries.Keys.FLUID_TYPES` (primary) |
| `ruby_fluid` | `ForgeFlowingFluid.Source` | `ForgeRegistries.FLUIDS` |
| `flowing_ruby_fluid` | `ForgeFlowingFluid.Flowing` | `ForgeRegistries.FLUIDS` |
| `ruby_fluid` | `LiquidBlock` | `ForgeRegistries.BLOCKS` |
| `ruby_fluid_bucket` | `BucketItem` | `ForgeRegistries.ITEMS` |

matching vanilla's own `water`/`flowing_water` naming. The four all reference each other lazily
(a `Source`'s `Properties` needs a supplier for the not-yet-registered `Flowing`, and vice versa)
via mutable forward references filled in the moment each is created — the same shape
`ForgeFlowingFluid.Properties` itself is built around, so nothing here fights Forge's own design.

Client rendering (still/flowing textures) is **not** wired through the expander — Forge 1.20.1 has
no data-driven file for it, a `FluidType` resolves its own render properties in code
(`FluidType.initializeClient`), lazily, per-instance. `AutoFluidType` — the base class a mod author
instantiates instead of raw `FluidType` — looks its own registered id up from
`ForgeRegistries.FLUID_TYPES` the first time that's called (always after registration, since it's a
client-render lookup) and points at `assets/<modid>/textures/block/<id>_still.png` /
`<id>_flow.png` by convention, the same folder and naming vanilla uses for water and lava. A mod
author who extends `FluidType` directly instead is opting out of the convention.

`LiquidBlock`s are excluded from `AssetGenerator`'s blockstate/model generation (see below): fluids
render from `FluidState`, not a block model, so generating one would be actively wrong, not just
unnecessary.

**`MobEffect`** needs no expander and no generated JSON at all: `ForgeRegistries.MOB_EFFECTS` is a
plain single-object Forge registry, and its icon is picked up automatically by vanilla's own
`mob_effects` texture atlas from `assets/<modid>/textures/mob_effect/<id>.png` — that atlas's
`directory` sprite source scans every namespace, not just `minecraft`, so no atlas JSON needs
touching either. It's the simplest content category the engine supports, and required no new code
beyond a `ContentTypeRegistry.register(MobEffect.class, ...)` line.

**`PaintingVariant`** also needs no expander, but does need something `MobEffect` doesn't: a
*data*-pack contribution, not a resource-pack one. `PaintingVariant` (verified against 1.20.1
source) carries only `width`/`height` — no title/author fields, so no lang key — and its texture
just needs to exist at `assets/<modid>/textures/painting/<id>.png`, sized exactly `width × height`
pixels, no JSON pointing at it required. But `Painting#create` (also verified against source) only
ever *considers* a variant when placing a painting if it's in the vanilla `minecraft:placeable` tag
— a registered `PaintingVariant` absent from that tag is real and loadable but permanently
unreachable in survival. `DataGenerator` (see below) closes that gap the same way the `Block →
BlockItem` expander closes "registered but unusable" for blocks, just through a data pack instead
of a registry entry.

**`ParticleType`** (in practice, `SimpleParticleType` — the only concrete, parameterless subtype;
`ParticleType<T>` itself is abstract and generic) needs a resource-pack entry (`particles/<id>.json`,
listing its texture — see below) and, unlike every other content type here, a piece of *client-only*
registration that isn't a registry entry at all: `RegisterParticleProvidersEvent`, which supplies
the `Particle` subclass actually drawn each frame. `AutoParticle` is the default the engine gives
every discovered `SimpleParticleType` for free — a plain textured, gravity-affected, fading
billboard — registered via `RegisterParticleProvidersEvent#registerSprite`, which (verified against
`ParticleEngine#register(ParticleType, ParticleProvider.Sprite)` source) calls `pickSprite` on the
created particle automatically, so `AutoParticle` itself never touches sprite assignment; only its
physics/fade are its own. `AutoParticleRegistration` does the registering, filtered to descriptors
in the calling mod's own namespace (mirroring `AssetGenerator`/`DataGenerator`'s `isOwnNamespace`
check) so one mod's client bus doesn't redundantly re-register another mod's particles.

This is the one case where `ContentTypeRegistry`'s `Class<T> → ContentRegistryBinding<T>` shape
runs into a real limit: `ParticleType<?>`'s registry is `IForgeRegistry<ParticleType<?>>`, but the
`Class<T>` `ContentDiscovery` actually has (read via reflection off a field's generic type
argument, e.g. `SimpleParticleType.class`) is a raw class literal — `Class<SimpleParticleType>`,
never `Class<ParticleType<?>>` — and raw class literals for a generic type are never `Class<T>` for
that type's own parameterization. No call shape fixes this at the call site; it's inherent to how
generics erase. `ContentTypeRegistry.registerRaw` is the dedicated, deliberately-unchecked entry
point for exactly this situation, used once, for `ParticleType.class`.

Client/server safety for `AutoParticleRegistration` follows the same rule the codebase already
established for `Config`/`ClientModEvents`-style code, just without a compile-time modid constant
to hang a `@Mod.EventBusSubscriber(..., value = Dist.CLIENT)` on (the engine doesn't know a calling
mod's id at compile time). `EngineBootstrap.init` checks `FMLEnvironment.dist == Dist.CLIENT`
itself and only *then* evaluates `AutoParticleRegistration.register(...)` — evaluating that method
reference is what would force the JVM to resolve `AutoParticle` (a true `TextureSheetParticle`
subclass, unusable on a dedicated server), so the check has to gate that evaluation, not live
inside the class being guarded. `RegisterParticleProvidersEvent` itself is safe to reference
unconditionally — Forge deliberately leaves that one event class free of `@OnlyIn` so mods *can*
register a listener for it from common code, even though it only ever fires client-side.

### Resource generation — no `runData`, no hand-authored JSON

Forge Data Generation (`GatherDataEvent` and its providers) is the real, intended way to produce
assets/data *at build time*. That's not what's used here, deliberately: the goal is for
`@AutoContent` + a texture to work the moment Minecraft launches, without a separate `runData` step
the engine can't assume the mod author has run. Instead, `resource` supplies the same kind of JSON
a datagen provider would — computed in Java, served straight out of memory, every time the game
(re)loads its resources:

1. **Resource Description** — every successfully registered field already produces a
   `ContentDescriptor<T>` (id, content type, supplier), collected in
   `ContentDiscovery.getDiscovered()`. This *is* the boundary between registration and resources:
   `AssetGenerator` never touches a `Field` or an `AutoContent` annotation, only this list.
2. **Generation** — `AssetGenerator.generate(modId, descriptors)` infers, per descriptor:
   - a `Block` → a blockstate (`{"variants":{"":{"model":"<modid>:block/<id>"}}}`) and a block
     model (`{"parent":"minecraft:block/cube_all","textures":{"all":"<modid>:block/<id>"}}`);
   - an `Item` whose id also has a `Block` descriptor (i.e. a `BlockItem` companion) → an item
     model that just parents the block model (`{"parent":"<modid>:block/<id>"}`);
   - any other `Item` → an item model with a texture layer
     (`{"parent":"minecraft:item/generated","textures":{"layer0":"<modid>:item/<id>"}}`);
   - every descriptor whose type has a real vanilla translation key (`Block`, `Item`, `MobEffect`,
     `FluidType` — a raw `Fluid` doesn't; its name comes from its `FluidType`) → one entry in a
     single generated `lang/en_us.json`, keyed exactly the way that type's own
     `getDescriptionId()` builds it (`"block."`/`"item."`/`"effect."`/`"fluid_type." + modid +
     "." + path`, matching `Util.makeDescriptionId` — verified against vanilla source, not
     assumed), valued by title-casing the id (`ruby_block → "Ruby Block"`). A `BlockItem` is
     skipped here: its `getDescriptionId()` delegates to its block (also verified against source),
     so its own `item.<modid>.<id>` entry would just be dead JSON nobody ever reads. `PaintingVariant`
     has no translation key in 1.20.1 (verified against source — only `width`/`height` fields), so
     it produces no lang entry either.
   - a `ParticleType` → a `particles/<id>.json` (`{"textures":["<modid>:<id>"]}`) — the single-
     texture shape `net.minecraftforge.common.data.ParticleDescriptionProvider#sprite` produces
     (verified against source), required before `RegisterParticleProvidersEvent#registerSprite`
     can be used for it — see `AutoParticleRegistration` above.

   These are exactly the JSON shapes Forge's own datagen `BlockStateProvider`/`ItemModelProvider`/
   `LanguageProvider`/`ParticleDescriptionProvider` helpers produce for the same inputs — confirmed
   against real vanilla 1.20.1 assets and source, not guessed. Textures are **never** generated:
   the JSON just points at `assets/<modid>/textures/(item|block|particle)/<id>.png`, a real file
   the mod author supplies. If it's missing, Minecraft shows its normal missing-texture
   checkerboard (or, for a particle, logs a "missing particle sprites" warning and skips it) — the
   same fallback a hand-authored model/description with a bad texture reference would get. That's a
   content-authoring gap, not an engine bug.
3. **`DataGenerator`** is `AssetGenerator`'s `PackType.SERVER_DATA` counterpart: content that needs
   to function *in the world*, not render on screen. Today it does exactly one thing — every
   discovered `PaintingVariant` gets added to `data/minecraft/tags/painting_variant/placeable.json`
   (`"values"` array, `"replace": false`, merging with whatever else contributes to that tag rather
   than replacing it), for the reason explained above. New content types that need their own data
   (loot tables, recipes, other tags) extend this generator the same way `AssetGenerator` was
   extended for particles — a new branch over `ContentDiscovery.getDiscovered()`, no change to how
   either generator is invoked.
4. **Serving it** — `ResourcePacks.register(modEventBus)` listens for Forge's
   `AddPackFindersEvent`, which (verified against source: "fired on `PackRepository` creation")
   fires once per `PackType` — `CLIENT_RESOURCES` and `SERVER_DATA` are handled as two independent
   branches, each adding its own `Pack` backed by a `GeneratedResourcePack` built from the matching
   generator (`AssetGenerator` / `DataGenerator`) and tagged with its own `PackType`, so a single
   in-memory pack implementation never has to answer for both kinds of content at once. Both packs'
   `getResource`/`listResources` read straight from a `Map<ResourceLocation, byte[]>` — nothing is
   ever written to disk. Both are `required = true` (always enabled, no user action) and rebuilt
   fresh — a new call to the matching generator — every time resources/data (re)load, so they
   always reflect whatever's currently registered. This is also why a dedicated server, which never
   builds a `CLIENT_RESOURCES` pack repository at all, still correctly gets the `SERVER_DATA` one
   (verified live: a `runServer` smoke test logged `Generated 1 data-pack entr(y/ies)` and started
   cleanly with the painting tag merged in, no world-load warnings).

   Both are registered at **`Pack.Position.BOTTOM`** (changed from `TOP` while adding lang
   generation, once it actually mattered): `Pack.Position.insert` (verified against source) puts
   `TOP` packs at the *highest*-priority end of the pack list, which would mean the generated
   pack's own default always won over a mod author's own file for the same path — and, worse, for
   `lang/en_us.json` specifically, over their own value for the same translation *key*, since
   Minecraft merges language files across every contributing pack key-by-key (`ClientLanguage.
   loadFrom`/`appendFrom`, also verified against source) rather than picking one pack's whole file.
   `BOTTOM` — one step above vanilla's own always-bottom pack, below every mod's real resources —
   makes the generated pack a true *default*: a mod author overriding a specific model, or just one
   translation key, silently wins, with nothing to configure to get that behavior. For the data
   pack's tag file specifically, position doesn't even matter functionally — tag `values` merge
   across every contributing pack regardless of priority; only `"replace": true` entries are
   position-sensitive, and the generated tag never sets it.

`EngineBootstrap.init(modEventBus)` is what sequences all of this: `ContentDiscovery.run(...)`
first (discovery → registration → companions), then `ResourcePacks.register(...)` (arms both
generated-pack finders, which read `ContentDiscovery.getDiscovered()` later, once resources/data
actually load), then — only if `FMLEnvironment.dist == Dist.CLIENT` —
`AutoParticleRegistration.register(...)`. Mod authors call `EngineBootstrap.init(...)`, not any
subsystem directly — this is meant to remain the one constructor call as more subsystems need
their own wiring.

`runData` still exists and still works normally as a standard Forge dev tool (recipes, loot
tables, advancements, and anything else a mod builds real datagen providers for later) — the engine
simply never assumes it was run.

## `math` — vectors, interpolation, curves, transforms

The foundation for every future system that moves, blends, or shapes something over time or
space: cutscenes, camera paths, animation, FBX/GLTF/OBJ import, rendering/shaders, transforms,
particles, physics, UI motion, procedural generation. `math` has **no dependency on `content` or
`resource`** and nothing client-only in its core — only the usage examples touch client classes,
and only where the example itself is inherently client-side (a screen).

### What already existed — researched before writing anything

The instruction going in was not to duplicate Minecraft/Forge/JOML math, so before designing
anything the actual APIs were checked (via `javap` against the real Forge/JOML jars, not assumed):

| Already provided by | What it covers | Where `math` builds on it instead of reimplementing |
|---|---|---|
| `net.minecraft.util.Mth` | `lerp`, `clampedLerp`, `inverseLerp`, `map`/`clampedMap`, `smoothstep`, `wrapDegrees`, `rotLerp` (shortest-path angle lerp), `catmullrom` (scalar), `PI`/`DEG_TO_RAD`/... | `Interpolators.FLOAT`/`DOUBLE`/`ANGLE_DEGREES` call straight into it; `Numbers` only adds the handful of things missing |
| `org.joml.Vector2f`/`Vector3f`/`Vector4f` | `lerp(other, t)`, `lerp(other, t, dest)`, plus all the usual vector algebra | `Interpolators.VECTOR2F`/`VECTOR3F`/`VECTOR4F` delegate directly |
| `org.joml.Quaternionf` | `slerp(target, alpha, dest)`, `nlerp(...)`, `rotateX/Y/Z`, `rotationYXZ`, `fromAxisAngle*` | `Interpolators.QUATERNION_SLERP`/`QUATERNION_NLERP`; `Angles.fromYawPitchRoll` builds on `rotateY`/`rotateX`/`rotateZ`, verified against `Camera.setRotation`'s own `rotationYXZ(-yaw, pitch, 0)` call so it matches vanilla's camera convention exactly |
| `net.minecraft.util.FastColor.ARGB32` | `lerp(t, argbA, argbB)` | `Interpolators.ARGB_COLOR` |
| `com.mojang.math.Transformation` | translation/leftRotation/scale/rightRotation, `compose`, `inverse`, `slerp`, `getMatrix` | `Transform` (see below) is a plain single-rotation facade over it — `compose`/`inverse`/interpolation all delegate, none reimplemented |
| `Entity.xo`/`x`, `getPosition(partialTick)` | Minecraft's own per-field tick interpolation, hardcoded to `Entity` | `TickValue<T>` generalizes the same previous/current + partial-tick pattern to any type an engine subsystem owns |

Nothing in `math` reimplements any row of the right-hand column. What's missing from all of the
above — and what `math` actually adds — is a **type-agnostic interpolation and curve system**:
none of `Mth`, JOML, or `Transformation` let the same blend/curve code work across a float, a
`Vector3f`, a `Quaternionf`, a color, and a `Transform` uniformly. That's the actual gap this
package fills.

### The core idea: everything reduces to one pairwise blend

`interp.Interpolator<T>` is a single method: `T interpolate(T start, T end, float t)`. Every other
piece of `math` is built from nothing but repeated calls to it:

- **`Easing`** reshapes `t` before it reaches an interpolator (`Interpolator#eased(Easing)`) —
  pure `float → float`, no knowledge of `T` at all.
- **`BezierCurve<T>`** evaluates via de Casteljau's algorithm: blend adjacent points pairwise,
  one fewer each pass, until one point remains. De Casteljau never needs anything but pairwise
  blending — degree is just "how many passes."
- **`CatmullRomSpline<T>`** evaluates via the Barry-Goldman construction: six *nested* pairwise
  blends per sample (three first-level, two second-level, one final), using per-point knot values
  instead of a closed-form basis. This is the same trick as de Casteljau, generalized — it's why a
  spline that visibly passes through its control points doesn't need `T` to support addition or
  scalar multiplication, only `Interpolator<T>`.

The payoff: **rotation is correct by construction**, not by special-casing. A
`CatmullRomSpline<Quaternionf>` (or `<Transform>`, whose interpolator itself wraps a quaternion
slerp) never averages quaternion components at any nesting level — every blend, at every level of
the Barry-Goldman construction, is a proper `Quaternionf.slerp`. Naively lerping Euler angles, the
usual mistake, isn't structurally possible in this design because nothing in the curve code ever
touches yaw/pitch/roll directly.

A **B-spline was deliberately not added** in this pass. De Boor's recurrence is, like de Casteljau
and Barry-Goldman, expressible purely through nested `Interpolator<T>` calls — it would slot into
`curve` the same way, with the same guarantees — but B-splines don't interpolate their control
points (useful for smoothing an over-specified path, not for a keyframed camera move), and the
knot-vector bookkeeping has enough edge cases (clamping, multiplicity at the ends) that it wasn't
worth the risk of shipping an unverified implementation for a type of curve the roadmap doesn't
have an immediate use for. Bezier + centripetal Catmull-Rom already cover authored curves and
interpolated waypoint paths, the two cases cutscenes/camera-paths/animation actually need first.

### Package layout

```
com.dimalab.storymodengine.math
├── Angles.java              — Mth already covers wrapping/shortest-path/degree conversion; this
│                               adds the one real gap: Minecraft yaw/pitch ↔ Quaternionf
├── Numbers.java              — saturate, smootherstep, nearlyEqual(epsilon) — genuinely missing
│                               from Mth only; everything else, use Mth directly
├── Geometry.java             — closestPointOnSegment, rayPlaneIntersection (Mth.rayIntersectsAABB
│                               already covers ray/AABB)
├── interp/
│   ├── Interpolator.java     — the one primitive: interpolate(start, end, t), + dest-param
│   │                            overload for allocation-free hot paths, + .eased(Easing)
│   ├── Easing.java            — standard Penner set (quad/cubic/quart/quint/sine/expo/circ/back/
│   │                            elastic/bounce, each in/out/in-out) + .mirrored()/.reversed()
│   ├── Interpolators.java     — the built-in Interpolator<T> constants, one per type, each
│   │                            delegating to Mth/JOML/FastColor/Transformation
│   └── TickValue.java         — generic previous/current + partial-tick sampling (Entity's own
│                                pattern, made reusable for engine-owned state)
├── curve/
│   ├── Curve.java              — T sample(float t), t ∈ [0,1]
│   ├── BezierCurve.java        — any degree, via de Casteljau
│   ├── CatmullRomSpline.java   — uniform/centripetal/chordal, via Barry-Goldman
│   └── ArcLengthTable.java     — Vector3f-specific: constant-speed traversal of a Curve<Vector3f>
└── transform/
    └── Transform.java          — immutable T/R/S; thin facade over com.mojang.math.Transformation
                                   (compose/inverse/matrix all delegate, nothing reimplemented)
```

### Design decisions

- **Immutable by default, mutable escape hatch.** Every `Interpolator`/`Curve`/`Transform` call
  that returns a value allocates a fresh, independent one — safe to hand around, compare, cache.
  For a genuinely hot per-frame path (a camera updated every render tick), `Interpolator` also
  exposes `interpolate(start, end, t, dest)`; the JOML-backed built-ins in `Interpolators`
  override it to write into `dest` using JOML's own zero-allocation overloads. `BezierCurve`/
  `CatmullRomSpline#sample` still allocate a small scratch array per call — cheap for a handful of
  control points, but not zero-allocation; cache samples rather than re-evaluating a curve every
  frame in a hot loop.
- **JOML types passed and returned directly** (`Vector3f`, `Quaternionf`) rather than wrapped —
  they're already Minecraft/Forge's own vector/rotation types, used everywhere from `PoseStack` to
  entity rendering; wrapping them would just be friction at every integration point.
- **Tick interpolation.** `TickValue<T>` is the generic version of what `Entity` already does for
  position/rotation internally — advance with `tick(newValue)` once per game tick, read with
  `get(partialTick)` once per render frame. See `CameraPathAnimator` for it in the position/rotation
  case (there, folded directly into `Transform` sampling rather than via `TickValue`, since the
  camera path itself is already continuous in `t`).
- **Client/server safety.** `math`'s core has no `Dist.CLIENT`-only imports — `Vector3f`,
  `Quaternionf`, `Mth`, and `Transformation` are all common-side classes, so curve/transform math
  can run on a dedicated server (e.g. driving a server-authoritative cutscene) exactly as it can on
  the client. Only `math.example.EasingDemoScreen`/`MathUiCommand` are client-only, and are kept in
  their own files for exactly that reason.
- **Numerical precision.** Curves and transforms operate in `float` (JOML/rendering precision),
  matching what `PoseStack`/`Transformation`/entity rendering already use — not `double`. World
  positions (`Vec3`, `BlockPos`) are `double`; a curve driving something at huge world coordinates
  should convert to a local/relative space first (subtract a reference origin) rather than feeding
  absolute world coordinates through `Vector3f`, the same precision discipline vanilla rendering
  code already follows.
- **A future scripting language** can sit on top of this without any redesign: `Interpolator<T>`,
  `Easing`, and `Curve<T>` are all single-method functional interfaces, and `Interpolators`/`Easing`
  are just named constants of those interfaces — trivial to expose as script-callable values/
  functions once `scripting` exists, with no engine-side change needed.

### Usage examples (`math.example`)

- **`CameraPathCommand`** (`/storymodengine campath`) builds a closed loop of six `Transform`
  waypoints (position *and* rotation together) around the executing player and hands it to a
  `CatmullRomSpline<Transform>` using centripetal parametrization.
- **`CameraPathAnimator`** walks that spline one server tick at a time: an `ArcLengthTable` (built
  from a `Curve<Vector3f>` view of the same spline — `t -> path.sample(t).translation()`) converts
  an eased overall progress (`Easing.EASE_IN_OUT_CUBIC`) into constant-speed travel, and each tick's
  sampled `Transform` is decomposed back into position + yaw/pitch (`Angles.toYawPitch`) and applied
  via `ServerPlayer.teleportTo`. One spline sample drives both the "camera path" and the "transform
  interpolation" halves of the ask at once — that's the point of `CatmullRomSpline<Transform>`
  rather than a separate position curve and rotation curve.
- **`MathUiCommand`** (`/storymodengine mathui`) opens **`EasingDemoScreen`**, a real, visible UI
  animation: a button slides in from off-screen using `Interpolators.FLOAT` reshaped by
  `Easing.EASE_OUT_BACK` (the "overshoot and settle" easing), driven by `Screen#render`'s own
  `partialTick` rather than a fixed per-frame step — the same `Interpolator`/`Easing` pair as the
  camera path, applied to a widget position instead of a world position.

Known limitation, stated rather than hidden: `CameraPathAnimator` teleports the player every tick,
which will fight actual WASD input — it's a demonstration of the math, not a finished
cutscene-camera system (that's `render`/`narrative`'s future job, once they exist).

## `logging` — one log call, console and/or chat

The engine-wide logging API: `EngineLog.info("...")`/`.warn(...)`/`.error(...)`, matching the
literal request. Like `math`, `logging` has **zero dependency on any other engine package** and
nothing StoryModEngine-specific baked into its classes — the whole point is that another mod gets
the identical capability under its own name (`EngineLogger.of("TheirModName")`), not a system only
StoryModEngine itself can use.

### What already existed

Checked before writing anything, same as `math`: Minecraft/Forge already give a mod everything a
logging system needs to actually deliver a message — this package supplies *routing*
(console vs. chat, one player vs. everyone, level filtering), not a replacement for any of the
following:

| Already provided by | What it covers | Used instead of reinventing |
|---|---|---|
| `org.slf4j.Logger` (`LoggerFactory.getLogger(name)`) | Console/log-file output, level methods, thread-safety | `EngineLogger`'s console side is a thin call-through, nothing more |
| Log4j2's pattern layout (Forge's own `log4j2.xml`) | Bracketing the logger name in every console line (`[LoggerName/]: ...`) | Naming the SLF4J logger after the channel gets `[ChannelName]` in the console for free — no hand-formatted prefix on that side |
| `org.slf4j.helpers.MessageFormatter.arrayFormat` | `{}`-placeholder substitution | Reused directly for the `"text {}", value` overloads — no bespoke templating |
| `net.minecraft.network.chat.Component`/`MutableComponent`/`Style`/`ChatFormatting`/`TextColor` | Rich text, per-segment styling, legacy *and* 24-bit RGB color | `ChatTemplate` builds the prefix from these — one `Component` per visual segment, one `Style` each; a caller can also hand in their own `Component` directly (`EngineLog.info(Component...)`) instead of a plain string, and its own styling is preserved (`Style#applyTo`) |
| `ServerPlayer.sendSystemMessage(Component)` | Delivering a chat message to one player | `LogEntry.toChat(ServerPlayer)` |
| `PlayerList.broadcastSystemMessage(Component, boolean)` | Delivering a chat message to everyone online | `LogEntry.toChat()` |
| `ServerLifecycleHooks.getCurrentServer()` | Finding the running server (integrated *or* dedicated) from anywhere, or `null` if there isn't one | How `.toChat()` finds a `PlayerList` without needing a server reference threaded through every call site |
| `MinecraftServer.execute(Runnable)` / `.isSameThread()` (`BlockableEventLoop`) | Scheduling work onto the server thread from any thread, or running inline if already there | How chat dispatch stays thread-safe without a bespoke executor/queue |

### The design: the console write already happened by the time you have a return value

```java
EngineLog.info("Text Message");                           // console only — the literal example
EngineLog.warn("Something went wrong");
EngineLog.error("Something failed");

EngineLog.success("World generated").toChat();             // console + every player online
EngineLog.error("Save failed: {}", fileName).toChat(player); // console + one player
```

Every level method (`trace`/`debug`/`info`/`success`/`warn`/`error`) writes to the SLF4J logger
**immediately**, then returns a `LogEntry`. "Console only" needs no parameter, no flag, no second
call — it's just not chaining anything onto that return value. "Console and chat simultaneously"
is chaining `.toChat()`/`.toChat(ServerPlayer)`/`.toChat(Collection<ServerPlayer>)` onto the same
call — there's no separate "chat mode," only an additional destination layered onto a log that
already happened. This is what keeps the API matching the requested three-line shape exactly while
still covering every destination combination asked for (one player / everyone / console only /
both at once) with a single set of methods, not a parallel one per destination.

A level filtered out by `EngineLogger.setMinimumLevel(...)` returns a shared `LogEntry.noop()`
instead — every chat method on it is a no-op, so filtering suppresses console *and* chat uniformly,
with nothing for a caller to check.

### Chat formatting: one Component segment per visual piece, one Style each

The default chat line is `[StoryModEngine]: Text Message`, and it is **assembled**, not colored:

```java
Component.empty()
    .append(Component.literal("[").withStyle(bracketStyle))
    .append(Component.literal("StoryModEngine").withStyle(nameStyle))
    .append(Component.literal("]").withStyle(bracketStyle))
    .append(Component.literal(": ").withStyle(separatorStyle))
    .append(message.copy().withStyle(message.getStyle().applyTo(resolvedMessageStyle)));
```

— five independent `Component` siblings, five independent `Style`s, joined with
`MutableComponent.append`. This was verified structurally (not just visually): rendering the
default template and walking `getSiblings()` produces exactly

```
'['              → gold
'StoryModEngine' → gray
']'              → gold
': '             → gold
'Text Message'   → white   (INFO's level color — no explicit message color set)
```

i.e. the color lives on each segment's `Style` object, never in the text itself the way
`"§6[§7StoryModEngine§6]: "` would.

`ChatTemplate` is the class that owns this — immutable, one `with*` method per configurable piece:
`withBracketStyle`/`withNameStyle`/`withSeparatorStyle`/`withMessageStyle` (all take a `Style`, so
color can be `Style.EMPTY.withColor(ChatFormatting.GOLD)` *or* `Style.EMPTY.withColor(0xFF8800)` —
a real 24-bit RGB `TextColor`, not just the 16 legacy colors — plus `.withBold(true)`/
`.withItalic(true)`/`.withUnderlined(true)` composed the same way), and `withBrackets(open, close)`/
`withSeparator(text)` for the literal characters themselves. `ChatTemplate.DEFAULT` is the format
above; `EngineLogger.setChatTemplate(...)` attaches a customized one per channel — independent of
every other channel's template, including `EngineLog`'s own.

The message segment specifically: if a caller passes a plain string, it gets the template's
`messageStyle` outright (color falling back to `LogLevel.color()` when `messageStyle` doesn't set
one — keeping the six `ChatFormatting` level colors as the *default*, per the request, while still
letting a template override them). If a caller passes their own `Component` — already styled, or
built from several differently-styled children — `Style#applyTo` merges the template's style in
only where the caller left something unset, so an explicitly-styled message (like
`LogTestCommand`'s "bold red text" example) is never silently overwritten by the template.

### Package layout

```
com.dimalab.storymodengine.logging
├── LogLevel.java      — TRACE/DEBUG/INFO/SUCCESS/WARNING/ERROR, severity + default ChatFormatting each
├── LoggingConfig.java  — the two global knobs: chat kill-switch, default level for new channels
├── ChatTemplate.java    — the five-segment Component/Style assembly described above
├── EngineLogger.java     — a named channel: console logger + independent level filter + ChatTemplate
├── LogEntry.java          — returned by every log call; console already written, chat is opt-in via .toChat*
├── EngineLog.java          — the static facade for StoryModEngine's own "StoryModEngine" channel
└── example/
    └── LogTestCommand.java — /storymodengine logtest, exercises every capability at once
```

### Design decisions

- **Channels, not a singleton.** `EngineLogger.of(name)` returns (creating on first use) an
  independent channel — its own SLF4J logger, its own minimum level, its own `ChatTemplate`.
  `EngineLog` is nothing but the pre-built channel named `"StoryModEngine"`, and
  `EngineLog.channel("Story")` is a shorter spelling of `EngineLogger.of("Story")` sitting next to
  it. Another mod calling `EngineLogger.of("TheirModName")` gets the identical API, its own prefix,
  and its own independently-stylable template under its own name; a future engine subsystem
  wanting its own category (`content`/`network`/`rendering`/`narrative`, as asked about) does the
  same — `EngineLogger.of("StoryModEngine/Network")`, no core class touched. This is the extension
  point for "logging channels"/"categories" without inventing either concept as a separate feature.
- **`SUCCESS` maps to SLF4J `INFO`** on the console side (SLF4J has no such level) with a
  `[SUCCESS]` marker in the line, and to a distinct green chat color — a styling choice, not a
  distinct severity tier (see `LogLevel`'s Javadoc for why it shares `INFO`'s filtering severity).
- **Chat dispatch is server-authoritative**, matching how chat actually works in Minecraft — a
  message is delivered *through* the server's `PlayerList`/`ServerPlayer`s whether the world is
  singleplayer (integrated server) or a dedicated server; `ServerLifecycleHooks.getCurrentServer()`
  finds the right one either way. **No player on the server** (a dedicated server nobody has
  joined yet, or a `.toChat(player)` for someone who disconnected) is a silent no-op, not an
  exception — `broadcastSystemMessage` over an empty list, or a null-checked player, both do
  nothing safely. A pure client-local echo with no server context at all (e.g. a menu-screen debug
  message) isn't implemented — a deliberate scope decision, not an oversight; see below.
- **Thread safety**: the channel registry is a `ConcurrentHashMap`, `minimumLevel`/`chatEnabled` are
  `volatile`, SLF4J loggers are thread-safe by contract, and `LogEntry`'s chat dispatch always runs
  on the server thread (inline if already there, via `MinecraftServer.execute` otherwise) — so
  calling `EngineLog.warn(...).toChat()` from a background thread (an async task, a mixin
  callback, anything) never races the player list.
- **Global chat kill-switch and default level** live in `LoggingConfig` as plain volatile fields,
  not a `ForgeConfigSpec` — a real, persisted config is a mod's own concern (read it during setup,
  call `LoggingConfig.setChatEnabled(...)`/`EngineLogger.setMinimumLevel(...)`); baking one
  specific config format into a shared library other mods are meant to use would be the wrong
  layer for it.
- **Deliberately not built yet, but the shape already supports it without a redesign**, matching
  what was asked to keep in mind: a **GUI/overlay logger** or **in-game debug console** would be
  another `LogEntry` destination method (`.toOverlay()`) next to `.toChat()`, nothing about
  `EngineLogger`/`LogLevel` would change; **file logging** is Log4j2's own job — point the
  appender in Forge's `log4j2.xml`/config at the channel names, no engine code involved; a
  **scripting API** can call `EngineLogger.of(name).info(...)` directly, since it's already a
  plain object with plain methods, not something wired through annotations/events; per-subsystem
  **channels/categories** already work today, as above.

`LogTestCommand` (`/storymodengine logtest`) exercises all six levels, a placeholder, a raw
`Component`, a `.color(...)` override, `.toChat()`, `.toChat(ServerPlayer)`, and a second named
channel in one place — see its Javadoc for exactly which line demonstrates which requirement.
Verified on a genuine dedicated server (`runServer`, `--nogui`, zero players ever connected): the
mod loads, registers the command, and reaches `Done (...)! For help, type "help"` with no
exceptions — confirming nothing in `logging` touches a client-only class at load time, the actual
risk this requirement was checking for.

## `network` — packets that are just records

`Network.sendToServer(new PingServer(...))`. A `@Packet` record describes data (its components)
and, optionally, behavior (`implements PacketHandler`, `handle(PacketContext)` right there on the
record) — everything Forge actually requires to move bytes between client and server (a
discriminator id, an encoder, a decoder, a `NetworkEvent.Context`-shaped handler, a registered
`SimpleChannel`) is inferred and hidden. No mod-author code anywhere mentions `SimpleChannel`,
`FriendlyByteBuf`, `NetworkEvent.Context`, or `PacketDistributor`.

### What already existed

Checked before writing anything (`javap`/decompiled source from the real
`forge-1.20.1-47.4.22...-sources.jar`, not assumed):

| Already provided by | What it covers | Used instead of reinventing |
|---|---|---|
| `net.minecraftforge.network.simple.SimpleChannel` | Discriminator-multiplexed message registration, encode/decode dispatch, reply routing | The actual backend — `NetworkBootstrap` builds one per mod and feeds it every discovered packet via `messageBuilder(...)` |
| `net.minecraftforge.network.NetworkRegistry.newSimpleChannel` | Channel creation + version-predicate negotiation | Called once, synchronously, in `NetworkBootstrap.init` — verified this must happen before Forge's registry-phase lock, same constraint as `DeferredRegister` |
| `net.minecraftforge.network.PacketDistributor` (`PLAYER`/`ALL`/`SERVER`/`TRACKING_ENTITY`/`TRACKING_ENTITY_AND_SELF`) | Every routing target the task asked for, already built | `Network`'s five `sendTo*` methods are thin calls into these — no custom routing logic |
| `NetworkHooks.validatePacketDirection` | Disconnects a connection that sends a packet the wrong declared direction (verified against source) | `PacketDescriptor`'s optional `NetworkDirection`, passed straight through — real spoofing protection, not reimplemented |
| `FriendlyByteBuf` (`UUID`/`ResourceLocation`/`BlockPos`/`Component`/`ItemStack`/`CompoundTag`/`Vector3f`/`Quaternionf`/`VarInt`/`VarLong`) | Read/write for most of the required "supported out of the box" type list | `serialization/serializers` delegates to these directly; only `Vec3`/`Vec2`/`Vector2f`/`Vector4f` (no built-in pair) and `BlockState` (global palette id via `Block.BLOCK_STATE_REGISTRY`, verified against `Block` source) are written by hand |
| `Class#isRecord()`/`getRecordComponents()`/`java.lang.invoke.MethodHandles` | Enumerable, typed component state for any record | `PacketSerializer` composes a `Serializer<T>` from these — the same reflective approach `ContentDiscovery` already uses (`Field#getGenericType()`) for a different purpose |

### Why `@Packet` requires a record, and why behavior lives on it too

"The developer describes only data and behavior" is not a slogan here — it dictated two concrete
constraints. A `record`'s components are its entire enumerable state (`getRecordComponents()`),
which is what makes fully automatic serialization possible without a schema declared anywhere
else; a non-record `@Packet` type is rejected by `PacketDiscovery` with a warning, the same
forgiving-skip `ContentDiscovery` already uses for a malformed `@AutoContent` field, not a hard
crash. And rather than a separate handler-registration call, a packet implements `PacketHandler`
(`void handle(PacketContext context)`) directly — `this` already has every component, so "data and
behavior" really do stay in the same file, with no lambda handed to the engine from somewhere else.
A packet that implements neither is still valid (sendable, decodable) — `PacketRouter` just logs
`debug` and drops it, a legitimate shape for a future subsystem that reacts to packets some other
way (an event, a queue) rather than a mistake.

### Direction, without forcing it

The task explicitly said not to force a direction parameter if it can be "determined or configured
another way." The other way: `ServerboundPacket`/`ClientboundPacket` are optional marker interfaces
a record can implement. Neither implemented → `PacketDirection.BIDIRECTIONAL`, which maps to *no*
Forge-level direction check at all (`Optional.empty()`) — `PacketHandler#handle` must itself branch
on `PacketContext#isClient()`/`isServer()` if behavior differs by side. Either marker implemented →
the matching `NetworkDirection` is passed to `messageBuilder`, and Forge itself disconnects a
connection that violates it (verified — not custom code). Both markers implemented is a discovery
error, skipped with a warning, the same as a non-record type.

### Automatic serialization

`serialization/SerializerRegistry` resolves a `Serializer<T>` from a field's `java.lang.reflect.Type`
(not just `Class`, so `List<UUID>` and `List<ItemStack>` — same raw class, different element
serializer — resolve correctly, via the same generic-reflection approach `ContentDiscovery` already
leans on). Direct hits (primitives, `String`, the vanilla/JOML types above) come from
`serialization/serializers/*`; everything else is composed on demand:

- **Arrays** — length-prefixed, element serializer resolved from the component type.
- **Enums** — ordinal as a `VarInt`, validated against the actual constant count on read (an
  out-of-range ordinal is treated as a malformed packet, not an `ArrayIndexOutOfBoundsException`).
- **`List`/`Set`/`Map`** — size-prefixed (`VarInt`), each element/entry via its own resolved
  serializer. Every size read off the wire is checked against `SerializerRegistry.MAX_COLLECTION_SIZE`
  (65536) before any allocation — a deliberately cheap guard against a malformed or hostile length
  header driving a huge allocation from a single packet, not a substitute for real rate limiting.
- **`Optional<T>`** — this engine's nullable-value convention: a presence `boolean`, then the value
  if present. A raw nullable reference field (no `Optional`) is not supported — encoding `null`
  generically is ambiguous for an arbitrary serializer, so this is the one explicit, documented
  convention rather than a silent trap.
- **Any `record`** — recursively, via `PacketSerializer` (the same class that composes a top-level
  `@Packet` record) — which is why `math.transform.Transform` (`record Transform(Vector3f, Quaternionf, Vector3f)`)
  needs no entry of its own: once `Vector3f`/`Quaternionf` are registered, its components resolve,
  and the record fallback does the rest. This is the concrete answer to "networking must be able to
  automatically serialize math types" — `math` gained no networking dependency to make it happen.
- **`EntityRef`** — the answer to "Entity references." A raw `Entity` field is deliberately *not*
  auto-serializable: `SimpleChannel`'s decoder is a plain `Function<FriendlyByteBuf, MSG>` (verified
  against source) with no `Level` to resolve an id against on either side, and a server can have
  several levels — blind auto-resolution at decode time would be exactly the kind of
  automatic-for-convenience unsafety the security section says not to do. `EntityRef` carries just
  the network id; `resolve(Level)` — called explicitly inside `handle`, where `PacketContext.level()`
  already has the right answer — is the actual (safe) resolution point.
- **Extension point** — `SerializerRegistry.register(Class<T>, Serializer<T>)`, exactly as asked,
  usable for any type this list doesn't cover, without touching anything above it.

### Security — tools, not automatic decisions

The task was explicit: don't auto-validate sender/permissions/distance/entity-existence/loaded
chunks/rate limits "just for convenience." What's actually built: `PacketContext.sender()` (only
populated server-side) and `.level()` are available for a handler to check itself; malformed input
can't crash anything (see below); collection sizes are capped before allocation (see above). What's
deliberately *not* built: any of the specific checks the task listed — those stay in each packet's
own `handle`, where only that packet's author knows what "valid" means for it.

**Malformed-packet safety** relies on `IndexedMessageCodec#tryDecode` (verified against source)
wrapping the decoder's result in `Optional.map` — since `Optional.map` treats a `null` return as
"map to empty," a decoder that catches its own exception and returns `null` makes Forge silently
skip the message consumer entirely: no crash, no disconnect, nothing thrown into Netty.
`PacketRouter.decode` does exactly that (logs a `warn`, returns `null`); `PacketRouter.route` wraps
a handler's `handle` call the same defensive way, so one broken `PacketHandler` can't take the
network thread — or the main thread, for `consumerMainThread`-dispatched packets — down with it.
Verified live: a temporary smoke test fed a truncated buffer and a hostile `Integer.MAX_VALUE`
collection-size header through `PacketRouter.decode` on a real `runServer` instance — both logged a
`warn` and returned `null`, no exception escaped.

**Extension points for later** (not built now, per the task's own future-proofing list):
`PacketRouter` is the single funnel every inbound packet passes through, specifically so rate
limiting, a permission gate applied before any handler runs, or size-based rejection beyond the
collection cap can attach there later without touching `@Packet` records or `NetworkBootstrap`'s
wiring. `PacketRegistry`'s sequential 0..255 ids (`IndexedMessageCodec`'s discriminator is a single
unsigned byte, verified against source — 256 packets per channel, one channel per mod) are
registration-order-based, stable within a build but not guaranteed across mod versions if packets
are added/removed — real protocol versioning, if ever needed, is a `NetworkBootstrap` change, not a
public-API one.

### Package layout

```
com.dimalab.storymodengine.network
├── annotation/
│   └── Packet.java             — @Packet, TYPE, RUNTIME, no parameters
├── PacketHandler.java           — void handle(PacketContext); a record implements this directly
├── ServerboundPacket.java       — marker: this record may only travel CLIENT_TO_SERVER
├── ClientboundPacket.java       — marker: this record may only travel SERVER_TO_CLIENT
├── PacketDirection.java         — CLIENT_TO_SERVER / SERVER_TO_CLIENT / BIDIRECTIONAL
├── EntityRef.java                — record EntityRef(int id) { Optional<Entity> resolve(Level) }
├── discovery/
│   └── PacketDiscovery.java     — ModFileScanData scan for @Packet (TYPE) — same mechanism as
│                                    ContentDiscovery, filtered to a different ElementType
├── registry/
│   ├── PacketRegistry.java      — per-mod: assigns ids 0..255, holds PacketDescriptors
│   └── PacketDescriptor.java    — record(id, type, Serializer<T>, PacketDirection)
├── serialization/
│   ├── Serializer.java          — write(T,buf)/read(buf) — same one-method shape as
│   │                                math.interp.Interpolator<T>, for the same reason
│   ├── SerializerRegistry.java  — Type → Serializer<?>; register(Class,Serializer) extension point;
│   │                                resolves List/Set/Map/arrays/enums/Optional/records
│   ├── PacketSerializer.java    — composes a record's Serializer<T> via MethodHandles over its
│   │                                components — reused for top-level packets and nested records
│   └── serializers/
│       ├── PrimitiveSerializers.java  — boolean/byte/short/char/int/long/float/double/String
│       ├── MinecraftSerializers.java  — UUID/ResourceLocation/BlockPos/Vec3/Vec2/ItemStack/
│       │                                 Component/BlockState/CompoundTag
│       └── MathSerializers.java       — Vector2f/Vector3f/Vector4f/Quaternionf (JOML)
├── context/
│   ├── PacketContext.java       — sender()/level()/direction()/isClient()/isServer()/enqueue(...)
│   └── ClientLevelLookup.java   — package-private; isolates the one client-only field access
│                                    level() needs — see "A real dist-loading bug" below
├── routing/
│   └── PacketRouter.java        — decode+dispatch funnel; malformed-packet safety; EngineLog
│                                    debug/trace ("Received X ← CLIENT/SERVER")
├── Network.java                  — sendToServer/sendToPlayer/sendToAll/sendToTracking/
│                                    sendToTrackingAndSelf — the entire public API
├── NetworkBootstrap.java         — per-mod: builds the SimpleChannel, feeds it every
│                                    PacketDescriptor, called from EngineBootstrap.init
└── example/                      — PingServer/PongClient (C2S↔S2C round trip), SyncPlayerState
                                     (the task's own literal example), BroadcastAnnouncement
                                     (String/List/Optional/JOML in one packet); NetworkTestCommand
                                     (/storymodengine nettest broadcast|sync, server) and
                                     NetworkTestClientCommand (.../nettest ping, client)
```

One channel per mod (`<modid>:main`), not one global channel — mirrors `ResourcePacks`/
`AssetGenerator` already being per-`modId` rather than global, and keeps `PacketRegistry`'s 0..255
id space scoped to what one mod actually needs. `Network`'s static `Class<?> → SimpleChannel` map is
what lets the shared, mod-agnostic `Network.sendToServer(packet)` call still find the right channel
without the caller ever naming one — populated once per packet when that packet's own mod calls
`EngineBootstrap.init(...)`.

`NetworkBootstrap.init` runs with no `Dist` gate, unlike `AutoParticleRegistration` — channel
creation and message registration are common-side Forge APIs (verified against source: nothing in
`NetworkRegistry`/`SimpleChannel`'s registration path touches a client-only class), so they behave
identically on a dedicated server. Verified live: `runServer` (`--nogui`, real dedicated server)
discovered and registered all 4 example packets and reached `Done (...)!`, then separately ran the
serialization round-trip and malformed-packet smoke tests described above with no client classes on
the classpath at runtime.

### A real dist-loading bug, found live and fixed

The `runServer` verification above proved registration and serialization worked, but not the full
round trip through a real client↔server connection — that surfaced a genuine bug the isolated
smoke tests couldn't catch. `PacketContext.level()` originally read the client case inline:

```java
return isClient() ? Minecraft.getInstance().level : null;
```

`Minecraft.level`'s declared type is `ClientLevel`, a true `@OnlyIn(Dist.CLIENT)` type — and even
though the ternary only ever *evaluates* that branch on the client, the compiled bytecode of
`PacketContext.level()` still *references* `ClientLevel`'s type descriptor directly, in a class
(`PacketContext`) that's loaded unconditionally on both sides, for every packet, with no `Dist`
gate anywhere above it. Forge's `RuntimeDistCleaner` scans a class's bytecode for `@OnlyIn`-
mismatched type references the moment that class *loads* — not when the specific branch containing
the reference actually executes — so on the dedicated server, the first packet ever routed there
(`PingServer`, the one example packet actually processed server-side; the other three are S2C and
so construct their `PacketContext` client-side, where the reference is legal) triggered:

```
[Server thread/ERROR] [RuntimeDistCleaner/DISTXFORM]: Attempted to load class
net/minecraft/client/multiplayer/ClientLevel for invalid dist DEDICATED_SERVER
```

`new PacketContext(forgeContext)` — the very first line of `PacketRouter.route`, *before* its own
try/catch — then threw a `NoClassDefFoundError`. That's a `LinkageError`, not an `Exception`, so
nothing in this codebase caught it; it propagated out of the `consumerMainThread` callback and was
swallowed by Forge's own async work-queue machinery with nothing more than that one console line —
no warning, no chat message, `PingServer.handle()` never even started. Exactly the kind of silent
failure `/storymodengine nettest ping` was built to catch, and did: `sync`/`broadcast` (processed
client-side) worked from the first live test; `ping` (the only server-processed packet) didn't,
and the one clue was a single `RuntimeDistCleaner` line in the *server* console the client-side
view of the bug gave no hint of.

The fix is the same shape already used for `AutoParticleRegistration`, applied one level deeper:
`Minecraft.getInstance().level` moved into `ClientLevelLookup`, a small class `PacketContext.level()`
only ever calls behind its own `isClient()` check. `PacketContext`'s own bytecode now has no direct
reference to `ClientLevel` at all — only to `ClientLevelLookup`, an ordinary type name with nothing
dist-restricted about it — so `RuntimeDistCleaner` finds nothing to object to when `PacketContext`
loads on the server; `ClientLevelLookup` itself is only ever loaded (and only ever has *its* bytecode
scanned) on the client, since that's the only place the call to it is ever reached. The general
lesson, worth remembering for anything added to `context`/`routing` later: a class loaded
unconditionally on both sides must never contain a direct bytecode reference to a client-only type,
even inside a runtime-dead branch — the guard has to be "this class is never loaded on the wrong
side," not "this branch never runs there." `AutoParticleRegistration` already got this right by
being gated at its *call site*; `PacketContext` needed the same discipline applied to a *method
inside* an always-loaded class, one level more subtle, which is exactly what an isolated
`javap`/unit-style check couldn't have surfaced without an actual client-server connection.

## `capabilities` — persistent, synchronized mod-author data

```java
@Capability(sync = true)
public static final CapabilityData<StoryPlayerData> STORY_DATA = CapabilityData.of(StoryPlayerData::new);
```

```java
StoryPlayerData data = Capabilities.get(player, StoryPlayerData.class);
data.storyPoints += 10;
Capabilities.markDirty(player, StoryPlayerData.class);
Capabilities.sync(player, StoryPlayerData.class);
```

A mod author writes a plain mutable POJO (`StoryPlayerData implements EntityCapability`), a
one-line `@Capability` field declaring it, and gets automatic Forge `Capability<T>` handling,
`AttachCapabilitiesEvent` wiring, NBT persistence, and — opt-in — network synchronization, for
`Entity`, `BlockEntity`, or `Level` owners. `Capability<T>`, `LazyOptional<T>`,
`AttachCapabilitiesEvent`, `ICapabilitySerializable` — every Forge capability type — stay behind
`Capabilities`/`CapabilityData`; a mod author never imports `net.minecraftforge.common.capabilities`.

### What already existed

| Already provided by | What it covers | Used instead of reinventing |
|---|---|---|
| `Entity`/`BlockEntity`/`Level` all extending `CapabilityProvider<B>` | The attach point itself — verified against source, symmetric across all three owner kinds, no special-casing needed | `CapabilityStorage` is one `ICapabilitySerializable` shape that works unmodified for all three |
| `Entity#serializeCaps`/`deserializeCaps` under `"ForgeCaps"`, called from `Entity`'s own save/load (verified — `Entity.java:1658-1659,1739`); same for `BlockEntity` | Automatic NBT persistence for Entity/BlockEntity — no manual save-hook | `CapabilityStorage` only implements `serializeNBT`/`deserializeNBT`; nothing calls them by hand |
| `net.minecraftforge.common.util.LevelCapabilityData extends SavedData` (`ServerLevel.java:1422`) | Automatic per-dimension persistence (`data/capabilities.dat`) for `Level` owners | Same `CapabilityStorage`, no Level-specific code needed |
| `PlayerEvent.Clone` + `CapabilityProvider#reviveCaps`/`invalidateCaps` | The documented pattern for copying capability data across a replaced `Player` instance | `CapabilityLifecycle#onPlayerClone` uses exactly this pattern |
| `network`'s `SerializerRegistry`/`Serializer<T>` | Typed, recursive encode/decode for arbitrary field graphs | Reused directly for both NBT and network payloads (see below) — no second serialization system |

### Why there's exactly one `Capability<T>` for the whole engine

`CapabilityManager.get(new CapabilityToken<T>(){})` resolves `Capability<T>` through an ASM
transformer that reads the anonymous class's captured generic parameter at a fixed source
location — verified against source, this makes `Capability<T>` for a given `T` a single,
JVM-wide object, not something a reflective per-`@Capability`-class factory could mint at runtime
(generic erasure makes that fundamentally unavailable, not just inconvenient). So the engine
declares exactly one, `CapabilityLifecycle.CAPABILITY: Capability<CapabilityStorage>`, and every
mod author's data class lives *inside* the single `CapabilityStorage` attached to each owner —
`Map<Class<?>, Object>`, keyed by data type — rather than getting its own `Capability<T>`.
`CapabilityRegistry` is consequently **global**, not per-mod (unlike `PacketRegistry`, which is
per-mod-channel): every mod using this engine shares the one dispatcher and the one attach
listener set, registered exactly once (`AtomicBoolean` guard in `CapabilityLifecycle.registerOnce`).

Naming note: the task's suggested `CapabilityManager.java`/`CapabilityProvider.java` collide
directly with Forge's own `net.minecraftforge.common.capabilities.CapabilityManager`/
`CapabilityProvider`, which this package calls. Renamed to `CapabilityLifecycle` (the Forge-bus
attach/clone wiring) and `CapabilityStorage` (the real `ICapabilitySerializable`) — the task
explicitly allowed better names where the architecture calls for it. Separately, `@CapabilityData`
(annotation) can't coexist with a `CapabilityData<T>` class in the same file (Java forbids two
imported types under one simple name), so the annotation is `@Capability` and the handle class
kept the requested name `CapabilityData<T>`.

### Persistence: one encode path for disk and wire

`Serializer<T>` (from `network`) already writes to a `FriendlyByteBuf`; `capabilities` adds
`Serializer#toBytes(T)`/`fromBytes(byte[])` default methods bridging that to a raw `byte[]`, and
stores one `byte[]` per registered data type in a `CompoundTag`, keyed by
`descriptor.id().toString()`. The exact same `Serializer<T>` — resolved once by the existing
`SerializerRegistry.resolve(Type)` — encodes a data instance for NBT *and* for the network sync
packet's payload. The literal architecture the task specified —
`Capability → CapabilitySerializer → existing SerializerRegistry → Packet → existing Network
system` — is what this produces: no second serialization framework, no NBT-native codec written by
hand. The tradeoff, stated rather than hidden: NBT stores an opaque `byte[]` per capability, not
human-readable per-field tags — accepted deliberately to keep exactly one code path.

`SerializerRegistry` itself gained one new fallback for this: capability data classes are mutable
POJOs (`public int storyPoints;`), not the immutable `record`s `PacketSerializer` composes.
`PojoSerializer` (reflection over public non-static non-final fields + a public no-arg
constructor, via `MethodHandles`, the same technique `PacketSerializer` already uses for records)
is `resolveClass`'s third fallback — record → `PacketSerializer`, plain mutable class →
`PojoSerializer` — an extension of the existing registry, not a parallel one. Every leaf/nested
field type (including `math`'s `Vector3f`/`Quaternionf`, if a capability class has a field of that
type) still resolves through the same `SerializerRegistry.resolve(Type)` recursively, so math
types serialize inside a capability for free — no new code required to support that.

**Per-descriptor fault isolation**, matching `PacketRouter`'s established discipline: both
`serializeNBT`/`deserializeNBT` wrap each descriptor's encode/decode in its own try/catch, logging
`error` via `EngineLog.channel("Capabilities")` and either skipping that entry (write) or keeping
the previously-constructed default (read) — one corrupted or unreadable field never takes the rest
of the owner's data down with it. `CapabilitySyncPacket.apply` does the same on the network side.

### Synchronization: explicit dirty state, server-authoritative by construction

`Capabilities.markDirty(owner, Class<?>)` is a call-site signal, not a proxied/automatically-
tracked flag — transparently intercepting arbitrary mutable field writes on an arbitrary POJO
without bytecode instrumentation isn't attempted here (an explicit extension point for later, see
below). Persistence itself needs no dirty flag at all: `Entity`/`BlockEntity` save unconditionally
whenever the game saves, and `CapabilityStorage` inside them does too — `markDirty` only matters
for deciding *when to sync over the network*, which is genuinely worth avoiding every tick.

`Capabilities.sync(owner, Class<?>)` is implemented for `Entity` owners today — `sendToPlayer` when
the owner is the `ServerPlayer` itself, `sendToTracking` otherwise — both existing `Network`
methods. `BlockEntity`/`Level` sync is *not* wired to a network target yet: `Network`'s facade has
no "players near this `BlockPos`" / "players in this `Level`" method (Forge's own
`PacketDistributor.NEAR`/`TRACKING_CHUNK` exist but aren't exposed), and adding one without a real
use case would be exactly the over-engineering the task warned against. `Capabilities.sync` for
those owner kinds logs a `warn` naming this as a documented extension point rather than silently
no-oping or throwing.

**Client → server is deliberately narrow.** The only packet in that direction,
`CapabilityResyncRequest`, carries a `dataId` and nothing else — never a value. The server
validates it (`sync == true`, `ownerKind == ENTITY`, sender non-null) and replies with its own
authoritative `CapabilitySyncPacket`. There is no generic "client writes a capability value"
endpoint anywhere in this package — the task's "must be explicit and validated, never trust
client-written data" requirement is satisfied by that endpoint not existing, not by validating one
that does. A game feature that genuinely needs client-initiated mutation (a GUI selection, for
example) is a separate, specifically-validated packet for that feature, not a capability-system
primitive.

### Lifecycle, and a real bug found live: `LazyOptional` doesn't survive `invalidate()` + `revive()`

`AttachCapabilitiesEvent<Entity/BlockEntity/Level>` constructs one `CapabilityStorage` per owner
(skipped entirely if `CapabilityRegistry.forOwner(kind)` is empty — no wasted attachment for a mod
that only declares, say, `Entity`-kind data). `PlayerEvent.Clone` — fired when a `Player` instance
is replaced — is handled by copying `instances` from the original to the fresh player, bracketed by
`original.reviveCaps()`/`invalidateCaps()`, the Forge-documented pattern for reading from an
already-invalidated provider.

Verified live (the required in-game test, not just compilation): points added via
`/storymodengine capability add` **initially did not survive** two of the three required
scenarios — reset to 0 across death/respawn, and threw inside the command entirely (Brigadier's
generic "unexpected error") after any Nether/End portal trip — despite passing every offline
round-trip/malformed-NBT smoke test. Root-caused by reading `CapabilityProvider`/`LazyOptional`
source directly rather than guessing:

- `Entity#remove(RemovalReason)` calls `invalidateCaps()` **unconditionally**, for *every* removal
  reason — including `CHANGED_DIMENSION`, where `ServerPlayer#changeDimension` reuses the *same*
  entity instance and calls `this.revive()` immediately after (verified against
  `ServerPlayer.java`/`PlayerList.java` source: portal travel never constructs a new `ServerPlayer`;
  only death/respawn does, via `PlayerList#respawn`'s `new ServerPlayer(...)`).
- `CapabilityProvider#invalidateCaps()` calls `disp.invalidate()`, which runs every registered
  `Runnable` listener — including `CapabilityStorage`'s own `event.addListener(storage::invalidate)`,
  which called `self.invalidate()` on its single, field-held `LazyOptional`.
- `LazyOptional#invalidate()` sets `isValid = false` **permanently** — verified against source,
  there is no un-invalidate. `CapabilityProvider#reviveCaps()` only flips the *entity's own* outer
  `valid` gate; it does not, and cannot, revive a specific provider's already-dead `LazyOptional`.

So after **any** dimension change — not just death — `CapabilityStorage`'s `self` field stayed
permanently dead, and `owner.getCapability(CAPABILITY)` returned empty for the rest of that
entity's life, even though the underlying `instances` map (the actual data) was never touched and
never lost. `Capabilities.get(...)` returning `null` is exactly what surfaced as an NPE inside
`CapabilityTestCommand`; the same dead `self` is why the `PlayerEvent.Clone` copy silently found
nothing to read on respawn.

**Fix**: `CapabilityStorage#getCapability` recreates `self` lazily —
`if (!self.isPresent()) self = LazyOptional.of(() -> this);` — before returning it. Safe precisely
because invalidation only ever kills the *wrapper*, never the `instances` map it wraps; recreating
the wrapper on next access loses nothing and correctly re-enables both the revive-and-copy window
around `PlayerEvent.Clone` and ordinary same-instance access after ordinary portal travel. Re-tested
live after the fix: points survive Nether/End travel, death/respawn, and disconnect/reconnect, in
that order, in one continuous session. The general lesson, worth remembering for any future
`ICapabilityProvider` in this engine: **never hold a single one-shot `LazyOptional` as a field
across an owner's full lifetime** — Forge's invalidate/revive cycle fires for routine, non-destructive
events (dimension travel), not only true teardown, so any capability implementation must be able to
mint a fresh `LazyOptional` after invalidation, not just after construction.

### Security

Same posture as `network`: malformed NBT and malformed network payloads are caught per-descriptor
(above) and logged via `EngineLog.channel("Capabilities")`, never thrown into save/load or packet
routing. The C2S direction has no value-carrying endpoint at all (above), which is the actual
security boundary — there's nothing to validate-and-reject because there's nothing to receive.

### Extension points

- **`BlockEntity`/`Level` network distribution** — add `Network.sendToTracking(BlockPos, ...)` /
  `sendToLevel(Level, ...)` once a real use case needs it; `Capabilities.sync`'s `switch` on
  `OwnerKind` is exactly where the new cases attach.
- **Automatic dirty tracking** — `markDirty`/`sync` are explicit calls today; a future proxy- or
  bytecode-based approach could replace the call site without changing `Capabilities`' public shape.
- **New owner kinds** (`ItemStack`, `LevelChunk`, players-as-a-distinct-kind, anything else) — add a
  marker interface next to `EntityCapability`/`BlockEntityCapability`/`LevelCapability`, an
  `OwnerKind` entry, and an `AttachCapabilitiesEvent<T>` listener in `CapabilityLifecycle` matching
  the existing three; `CapabilityStorage`, `CapabilityRegistry`, `CapabilityDiscovery`, and
  `Capabilities` need no change, since none of them special-case a specific owner type today.
- **`SerializerRegistry.register(Class<T>, Serializer<T>)`** — same extension point `network`
  already exposes; any type a capability class's fields need that isn't covered yet plugs in there,
  not in `capabilities`.

This is the direct answer to the task's own self-check ("could this architecture still work for
quests, RPG stats, stamina/mana, custom inventories, story flags, relationships, achievements,
faction reputation, persistent world state, scripted/AI state, save-game systems, mod interop?"):
every one of those is just another `@Capability`-annotated mutable POJO on an existing or future
owner kind — nothing about `CapabilityRegistry`, `CapabilityStorage`, or `Capabilities` is shaped
around the one example (`StoryPlayerData`) that happens to exist today.

### Package layout

```
com.dimalab.storymodengine.capabilities
├── annotation/
│   └── Capability.java             — @Capability(sync = false), FIELD, RUNTIME
├── EntityCapability.java            — marker: this data class targets an Entity owner
├── BlockEntityCapability.java       — marker: this data class targets a BlockEntity owner
├── LevelCapability.java             — marker: this data class targets a Level owner
├── OwnerKind.java                   — ENTITY / BLOCK_ENTITY / LEVEL, derived from the markers above
├── CapabilityData.java              — public handle: of(Supplier<T>), get/sync/markDirty(owner)
├── Capabilities.java                 — the public facade: get/markDirty/sync(owner, Class<T>|CapabilityData<T>)
├── CapabilityLifecycle.java          — the one Capability<CapabilityStorage>; AttachCapabilitiesEvent
│                                        <Entity/BlockEntity/Level> + PlayerEvent.Clone listeners
├── discovery/
│   └── CapabilityDiscovery.java      — ModFileScanData scan for @Capability (FIELD) — same
│                                        mechanism as ContentDiscovery/PacketDiscovery
├── registry/
│   ├── CapabilityRegistry.java       — global: id/type/owner-kind indexes over every descriptor
│   └── CapabilityDescriptor.java     — record(id, dataType, factory, ownerKind, sync, Serializer<T>)
├── storage/
│   └── CapabilityStorage.java        — ICapabilitySerializable<CompoundTag>; per-owner data bag;
│                                        the lazily-recreated LazyOptional described above
├── sync/
│   ├── CapabilitySyncPacket.java     — @Packet ClientboundPacket: one data type's current value
│   └── CapabilityResyncRequest.java  — @Packet ServerboundPacket: "resend current state" — the
│                                        only C2S message, carries no value
├── CapabilityBootstrap.java          — CapabilityDiscovery.run(modId) + CapabilityLifecycle
│                                        .registerOnce(modEventBus); called from EngineBootstrap.init
└── example/
    ├── StoryPlayerData.java          — the task's required example: storyPoints/metTheGuide/questsCompleted
    ├── StoryBlockData.java           — exercises the BlockEntity owner kind specifically
    ├── StoryLevelData.java           — exercises the Level owner kind specifically
    ├── ModCapabilities.java          — the three @Capability field declarations
    └── CapabilityTestCommand.java    — /storymodengine capability [add <amount>]
```

`CapabilityBootstrap.init` runs with no `Dist` gate, same reasoning as `NetworkBootstrap` — attach,
NBT persistence, and sync-sending are all common-side Forge APIs, verified identical on client and
server; none of `CapabilityStorage`/`CapabilityLifecycle`/`Capabilities`/`CapabilityData` reference
a client-only type directly, applying the same discipline the `network` section's dist-loading bug
established.

Verified live on `runServer`/`runClient` together: `AttachCapabilitiesEvent` firing for all three
owner kinds (`ServerPlayer`, natural mob spawns, `ChestBlockEntity`/`SpawnerBlockEntity`/
`BedBlockEntity`, and `ServerLevel` for every loaded dimension); an offline round-trip and a
malformed-NBT resilience smoke test; and, after the `LazyOptional` fix above, the full required
in-game sequence in one continuous session — add points, view them, travel to the Nether and back,
die and respawn, disconnect and reconnect — with points correctly preserved at every step.

## `event` — the engine-wide event bus

```java
public record QuestCompleted(String questId) implements Event {
}

public final class StoryListeners {
    @SubscribeEvent
    public static void onQuestCompleted(QuestCompleted event) {
        EngineLog.channel("Story").info("Quest completed: {}", event.questId());
    }
}
```

That's the whole contract: declare an event as a plain `record implements Event`, declare a
`static` listener anywhere in the mod's own jar, and it fires — no `DeferredRegister`, no
`IEventBus`, no `MinecraftForge.EVENT_BUS.register(...)`, no packet, no capability, no modid. This
is the foundation every future narrative/gameplay system (Story Flow, Quests, Cutscenes, Dialogue,
AI, camera/animation) is meant to be built on, not a one-off utility — so it had to stay generic,
composable, transport-agnostic, and side-safe rather than shaped around today's one example event.

### What already existed

Real Forge `EventBus`/`IEventBus`/`@SubscribeEvent` semantics (priority tiers, static+instance
listener discovery via `register(Object)`, cancellation) were used throughout as an *architectural
reference* — verified by disassembling Forge's own scanner classes, never as an implementation
dependency (nothing in this package extends, implements, or imports a Forge event type outside
`event.bridge`, the one deliberate exception — see below).

| Already provided by | What it covers | Used instead of reinventing |
|---|---|---|
| `ModFileScanData` (via `ModList`) | Whole-jar annotation scanning without a package list or extra Gradle module | Same mechanism `ContentDiscovery`/`PacketDiscovery`/`CapabilityDiscovery` already use, here filtered to `ElementType.METHOD` |
| `java.lang.invoke.MethodHandles` | Fast, reflection-free invocation after one-time binding | `EventBus#bind` mirrors `PacketSerializer`/`PojoSerializer`'s existing `MethodHandles.lookup().unreflect(...)` pattern exactly |
| `EngineLog.channel(name)` | Named logging channel, console + optional chat | `EngineLog.channel("Events")` — no new logging system |
| `PacketContext#enqueue(Runnable)` | Marshaling work onto the receiving side's main thread | Reused directly as *the* answer to "post an event from a network thread on the main thread" — see Threading below |

### Discovery mechanism — and the one real surprise in Forge's own scanner

Discovery is the same `ModFileScanData` mechanism `ContentDiscovery`/`PacketDiscovery`/
`CapabilityDiscovery` already use, but `@SubscribeEvent` is the first annotation in this engine
targeting `ElementType.METHOD` rather than `FIELD`/`TYPE`, and that mattered: disassembling Forge's
own scanner (`ModClassVisitor`/`ModMethodVisitor` in `fmlloader`) shows that for a `METHOD`
annotation, `ModFileScanData.AnnotationData#memberName()` is **not** a bare method name the way it
is for a field or a type — it's the method's name and its bytecode descriptor concatenated with no
separator (`"onTest(Lcom/example/TestEvent;)V"`, built via `invokedynamic
makeConcatWithConstants`), needed to disambiguate overloads. `EventListenerDiscovery` sidesteps
parsing that string entirely: it only needs the *owning class* of each match, collects the distinct
set of those, and hands each one to `EventBus#register(Class<?>)` — the exact same validation and
`MethodHandle`-binding path a manual `EventBus.register(SomeClass.class)` call goes through. One
code path serves both automatic and manual registration; the descriptor-parsing subtlety never
needed to be solved because `register` re-derives the real parameter type from the actual `Method`
object via ordinary reflection.

Only `static` `@SubscribeEvent` methods are found this way — an instance method has no object to
bind to at discovery time, so it's silently skipped during the scan (not an error) and must be
registered once, explicitly, when a real instance exists: `Events.register(this)` (see `/storymodengine
eventtest instance`'s `InstanceListenerExample`). This mirrors real Forge `EventBus.register(Object)`
semantics exactly, used here as the reference, not as a dependency: passing a `Class<?>` registers
only its static listeners; passing any other object registers both its static and instance listeners,
bound to it.

### `EventBus` vs `Events` — why there are two names

Java forbids a `static` and an instance method sharing one signature on the same class, and
`EventBus.post(event)` as a *static* call is the literal shape the task asked for. `EventBus` itself
stays a real, independent, instantiable dispatch engine (`post`/`register`/`unregister` as instance
methods) specifically so it can be unit-tested in total isolation — `new EventBus()`, register some
listeners, post, assert, discard — with no shared global state between test cases (see
`TempEventSmokeTest`, temporary, for exactly this). `Events` is a tiny static facade over one shared
global `EventBus` instance — the same shape as `Network` (a facade over per-packet `SimpleChannel`s)
and `Capabilities` (a facade over `CapabilityStorage`), the third time this exact split has been
used in this engine. `EventBus` is **not** renamed to avoid Forge's own `IEventBus`/internal
`EventBus` the way `CapabilityManager`/`CapabilityProvider` were renamed — there is no real
same-file import collision (different packages, engine code never imports Forge's), only a similar
name, which is normal and doesn't need resolving; its Javadoc says as much explicitly.

### Priority and dispatch order

Five tiers — `HIGHEST`, `HIGH`, `NORMAL` (the default), `LOW`, `LOWEST` — no `MONITOR`, since
nothing in this engine yet needs an "always last, never affects the outcome" observer distinct from
`LOWEST`; adding one without a concrete use would be exactly the premature complexity the task asked
to avoid. Within a priority tier, listeners run in **registration order** — a monotonic counter on
`EventBus`, not reflection or hash order, which the task explicitly warned not to depend on.

Dispatch is **cached, not scanned per post**: the ordered listener list for a given *concrete* event
class is computed once, lazily, on its first `post` — walking that class's full superclass **and**
interface chain up to `Event` (so `@SubscribeEvent void onAny(Event e)` genuinely receives
everything, and an interface like `CancellableEvent` can be subscribed to directly, not just
concrete classes) — then cached by that exact `Class`. `register`/`unregister` simply drop the whole
cache rather than attempt incremental invalidation: both are cold, rare operations relative to
`post`, so correctness-by-simplicity was chosen over a cleverer scheme neither performance nor the
task's own scope justified.

### Parent-event dispatch

```java
class PlayerEvent implements Event { }
class PlayerJoinEvent extends PlayerEvent { }
```

A listener declared for `PlayerEvent` receives a posted `PlayerJoinEvent` automatically — proper
class-hierarchy dispatch, verified live (`/storymodengine eventtest parent`: both a
`PlayerTestEvent`-typed listener and a `ChildTestEvent`-specific listener ran from one
`ChildTestEvent` post; posting a plain `PlayerTestEvent` reached only the former). This only works
through real inheritance, which is exactly why it's a `class`, not a `record` — records can only
implement interfaces, never extend another type. A leaf event with no family stays a plain `record
implements Event`; an extensible event family uses ordinary classes. Both styles coexist freely; a
mod author picks per event based on whether it needs a family at all.

### Cancellation — a deliberate simplification of Forge, not a copy of it

```java
public interface CancellableEvent extends Event {
    boolean isCancelled();
    void setCancelled(boolean cancelled);
}
```

Only an event that implements this is cancellable — `Event` itself carries no such method, so
cancellation stays opt-in per event, not forced on everything. The rule, chosen deliberately rather
than copied from Forge (the task explicitly asked not to copy it blindly): **once a
`CancellableEvent` becomes cancelled, dispatch stops outright** — no further listener runs, at any
priority, no exceptions. Forge's own per-listener `receiveCanceled` opt-in (an "always-runs
observer") was deliberately not built: it's one more `@SubscribeEvent` attribute the task asked to
keep minimal, and nothing in this engine needs an observer that ignores cancellation yet — it's a
small, isolated, backward-compatible addition if a real use case ever appears. Verified live
(`/storymodengine eventtest cancel`): a `HIGH`-priority listener cancels; `NORMAL`/`LOW` listeners on
the same event never ran.

### Error isolation

A listener that throws is caught inside `EventBus#post`, logged via
`EngineLog.channel("Events").warn(...)` with the listener's own name and the exception, and dispatch
continues to the next listener in order — one broken handler never stops the rest, and never
crashes the game. Verified live (`/storymodengine eventtest exception`): three listeners A/B/C at
distinct priorities, B throws deliberately; A and C both ran, the warning was logged, nothing
escaped to crash the server.

### Threading

`EventBus#post` is fully synchronous on the calling thread — no queueing, no thread hop, no
scheduling of any kind; the bus itself stays deliberately dumb about threads, per the task's own
instruction not to pretend every event is always on the main thread. A caller on a network thread
(a packet handler, most commonly) that needs its posted event handled on the main thread uses the
network system's own existing mechanism for exactly this — nothing new was built:

```java
context.enqueue(() -> Events.post(event));
```

Concurrent `register`/`unregister`/`post` calls from different threads are safe
(`ConcurrentHashMap`-backed indices, a small `synchronized` block only around the rare structural
mutations); a listener registered concurrently with an in-flight `post` of the exact same event
class may or may not be included in that one call — never a crash or corrupted state, a benign race
not worth eliminating for a game loop that's overwhelmingly single-threaded in practice.

### Side-awareness

Nothing in `event`'s core (`Event`, `CancellableEvent`, `EventPriority`, `SubscribeEvent`,
`EventBus`, `Events`, `AnnotationProcessorEvent`, `discovery`) references a client-only type —
`EventListenerDiscovery` uses the same `ModFileScanData` mechanism already proven safe on a
dedicated server three times over. The one deliberate exception is `event.bridge` (below), whose
entire job requires touching real Forge event types; everything else stays common-side by
construction, following the discipline the `network` section's `RuntimeDistCleaner` incident
established: a class loaded unconditionally on both sides must never carry a direct bytecode
reference to a client-only type.

### Minecraft/Forge bridge — the one place this package touches Forge events

```
Forge PlayerEvent.PlayerLoggedInEvent
        ↓
  MinecraftEventBridge  (MinecraftForge.EVENT_BUS, registered once, idempotent)
        ↓
  Events.post(new PlayerConnectedEvent(player))
        ↓
  any @SubscribeEvent listener — no Forge type visible
```

`MinecraftEventBridge` translates Forge's `PlayerEvent.PlayerLoggedInEvent`/`PlayerLoggedOutEvent`
into this engine's own `PlayerConnectedEvent`/`PlayerDisconnectedEvent` — the exact pair of names
the task itself used as an example — and posts them through `Events`. It registers on
`MinecraftForge.EVENT_BUS` idempotently (an `AtomicBoolean` guard), the identical pattern
`CapabilityLifecycle.registerOnce` already uses, rather than `@Mod.EventBusSubscriber`, which needs
a compile-time modid tied to one specific mod and doesn't fit infrastructure meant to serve every
mod built on this engine. This is deliberately the *only* file in `event` that imports a Forge event
type — the engine-level `EventBus`/`Events` API stays completely independent of Forge, exactly as
required; a future subsystem needing more Forge events bridged (block break, entity death, whatever
a real feature needs) adds another small translation here, not a change to `EventBus` itself.

### Network integration

An `Event` is never automatically a `Packet` — nothing in `EventBus` knows networking exists, and
nothing was built to synchronize every posted event across the wire "for convenience". Where an
event genuinely needs to cross client/server, the existing `network` system does the whole job
unmodified:

```
client command
      ↓
Network.sendToServer(new EventTestPacket(position))   ← existing @Packet, ServerboundPacket
      ↓
existing PacketRouter → EventTestPacket#handle(PacketContext)
      ↓
Events.post(new NetworkTriggeredTestEvent(context.sender(), position))
      ↓
@SubscribeEvent listener — no networking type visible here at all
```

Verified live end to end (`/storymodengine eventtest network`): the client sends its position as a
`Vector3f` inside an ordinary `@Packet`; the *existing* `SerializerRegistry` `Vector3f` serializer
(from `network.serialization.serializers.MathSerializers`, already registered for `network`'s own
math-integration example) encodes and decodes it with zero new serialization code; the server's
`handle` posts an engine event; a listener reacts. This is the literal answer to the task's own
`Event → SerializerRegistry → Packet → Network` diagram, and it stayed true only because `EventBus`
never tried to know about any of those types itself.

### Capabilities integration

Same story: no event-specific storage was built. A listener that needs persistent state just calls
the existing `Capabilities` facade, exactly like any other game code would:

```java
@SubscribeEvent
public static void onNetworkTriggered(NetworkTriggeredTestEvent event) {
    StoryPlayerData data = Capabilities.get(event.player(), ModCapabilities.STORY_DATA);
    data.storyPoints += 1;
    Capabilities.markDirty(event.player(), ModCapabilities.STORY_DATA);
    ModCapabilities.STORY_DATA.sync(event.player());
}
```

Verified live as part of the same network-integration test above — the already-existing
`StoryPlayerData`/`ModCapabilities.STORY_DATA` from the `capabilities` example is reused directly;
no second capability, no second persistence path.

### Serialization integration

`EventBus` itself never touches `SerializerRegistry` — an `Event` only needs serializing at all if
it becomes a `Packet` payload (above), at which point it's just ordinary `network` data, subject to
the same rules as everything else there: nested records, collections, `Optional`, `UUID`,
`ResourceLocation`, `Vec3`, JOML math types all resolve automatically, with zero event-specific
serialization code anywhere in this package.

### `AnnotationProcessorEvent` — the discovery-lifecycle checkpoint

Posted once, by `EventBootstrap.init`, after content/network/capability/event discovery has all
finished for one mod — a marker meaning "this mod's engine setup is complete", not a gameplay event.
Deliberately a single, generic lifecycle checkpoint rather than one event per annotation type
discovered: a future engine subsystem introducing its own annotation (Story Flow's own discovery,
say) just adds its own discovery call ahead of this post in `EngineBootstrap.init` and can react to
(or ignore) the very same event — nothing about its shape is tied to today's specific annotation set.

### Package layout

```
com.dimalab.storymodengine.event
├── Event.java                       — marker interface, zero methods
├── CancellableEvent.java            — extends Event; isCancelled()/setCancelled(boolean)
├── EventPriority.java               — HIGHEST, HIGH, NORMAL, LOW, LOWEST
├── SubscribeEvent.java              — @Target(METHOD) @Retention(RUNTIME); priority() default NORMAL
├── EventBus.java                    — the real dispatch engine (instantiable, unit-testable):
│                                       post(Event) / register(Object) / unregister(Object)
├── Events.java                      — static facade over one shared global EventBus
├── AnnotationProcessorEvent.java    — record(modId) implements Event — discovery-lifecycle checkpoint
├── discovery/
│   └── EventListenerDiscovery.java  — ModFileScanData scan for @SubscribeEvent (METHOD) →
│                                       EventBus.register(ownerClass) per distinct owning class
├── bridge/
│   ├── MinecraftEventBridge.java    — the one Forge-event-aware file in this package (above)
│   ├── PlayerConnectedEvent.java    — record(ServerPlayer) implements Event
│   └── PlayerDisconnectedEvent.java — record(ServerPlayer) implements Event
├── EventBootstrap.java              — EventListenerDiscovery.run + AnnotationProcessorEvent post +
│                                       MinecraftEventBridge.registerOnce(), called from
│                                       EngineBootstrap.init after every other discovery pass
└── example/                         — PlayerTestEvent/ChildTestEvent (priority + parent dispatch),
                                        CancellableTestEvent, ExceptionTestEvent, InstanceTestEvent +
                                        InstanceListenerExample, NetworkTriggeredTestEvent +
                                        EventTestPacket (network/math/capability demo),
                                        EventTestListeners (every static listener, all
                                        auto-discovered), EventTestCommand + EventTestClientCommand
                                        (/storymodengine eventtest priority|parent|cancel|instance
                                        |exception|network)
```

`EventBootstrap.init()` needs no `Dist` gate — discovery, dispatch, and the Forge bridge all behave
identically on a dedicated server (verified live: `@SubscribeEvent discovered N static listener(s)`
appears in a `runServer` log with no client classes ever loaded). It runs last among
`EngineBootstrap`'s common-side subsystems, after content/network/capability discovery, so
`AnnotationProcessorEvent` genuinely means "everything is ready" for anything listening.

### Future Story Flow compatibility

Nothing about `EventBus` assumes today's example events are the only shape events ever take.
Quests, dialogue, cutscenes, branching signals, and completion notifications are all just more
`Event`/`CancellableEvent` implementations posted through the same `Events.post(...)`:
a `QuestStarted → NPCSpawned → DialogueStarted → PlayerChoiceMade → QuestCompleted` chain is a
sequence of ordinary posts, potentially from ordinary listeners reacting to the previous event in
the chain and posting the next one themselves; a parallel `SceneStarted → {Camera, Dialogue,
Animation} → SceneEnded` fan-out is just several independent listeners on one `SceneStarted` post,
each free to post its own follow-up events on its own schedule. `CancellableEvent` already gives
branching signals ("was this choice/action allowed to proceed") for free. None of this is built yet
— per the task's own instruction not to implement Story Flow now — but nothing here would need
redesigning to support it: the dispatch cache is keyed by runtime class (any new event type just
works), priorities and parent-dispatch are already general, and the Minecraft/Network/Capabilities
integration points above are exactly the seams a narrative system would need.

### Extension points

- **More Forge events bridged** — `event.bridge` is exactly where a future subsystem adds another
  Forge → engine translation (block interactions, entity death, whatever a real feature needs),
  without `EventBus` itself changing.
- **A `receiveCanceled`-style always-run observer** — one more `@SubscribeEvent` attribute and one
  more branch in `EventBus#post`'s cancellation check, if a real use case ever needs it (see
  Cancellation above).
- **New annotations participating in the discovery lifecycle** — any future `@Something` just adds
  its own discovery call in `EngineBootstrap.init` ahead of `EventBootstrap.init`'s
  `AnnotationProcessorEvent` post; that event's shape never needs to change to accommodate it.
- **`EventBus.register(Class<?>, Object)`-style scoped registration, event history/replay, async
  posting** — all deliberately not built now (the task's own "no unnecessary complexity" list);
  `EventBus`'s current shape doesn't preclude any of them later.

## `flow` — the foundational Story Flow system

```java
Flow story = Flow.sequence(
    Flow.action(ctx -> ctx.log("NPC appears")),
    Flow.action(ctx -> giveQuest(ctx)),
    Flow.condition(ctx -> Capabilities.get(ctx.player(), StoryPlayerData.class).storyPoints >= 1),
    Flow.choice(
        new Transition("accept", Flow.action(ctx -> npcApproves(ctx))),
        new Transition("decline", Flow.action(ctx -> npcRefuses(ctx)))
    )
);
```

Flow describes a sequence of logical actions and decisions in a game scenario — it knows nothing
about NPCs, quests, dialogue, or cutscenes; those are external systems this version deliberately
does not build, but the architecture has to hold up once they exist. This is the first version:
small, complete, and built specifically so Cutscene/Dialogue/Quest/AI/Timeline systems can be added
on top of it later without changing `flow`'s core.

### The central design problem: one definition, many independent players

The task's own framing — "Flow Definition ≠ Flow Runtime" — is the whole architectural challenge.
If a `Flow`'s nodes carried their own mutable lifecycle state directly, two players running the same
`Flow` would share those objects and corrupt each other's progress. The resolution:
**`Flow` is a factory of node *factories*, not a tree of nodes.**

```java
public final class Flow {
    private final Supplier<Node> factory;
    public Node instantiate() { return factory.get(); }
    public static Flow sequence(Flow... children) {
        List<Flow> copy = List.of(children);
        return new Flow(() -> new Sequence(copy));   // each call builds fresh Node instances
    }
    ...
}
```

`Flow.sequence(a, b, c)` doesn't build a `Sequence` node — it builds a `Flow` whose factory, called
later, builds a `Sequence` whose own children are built by calling `a`/`b`/`c`'s factories in turn.
`FlowRuntime.start(...)` calls `flow.instantiate()` exactly once, getting a private `Node` tree that
belongs to that one runtime alone. Two `FlowRuntime`s from the same `Flow` share nothing but the
immutable `Flow` value and its lambdas — no external `Map<Node, State>`, no Blackboard, no second
indirection layer was needed to get this property; it falls directly out of "definitions are
factories, not instances."

### `Node` — lifecycle

Four states, exactly as specified: `NOT_STARTED → RUNNING → COMPLETED`/`FAILED`. `Node` is an
abstract class (not an interface) using the template-method pattern: `start`/`tick` are `final` and
call protected `onStart`/`onTick`; subclasses call the protected `complete()`/`fail()` helpers to
end themselves. Nothing in `Node` knows what a Minecraft tick is — `tick(FlowContext)` is invoked by
whatever the integration layer decides should drive it (see Threading below), never assumed.

- **`Action`** — one effect, synchronous. A plain `Consumer<FlowContext>` always succeeds unless it
  throws (caught, logged via `EngineLog.channel("Flow")`, treated as a failure — never crashes the
  flow); `Flow.actionResult(Function<FlowContext, Boolean>)` reports failure explicitly, without
  needing to throw. (Two overloads of the same name, `action(Consumer)`/`action(Function)`, turned
  out to be genuinely ambiguous at lambda call sites — Java can't disambiguate two structurally-
  compatible functional interfaces from an expression-statement lambda body alone — so the
  boolean-returning variant has its own name instead of an overload.)
- **`Condition`** — evaluates once, completes if true, fails if false. Used directly inside a
  `Sequence` for a simple gate (exactly the required example's "Story Points >= 1?" step); for two
  genuinely different paths rather than "stop here", use `Branch`.
- **`Sequence`** — runs children one at a time; a completed child advances to the next, a failed
  child fails the whole sequence immediately without starting the rest. Children are instantiated
  lazily, one at a time, right before each starts — a sequence that fails early never even builds
  the nodes it never reached.
- **`Parallel`** — starts every child at once; completes when all complete, fails as soon as any one
  fails (the other still-running children are simply abandoned — no cancellation framework exists in
  this version). No `Race`/`First`/`N-of-M` policies, per the task's own instruction.
- **`Branch`** — evaluates a condition once, at start, and runs one of two child `Flow`s accordingly;
  its own completion mirrors whichever path was chosen.
- **`Choice`** — the mechanism for "the player picks the next logical path," explicitly not a
  dialogue system: no UI, no text, no dialogue manager. On start it does nothing but stay `RUNNING`,
  waiting indefinitely (no timeout in this version) for external code to call
  `choice.select(transitionId, context)` — a command, in the required example; a future dialogue UI
  later. Each `Transition(String id, Flow target)` is a named option; selecting one instantiates and
  starts that option's `Flow`.

A mod author never constructs `Action`/`Condition`/`Sequence`/`Parallel`/`Branch`/`Choice` directly —
`Flow`'s static factories are the entire surface a mod author touches, matching the task's own
example precisely.

### Persistence: path-based restore, not a saved object graph

`FlowState` — a plain mutable POJO, the same shape `StoryPlayerData` already uses — holds exactly
what the task asked for and nothing more: `ResourceLocation flowId`, `List<Integer> activePath`,
`NodeState rootState`. It flows through the *existing* `SerializerRegistry`/`PojoSerializer`
pipeline unmodified — `ResourceLocation` and `enum` already have direct serializers, `List<Integer>`
already resolves through the existing generic-collection support — zero new serialization code.

The restore mechanism is the piece that needed real design: every composite `Node` can report
`activePath()` — the index-path from itself down to whichever descendant is currently active — and
later accept that same path back via `fastForwardTo(path, context)`, which sets its internal
cursor/choice directly rather than replaying every step before it:

```
Sequence [index=3, current=Choice]
   activePath() → [3] + Choice.activePath()
                → [3] (Choice itself is the active leaf, still awaiting selection)
```

On restore, `FlowRuntime.resume` instantiates a *fresh* `Node` tree from `Flow.instantiate()` (the
old runtime's tree is simply discarded, per the "definition, not instance" design above) and calls
`root.fastForwardTo(state.activePath, context)`, which jumps straight to index 3 without ever
touching indices 0–2 again. `Sequence`/`Branch`/`Choice` all restore faithfully this way — verified
live: a flow parked at its `Choice` step, world saved and the client reconnected, `flowtest status`
still showed `RUNNING`, and selecting an option from there completed the flow normally.

**Two honestly documented v1 limits**, both explicit consequences of not building a Checkpoint/
idempotency-key framework (out of scope per the task):

- **`Parallel` does not override `activePath()`/`fastForwardTo`** — it inherits the leaf default,
  so a `Parallel` that's active when a `FlowState` is saved simply restarts every child from scratch
  on load rather than resuming several simultaneously-active branches faithfully. Persisting several
  concurrent branches would need a richer-than-single-path shape, which is exactly the kind of
  scheduler complexity this version deliberately avoids; a demo (or real content) that cares about
  save/reload correctness should not park mid-`Parallel`.
- **Resuming exactly mid-`Action` re-runs that action's side effect** — there is no idempotency key
  to detect "this already happened." `Condition`/`Choice`/composite resume points are all safe by
  construction (re-evaluating a condition or re-entering an awaiting-selection `Choice` has no
  side effect), which is why the required example is deliberately structured to park at its
  `Choice` — a naturally safe, realistic resting point — rather than mid-`Action`.

### `FlowContext`

The bridge to the outside world, deliberately small: `player()` (`ServerPlayer`), `level()`, a
`log(...)` passthrough to `EngineLog.channel("Flow")`, and a small `Map<String, Object>` scratch
store — explicitly *not* a formal Variable/Blackboard system (out of scope), just a per-runtime,
non-persistent convenience the task itself allowed ("if you need state for demonstration, use a
minimal FlowContext"). A `Node`'s lambda calls the engine's *existing* facades directly —
`Capabilities.get(context.player(), ...)`, `Network.sendToPlayer(context.player(), ...)` — rather
than `FlowContext` wrapping or re-exposing them; no Forge implementation type (`LazyOptional`,
`SimpleChannel`, `AttachCapabilitiesEvent`) is reachable from `flow`'s core at all.

### `FlowRuntime` / `FlowRegistry` / `FlowManager`

- **`FlowRuntime`** owns exactly one instantiated `Node` tree for one player's one run —
  `start`/`resume` (static factories), `tick()`, `state()`, `selectChoice(id)`, `toFlowState()`.
- **`FlowRegistry`** is a plain `id → Flow` map, `register`/`get`. Deliberately not annotation-driven
  discovery like `@AutoContent`/`@Packet`/`@Capability`: with only a handful of top-level Flows
  expected in this version, a `ModFileScanData` scanner would be machinery this version doesn't need
  — a clean, obvious extension point (`@AutoFlow` on a `public static final Flow` field, mirroring
  `@AutoContent` exactly) if that ever changes.
- **`FlowManager`** tracks every player's active `FlowRuntime`s (`Map<UUID, Map<ResourceLocation,
  FlowRuntime>>` — several flows can be active per player at once, e.g. a main story flow and a side
  flow, each independent), and is the *only* place that touches more than one player's flows.
  `tick()` advances every active runtime by one step; on any state transition it updates
  `StoryFlowData#activeFlows` in memory (cheap) and calls the existing `Capabilities.markDirty`/
  `.sync` — never unconditionally every tick, the same "explicit, not automatic" dirty discipline
  `capabilities` already established.

### Integration — reused, not rebuilt

- **Persistence** (`flow.integration.persistence`) — `StoryFlowData implements EntityCapability`
  (`Map<String, FlowState> activeFlows`) plus one `@Capability(sync = true)` field
  (`ModFlowCapabilities.FLOW_DATA`). Discovered automatically by the *already-existing*
  `CapabilityDiscovery` — Flow's persistence needed zero new bootstrap code, only one more
  `@Capability`-annotated field for it to find. Actual NBT persistence needs no Flow-specific save
  hook at all: `Entity` already serializes `ForgeCaps` automatically, the same mechanism every other
  capability already gets for free.

  Building `StoryFlowData` surfaced a genuine, previously-latent bug in the shared
  `PojoSerializer`: resolving a POJO capability whose own field is itself another POJO type (`Map<
  String, FlowState>` inside `StoryFlowData` — the first time this engine has had a POJO-inside-POJO
  shape) calls `PojoSerializer.forClass` reentrantly for the nested type from *inside*
  `CACHE.computeIfAbsent`'s own mapping function for the outer type. Nesting a second
  `computeIfAbsent` call inside another one on the *same* `ConcurrentHashMap` is a documented JDK
  trap — it throws `IllegalStateException("Recursive update")`, and (verified live, twice, on
  identical bytecode) does so **non-deterministically**, depending on the map's internal bin layout,
  not on whether the two keys actually collide. Fixed with a small manual double-checked cache
  (`get` → `build` outside any lock → `putIfAbsent`) in place of the single atomic
  `computeIfAbsent` call — plain `get`/`putIfAbsent` carry no such nesting restriction. This was a
  pre-existing bug in shared engine infrastructure that Flow was simply the first feature to expose,
  not something specific to `flow` itself.
- **Network** (reused, not extended) — `ModFlowCapabilities.FLOW_DATA.sync(player)` (the existing
  `Capabilities` sync path, itself built on `@Packet`/`SerializerRegistry`/`Network` already) is the
  entire "sync Flow state to the client" story. A dedicated `FlowStateSyncPacket` was drafted during
  planning and then deliberately dropped once it became clear the existing capability-sync
  mechanism already covers exactly this need — one fewer packet type, one fewer thing to keep in
  sync with itself, and a cleaner demonstration that persistence and network sync ride the same
  existing rail rather than two.
- **EventBus** (`flow.integration.event`) — `FlowCompletedEvent`/`FlowFailedEvent` post through the
  existing `Events.post(...)` on every terminal transition. `FlowJoinBridge` is one **static**
  `@SubscribeEvent` method reacting to the *already-existing* `event.bridge.PlayerConnectedEvent`
  (built in the previous phase) by calling `FlowManager.resumeAll(player)` — found and registered
  automatically by the *already-existing* `EventListenerDiscovery`, zero new discovery code. This is
  the literal chain the task asked to demonstrate: `Forge PlayerLoggedInEvent →
  MinecraftEventBridge → PlayerConnectedEvent → EventBus → Flow`, built entirely from
  infrastructure that predates this package.
- **Math** — not forced in. `flow`'s core has no math dependency at all; nothing about `FlowContext`/
  `Node` would need to change for a future Cutscene/Camera system's `Action`s to carry `Vector3f`/
  `Quaternionf`/`Transform` fields and have them resolve through the exact same `SerializerRegistry`
  already used everywhere else once a `Flow`-driven event needs to cross the network — the same
  "math is an integration point, not a dependency" posture `event` already established.

### Threading

`FlowManager.tick()` runs synchronously on whichever thread calls it — nothing in `flow`'s core
hops threads, queues work, or assumes a specific thread identity. The one real driver is
`flow.integration.FlowTickBridge`: an idempotently-registered (`AtomicBoolean`-guarded, the same
pattern `CapabilityLifecycle`/`MinecraftEventBridge` already use) `MinecraftForge.EVENT_BUS`
listener on `TickEvent.ServerTickEvent` (filtered to `Phase.END` so one Minecraft tick drives
exactly one `FlowManager.tick()` call), living entirely in the integration layer — `Node#tick`
itself has no idea a "server tick" exists.

### Side-awareness

Nothing in `flow`'s core (`Flow`, `node.*`, `FlowContext`, `FlowState`, `FlowRuntime`,
`FlowManager`, `FlowRegistry`) references a client-only type. The only files that touch Forge at all
are `flow.integration.*` — `FlowTickBridge` (`TickEvent`) and `FlowJoinBridge` (the engine's own
`PlayerConnectedEvent`, itself already Forge-independent) — following the same discipline the
`network` section's `RuntimeDistCleaner` incident established: a class loaded unconditionally on
both sides must never carry a direct bytecode reference to a client-only type.

### Required example — `/storymodengine flowtest`

```
Sequence
 ├─ Action:    "Story Flow started"                      (EngineLog)
 ├─ Action:    +1 Story Point                             (Capabilities — existing StoryPlayerData)
 ├─ Condition: Story Points >= 1?                          (fails the sequence outright if not)
 └─ Choice("accept" → +5 points, "decline" → -1 point)
```

`start` runs the flow to its `Choice` and parks there; `status` prints the runtime's current state
(used to prove restoration after a save/reload); `choice <id>` resolves the pending choice and
finishes the flow. Verified live end to end, including the required persistence scenario (start →
park at `Choice` → disconnect/reconnect → `status` still `RUNNING` at the same point → `choice
accept` → completes, Story Points correctly reflect both the initial +1 and the chosen +5) and two
independent runs in the same session without cross-contamination.

### Package layout

```
com.dimalab.storymodengine.flow
├── Flow.java                        — immutable definition: a factory of Node factories
├── Transition.java                  — record(String id, Flow target) — one named Choice option
├── FlowContext.java                 — player()/level()/log()/scratch — no Forge internals
├── FlowState.java                   — serializable snapshot: flowId, activePath, rootState
├── FlowRuntime.java                 — one instantiated Node tree, private to one player's run
├── FlowManager.java                 — tracks every player's active FlowRuntimes; tick()/persist
├── FlowBootstrap.java                — FlowTickBridge.registerOnce(), called from EngineBootstrap.init
├── node/
│   ├── Node.java                     — lifecycle template method + activePath()/fastForwardTo()
│   ├── NodeState.java                — NOT_STARTED / RUNNING / COMPLETED / FAILED
│   ├── Action.java / Condition.java / Sequence.java / Parallel.java / Branch.java / Choice.java
├── registry/
│   └── FlowRegistry.java             — id → Flow lookup
├── integration/
│   ├── persistence/
│   │   ├── StoryFlowData.java        — implements EntityCapability
│   │   └── ModFlowCapabilities.java  — the one @Capability field, auto-discovered
│   ├── event/
│   │   ├── FlowCompletedEvent.java / FlowFailedEvent.java
│   │   └── FlowJoinBridge.java       — @SubscribeEvent on PlayerConnectedEvent, auto-discovered
│   └── FlowTickBridge.java           — the one new Forge touch-point: the tick source
└── example/
    ├── FlowExamples.java              — the required scenario, built from Flow's own factories
    └── FlowTestCommand.java           — /storymodengine flowtest start|status|choice <id>
```

### Architecture review

The task asked these ten questions explicitly after implementation, not before — answered against
what was actually built and verified live, not the original plan:

1. **Which classes are actually necessary?** All of them, at the granularity they exist:
   `Flow`+`Node` (the factory/instance split is the one idea the whole design hangs on),
   `Action`/`Condition`/`Sequence`/`Parallel`/`Branch`/`Choice` (each encodes a genuinely distinct
   control-flow rule, none reducible to another), `Transition` (Choice's option unit — small, but
   real: without it Choice would need to invent its own ad hoc labeled-target shape), `FlowContext`
   (the one seam that keeps `Node` implementations from touching Forge or Capabilities/Network
   directly), `FlowState`/`FlowRuntime`/`FlowManager`/`FlowRegistry` (definition vs. runtime vs.
   registry vs. multi-player bookkeeping are four genuinely different responsibilities — collapsing
   any two would blur "one Flow, many independent runtimes").
2. **Which abstractions turned out unnecessary?** The originally-planned `FlowStateSyncPacket` — a
   dedicated Flow network packet — was dropped once it became clear `Capabilities`' own existing
   sync mechanism already covers "push Flow state to the client" completely. That's the one place
   this build over-planned before implementation caught it. Nothing shipped turned out unnecessary
   in hindsight; the temptation avoided was building a second Action-failure API (`Flow.action`
   overloaded on `Function` — reverted to a differently-named `actionResult` once the overload proved
   genuinely ambiguous, not just stylistically awkward) and a `receiveCanceled`-style flag on
   `Choice`/`Branch` that nothing here needs yet.
3. **Where is there coupling?** `flow`'s core has exactly three coupling points, all deliberate and
   all one-directional: `FlowContext` → `ServerPlayer`/`Level` (the vocabulary `Capabilities`/
   `Network` already use, not a new one); `FlowManager`/`StoryFlowData` → `Capabilities` (persistence
   only, through the public facade, never `CapabilityStorage` directly); `FlowManager` →
   `Events`/`event.bridge.PlayerConnectedEvent` (both existing public facades). Nothing in `node`
   package couples to any of this — `Action`/`Condition`/etc. only ever see `FlowContext`, never
   `Capabilities`/`Network`/`Events` directly; those are the *demo*'s coupling (`FlowExamples`), not
   the core's.
4. **Can Cutscene be added later without changing Flow Core?** Yes. A cutscene is a `Sequence`/
   `Parallel` of `Action`s that move a camera/entity over time — nothing about that needs a new
   `Node` type; it needs `Action`s whose lambdas drive `math.transform.Transform`/`CatmullRomSpline`
   sampling per tick, using `FlowContext`'s existing `player()`/`level()` and (if it needs to persist
   mid-flight, unlike this version's actions) a capability of its own. The one real gap a cutscene
   system would want that this version doesn't have is a node that runs *across several ticks* while
   reporting incremental progress rather than completing in one `onStart` call — `Action` already
   supports that shape (nothing requires `onStart` to call `complete()` immediately; it's free to
   stay `RUNNING` and finish in a later `onTick`), so even that doesn't need a new `Node` subtype.
5. **Can Dialogue be added later?** Yes, and `Choice` is already exactly its logical primitive — a
   dialogue system adds text/UI *around* `Choice`, it doesn't need to change it. A `DialogueNode`
   wrapping a `Choice` with associated display text is an `integration`/future-package concern, not
   a `flow.node` change.
6. **Can a Quest System be added later?** Yes — a quest is a `Flow` (or several, one per objective,
   composed with `Sequence`/`Parallel`) plus its own capability for quest-specific state (quest log,
   objectives), following exactly the pattern `StoryFlowData` already demonstrates. Nothing in `flow`
   is quest-shaped or would need to become quest-shaped.
7. **Can `EventWaiter` be added later?** Yes, as a new leaf `Node` (`Wait`/`EventWaiter`) that
   registers an `Events`-listener in its own `onStart` (via the existing `Events.register`/
   `unregister`) and calls `complete()` from that listener when the awaited event arrives — the same
   shape `Choice` already uses for "stay `RUNNING`, resolve from outside." No change to `Sequence`/
   `Parallel`/`Branch`/`FlowManager` would be needed; it's a pure addition.
8. **Can `SubFlow` be added later?** Yes, as a new leaf `Node` wrapping a nested `Flow` — instantiate
   it in `onStart`, delegate `tick`/`activeLeaf`/`activePath`/`fastForwardTo` to it exactly the way
   `Branch`/`Choice` already delegate to their chosen child. The factory-not-instance design means a
   `SubFlow` referencing another top-level `Flow` by id (via `FlowRegistry.get`) composes cleanly
   with no special-casing.
9. **Is any of this abstraction for its own sake?** The one candidate worth naming honestly is
   `Transition` — a two-field record (`id`, `target`) that could arguably have been inlined as two
   parallel arrays/lists on `Choice`. It earns its place because `Choice`'s options genuinely need a
   *name* independent of position (selection is by string id from a command, not by index), and
   because it's a small, real, reusable shape rather than Choice-specific glue — but it's the
   narrowest justification in this codebase, worth reconsidering if it never gains a second use.
10. **What should stay an extension point rather than be built now?** Automatic `Flow` discovery
    (`@AutoFlow` mirroring `@AutoContent`, once there are enough top-level flows that a manual
    `FlowRegistry.register` call per flow becomes tedious); `Parallel`'s faithful mid-flight
    persistence (needs a genuinely richer state shape than the single-path model, likely tied to
    whatever a future Checkpoint system looks like); a `receiveCanceled`-style "always-runs observer"
    priority tier; and a multi-tick `Wait`/timeout primitive (the natural home for `EventWaiter`
    once it exists, per Q7) — all four are additive, none require touching what's shipped here.

### Runtime expansion — Definition/Instance/Handle, cancellation, and six new primitives

The sections above describe `flow`'s first version. This expands it into the fuller runtime the
task's second pass asked for — `FlowDefinition`/`FlowInstance`/`FlowHandle`, `Wait`/`SubFlow`/
`EventWaiter`, `Blackboard`/`Scope`, `Evaluator`, `Task`/`Result`, `FailurePolicy`, first-class
cancellation, `Timeout`, `Checkpoint`, `@AutoFlow` discovery, and faithful `Parallel` persistence —
without rewriting the working core: `Flow`, `Action`, and `Sequence`/`Branch`'s continuation logic
are exactly as before; every addition is either a new file or a small, additive change to an
existing one (see "Compatibility" below for the precise list).

#### `FlowDefinition` / `FlowInstance` / `FlowHandle`

`Flow` already *was* the task's `FlowDefinition` concept — an immutable factory of `Node` factories,
never a shared mutable instance. `FlowDefinition` doesn't replace it; it's a thin
`record FlowDefinition(ResourceLocation id, Flow graph)` giving a `Flow` the stable identity it
previously carried as a separate parameter threaded alongside it everywhere. `FlowInstance` is new
and sits *above* the existing `FlowRuntime` (composition, not a rewrite): `FlowRuntime` still owns
the `Node` tree and ticks it exactly as before; `FlowInstance` adds a `FlowRunState` — `NOT_STARTED
/RUNNING/PAUSED/COMPLETED/FAILED/CANCELLED` — deliberately independent of the root node's own
`NodeState`, per the task's explicit requirement. `PAUSED` in particular has no node-level
equivalent at all: `FlowManager.tick()` simply skips a non-`RUNNING` instance, freezing the node
tree underneath without the tree itself needing to know it's paused. `FlowHandle` is what
`FlowManager.start(...)` now returns — `isRunning/isPaused/isCompleted/isFailed/isCancelled/pause/
resume/cancel/selectChoice/state()` — deliberately narrower than `FlowInstance`/`FlowRuntime`, with
no `Node` or `FlowContext` reachable from it.

#### Cancellation

`NodeState` gained a fifth value, `CANCELLED`, specifically so a cancelled node is distinguishable
from one that failed on its own logic. `Node#cancel(FlowContext)` calls `onCancel` (default no-op)
and moves to `CANCELLED` — a no-op if the node isn't `RUNNING`. Composites propagate into their
active child/children; `EventWaiter` releases its `EventBus` subscription there. `Parallel` is the
concrete reason this needed to be a real, propagating operation rather than a cosmetic state: when
one child fails, every other still-`RUNNING` sibling is now genuinely cancelled (previously
documented as "simply abandoned") — verified live to matter, not just in theory: a still-subscribed
`EventWaiter` sibling that wasn't cancelled would leak its subscription forever.

#### `Selector` — closing a real gap against standard behavior-tree taxonomy

Added after independently verifying this expansion against real-world practice (Unreal's own
Behavior Tree documentation and general game-AI literature), not from the task text itself: the
canonical behavior-tree composite set is Sequence/Selector/Parallel/Decorator — Sequence is AND
(stop on first failure), **Selector** ("Fallback") is its OR mirror (try children in order, stop on
first *success*, fail only if all fail). Neither `Branch` (a one-time, pre-evaluated if/else that
never tries a different child after picking one) nor `PolicyNode`'s `FALLBACK` policy (exactly one
alternative, not an arbitrary ordered list) actually covers this. `Selector` (`Flow.selector(Flow...)`)
is structurally `Sequence` with success/failure swapped — same lazy instantiation, same
collect/restore/cancel shape — verified live: stops at the first succeeding child without starting
the rest, fails only when every child fails, and restores from a saved mid-selector state without
re-running any child that already failed.

#### `Wait`, `SubFlow`, `EventWaiter`

- **`Wait`** (`Flow.wait(int ticks)`) — a leaf tick counter, non-blocking by construction (just
  another `onTick`). Its elapsed count is **not** part of persistence — restoring a flow parked
  mid-`Wait` restarts the wait from zero, the same documented category of limitation as resuming
  mid-`Action` (both would need a Checkpoint/idempotency mechanism this version deliberately doesn't
  build).
- **`EventWaiter<E>`** (`Flow.waitForEvent(Class<E>, BiPredicate<FlowContext,E>[, Timeout])`) —
  subscribes through the *existing* `EventBus` (see below) and completes the instant a matching
  event arrives: genuinely push-based, never polling for the event itself (only an optional
  `Timeout` is ticked, which is bookkeeping, not polling). The filter takes `FlowContext` as well as
  the event — an `EventBus` subscription is engine-wide, not per-player, so a multiplayer-safe
  filter needs to check the event against `context.player()` (exactly what the required demo's
  filter does: `event.player().equals(ctx.player())`). Always unsubscribes exactly once — on match,
  timeout, or `onCancel` — verified live via the smoke test: cancelling a `Parallel` sibling
  `EventWaiter` and then posting the event it *would* have matched produces no effect and no
  exception, proving the subscription was actually released.
- **`SubFlow`** (`Flow.subFlow(Flow inner)`) — runs another `Flow` as one composed step, delegating
  every lifecycle method to the inner root the same way `Branch`/`Choice` already delegate to their
  chosen child. Composition, not a second runtime — `FlowManager`/`FlowRuntime` never know a
  `SubFlow` is anything but an ordinary node.

#### Persistence: from a single path to a flat snapshot list

The original `activePath()`/`fastForwardTo(List<Integer>)` could express exactly one active
descendant — physically incapable of recording several simultaneously-active `Parallel` children.
Replaced by `Node#collect(path, out)` / `Node#restore(path, all, context)`: each node that ever left
`NOT_STARTED` appends one `NodeSnapshot(path, state)` to a **flat** list (not a nested tree —
`NodeSnapshot` deliberately holds no `NodeSnapshot`-typed field of its own, since a genuinely
self-referential POJO can't be resolved by `PojoSerializer` as it stands: its cache can only return
a serializer for a type once that type has *finished* building its own, which a truly recursive type
can never do). `Sequence`/`Branch`/`Choice` restore exactly as before (one meaningful entry per
level); `Parallel` now records — and restores — one entry per child: an already-`COMPLETED` sibling
jumps straight to `COMPLETED` via a new `forceState` helper (no re-execution), a still-`RUNNING`
sibling resumes exactly where it left off, including through its own nested composites. Verified
live via the smoke test with a `Parallel` of one fast `Action` and two nested `Sequence→Choice`
branches: after restore, neither branch's already-run first step re-ran, and the fast action's
side effect fired exactly once across the whole save/restore cycle.

`FlowState` changed shape to carry this: `activePath: List<Integer>` became `snapshots:
List<NodeSnapshot>`, plus a new `runState: FlowRunState` field (the flow-level state `FlowInstance`
now needs to restore correctly) alongside the pre-existing `rootState: NodeState`. **This changed
the serialized format** — a `FlowState` saved before this change failed to decode against the new
layout. `CapabilityStorage`'s existing per-descriptor try/catch (verified live in the previous
phase) still caught this cleanly: no crash, just a reset to no active flows for whichever player had
one in progress — but the reset itself was *incidental*, discovered only because some field happened
to misread further down the buffer, never announced as "this is an old save."

#### Format versioning

`FlowState.registerSerializer()` closes that gap for every format change *from this point on*: it
installs a hand-written `Serializer<FlowState>` directly into `SerializerRegistry` — bypassing the
automatic `PojoSerializer` reflection every other capability POJO uses — whose first written value
is `FlowState.CURRENT_VERSION`. On read, that value is checked immediately: a match decodes the rest
normally, a mismatch throws a clear, explicit `IllegalStateException` naming both versions, still
caught by the same `CapabilityStorage` try/catch (same outward behavior — a clean reset — but now a
deliberate, diagnosable one instead of an accidental one). This is **not** retroactive migration: a
positional, untagged binary format has no way to tell "an old shape" from "garbage" without a marker
that was never there to begin with, so a `FlowState` saved before this method existed still resets
exactly as before — that specific gap is permanent, not solvable without a marker nobody wrote at
the time. What's fixed is every *future* shape change: bump `CURRENT_VERSION`, and the mismatch is
caught on the very first field read, not wherever the old and new layouts first happen to diverge.

Registration has a real ordering constraint: a `CapabilityDescriptor`'s serializer chain (for
`StoryFlowData`'s `Map<String, FlowState>` field) is resolved once, by `CapabilityBootstrap`, and
never re-resolved — so `FlowState.registerSerializer()` has to run *before* that, not merely before
`FlowBootstrap`'s own setup. `EngineBootstrap.init` calls it explicitly between `NetworkBootstrap`
and `CapabilityBootstrap` for exactly this reason (see that class's Javadoc). Verified live: a
round-trip through the registered serializer preserves `flowId`/`runState`/`snapshots` exactly, and
feeding it a buffer with a deliberately wrong leading version throws the expected, clearly-labeled
exception rather than misreading unrelated fields.

#### `Blackboard` / `Scope`

The task's explicit line: `Capabilities` owns persistent Minecraft state, `Blackboard` owns
transient Flow execution state — nothing in `Blackboard` is ever written to NBT. Four scopes:
`GLOBAL` (one map, whole JVM), `FLOW` (one map per `FlowDefinition` id, shared across every
instance of that definition), `INSTANCE` (one map per `FlowInstance` — this *is* `FlowContext`'s
pre-existing scratch map, now just addressable by `Scope` instead of being the only option), `NODE`
(a separate map, but — deliberately, to avoid overengineering a scope nothing here needs yet — not
automatically namespaced by the calling node's own path, since no node lifecycle method threads
that path down to `FlowContext`). `FlowContext#getVariable`/`setVariable(Scope, String)` are the
access point; the pre-existing `put`/`get(String)` remain as plain `Scope.INSTANCE` aliases.

#### `Evaluator<T>`

`Condition`/`Branch` now take `Evaluator<Boolean>` (`FlowContext -> T`) instead of a raw
`Predicate<FlowContext>` — the shared shape a future transition-requirement or expression evaluator
can reuse rather than each node inventing its own function type. Safe for every existing call site:
a lambda literal like `ctx -> storyPoints(ctx) >= 1` is structurally compatible with either
functional interface, so nothing in `FlowExamples`/`FlowTestCommand` needed to change.

#### `Task` / `Result`, `FailurePolicy`, `Timeout`, `Checkpoint`

- **`Task`/`Result`** — `Result` (`SUCCESS/RUNNING/FAILURE/CANCELLED`) is used narrowly: only by
  `Task` (`Result tick(FlowContext)`, `default void cancel(...)`) and its bridge node,
  `TaskAction` (`Flow.task(Supplier<Task>)`), which ticks a fresh `Task` per instantiation and
  translates its `Result` into `NodeState`. Deliberately *not* retrofitted onto `Action`/`Condition`
  — their existing boolean-based factories are untouched, and `NodeState` already gives them
  deterministic propagation. No real multi-tick task (animation, movement, scripted interaction) is
  implemented — only the bridge future systems can build one against.
- **`FailurePolicy`** (`FAIL/IGNORE/RETRY/FALLBACK`) — implemented as `PolicyNode`, a wrapper any
  `Flow` composes with (`Flow.withPolicy`/`Flow.retry`/`Flow.fallback`), not a parameter baked into
  `Sequence` — policy stays orthogonal to control flow. `activeLeaf`/`cancel` delegate correctly
  through a `PolicyNode` (so `Choice` selection and cancellation both work normally even wrapped),
  and persistence now does too: `PolicyNode` overrides `collect`/`restore` the same way
  `Sequence`/`Selector` do, so a `RETRY` in progress resumes at the *same attempt number* rather than
  restarting at 1, and a policy currently running its `FALLBACK` branch resumes the fallback rather
  than restarting the primary. `PolicyNode` only ever has two children (`primary`/`fallback`), not a
  list, so the child index the flat `NodeSnapshot` path already needs is repurposed as a small
  encoding instead of a list position: a non-negative index `n` means "running `primary`, this is
  attempt `n + 1`"; `-1` means "running `fallback`" — a private detail nothing outside `PolicyNode`
  interprets. Verified live: a `RETRY(5)` driven to attempt 3, saved, and restored resumes at attempt
  4 (not 1) and exhausts at exactly 5 total attempts; a `FALLBACK` saved mid-flight through its
  alternative branch (a `Sequence` whose first step already ran, now waiting) restores straight into
  that same waiting point without re-running the primary or the fallback's already-completed step.
- **`Timeout`** — a small reusable tick counter (`Timeout.ticks(n)`/`Timeout.NONE`,
  `tick(): boolean`), not a scheduler: `EventWaiter` is the one place it's wired in today, but
  `Wait`/`Task`/`SubFlow` could each hold one the same way without any change to `Timeout` itself.
- **`Checkpoint`** (`Flow.checkpoint()`) — a self-documenting no-op marker. Does not change *how
  often* persistence happens: `FlowManager` already persists on every node-state transition, not
  blindly every tick, which already *is* the checkpoint philosophy in spirit. What `Checkpoint`
  adds is intent — a `Flow` author can mark the specific points they're confident are safe to
  resume from, a real hook for a future "persist less often than every transition" mode without
  that mode needing to exist yet.

#### `EventBus.subscribe` — the one extension to `event`, not a second event system

```java
public <E extends Event> Subscription subscribe(Class<E> type, EventPriority priority, Consumer<E> handler)
```
`EventWaiter` needs a dynamic listener for exactly one event type at runtime — `@SubscribeEvent`
can't do that, it's discovered from compile-time-known annotated methods. `subscribe` wraps the
handler in a `MethodHandle` bound to `Consumer.accept`, the identical `(Event)->void` shape a
`@SubscribeEvent` method already resolves to — so `post`/`register`/`unregister`/`bind` didn't
change by one line; the new listener flows through the exact same dispatch/priority/cancellation/
cache machinery. Returns a `Subscription` (`void unsubscribe()`).

#### `@AutoFlow`

Mirrors `@AutoContent`/`@Packet`/`@Capability`/`@SubscribeEvent` exactly: a `public static final
FlowDefinition` field, found via the same `ModFileScanData` scan, registered into `FlowRegistry`
automatically. The required demo's own `FlowDemoScenario.DEMO` is `@AutoFlow`-registered — proof by
construction, not just documentation, that nothing manually registers it. Manual
`FlowRegistry.register(id, flow)` (still used by the original `flowtest` example) remains fully
supported side by side.

#### Threading and Minecraft isolation, reconfirmed

`FlowManager.tick()` is still fully synchronous on whichever thread calls it (the server thread, via
`FlowTickBridge`) — nothing new here hops threads or schedules work. "Parallel" still means
concurrent *logical* execution within one call to `tick()`, never OS threads. Every new core file
(`FlowDefinition`, `FlowInstance`, `FlowHandle`, `FlowRunState`, `Blackboard`, `Scope`, `Evaluator`,
`Task`, `Result`, `FailurePolicy`, `Timeout`, and every new `node` class) is exactly as
Forge-independent as the original core — the only Forge-aware file added is `FlowDiscovery` (same
`ModFileScanData` mechanism three other discovery classes already use safely on both sides).

#### Compatibility — what changed vs. what didn't

**Unchanged, byte-for-byte behavior**: `Flow`'s existing factories (`action`/`sequence`/`parallel`/
`choice`), `Action`, `Sequence`'s and `Branch`'s continuation/failure logic, `Transition`,
`FlowRegistry.register(ResourceLocation, Flow)`, `FlowManager.tick/selectChoice/resumeAll`'s
external behavior, `StoryFlowData`/`ModFlowCapabilities`, `FlowCompletedEvent`/`FlowFailedEvent`,
`FlowJoinBridge`, `FlowTickBridge`. The original `flowtest` example and its command still work
unmodified apart from one adaptation (below).

**Real, contained API changes**, each with exactly one affected call site: `Flow.condition`/
`Flow.branch` now take `Evaluator<Boolean>` instead of `Predicate<FlowContext>` (source-compatible
for every existing lambda call site — nothing needed editing); `FlowManager.start`/
`getActiveFlows` now return `FlowHandle`/`Collection<FlowHandle>` instead of `FlowRuntime`/
`Collection<FlowRuntime>` (`FlowTestCommand` was the only caller — three lines updated);
`FlowContext`'s constructor gained a `ResourceLocation flowId` parameter for `Blackboard` (both
existing construction sites are inside `FlowManager`, already touched by this pass).

**Format change, not a code change**: `FlowState`'s serialized shape — see the persistence section
above.

### Required example — `/storymodengine flowdemo`

```
Sequence
 ├─ Action:    "Flow started"
 ├─ Choice("optionA" → set var path=A → Wait 60 ticks,
           "optionB" → set var path=B → Wait 60 ticks)
 ├─ Action:    report the path taken (Blackboard read-back)
 ├─ SubFlow:   a short nested Sequence
 ├─ Action:    "SubFlow completed"
 ├─ Action:    "Waiting for event..."
 ├─ EventWaiter(FlowDemoSignalEvent, scoped to this player)
 └─ Action:    "Event received"
```

`start` / `status` / `choice <optionA|optionB>` / `signal` / `pause` / `resume` / `cancel`. Verified
live end to end: `start` → `choice optionA` → the ~3-second `Wait` → `SubFlow` runs → `EventWaiter`
parks awaiting `signal` → `signal` → `Event received` → flow completes, exactly the chat sequence
the task's own example describes.

## `multiblock` — spatial pattern description and detection

```java
import static com.dimalab.storymodengine.multiblock.PatternMatchers.*;

public static final Multiblock ALTAR = Multiblock.define("altar")
        .layer("ODO", "DAD", "ODO")
        .layer("OAO", "AAA", "OAO")
        .layer("ODO", "DOD", "ODO")
        .where('O', block(Blocks.OBSIDIAN))
        .where('D', block(Blocks.DIAMOND_BLOCK))
        .where('A', air())
        .build();

Optional<MultiblockMatch> match = ALTAR.find(level, pos);
```

**Why this exists**: a lot of mod content — altars, portals, multi-block machines, ritual
circles — needs to recognize "is this specific 3D arrangement of blocks present here?" Every mod
that needs this either hand-rolls nested loops of `level.getBlockState(pos.offset(...))` calls, or
pulls in a framework several times larger than the problem (GregTech/Multiblocked-scale systems
carry controller lifecycles, recipes, energy, and rendering baked in). `multiblock` is deliberately
just the one piece every one of those systems actually shares: **describe a spatial pattern, check
whether it's present, report exactly where.** What happens after a match — spawning a controller,
starting a machine, triggering a `Flow`, opening a portal — is the mod's decision, never this
package's. Researched HollowCore's own Kotlin multiblock implementation for ideas (not code) before
building this; see the "Rotation" subsection below for the one concrete thing it does worse.

### Pattern vs. Matcher

A **pattern** is a 3D grid of characters, one `PatternMatcher` per character (`Multiblock.Builder
#where`). A **matcher** decides whether one world position satisfies one pattern cell:

```java
public interface PatternMatcher {
    boolean matches(BlockGetter level, BlockPos pos, BlockState state);
}
```

Five implementations ship: `ExactBlockMatcher` (`block(Block)` — any `BlockState` of that block,
properties ignored), `BlockStateMatcher` (`state(BlockState)` — exact state, properties included),
`TagBlockMatcher` (`tag(TagKey<Block>)`), `AirMatcher` (`air()`), `AnyBlockMatcher` (`any()` — a
"don't care" cell). `PatternMatcher` takes `BlockGetter`, not `Level` — the read-only slice of the
world API a matcher could ever need, and it already exposes `getBlockEntity(BlockPos)`, so a future
BlockEntity-aware (or capability-aware, or fluid-aware) matcher is just another implementation of
this same one-method interface — no change to `Multiblock`, `MultiblockPattern`, or
`MultiblockDetector` required to add one.

`MultiblockPattern` is what `Multiblock.Builder#build()` compiles the raw `layer(...)`/`where(...)`
calls into: a precomputed, immutable `PatternMatcher[height][depth][width]` grid, validated once
and never touched again. Every malformed-pattern case fails with a specific message, never an
`ArrayIndexOutOfBoundsException`:

```
Multiblock 'altar': pattern is empty — call layer(...) at least once before build()
Multiblock 'altar': layer 1 has 2 row(s), expected 3
Multiblock 'altar': layer 0 row 2 has length 4, expected 3
Multiblock 'altar': unknown pattern symbol 'X' at layer 1, row 0, column 2 — no matcher registered via where('X', ...)
```

**Coordinate convention** (the one thing every pattern author has to know, so it's stated once,
precisely, everywhere it matters — `Multiblock`'s own Javadoc included): layer index = **Y** (0 =
bottom; one `.layer(...)` call per level), row index within a layer = **Z**, character index within
a row = **X**. `PatternRotation.NORTH` is the identity — a pattern is authored exactly as it will
appear in the world under `NORTH` (pattern-local +X → world +X/EAST, +Z → world +Z/SOUTH).

One deliberate deviation from the task's own example: layers are built with repeated
`.layer(String... rows)` calls rather than one flat `.pattern(String...)` call spanning every
layer. A single flattened varargs list has no way to recover where one layer ends and the next
begins without an extra dimension argument or a sentinel value — the task's own example only reads
unambiguously because of blank lines Java doesn't preserve. Vanilla Minecraft's own
`BlockPatternBuilder` resolves the identical ambiguity the same way, with repeated `.aisle(...)`
calls; `.layer(...)` is that same idea under this engine's own vocabulary.

### Rotation — one transform, not four

`PatternRotation` has four values (`NORTH`/`EAST`/`SOUTH`/`WEST`) — Y is never rotated, only
horizontal orientation. The mechanism is a single method every cell of every rotation goes through:

```java
public BlockPos toWorldPos(BlockPos origin, int dx, int dy, int dz) {
    int worldDx = xAxis.getX() * dx + zAxis.getX() * dz;
    int worldDz = xAxis.getZ() * dx + zAxis.getZ() * dz;
    return origin.offset(worldDx, dy, worldDz);
}
```

`xAxis`/`zAxis` are precomputed once per enum constant, at class-init time, by asking vanilla
`Rotation.rotate(Direction)` where the pattern's local +X/+Z basis directions end up under that
rotation (`Rotation.NONE`/`CLOCKWISE_90`/`CLOCKWISE_180`/`COUNTERCLOCKWISE_90` map 1:1 to
`NORTH`/`EAST`/`SOUTH`/`WEST`). No hand-rolled trigonometry, no new math system — reuses vanilla
`net.minecraft.world.level.block.Rotation`/`Direction` exactly where those already fit. This is the
one concrete place this implementation improves on HollowCore's own multiblock code, which hand-writes
four separate offset-branches (one `when (direction) { NORTH -> ...; SOUTH -> ...; ... }` per
direction) instead of a single formula — exactly the design this task's own instructions call out
to avoid, and the existing `math` package (built for smooth camera-path interpolation, not integer
90°-step grid rotation) genuinely doesn't fit the job either, so nothing there is reused.

### `MultiblockMatch` — never just a boolean

```java
public record MultiblockMatch(Multiblock multiblock, BlockPos origin,
                               PatternRotation rotation, List<BlockPos> positions) {}
```

`MultiblockDetector.find` returns `Optional<MultiblockMatch>`, not `boolean` — the reference
implementation researched for this task returns a plain boolean with no position/orientation
info at all, which is real, confirmed dead end for anything downstream: a caller that only learns
"yes, matched" can't highlight the structure, replace its blocks, look up a `BlockEntity` at a
specific cell, or start whatever comes next. `positions()` carries every matched world position, in
pattern-iteration order (layer, then row, then column) — one entry per pattern cell, air/any cells
included, since a controller-block use case may care just as much about the empty interior as the
solid shell. `origin()`/`rotation()` round out enough for a caller to reconstruct the full
structure's placement without recomputing any of this package's own math.

### Why `Multiblock` holds no runtime state

`Multiblock`/`MultiblockPattern` are pure, immutable data — no `Level`, no `BlockPos` of a specific
instance, no "is this one currently formed" flag, no controller reference. The same `Multiblock`
value can be checked against a thousand different candidate locations, by any number of unrelated
mods, without any of them stepping on each other — exactly the same "definition, not instance"
shape `Flow` already established for the same reason (see the `flow` section above). All
per-attempt state — which rotation matched, where, what positions were found — lives only in the
`MultiblockMatch` a specific `find()` call returns, never in the definition itself.

### Detection is always explicit, never automatic

`MultiblockDetector.find(multiblock, level, origin[, rotation])` is called once per check. There is
no per-tick scanning, no "find every multiblock in the world" pass, and no positional search beyond
the exact `origin` given — only the four rotations vary, never a neighborhood of candidate origins.
This keeps the system's cost proportional to how often *callers* choose to check, not to world size
or tick rate, and keeps the answer to "when does a check happen" entirely in the calling mod's
hands (a redstone-triggered check, a right-click, a periodic-but-mod-owned scheduler — none of
that is this package's concern). `MultiblockDetector.find` exits on the first mismatched cell (no
wasted iteration past the first failure) and never logs above `debug` per attempt — only a
successful match reaches `info`, so nothing here spams the log on a hot path, because there is no
hot path.

### Deliberately not built (extension points, not gaps)

- **Events** (`MultiblockMatchedEvent`/`MultiblockBrokenEvent`) — "matched" is just calling
  `find()` and acting on the `Optional`; "broken" has no meaning without stateful tracking, which
  the previous section rules out at this layer. A consumer can already post its own event through
  the existing `EventBus`/`Events` with zero new code here.
- **Network sync** — nothing here needs syncing yet; `network`'s `SerializerRegistry` stays an
  extension point, not a new packet.
- **Capabilities** — nothing here needs persisting yet, but nothing blocks a future system from
  storing formed-multiblock state (e.g. a matched footprint) through the *existing* `@Capability`
  mechanism, unchanged.
- **Serialization** — no bespoke code needed or added: `PatternRotation` (a plain enum) and
  `List<BlockPos>` already resolve for free through the existing `SerializerRegistry` generic-enum
  and generic-collection support the moment anything needs them. `Multiblock`/`MultiblockPattern`
  (behavior-holding definitions, not data) deliberately stay outside serialization, exactly like
  `Flow` itself is never serialized — only `FlowState` (pure data) is.
- **BlockEntity/capability/fluid matchers, dynamic/data-driven (JSON) patterns, a
  `MultiblockRegistry`, `@AutoContent`-style auto-registration, controller blocks, formed/broken
  lifecycle, caching, structure visualization/previews** — every one of these is a natural next
  layer *on top of* this package (most needing zero change to it — see `PatternMatcher`'s
  `BlockGetter` signature above), none of them built now. The next large system on this engine's
  roadmap is Cutscenes, not a multiblock framework, so this stays small on purpose.

### Live demonstration

`multiblock.example.MultiblockExamples.DEMO` — a deliberately **asymmetric** single-layer ring
(diamond block fixed on one edge, not the fully-symmetric `ALTAR` shown above), because a
rotationally-symmetric pattern can't actually prove rotation detection works: a broken or no-op
rotation transform would still "accidentally" match a symmetric layout from every orientation.
`/storymodengine multiblock build|check|clear` places it at the player's feet, runs `find` (all
four rotations), and reports the match (with the correct detected rotation) or its absence — mirrors
`FlowDemoCommand`'s existing shape exactly. Verified live: `build` → `check` reports
`Multiblock 'demo_ring' matched!` with the origin and the correct rotation → `clear` → `check`
again reports `Structure not found.`

## `cinematic` — deterministic cutscene/cinematic runtime

```java
CutsceneDefinition INTRO = CutsceneDefinition.define(id("intro"))
        .duration(200) // ticks — 10s at 20 tps
        .shot(0, 100, camera -> camera
                .position(0, pos(0, 2, -3))
                .position(40, pos(0, 1.7, 4))
                .rotation(0, rot(0, 5)))
        .actor("npc", actor -> actor.rotation(60, rot(180, 0)))
        .subtitle("You're finally here.", 70, 30)
        .build();
```

**Why this exists**: Flow already answers "what happens in a story"; nothing in the engine answered
"how is a moment *presented*" — a moving camera, an NPC turning to face the player, a subtitle, a
sound cue, all in a deterministic, tick-based sequence a mod author can describe declaratively
instead of hand-writing per-tick camera math. `cinematic` is exactly that layer, and nothing more:
it describes and plays back a timeline; it never decides *when* to start (that's a command, a
Flow action, a redstone trigger — always the calling mod's decision), and it never becomes a second
narrative system (branching, dialogue trees, scripting stay in `flow`, deliberately not duplicated
here).

This section originally covered a first, deliberately minimal version. It was later expanded (a
second, 27-section task, explicitly building on top of the same architecture rather than replacing
it) to add: `Clip`/`Binding` as generic primitives, screen fades, camera roll/LookAt/shot
transitions, `EventTrack`/`ActionTrack`, richer subtitles/audio, and playback control (seek/speed/
skip/markers). Everything below reflects the current, expanded system; where something changed
shape from the original version, that's called out explicitly rather than silently rewritten.

```java
CutsceneDefinition SHOWCASE = CutsceneDefinition.define(id("showcase"))
        .duration(300)
        .fade(0, 20, 0x000000, 1f, 0f)                       // fade in from black
        .shot(0, 110, camera -> camera
                .position(0, pos(0, 3, -4)).position(100, pos(0, 2, -1))
                .rotation(0, rot(0, 5))
                .roll(0, 0f).roll(30, 14f).roll(65, 0f)        // a brief camera tilt
                .fov(0, 75f).fov(100, 60f))
        .shot(110, 90, Shot.Transition.CROSSFADE, 20, camera -> camera  // blends in from shot 1
                .position(110, pos(-4, 2, 8)).position(190, pos(2, 2, 6))
                .lookAt(LookAt.entity("npc")))                // stays framed on "npc" regardless of the camera's own path
        .actor("npc", actor -> actor.position(230, pos(1, 0, 8)))
        .sound(130, SoundEvents.VILLAGER_AMBIENT, 1f, 1f, pos(0, 1, 8))  // spatial
        .subtitle("A traveler returns.", 120, 60, "Elder Maren", 8, 8, 0xFFD27F, Subtitle.Position.BOTTOM)
        .marker("reveal", 110)
        .event(150, () -> new ShowcaseSignalEvent(player))     // fires an arbitrary Event through the existing EventBus
        .fade(280, 20, 0x000000, 0f, 1f)                      // fade out to black
        .build();
```

### Definition, Instance, Runtime, Context — and one deliberate difference from `flow`

`CutsceneDefinition` (id + `Timeline`) is fully immutable, exactly like `FlowDefinition`. Where this
diverges from `flow` on purpose: a `Flow`'s `Node` tree *is* mutable runtime state (rebuilt fresh
per `instantiate()`), but every `Track`/`Timeline` in `cinematic` stays a **pure function of time**
all the way through — `track.evaluate(tick, partialTick)` never mutates anything and always returns
the same value for the same input. All actual mutable state (`currentTick`, `PlaybackState`) lives
on `CutsceneInstance`, driven only by `CutsceneRuntime`. This isn't a stylistic choice: §22 of the
task this was built from requires it explicitly — `time = 4.5s` must always evaluate to the same
state, which is what makes scrubbing, replay, and deterministic multiplayer playback possible later
without touching a single `Track` implementation. `CutsceneContext` (level, viewer, symbolic
actor-name → runtime-entity-id map) is the per-play binding layer — the same role `FlowContext`
plays for `Flow` — so a definition never hardcodes a specific runtime entity id, only a symbolic
name (`ActorBinding.named("npc")`) resolved fresh every time it's played.

That purity is also exactly what makes `CutsceneInstance#seek(int)` cheap and correct: it does
nothing but reposition `currentTick` (clamped to the timeline's range) and leave the very next
normal per-tick apply pass in `ClientCutscenePlayer` to produce the right frame — no replay logic
needed anywhere, and no one-shot cue (audio, `EventTrack`, `Trigger`) between the old and new tick
is scanned or fired, which is what keeps scrubbing from spamming them. Playback **speed** is a
`float` on `CutsceneInstance` (default `1.0`) driving a fractional `cinematicTime` accumulator
(`currentTick = floor(cinematicTime)`) — deliberately instance-local, not on `CutsceneDefinition`,
so it never changes what a definition *is*, only how fast one particular play-through advances
through it. `SkipPolicy` (`SKIPPABLE`/`NON_SKIPPABLE`/`SKIP_TO_END`/`SKIP_TO_MARKER`, defaulting to
`SKIPPABLE`) and named `CutsceneMarker`s (`Timeline#markerTick(name)`) round out playback control —
`CutsceneRuntime#skip()`/`jumpTo(name)` are both just `seek(...)` under the hood.

### The server/client split — and why there's almost no network traffic

This is the most consequential architectural decision in this system. The **entire visual runtime**
(camera, actor visual override, subtitles, audio) runs **client-side only**, driven by
`cinematic.client.ClientCutscenePlayer`. The server's only two jobs, both in `CinematicManager`:
decide *whether* a cutscene may start (the "one active camera-controlled cinematic per player"
policy — starting a new one cancels any currently-active one for that player first, firing
`CutsceneCancelledEvent`), and independently detect completion by counting its own ticks against
`Timeline#durationTicks()` — the same deterministic number the client counts against locally. One
`PlayCutscenePacket {cutsceneId, actorEntityIds}` starts it; **zero** further packets are needed for
normal playback, since both sides already loaded the identical `CutsceneDefinition` via
`@AutoCutscene`. `Trigger` fires from `CinematicManager#tick()`, server-side — the expansion's
`EventTrack` (arbitrary `Event` suppliers) and `ActionTrack` (arbitrary `Consumer<ServerPlayer>`,
the escape hatch for gameplay effects like a capability write or starting a `Flow`, without
`cinematic` itself ever hardcoding gameplay logic) fire from the exact same place, on the exact same
per-cutscene exact-tick-match check `Trigger` already used — no new firing mechanism, just two more
lists walked next to it. This is the strongest form of "server: play cutscene X; client:
deterministically evaluates the timeline" the task asked for, and because `EventTrack`/`ActionTrack`
are server-only, client-local playback speed/seek/pause never affects when they fire.

`CinematicManager#tick()` now wraps each active player's own cutscene in its own `try`/`catch` — a
real gap found while adding `EventTrack`/`ActionTrack`: before this, one throwing cue would abort
that whole server tick's loop, silently skipping *every other* player's active cutscene that tick.
A failure is now logged (`EngineLog.channel("Cinematic")`) and only that one player's cutscene is
dropped. `ClientCutscenePlayer#onClientTick` got the matching treatment client-side (catch, log,
clean shutdown via the existing `stopInternal()` — never leave a player stuck with a detached
camera). Audio is the one client-local one-shot mechanism speed can actually skip past (a jump from
tick 8 to tick 12 at 4x would otherwise silently drop cues at 9–11), so `ClientCutscenePlayer` scans
every tick crossed since the last one it applied, not just the latest — `seek()`/`jumpTo()`/`skip()`
instead resync that bookkeeping to the new tick *without* scanning the gap, which is what keeps
scrubbing itself from replaying cues while normal fast-forward still can't silently skip them.

### `Track`/`Keyframe` — built entirely on the existing `math` module

`Track<S>` (`S evaluate(int tick, float partialTick)`) is the shared contract every concrete track
(`CameraTrack`, `ActorTrack`, `SubtitleTrack`, `AudioTrack`) implements. `Keyframe<T>(tick, value,
Interpolator<T> interpolator)` and `KeyframeTrack<T>` (the shared "find the surrounding pair, blend
between them" logic, used by both `CameraTrack`'s and `ActorTrack`'s position/rotation/FOV channels)
reuse `math.interp.Interpolator`/`Interpolators` directly — `Interpolators.VECTOR3F` for position,
`QUATERNION_SLERP` for rotation, `FLOAT` for FOV — with per-segment easing attached the existing way,
`interpolator.eased(Easing.EASE_IN_OUT_CUBIC)`, no new interpolation code anywhere. The one addition
to `math` itself is `Interpolators.step()` (holds the start value until `t = 1`, then snaps) — for
discrete channels like `ActorTrack`'s visibility, added to the *shared* class since "hold then snap"
is a genuinely generic interpolation shape, not a cinematic-specific one.

`ActorState`'s three channels (position/rotation/visible) are each independently `Optional` — an
`ActorTrack` built with only a rotation keyframe leaves `position()`/`visible()` empty rather than
snapping the bound entity to an arbitrary default, so `ActorApplier` only ever touches the channel a
mod author actually animated.

### Camera — the one class that actually touches Minecraft's concrete camera

There is no Forge event to override camera *position* — only `ViewportEvent.ComputeCameraAngles`
(angles) and `ComputeFov` (FOV) exist. Camera position is instead controlled by whichever `Entity`
`Minecraft#setCameraEntity(Entity)` currently points the render camera at — the same mechanism
spectator camera-riding and every existing freecam-style mod already uses. `cinematic.client
.CameraRig` creates a vanilla `Marker` (a genuinely invisible, zero-size, do-nothing anchor entity)
**client-side only, never added to the level** — never ticked, never rendered, never seen by
anything else — and drives its raw position/rotation directly every render frame.

Because this rig entity is never ticked by the level, nothing manages its own previous/current pose
the way a normally-simulated entity's own `tick()` would, so `CameraRig` does that explicitly with
this engine's own `math.interp.TickValue` — one per channel (position, rotation, FOV), fed a new
target value once per **client tick** (`CameraRig#tick`) and sampled at partial-tick resolution once
per **render frame** (`CameraRig#renderFrame`), which snaps the marker's raw pose to the smoothly
interpolated value and returns the FOV to apply via `ComputeFov`. This is `TickValue`'s own
documented example use case ("a cutscene camera's pose") realized directly. `ActorTrack`'s bound
entity, by contrast, is a normal, already-ticking entity — its own built-in interpolation already
smooths whatever `ActorApplier` sets each tick, so no separate `TickValue` is needed there.

**Roll** has no vanilla entity field the way position/rotation do (no entity ever rolls), so it
doesn't go through the marker at all — verified directly against the Forge 1.20.1 sources before
building on it: `ViewportEvent.ComputeCameraAngles` has `getRoll()`/`setRoll(float)`, added
specifically "to apply roll" per its own Javadoc. `CameraTrack` gained a fourth `KeyframeTrack<Float>
roll` channel (defaults to a constant 0°, the same pattern FOV's own default already used), fed into
its own `TickValue<Float>` in `CameraRig` and applied through a new `ComputeCameraAngles` handler —
exactly parallel to how FOV already goes through `ComputeFov` instead of the entity.

**LookAt** (`cinematic.binding.LookAt`, via the new `cinematic.binding.Binding<T>` — the generalized
`T resolve(CutsceneContext)` shape `ActorBinding` already followed in spirit, kept as its own
two-argument method for compatibility rather than retrofitted) is an optional target on
`CameraTrack` — `LookAt.position(Vector3f)` or `LookAt.entity("npc")` (built on the existing
`ActorBinding.named`, resolved to the target's eye position). Resolution happens in
`ClientCutscenePlayer` (the only place with a live `CutsceneContext`) **once per game tick**, the
same cadence every other channel already updates at, then baked into the `CameraState` handed to
`CameraRig#tick` — so it gets exactly the same inter-tick smoothing as a keyframed rotation would,
no special-cased render-time override. A missing target (despawned entity, bad binding) resolves to
`null` and the shot's own keyframed rotation is used for that frame instead — logged at `debug`
(`LookAt` can be checked up to 20×/sec, so anything louder would spam) — never a crash. A shot with
no `lookAt()` set behaves exactly as before this existed.

### `Shot` — multi-shot, transitions, and where `Clip` actually landed

`Shot(startTick, durationTicks, CameraTrack, Transition, transitionTicks)` — the original
three-argument shape is still exactly how most shots are built (a `Consumer<CameraTrackBuilder>`
overload without a transition defaults to `Transition.CUT, 0`, so every pre-existing `shot(...)`
call is unaffected). Whichever `Shot`'s range contains the current tick is "in control" of the
camera, unchanged.

`Transition.CROSSFADE`/`FADE` are new: `ClientCutscenePlayer` remembers the previously-active shot
for `transitionTicks` after a new one starts and, for `CROSSFADE`, blends the outgoing shot's camera
state into the incoming shot's own state over that window using the *existing* `Interpolators`
(`VECTOR3F`/`QUATERNION_SLERP`/`FLOAT` — no new interpolation code); `FADE` reuses the new
`FadeOverlay` for a short opacity dip instead of a separate rendering path. A full generic
`BlendMode`/`Blendable<T>`/`TrackMixer<T>` framework was **deliberately not built** — the one real
need this task named (adjacent shots blending into each other) is solved concretely by
`Shot.transition()`; a generic mixer is a documented future extension point, not a missing feature.

`Clip<S>` (`record Clip<S>(int startTick, int durationTicks, Track<S> content)`, evaluated at time
*local* to the clip — the one place in this system that convention applies, unlike every keyframe
tick elsewhere which is absolute) is the generic primitive the task's package diagram asked for, now
actually built — but `Shot`/`CameraTrack`/`ActorTrack` were **not** retrofitted onto it (the task
says extend, not rewrite, and they already had a working, tested shape). Its first and only concrete
user is `FadeTrack` (`List<Clip<FadeState>>` — multiple, non-overlapping fades on one timeline,
first-match-wins on overlap, the same rule `Timeline#activeShot` already used for shots). Speculative
`CameraClip`/`ActorClip`/`AudioClip`/`SubtitleClip`/`EventClip` concrete classes were not built —
`Clip<S>` plus the tracks that already exist covers what they'd have been for.

### Subtitle / Audio / Fade — data separated from presentation

`Subtitle(text, startTick, endTick, speaker, fadeInTicks, fadeOutTicks, rgbColor, Position)` and
`AudioCue(sound, volume, pitch, position)` are still plain data (the original 4-argument `Subtitle`
constructor and the original 4-argument `sound(...)` builder overload both still exist, defaulting
the new fields, so nothing that already called them changed behavior); `cinematic.client
.SubtitleOverlay`/`AudioApplier`/the new `FadeOverlay` remain the only classes that actually draw
text, play a sound, or paint a screen overlay — kept deliberately separate so a future UI system or
audio-mixing layer could replace any of them without a `Track` changing shape. `Subtitle#opacityAt
(tick)` computes the fade-in/out ramp (via the existing `Interpolators.FLOAT`) so `SubtitleOverlay`
never needs to know a tick number itself. `AudioCue#position()`, when set, plays through
`Level#playLocalSound` instead of the original `Player#playSound` — a real spatial sound instead of
one played "on" the viewer. `FadeTrack`/`FadeOverlay` (§4, screen fades) work the same way — plain
`FadeState(rgbColor, opacity)` data, one renderer, reusing `Interpolators.FLOAT`/`Easing` for the
opacity ramp, no second color-blend framework. Looping/stoppable audio (a full `SoundInstance`
lifecycle) was **not** built — a cutscene's cues are one-shot stingers/lines by nature, and every
example so far only ever needed that.

`AudioTrack`/`EventTrack`/`ActionTrack`/`Trigger` all share the same "fires exactly once, on its
exact tick" contract — evaluated/checked once per game tick, never once per render frame like
`CameraTrack`/`ActorTrack`, or the same cue would fire many times a second.

### Flow integration — the smallest possible, no new node type

```java
Flow.sequence(
    Flow.action(ctx -> CinematicManager.play(ctx.player(), INTRO, Map.of("npc", npcEntityId))),
    Flow.waitForEvent(CutsceneCompletedEvent.class, (ctx, event) -> event.cutsceneId().equals(INTRO.id())),
    Flow.action(ctx -> { /* story continues */ })
);
```

No `WaitForCutscene` node was added — `Flow.waitForEvent`/`EventWaiter` already do exactly this,
unchanged, reacting to `CutsceneCompletedEvent` the same way any other engine event is awaited.
Starting a cutscene from a `Flow.action` is the same pattern every other subsystem (network sends,
capability writes) already uses from inside a Flow — no special-casing needed for `cinematic`.

### Title Card — a small, independent presentation primitive

```java
TitleCard card = TitleCard.builder(Component.literal("Тем временем..."))
        .subtitle(Component.literal("Где-то глубоко под землёй"))
        .fadeIn(20).hold(60).fadeOut(20)
        .build();
CinematicManager.playTitleCard(player, card);
```

A full-screen black narrative card (fade in → hold → fade out, title + optional subtitle) for
transitions between cutscenes — `Cutscene A → TitleCard → Cutscene B`. It lives in
`cinematic.titlecard`/`cinematic.state.TitleCardState`, follows the same Definition → Instance →
Runtime → Context shape as Cutscene, and is *presentation only* — it never touches the world,
entities, or the camera. It's deliberately **not** wired into Cutscene's own `Timeline`/`Shot`
machinery — a mod author sequences `Cutscene A → TitleCard → Cutscene B` themselves (a `Flow`, or
three back-to-back calls with `Flow.waitForEvent` between each), the same way any two independent
`cinematic` primitives compose, rather than `cinematic` growing a sequencing layer of its own.

A few things came out differently from Cutscene once actually built, each for a concrete reason:

- **No registry, no id.** `CutsceneDefinition` needs one because its `Track`s hold Java lambdas
  that can't cross the network — that's the entire reason `PlayCutscenePacket` only ever carried an
  id, resolved through `CutsceneRegistry` on both sides. `TitleCard(Component title,
  Optional<Component> subtitle, int fadeInTicks, int holdTicks, int fadeOutTicks)` has no such
  problem — every field is already-serializable data (`MinecraftSerializers` already registers
  `Component` via `FriendlyByteBuf#writeComponent`/`readComponent`, and `SerializerRegistry` already
  handles `Optional<T>` generically) — so `PlayTitleCardPacket` just embeds the record directly, the
  same way any other nested record (e.g. `math.transform.Transform`) already "just works" once its
  own components resolve. No `TitleCardRegistry` exists.
- **`TitleCard implements Track<TitleCardState>`** — the exact interface `CameraTrack`/`ActorTrack`/
  `SubtitleTrack` already implement, not a new one. `evaluate(tick, partialTick)` is a pure
  fadeIn/hold/fadeOut opacity envelope built on the existing `Interpolators.FLOAT`, same determinism
  contract as every other track. No `Easing` is wired in yet (the task's own "keep it simple" for
  v1) — attaching one later is a one-line change at the two `interpolate(...)` call sites, not a
  redesign.
- **`TitleCardInstance` reuses the existing `PlaybackState` enum** (`NOT_STARTED/PLAYING/PAUSED/
  COMPLETED/CANCELLED`) rather than a duplicate NOT_STARTED/PLAYING/COMPLETED/CANCELLED enum —
  `PAUSED` is simply never reached. A new `TitleCardPhase` (`FADE_IN/HOLD/FADE_OUT`) *was* added,
  since nothing existing represents that.
- **`CinematicManager.playTitleCard`/`cancelTitleCard`**, not a new manager class — one server-side
  gatekeeper, with its own `Map<UUID, ActiveTitleCard>` (independent of the cutscene one, so a title
  card and a cutscene never fight over one "active" slot) ticked from the same existing
  `CinematicTickBridge` source, with the same per-player fault isolation the cutscene loop already
  has.
- **The renderer draws its own full-screen black rectangle rather than reusing `FadeOverlay`.**
  This looked like an obvious reuse (identical visual effect) but was rejected: `FadeOverlay` is one
  shared mutable static field written every tick by `ClientCutscenePlayer`'s own loop; a title card
  is driven by a fully independent client driver (`ClientTitleCardPlayer`) with its own "one active
  per player" policy. Routing both through the same static field would let a concurrently-active
  cutscene fade and a title card visibly stomp each other's opacity. `cinematic.client
  .TitleCardOverlay` fills its own rectangle (the same `alpha << 24` packing technique, a few lines,
  not a second fading *framework*) and layers a scaled-up, centered title (`GuiGraphics#pose()`
  scale + `drawCenteredString`) with a smaller centered subtitle below it, both driven by the same
  `TitleCardState#opacity()` so background and text always fade in lockstep.
- **Events carry no id** — `TitleCardStartedEvent`/`CompletedEvent`/`CancelledEvent(ServerPlayer)`
  only. `CutsceneCompletedEvent` needs a cutscene id because several different cutscenes could
  plausibly be relevant to one `Flow.waitForEvent`; a player has at most one active title card ever
  (the same "starting a new one cancels the old" policy), so nothing needs disambiguating.
- **Two-layer validation**: `TitleCard`'s own compact constructor clamps negative durations to `0`
  and rejects a `null` title — defensive, since this constructor also runs on the network-
  deserialization path (a malformed value must never crash there); `TitleCard.Builder#build()`
  validates eagerly and throws `IllegalStateException` *before* that, the same developer-facing,
  fail-fast style `CutsceneDefinition.Builder#build()` already uses.

**Live demonstration**: `/storymodengine titlecard demo` (the required "Тем временем..." / "Где-то
глубоко под землёй" example) and `/storymodengine titlecard show "<title>" ["<subtitle>"]` for
custom text, both in `cinematic.example.TitleCardDemoCommand`.

### EventBus, Network, Capabilities, Serialization — what's reused vs. genuinely new

- **EventBus**: four small events — `CutsceneStartedEvent`/`CutsceneCompletedEvent`/
  `CutsceneCancelledEvent` (server-only, `ServerPlayer`) and `CutsceneTriggerEvent` (typed on the
  plain `Player` base class) — all plain records through the existing `EventBus`/`Events`, nothing
  new in `event` itself. `EventTrack` (§9) reuses exactly this — its cues are `Supplier<Event>`, so
  it posts whatever arbitrary mod-defined `Event` a mod author's supplier returns, still through
  `Events.post(...)`, never a second event mechanism. `ActionTrack` (§10) posts nothing itself — its
  cues are `Consumer<ServerPlayer>`, the deliberately-unopinionated escape hatch for gameplay
  effects (posting an event is one thing a mod's own `Consumer` is free to do).
- **Network**: still exactly two `@Packet` records, `PlayCutscenePacket`/`StopCutscenePacket` —
  unchanged. Every new field this expansion added (`Clip`/`Binding`/`LookAt`/`FadeTrack`/
  `EventTrack`/`ActionTrack`/`CutsceneMarker`/`SkipPolicy`/`Shot.Transition`/playback speed) lives
  entirely inside the identical, already-loaded `CutsceneDefinition` both sides build from ordinary
  Java code — none of it ever needs to cross the wire, so nothing new was registered in
  `SerializerRegistry` either.
- **Capabilities**: still not touched — §19 of this expansion's own task explicitly says not yet.
  `CutsceneInstance`'s state boundary (definition id, `currentTick`, `PlaybackState`, `speed`) stays
  clean and self-contained specifically so a future `@Capability` persisting it across logout
  remains a normal, unblocked addition later, not something this expansion needed to design around.
- **Discovery**: `@AutoCutscene` on a `public static final CutsceneDefinition` field, found by
  `CutsceneDiscovery` via the same `ModFileScanData` scan every other `@Auto*` annotation already
  uses — `CutsceneRegistry.register(...)` remains available for definitions that need to be built
  dynamically (see the live demo, which can't be a static field at all — see below).

### Live demonstration

Three layers, each still testable independently:

- **`/storymodengine cutscene demo`** — unchanged: a genuinely dynamic (not `@AutoCutscene`) demo
  built fresh from the player's live position, spawning a temporary villager, unaffected by anything
  in this expansion.
- **`/storymodengine cutscene play <name>`** — the 11-entry example gallery, also unchanged by this
  expansion (still `simple`/`move`/`zoom`/`talk`/`actor`/`shots`/`trigger`/`epic`/`flow`/
  `conversation`/`flythrough`).
- **`/storymodengine cinematic showcase`** — new, the mandatory demonstration for this expansion.
  One definition exercising every item the task's checklist named: multi-shot camera movement/FOV,
  a roll "tilt", a `CROSSFADE` transition into a `LookAt`-framed shot, a manually-framed third shot
  (both styles deliberately shown coexisting), actor movement, a fade-in/out subtitle, a spatial
  audio cue, screen-fade bookends, a named marker, an `EventTrack` cue with a real visible reaction
  (chat message + particles, `CinematicShowcaseCommand#onShowcaseSignal`), and started through a
  tiny `Flow` (`Flow.action` → play; `Flow.waitForEvent(CutsceneCompletedEvent.class, ...)` →
  announce) so Flow → Cinematic → EventBus is proven end to end inside the required command itself.
  Playback pause/resume/seek/jump/speed/skip are exercised live via `/storymodengine cutscene
  pause|resume|seek <ticks>|jumpto <marker>|speed <x>|skip` (`cinematic.client
  .CutsceneControlCommands`, registered through Forge's `RegisterClientCommandsEvent` — verified to
  exist and hand over the same `CommandSourceStack` shape every other command here already uses,
  chosen specifically *because* these touch `ClientCutscenePlayer`'s client-only state and must not
  be reachable from the server-side dispatcher).

### Closing the gaps: authoritative actors, JSON, server-validated control, collision, persistence, sequencing, recording

A later pass closed seven gaps identified against Unreal Sequencer/Unity Timeline and real
Minecraft cutscene mods. Each reuses existing infrastructure rather than adding a parallel system:

- **`ActorTrack#authoritative()`** (opt-in, default `false` — every existing cutscene is unaffected)
  makes `CinematicManager#tickOne` move the *real* server-side entity each tick via `Entity#moveTo`,
  which piggybacks on Minecraft's own existing entity-tracker sync — no new packet. `ClientCutscenePlayer`
  skips its own local `ActorApplier` call for an authoritative track so the two never fight over the
  same entity; both sides already know which mode a track is in, since both hold the identical
  `Timeline`.
- **`cinematic.json.CutsceneJsonLoader`** loads `CutsceneDefinition`s from
  `data/<namespace>/storymodengine/cutscenes/*.json`, the same `SimpleJsonResourceReloadListener` +
  `AddReloadListenerEvent` mechanism every vanilla data-driven system (loot tables, recipes) already
  uses, parsing straight into the *same* `CutsceneDefinition.Builder` a Java cutscene goes through —
  one validation path. `EventTrack`/`ActionTrack` (genuinely Java `Supplier`/`Consumer` code) are the
  one thing a JSON cutscene can't express; everything else (shots, roll/LookAt/transitions, actors
  incl. `authoritative`, subtitles, spatial audio, fades, triggers, markers, skip policy) is fully
  supported.
- **Server-validated playback control** replaces the earlier client-local-only `pause`/`seek`/
  `speed`/`skip`: `RequestCutsceneControlPacket` (`ServerboundPacket`) asks, `CinematicManager
  #handleControlRequest` validates (does the sender actually have *this* cutscene active; is a
  *forward* jump — an explicit forward `SEEK` or `SKIP` — allowed under `SkipPolicy`, refusing it
  outright under `NON_SKIPPABLE`, which a raw forward `SEEK` is deliberately held to the exact same
  standard as `SKIP` so it can't be used to bypass that policy) and always replies with
  `SyncCutscenePlaybackPacket`, the authoritative outcome, whether or not it matches the request.
  `PAUSE`/`RESUME`/`SPEED`/backward `SEEK` are always allowed — none of them can skip content a
  player hasn't already reached; a sped-up cutscene still fires every `Trigger`/`EventTrack`/
  `ActionTrack` cue in between (a range scan over every tick crossed since the last one checked, the
  same fix already applied to the client's own audio firing), it only compresses *how fast* time
  passes, never *how much* of the timeline gets evaluated.
- **`CameraCollision`** pulls a shot's camera position in toward the viewing player if the authored
  path clips through solid geometry — one `Level#clip(ClipContext)` raycast per render frame, the
  same primitive vanilla's own third-person camera already uses for the identical problem (verified
  against `Camera#getMaxZoom` source), not a new physics system.
- **`CutscenePlaybackData`** (`@Capability`, mirrors `flow.integration.persistence.StoryFlowData`)
  checkpoints an in-progress cutscene's tick roughly every second (not every tick — see its own
  Javadoc for that trade-off) and restores it via `CinematicManager#resumeAll` on `PlayerConnectedEvent`
  (mirrors `FlowManager#resumeAll`), resuming the client by sending `PlayCutscenePacket` with a
  non-zero `startTick`, which just calls the already-existing `seek`. Only a cutscene started from a
  registered id is restorable (a dynamically-built one, like the gallery's, has nothing to look up).
- **`CinematicManager#playSequence`** is sugar over the *existing* `Flow.sequence`/
  `Flow.waitForEvent` pattern (already demonstrated by `playFlowIntegration`) for chaining several
  cutscenes — not a new sequencing/nesting engine. A generic `BlendMode`/`Blendable<T>`/
  `TrackMixer<T>` framework remains a deliberate non-goal; `Shot.transition()` already covers the one
  real blending need (adjacent shots).
- **`cinematic.client.CameraRecorderCommands`** (`camrecord start`/`stop <name>`) captures the local
  player's position/rotation once per client tick and writes it out in the exact schema
  `CutsceneJsonLoader` reads — commands only, deliberately no GUI screen. The file lands under the
  game directory, not inside a data pack, so it's a draft a mod author promotes manually, not
  something that auto-loads.

Still explicitly out of scope, unchanged: no looping/stoppable audio (every cue remains a one-shot
stinger/line), no lip sync, no branching, no visual timeline editor. Nothing here needed to change
shape to make any of these addable later.

## `voxel` — model-JSON-driven `VoxelShape` generation

```java
public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
    return ModelShapes.get("mymod:block/machine", state.getValue(FACING));
}
```

**Why this exists**: a block model's `elements` (its cuboids) and its collision/outline shape are
the same geometry, described twice — once in the model JSON a Blockbench export produces, once
again by hand as `Shapes.box(...)` calls in Java, with nothing keeping the two in sync. `voxel`
derives the second from the first: `ModelShapeLoader` parses a model's cuboids into a
`ShapeDefinition`, `ShapeRotation` turns it to face NORTH/EAST/SOUTH/WEST without four hand-written
variants, and `ShapeCache` makes the whole pipeline pay its cost once instead of on every
`getShape()` call. This is a Phase 1 foundation, deliberately: cuboids only, four-way horizontal
rotation only, one shared cache — not a mesh/rigging/animation system.

### `ShapeBox` / `ShapeDefinition` — normalized coordinates, unioned once

A block model's `from`/`to` are in **model units**, `0..16` per axis; `Shapes.box(...)` wants
**normalized** `0..1` coordinates. `ShapeBox.fromModelUnits` is the one place that `/16` conversion
happens — `[0,0,0]→[16,8,16]` becomes `Shapes.box(0,0,0,1,0.5,1)` — so it's never redone, or gotten
slightly wrong, at a second call site. `ShapeDefinition` is an immutable list of `ShapeBox`es plus
the `VoxelShape` they union into via `Shapes.or(...)`; the union runs exactly once, in the
constructor, not on every `toVoxelShape()` call — a `ShapeDefinition` never changes after
construction, so recomputing it per access would be pure waste.

### Rotation — exact quarter-turns, not quaternions

`ShapeRotation` rotates a `ShapeDefinition` authored facing `NORTH` (Minecraft's own modeling
convention) to `EAST`/`SOUTH`/`WEST` by remapping each box's normalized X/Z corners directly around
the block center `(0.5, 0.5)`, `Direction.getClockWise()` many times over. This is deliberately
**not** built on the `math` package's `Angles`/`Transform` quaternion machinery: a quaternion
round-trip through `Shapes.box`'s min/max corners risks floating-point drift off the block grid,
and every rotation this package ever needs is an exact 90° step — there's no interpolation to reuse
`math` for in the first place. `Direction.getClockWise()` (Y-axis rotation) is reused directly for
the turn-counting bookkeeping, so no second direction system exists alongside vanilla's own.

The turn count is however many `getClockWise()` steps reach the target facing from `NORTH` — the
same direction a vanilla blockstate's `"y"` rotation turns a north-authored render model (`facing=
east` → `"y": 90`, and so on), so a rotated collision/outline shape stays aligned with the rotated
render without either side needing to know about the other.

### Why the model is read off the classpath, not through `Minecraft`'s resource system

`Block#getShape()` runs on **both** logical sides — a dedicated server needs it for real collision
physics, not just an outline to render — and a dedicated server has no `Minecraft` instance and no
live client `ResourceManager` for `assets/`. `ModelShapeLoader` never touches either: it resolves
`assets/<namespace>/models/<path>.json` with a plain `ClassLoader.getResourceAsStream` lookup, which
works identically on physical client, integrated server, and dedicated server, since every mod's
jar — `assets/` folder included — is already on the shared mod classloader's classpath on every
side. This mirrors the same dist-safety discipline `network.context.ClientLevelLookup` documents
elsewhere in this engine: a class that references `Minecraft` crashes a dedicated server the moment
it's *loaded* (Forge's `RuntimeDistCleaner` scans bytecode at class-load time, not per-branch), so
the model loader is written to never load that reference at all.

**Trade-off accepted for Phase 1**: this does not respect a resource pack overriding a model at
runtime — there's no reload listener involved, it reads whatever's on the classpath at JVM start,
which for a normal mod jar is exactly the model it ships. A `SimpleJsonResourceReloadListener`-based
variant (client-side only, like `cinematic.json.CutsceneJsonLoader`) is the natural Phase 2 path if
live override-awareness is ever needed for a render-only use case.

### `parent` — inherited `elements`, not model composition

If a model has no `elements` of its own and declares a `parent`, `ModelShapeLoader` follows the
parent chain (same classpath loader, capped at 8 levels against a cyclic reference) and uses the
nearest ancestor's `elements` — covering the common "my model just points at a shared base with the
real geometry" case. It does not resolve texture variables or reimplement any of vanilla's
`BlockModel`/model-baking machinery — that machinery is render-only and irrelevant to shape.

### Rotated elements — skipped, not approximated

A model element with its own `"rotation"` block (arbitrary-angle, not a 90° facing rotation) is
dropped from the resulting `ShapeDefinition` and logged once via `EngineLog.channel("Voxel").warn`,
never converted into an approximate axis-aligned box. A visibly-wrong collision shape is worse than
a documented gap.

### `ShapeCache` — two layers, both thread-safe

A model is parsed at most once (`ResourceLocation → ShapeDefinition`, unrotated) and each
`(model, rotation)` pair is unioned into a `VoxelShape` at most once (`record CacheKey(ResourceLocation,
Direction) → VoxelShape`), both `ConcurrentHashMap#computeIfAbsent` — `Block#getShape()` can run off
the main thread (chunk loading/world generation), so neither cache can assume single-threaded access.
`ModelShapes.clearCache()` exists for diagnostics (`/storymodengine voxel test` uses it to verify
parsing is deterministic independent of cache identity), not normal operation — a model doesn't
change shape while the game is running.

### No `BlockShapeProvider` interface

A block's `getShape()` calling `ModelShapes.get(id, facing)` is already a one-liner; wrapping that
single call in an interface would be an abstraction with no second implementation to justify it —
omitted, matching this engine's general preference for the direct call over a layer that only ever
has one member.

### Demo and diagnostics

`voxel.example.ExampleMachineBlock` (`HorizontalDirectionalBlock`, reusing vanilla's own `FACING`
rather than redeclaring it) is a two-cuboid "machine" — a base plus an offset vent — so its rotated
outline is visibly asymmetric, not a plain cube pretending to demonstrate rotation. Its model/
blockstate JSON are hand-authored under `assets/storymodengine/...`, which — per `ResourcePacks`'
documented `Pack.Position.BOTTOM` generated-pack ordering — silently wins over the trivial default
`@AutoContent` would otherwise generate for it, with no engine change required.

`/storymodengine voxel demo` places one instance per horizontal facing next to the player.
`/storymodengine voxel test` is where this package's test coverage actually lives: there is no
JUnit source set configured in this project (`src/test/java` is empty, `build.gradle` has no test
dependency), so — matching this engine's established convention of in-game diagnostic commands
verified live — it asserts single/multiple boxes, model-unit→normalized conversion, empty
definitions, all four rotations, cache reuse (same `VoxelShape` instance twice), malformed/missing
model handling (returns `ShapeDefinition.EMPTY`, never throws), and parse determinism (two
independent parses, cache cleared between, produce equal box lists) — reporting PASS/FAIL per case.

### Explicitly out of scope (Phase 1)

Automatic blockstate parsing, arbitrary mesh geometry, OBJ/FBX/GLTF, visual model rendering (the
model still renders through vanilla's own pipeline — this package only derives its *shape*),
automatic collision-behavior injection, per-tick shape generation, networking, capabilities,
JSON-defined custom shape files distinct from a real model, an editor, and automatic registration of
every `Block#getShape()` in a mod. Extension points if a later version needs them: rotated-element
conversion (an actual quaternion/rotated-`VoxelShape` composition, not a skip), texture-variable
`parent` inheritance, and a reload-listener-based loader for live override-awareness client-side.

## `dialogue` — NPC/player conversations, compiled onto `flow`

```java
DialogueDefinition dialogue = DialogueDefinition.builder(id("village_guard"))
        .node("start")
            .line("Guard", "Halt! Who are you?")
            .choice("I'm a traveller.").gotoNode("traveller")
            .choice("None of your business.").gotoNode("rude")
        .node("traveller")
            .line("Guard", "Then welcome to the village.")
            .end()
        .build();

DialogueSystem.start(player, dialogue);
```

**Why this exists**: a dialogue is a sequence of lines and branching choices, waiting on player
input at each step — exactly what `flow.node.Choice` already does ("park `RUNNING` until external
code calls `select(id)`"). `dialogue` compiles a `DialogueDefinition` into an ordinary `Flow` and
drives it through the *existing* `FlowManager`/`FlowRegistry` — it is an adapter and a GUI, not a
second execution engine, persistence layer, event bus, or network abstraction.

### The compiler — every entry maps onto an existing `flow` primitive

`DialogueRunner` (package-private) is the whole adapter. A `DialogueLine`'s "wait for Continue" and
a real `DialogueChoiceGroup` are **both** just `Flow.choice(Transition...)` — one option
(`"continue"`) for a line, one `Transition` per offered choice for a real branch; `DialogueAction`/
`DialogueConditionEntry` compile straight to `Flow.action`/`Flow.condition`; a choice's target node
(or a `DialogueJump`) compiles to `Flow.lazy(() -> nodeFlows.get(targetId))`. No new `Node` subtype
anywhere.

**One small, additive method was added to `Flow` itself**: `Flow.lazy(Supplier<Flow> supplier)` —
`new Flow(() -> supplier.get().instantiate())`. Every existing factory (`sequence`/`branch`/
`choice`/`subFlow`/...) takes an already-*constructed* `Flow` value, which cannot express a forward
reference to a node not compiled yet, or a cyclic one that refers back to itself (a dialogue graph
routinely does both). `DialogueRunner` fills a `Map<String, Flow> nodeFlows` in one pass; every
cross-node reference reads through it lazily, resolved only once the whole definition has finished
compiling — never during compilation itself, so no infinite recursion in the compiler. This mirrors
exactly how the earlier `flow` expansion added `Selector`/`Checkpoint`/`PolicyNode`: additive, zero
changed lines in any existing factory.

Compiled **once per `DialogueDefinition`**, cached, and registered under one stable id in the
*existing* `FlowRegistry` — not a fresh throwaway `Flow` per player. `FlowManager.start` already
gives every caller an independent `Node` tree from one shared `Flow` value, so two players running
the same `DialogueDefinition` get two fully independent runs for free, the same property
`flowdemo`/`flowtest` already rely on.

### Choice validation lives in `dialogue`, not in `Choice`

`flow.node.Choice` has no concept of a per-transition *condition* — that's dialogue-specific.
`DialogueInstance` keeps its own record of which `DialogueChoice`s are currently offered (already
filtered by condition). `DialogueSystem.selectChoice` refuses anything not found in that offered
list **before** ever calling `FlowHandle.selectChoice(...)` — a client can only ever select what the
server itself already sent it, never anything it merely guesses at.

### Two timings, one notification path

A compiled dialogue step's "show" action can run **synchronously** inside `DialogueSystem.start`/
`selectChoice`'s own call stack (via `Choice.select`'s cascade into its target's `onStart`) — but if
a `DialogueAction`/`DialogueConditionEntry` sits between two waiting points, `Sequence` only
advances past an already-completed child on its *next* `onTick`, one or more server ticks later.
Rather than have `DialogueSystem` assume one timing, `DialogueInstance#showLine`/`#showChoices`/
`#markCompleted`/`#markCancelled` each notify `DialogueSystem` themselves, from wherever they
actually run — the same `DialogueStepPacket`/`StopDialoguePacket` send fires correctly either way,
with no special-casing at the call site. (This top-level orchestrator started life as
`DialogueManager` and was renamed `DialogueSystem` once the client-side split below made "who talks
to Flow" and "what's on screen" genuinely separate concerns worth naming distinctly — its public
shape didn't change, and it deliberately still has no `tick()`/`advance()` of its own: `flow
.FlowManager`'s existing tick already drives every step, so a second driver here would be exactly
the second execution engine this package exists to avoid.)

### Network — 3 packets, not the 4 first sketched

- **`DialogueStepPacket`** (Clientbound) — `dialogueId`, `nodeId`, `entryIndex`,
  `availableChoiceIds`. Carries no text: the client already has the identical
  `DialogueDefinition` (loaded the same way on both sides via `@AutoDialogue`/JSON — the same
  assumption `CutsceneDefinition` already makes), so this only needs to say *which* entry is
  active and which choices passed server-side condition checks.
- **`DialogueChoicePacket`** (Serverbound) — `dialogueId`, `transitionId`. Covers both a real
  choice and "Continue" (the reserved id `"continue"`) — one packet instead of two, since both
  ultimately resolve the same parked `Choice`. `ServerboundPacket` already rejects a spoofed
  reverse-direction send (same precedent `RequestCutsceneControlPacket` relies on).
- **`StopDialoguePacket`** (Clientbound) — closes the window on completion/cancellation/failure.
  No separate "start" packet — the first `DialogueStepPacket` *is* the start.

### `DialogueWindow` (overlay) vs. `DialogueScreen` (modal) — presentation split from input

A plain line/thought/narration and a real choice have genuinely different input needs: a line
shouldn't stop the player moving or looking around while it's up (every reference use case —
ambient chatter, an internal thought, a shouted warning mid-fight — plays out *during* live
gameplay), but picking a choice needs focused mouse/keyboard capture. One class can't cleanly be
both, so this is two:

- **`DialogueWindow`** — *not* a `Screen`. A static control class mirroring `cinematic
  .client.FadeOverlay` in spirit, but delegating state and drawing out to two collaborators instead
  of holding either itself (see below). Since it never captures input, advancing needs a real
  `KeyMapping` (`DialogueContinueKeyMapping`, registered via `RegisterKeyMappingsEvent` — the first
  one this engine has needed) rather than a click, which would otherwise still reach the world
  underneath (swinging the held item, breaking a block). First press while the typewriter is still
  animating reveals the rest of the line locally (no packet); a second press sends the real
  `DialogueChoicePacket("continue")`.
- **`DialogueScreen extends Screen`** — the same plain-`Screen` shape `math.example.EasingDemoScreen`
  already establishes, opened *only* for a `DialogueChoiceGroup` step (mouse click on a row, up/
  down + Enter, sends `DialogueChoicePacket`, closes itself).
- **`ClientDialoguePlayer.onStep`** is the single place deciding which of the two is live at any
  moment — a `DialogueLine` entry drives the overlay (closing `DialogueScreen` first if one happened
  to be open); a `DialogueChoiceGroup` entry hides the overlay and opens `DialogueScreen`. Never
  both showing at once. `onStop` hides/closes whichever is active.

#### State/Renderer split inside `DialogueWindow` itself

`DialogueWindowState` (plain data — current line/style, `TypewriterState`, and entrance/exit
animation timestamps, no `GuiGraphics` reference anywhere in it) is what `DialogueWindow`'s two
`@SubscribeEvent` hooks actually drive; `DialogueWindowRenderer` (renamed from the earlier
`DialoguePresenter`, same file's job) reads that state and draws — `render`/`renderBackground`/
`renderAvatar`/`renderSpeaker`/`renderText`/`renderIndicator`, each a focused step, none of them
touching input. `hide()` doesn't null the state immediately: it starts an exit timer, and
`DialogueWindow`'s tick handler only calls `state.reset()` once `isHideComplete` says the exit
animation has actually finished playing.

#### Layout — anchors, not pixel coordinates

`HorizontalAnchor`/`VerticalAnchor` (`LEFT/CENTER/RIGHT`, `TOP/CENTER/BOTTOM`) plus `DialogueLayout`
(`maxWidthFraction`, `margin`, `padding`, `offsetX`/`offsetY`) resolve to actual pixels only inside
`DialogueWindowRenderer#render`, against the live `screenWidth`/`screenHeight` — nothing is ever
stored as an absolute coordinate, so the box lands correctly at any resolution or GUI Scale by
construction. `DialogueLayout.DEFAULT` is bottom-center at 75% screen width, matching the task's own
reference mock. Box **height is measured**, not guessed: `Font#split` against the line's full text
(not just what the typewriter has revealed so far — using the full text keeps the box a stable size
throughout the reveal instead of visibly growing tick by tick) gives the wrapped line count, which
plus the speaker line and padding gives an exact height; a present avatar is laid out *inside* the
box on the left (the task's own ASCII mock), pushing text content right by its width + padding, and
sets a height floor so a large avatar next to a short line still fits. **No true rounded corners**:
`GuiGraphics#fill` only draws axis-aligned rects, and re-filling a corner pixel with a translucent
color only composites it *more* opaque, never punches a hole in what's already drawn — a documented
rendering-abstraction limit, not a silently skipped feature.

#### Animation — frame time, eased, reusing `math.interp.Easing`

`DialogueWindowAnimation` (`NONE`/`FADE`/`SLIDE`, a nullable field on `DialogueLine` — falls back to
the active `DialogueStyle`'s own default) is timed the same way `math.example.EasingDemoScreen`
already times its own slide-in: a `Util.getMillis()` timestamp sampled fresh every `render()` call,
never a tick counter, so it's smooth regardless of tick rate or lag. Progress is run through
`Easing.EASE_OUT_CUBIC` on the way in and `Easing.EASE_IN_CUBIC` on the way out — reusing the
existing curve library rather than hand-rolling a second one. `FADE` scales every color's alpha
channel; `SLIDE` offsets the whole box (background, avatar, text, indicator — one `PoseStack.
translate` wrapping all of it) by its own height, toward its anchor; `NONE` skips both, appearing
instantly. `SHOUT`'s style defaults to `NONE` — a shout shouldn't ease politely into view.

#### `DialogueMode` — real font styling, never a text rewrite

`DialogueMode` (`NORMAL`/`THOUGHT`/`WHISPER`/`SHOUT`/`MUTTER`, a field on `DialogueLine`) resolves to
one named `DialogueStyle` constant via `DialogueStyle.forMode(...)` for colors/default-animation,
*and* to real `Style`/`PoseStack` flags in `DialogueWindowRenderer#renderText` for the parts a color
alone can't express: `THOUGHT`/`MUTTER` render italic, `SHOUT` renders bold, `WHISPER` renders at a
reduced scale (`PoseStack#scale`, with the word-wrap width computed against the *unscaled* text so
wrapping stays correct at any scale). The line's own text is never rewritten (no auto-uppercasing,
no added punctuation) — only how it's drawn changes, so a mod author's exact string is always what
appears.

`DialogueSpeaker` (`id`, display name, portrait, name color) is optional, reusable presentation
metadata for a `DialogueLine#speaker()` id — `DialogueSpeakerRegistry.get` resolving to nothing (the
normal case for an unregistered speaker) falls back to the raw id string and the style's own default
speaker color, so existing `.line("Guard", "...")` calls need no migration. Typewriter reveal
(`TypewriterState`) is **entirely client-side, never synced** — the runtime only ever knows "line
started"/"line completed", never how many characters are visible.

### `DialogueContext` — wraps `FlowContext`, doesn't replace it

`player()`/`level()`/Blackboard access (`getVariable`/`setVariable`) all delegate straight through
to the wrapped `FlowContext` — no second variable system. Adds only `bind(String name, int
entityId)`/`resolveActor(String name)` (a plain map, not a reuse of `cinematic.binding.Binding<T>`,
which is typed specifically to `CutsceneContext`) and `close()`. `DialogueCommand`'s named factories
(`playCutscene`/`showTitleCard`/`startFlow`/`closeDialogue`) are one-line delegates to the existing
`CinematicManager`/`FlowManager` facades — this package owns no cutscene/titlecard/flow logic.

### JSON and discovery — the same shape as `flow`/`cinematic`

`@AutoDialogue` mirrors `@AutoFlow` byte-for-byte (same `ModFileScanData` mechanism). `
DialogueJsonLoader extends SimpleJsonResourceReloadListener`, registered via `AddReloadListenerEvent`
exactly like `CutsceneJsonLoader`, parsing into the *same* `DialogueDefinition.Builder` the Java DSL
uses. **Not representable in JSON, by design**: `DialogueCommand`/`DialogueConditionEntry` are
genuinely Java code (an arbitrary `Consumer`/`Evaluator`) — a JSON node is lines followed by an
optional choice list (plain jumps, no condition/command) or a single unconditional jump, the same
boundary the JSON cutscene format already draws around `EventTrack`/`ActionTrack`.

### Explicitly deferred (not required for this version)

Persistence: `Flow`'s own mechanism already resumes the compiled node tree correctly (parked at the
same `Choice`) across a relog — but `DialogueSystem`'s own bookkeeping (`DialogueInstance`: current
node/entry, offered choices) is not persisted or rebuilt on reconnect in this version, matching the
task's own "not required for v1" instruction. `DialogueInstance`'s shape deliberately mirrors
`FlowInstance` closely enough that a future `DialogueJoinBridge` (mirroring `FlowJoinBridge`) could
rebuild it cheaply if this becomes needed. Portrait rendering is wired (`DialoguePresenter` blits
whichever texture `DialogueLine#portrait()` or the resolved `DialogueSpeaker` provides), but this
engine ships no portrait art of its own — the demo dialogues register a speaker with a name/color
and no texture, a fully supported combination, since drawing sample character art isn't this
engine's job. Also still deferred: a dialogue history log and voice-line playback (`voiceId` exists
on `DialogueLine`, unwired).

## `quest` — declarative quests, compiled onto `flow`

Full design rationale, data flow, and JSON schema: [QUEST_SYSTEM_DESIGN.md](QUEST_SYSTEM_DESIGN.md)
— written before any quest code existed, based on reading the real `flow`/`dialogue`/`capabilities`/
`event`/`network` APIs rather than assuming them. Summary of the shape, matching this file's own
style for `dialogue`/`cinematic`:

`QuestDefinition` (id, title, an ordered list of `QuestStep`s — `quest.objective.Objective` and
`BranchStep` both implement it — plus rewards and optional prerequisites) is immutable, built via
`Quest.define(id)...build()`. `QuestCompiler.compile(...)` turns one into a plain `Flow` — no second
runtime, no quest-specific ticking; `QuestSystem.start/stop` is the only entry point, and it just
hands the compiled `Flow` to the existing `FlowManager`, the same "cache the compiled Flow per
definition id" pattern `DialogueSystem` already uses. `Objective` is the extension point (task's own
words: "a mod author's own class implementing it is a first-class objective, `QuestCompiler` never
inspects which kind it has") — built-ins (`kill`/`collect`/`talkTo`/`interact`/`location`/
`findEntity`/`dialogue`/`quest`/`event`) all compile to `Flow.waitForEvent(...)`, event-driven, never
polling the world; `location`/`findEntity` are the one exception (no natural Minecraft event for
"player is near X"), self-polling once/second via `Flow.lazy`/`Flow.branch` recursion — existing
`Flow` primitives only, nothing new added to `flow` itself. `Objective.dialogue(...)` starts an
existing `DialogueDefinition` via `DialogueSystem.start` and waits for `dialogue.event
.DialogueCompletedEvent` — the exact structural analogue of `CinematicManager.playSequence`'s own
cutscene-chaining pattern, applied to Dialogue for the first time. Real-world triggers (kill/collect/
interact) are bridged from Forge events into this engine's own `event` system by *extending* the
existing `event.bridge.MinecraftEventBridge`, not a second bridge. Persistence is one more
`@Capability` (`QuestProgressData`, `sync = true`) — no bespoke NBT code, and no bespoke state-sync
packet either, since the existing generic `Capabilities.sync(...)` already round-trips it; the one
new packet (`QuestToastPacket`) exists only because a one-shot "this just happened" toast can't be
expressed by a state snapshot. `@AutoQuest`/`QuestDiscovery` mirror `@AutoDialogue` byte-for-byte;
`QuestJsonLoader` mirrors `DialogueJsonLoader`, with objective/reward `"type"` dispatched through a
registerable lookup map, never a switch. Demo quests: `quest.example.QuestExamples` (`find_the_rhino`
— dialogue + multiple objectives + `Reward.unlockQuest` chaining; `village_trial` — a `.prerequisite`
gate + `.branch` compiling straight to `Flow.branch`). `/storymodengine quest list|start|stop|
progress|test` (`quest.debug.QuestCompilerTestCommand`'s `test` subcommand is this project's
established in-game substitute for JUnit, since it has no test framework configured — every later
subsystem's own `debug`/`selftest` command follows the identical shape).

## `raycast` — a fluent wrapper over vanilla's block/entity raycast primitives

Not a runtime, not a subsystem with its own state — a small, stateless utility layer, the same
category as `math`. `Raycast.from(entity)` or `Raycast.from(level, start[, direction])` returns a
`RaycastQuery` (a mutable, chainable builder — same shape as `QuestDefinition.Builder`); `.distance
(...)`/`.to(end)`, one of `.blocks()`/`.entities()`/`.any()`, and optional shape/fluid/filter/exclude
calls configure it, and `.cast()` is the only method that actually touches the world — one call, one
result, no persistent object left behind. Block resolution is exactly `Level#clip(ClipContext)`, the
same primitive `cinematic.client.CameraCollision` (and vanilla's own `Camera#getMaxZoom`) already use
for an unrelated purpose; entity resolution is vanilla's own `ProjectileUtil.getEntityHitResult`, the
exact primitive every vanilla projectile/fishing-rod raycast already uses. Neither block nor entity
geometry is reimplemented anywhere in this package — `RaycastQuery` only composes those two calls and
gives them a readable builder, matching the project's standing "engine wraps vanilla, never replaces
it" principle.

`.any()` mode mirrors `ProjectileUtil.getHitResult`'s own approach: clip blocks first, then search
entities only up to that clip distance (so a solid wall correctly blocks an entity behind it,
matching real vanilla targeting behavior), and report whichever is nearer. A block filter
(`.blockFilter(...)`) applies only to the single nearest solid hit — vanilla exposes no "skip past
non-matching blocks and keep scanning" primitive, and hand-rolling one would mean re-implementing
voxel traversal (explicitly out of scope); this is also the physically honest behavior, since a ray
can't see through an opaque block it doesn't care about to find one it does further along. Entity
filtering (`.filter(...)`, `.exclude(...)`) is baked directly into the `Predicate<Entity>` handed to
`ProjectileUtil`, so a farther *matching* entity correctly wins over a nearer non-matching one — no
separate scan needed. `.all()` (every matching entity along the ray, nearest first) is offered only in
`.entities()` mode for the same vanilla-primitive-availability reason: `Level#getEntities` +
`AABB#clip` naturally support enumerating every match, but blocks have no vanilla equivalent.

`RaycastResult` is the one type a caller looks at instead of switching on vanilla's `BlockHitResult`/
`EntityHitResult`/`HitResult.Type` by hand — `.hitBlock()`/`.hitEntity()`/`.missed()`, `Optional`-
wrapped `.block()`/`.blockState()`/`.entity()`, and always-defined `.position()`/`.distance()`; `.raw()`
is the escape hatch back to the underlying vanilla `HitResult` for anything genuinely vanilla-specific
this doesn't wrap (e.g. `BlockHitResult#getDirection()`).

Debug visualization (`.debug()`/`.debug(particle)`/`.debug(particle, spacing)`) uses only vanilla
particles — repeated `ServerLevel#sendParticles`/`ClientLevel#addParticle` calls sampling the ray at
a capped point count (96 max, regardless of distance/spacing), never a custom renderer. It's entirely
one-shot: every particle for one `.cast()` is placed synchronously in that same call, so there's no
tick loop and nothing persists. `RaycastDebug` is the dispatcher (loaded on both sides, since
`RaycastQuery` itself has no `Dist` gate) and handles the `ServerLevel` branch directly — `ServerLevel`
isn't `@OnlyIn`, so that's safe; the client branch is delegated to `RaycastClientDebug`, the only class
in the package that references `ClientLevel`/`Minecraft`, mirroring `network.context
.ClientLevelLookup`'s already-established dist-safety split byte-for-byte (Forge's
`RuntimeDistCleaner` checks a class's own bytecode for `@OnlyIn`-mismatched references at class-*load*
time, not per executed branch, so the reference has to live in a class that's never loaded on a
dedicated server — not merely a branch that never runs there). Verified live: a `runServer` boot check
reached `Done` with the new package's command (`raycast.example.RaycastDemoCommand`,
`@Mod.EventBusSubscriber`-registered like every other subsystem's demo command) already loaded, with
no new crash report.

No `EngineBootstrap` registration at all — there's no `@Auto*` discovery, no `@Capability`, no
`@Packet` this package needs; a mod author just calls `Raycast.from(...)`. `/storymodengine raycast
test|debug [distance]` (`raycast.example.RaycastDemoCommand`) casts `.any()` from the invoking player
and reports block/entity/miss to chat, with `debug` additionally exercising the particle path.
`/storymodengine raycast selftest` (`raycast.debug.RaycastSelfTestCommand`) is this project's
established in-game substitute for JUnit (same reasoning as `quest.debug.QuestCompilerTestCommand` —
no test framework configured): block hit, miss, entity hit, entity exclusion, entity type filtering,
block-state filtering, distance limiting, explicit start/end ray distance, line of sight, and
nearest-hit-wins-over-farther-match, each asserted against real spawned/discovered world state and
reported pass/fail — every temporary marker entity it spawns is discarded in a `finally` block
regardless of outcome, so the command never leaves world state behind. No `RaycastHitEvent` was
added to `event` — nothing in this package or its demo/test content has a recurring need to *react*
to a raycast happening (as opposed to just reading its one-shot result), and the task's own
instructions were explicit not to add an event just to round out the API surface; add one if and when
a real use case needs to know "some code, somewhere, cast a ray and hit X" after the fact. Likewise,
`Raycast` needs no `Flow` integration code of its own — `Flow.condition(() -> Raycast.from(...)
.cast().isHit())` already works today, since a raycast is just a plain synchronous Java call.

## `trigger` — "when," compiled to nothing but a `FlowManager.start` call

**Trigger answers "when"; `Flow` answers "what."** A `Trigger` owns exactly one detection
mechanism — `LOCATION` (point/radius or AABB, `ENTER`/`EXIT`), `TIME` (an exact game-time tick or a
range, wrapping past midnight), or `EVENT` (a subscription on the existing `EventBus`) — and, once
that condition is met, does nothing but `FlowManager.start(flowId, player)`, the same "cache the Flow
under a derived id, re-register into `FlowRegistry` on every use" pattern `QuestSystem` already
established for its own compiled flows (`<id>_trigger_flow`, mirroring `_quest_flow`). There is no
Trigger execution engine, no `TriggerSequence`/`TriggerAction`/`TriggerWait` — those are Flow's job,
and `Trigger.location("x").radius(...).whenEntered().run(Flow.sequence(...))` is the whole point:
detection and reaction are two different concerns, kept in two different systems.

```java
@AutoTrigger
public static final Trigger VILLAGE_ENTRY = Trigger.location("village_entry")
        .radius(new BlockPos(100, 64, 200), 5)
        .whenEntered()
        .once()
        .run(Flow.sequence(...));
```

`Trigger` plays both the static-factory-facade role (`Quest`'s job) and the immutable-definition
role (`QuestDefinition`'s job) in one class — deliberately, so the field type in `@AutoTrigger public
static final Trigger X = Trigger.location(...)....run(...)` reads as one coherent noun, the same
self-factory shape `Optional.of(...)`/`Stream.of(...)` already use in the JDK. `@AutoTrigger` +
`trigger.discovery.TriggerDiscovery` mirror `@AutoQuest`/`QuestDiscovery` byte-for-byte, with one
addition: after registering into `TriggerRegistry`, discovery also calls `TriggerSystem.arm(trigger)`
so an `EVENT` trigger's subscription exists the moment discovery finishes.

**Scope (global vs. per-player) is inferred, never a builder call** — a `LOCATION` trigger and an
`EVENT` trigger whose event type exposes a `player()` accessor fire per player (`ONCE` = "once per
player," tracked via the player's own `@Capability`); a `TIME` trigger, and an `EVENT` trigger whose
event carries no identifiable player, fire independently for every currently-online player when the
condition is met (`ONCE` = "once for the server, ever" — whichever players are online at that moment
each get an independent `Flow` instance; a player who wasn't online never does, exactly how a
one-time world event actually reads in-game). The player lookup for `EVENT` triggers uses reflection
for the dominant `player()` convention (verified against every existing engine event: 33 of 34
player-carrying events follow it) via `EventPlayerExtractor`, with `Trigger.Builder#on(Class,
Function)` as the explicit escape hatch for the one real exception in this codebase,
`EntityKilledEvent#killer()`.

**`LOCATION`/`TIME` are polled, not event-driven** — there is no Forge "player moved" or "game time
reached X" event, and this engine has a standing, documented policy (`quest.objective
.LocationObjective`'s own Javadoc) against ever broadcasting a global per-tick movement signal just to
support this. `trigger.integration.TriggerTickBridge` mirrors `flow.integration.FlowTickBridge`
exactly (`Phase.END`, `AtomicBoolean`-guarded `registerOnce`) and, every `Trigger
.TIME_CHECK_WINDOW_TICKS` (20) ticks, walks `TriggerRegistry.all()` itself — filtering by kind —
rather than requiring a separate "armed" list to stay in sync with the registry. An exact `.at(tick)`
target is matched within that same 20-tick window (not exact equality), since a single-tick poll
target could otherwise be stepped over entirely between polls. Per-(trigger, player) "was already
inside" and per-trigger "was already in the matching time window" edge-detection state is
deliberately in-memory only, not persisted — it only decides whether a check counts as a fresh
crossing, not whether the trigger is *allowed* to fire (that's the persisted half, below); a restart
mid-window can cause at most one harmless extra edge-detection on the next poll, fully absorbed by the
real eligibility check for a `ONCE` trigger. `EVENT` triggers need no polling at all.

**Persistence is opt-in, per trigger, through the existing `@Capability` system only** —
`persistent()` is `false` by default, in which case `TriggerFiredTracker` never touches a capability
at all (a plain in-memory set, live for the server's lifetime — a trigger with no persistence need
shouldn't pay for one). `persistent()` routes the identical "has this already fired" check through
`trigger.persistence.TriggerStateStore` instead: per-player state is one more `EntityCapability`
(`TriggerPlayerStateData`, mirroring `QuestProgressData` exactly), and *global* state — the one thing
Quest's own persistence never needed — is a `LevelCapability` (`TriggerGlobalStateData`, the same
owner kind `capabilities.example.StoryLevelData` already exercises) attached to `server.overworld()`
specifically, the one canonical "world" a global flag needs regardless of which dimension the firing
condition happened to be checked in. Both are `sync = false` — server-only bookkeeping, never
displayed to a client.

`/storymodengine trigger list|info|fire` (`trigger.example.TriggerDemoCommand`) — `fire <id>` bypasses
the detector entirely and calls `TriggerSystem.fireForPlayer` directly, for testing. `/storymodengine
trigger selftest` (`trigger.debug.TriggerSelfTestCommand`) is this project's established in-game
substitute for JUnit: pure-logic geometry/time-window checks, then `ONCE`/`REPEAT`/persistent-`ONCE`/
disabled-trigger checks against the real invoking player — the policy checks read a fire counter
*synchronously* after calling `TriggerSystem.fireForPlayer` directly (no tick wait needed), since
`Node#start`→`Action#onStart` was verified from source to run a single-action `Flow` fully
synchronously within `FlowManager.start` itself, not deferred to the next server tick. Verified live:
`runServer` reached `Done` with `@AutoTrigger discovered 3 definition(s)` (one per kind,
`trigger.example.TriggerExamples`) and no new crash report.

Not related to `cinematic.Trigger` (an unrelated, narrower type — a cutscene timeline's own "at tick
N, fire cue X"). Different package, no compile collision, but the two are easy to confuse by name
alone if both ever needed importing into the same file.

## `concurrent` — background work, with exactly one door back to the main thread

Infrastructure underneath the engine, not a second runtime beside it: gameplay stays exactly as
deterministic and main-thread-oriented as before, and this package exists purely so CPU-bound work,
blocking I/O, and timed/delayed operations have somewhere safe to run *off* that thread. `Async` is
the whole public surface (`Async.run`/`.supply`/`.delay`/`.schedule`/`.parallel`/`.race`/`.cpu()`/
`.io()`/`.scheduled()`/`.main()`), and `AsyncTask<T>` is both the future and the cancellation handle —
no mod author ever touches `Thread`/`ExecutorService`/`Future` directly, and no `CompletableFuture`
(used internally) crosses the public API.

Exactly four executors, never a fifth: `CPU` (bounded, `max(2, cores − 1)` threads), `IO` (elastic,
0–64), `SCHEDULED` (one thread, timekeeping only — it never runs a task body, only ever hands one to
CPU/IO/MAIN once its delay elapses), and `MAIN`, which isn't a pool at all — `Async.main(...)`/
`.thenMain(...)` route through a `server.isSameThread() ? run() : server.execute(...)` dispatcher, the
same shape `logging.LogEntry#runOnServerThread` already used, generalized. Wall-clock scheduling
(`java.time.Duration` — new to this codebase, confined to this package) and Minecraft-tick scheduling
(`TickScheduler`, driving `Async.nextTick`/`.afterTicks`/`.everyTick`) are two deliberately separate
axes, never mixed; for gameplay/story logic, ticks stay authoritative.

**The main-thread rule is structural, not just documented**: a background lambda passed to
`Async.cpu()`/`.io()`/`.run`/`.supply` receives no `FlowContext`/`Level`/`Entity`/`ServerPlayer` — there
is nothing Minecraft-shaped to accidentally misuse off-thread. The only ways back are
`Async.main(...)`, `AsyncTask#thenMain(...)`, and `Flow.await`'s consumer; `AsyncTask#blockingGet(...)`
— the one call that could deadlock a caller — throws immediately if invoked on the main thread rather
than merely warning against it.

**Flow integration** is the one place this package touches another system: `Flow.async(Function)`/
`Flow.await(Function, BiConsumer)` build an `AsyncNode` (mirroring `EventWaiter`'s subscribe/release
shape) that suspends the flow without polling or blocking. Since `Node.state` is a plain,
non-volatile field, a background completion is never written to it directly — it's handed to
`FlowResumeQueue` and drained by `ConcurrencyTickBridge` at `TickEvent.Phase.START`, strictly before
`FlowTickBridge`'s existing `Phase.END` handler runs `FlowManager.tick()` — so the state change always
settles within the same tick it happened, with zero changes to `Flow`/`FlowManager`/`FlowRuntime`/
`Node` themselves. `FlowHandle.cancel()` reaches a running `AsyncTask` through the same composite
`onCancel` propagation every other cancellable node already uses.

Cancellation is cooperative only (`CancellationToken`/`CancellationSource`, never `Thread.interrupt()`),
cascading parent→child but never child→parent — an upstream task may be shared by `Async.parallel`/
`Async.race`. A background failure that nothing observes is logged once, at `ERROR`, never silently
dropped and never able to crash the server. `ConcurrencyBootstrap.init()` runs first in
`EngineBootstrap`, registering listeners only; the real thread pools are created on
`ServerStartingEvent` and torn down on `ServerStoppingEvent` (both fire on the integrated server too,
which is why pool creation isn't done once at mod-construction time).

The one seam this whole package exists to keep open: `executor.ExecutorFactory` is the entire
abstraction between "what `Async` calls" and "what actually runs the work." `PlatformThreadExecutorFactory`
(plain `java.lang.Thread` pools) is the only implementation today — no Java 21 API is referenced
anywhere in this pass — but a future `VirtualThreadExecutorFactory` is a second implementation and
nothing else, with zero change to `Async`/`AsyncTask`/any call site. See `ASYNC_SYSTEM_DESIGN.md` for
the full design, and `/storymodengine async list|info <id>|stats|debug <on|off>|selftest|stresstest
[count]` for the in-game observability and verification commands.

## Planned package layout

Not implemented yet — listed here so future systems have an obvious home and a consistent
relationship to `content`. Each is added only when actually built, not stubbed out in advance.

| Package | Responsibility |
|---|---|
| `vfx` | Particles and other visual effects |
| `world` | World and biome modification |
| `entity` | Entity registration and behavior helpers (registers into `content` via `ContentTypeRegistry`) |
| `ui` | Screens, widgets, HUD |
| `render` | Rendering, shaders/post-processing, camera |
| `animation` | Animation systems |
| `audio` | Sound and music |
| `narrative` | Timelines tying dialogues/cutscenes/quests together — `dialogue`, `cinematic`, and `quest` were each built as their own top-level package instead (same reasoning as `dialogue`'s own note on this), so what's left here is only cross-system sequencing, not any one of them individually |
| `scripting` | Scripting layer tying the above together |

Each future package is expected to follow the same shape `content` established: a small number of
annotations/entry points for the mod author, Forge-idiomatic wiring hidden inside, and — where it
introduces new registrable content — a one-line registration into `ContentTypeRegistry` rather than
a parallel discovery mechanism.
