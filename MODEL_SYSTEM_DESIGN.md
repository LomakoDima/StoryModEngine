# StoryModEngine — `model`: native glTF 2.0 / GLB import

Written before any code existed, based on reading the real `content`/`resource`/`math`/`concurrent`/
`logging`/`entity` APIs (not assumed). Matches this project's standing convention: an architecture
idea can be studied from another engine, but the implementation is written from the glTF 2.0
specification, from scratch, in this project's own idioms.

**Revised after a detailed architecture study of HollowEngine's own glTF implementation** (Kotlin,
CC BY-NC-SA — read for its approach and format-handling order only; nothing was copied, and its
license makes copying both wrong and, for this project's own licensing freedom, actively harmful).
That study found five things the first version here got structurally wrong, all now fixed and each
covered by a self-test:

| First version | Corrected |
|---|---|
| Picked the first node owning a mesh, dropped the rest of the graph | Whole node hierarchy imported, with parent links and per-node TRS |
| Node `matrix` ignored with a warning | Decomposed to TRS via JOML |
| No coordinate-system correction | `ModelSpace` synthetic root, with exporter detection (glTF is +Z-forward, Blockbench is not) |
| Animation stored as absolute transforms | Stored as deltas from bind pose — which is what makes blending possible at all |
| Linear keyframe scan, per-vertex allocation, leaked GPU textures on reload | Binary search, reused scratch buffers, textures released on registry reset |

## Why this exists, and why it's a new top-level package

Nothing in the engine can load an artist-authored 3D asset today — `entity`'s only content
(`ClassicEntity`/`ClassicModel`) is a Blockbench model hand-translated into vanilla
`ModelPart`/`CubeListBuilder` calls, which only expresses rigid parent-child cube hierarchies, never
per-vertex skin weights or arbitrary keyframe tracks. `model` is the missing loader: parse a real
`.gltf`/`.glb` file into a usable in-engine representation, skin it, animate it, render it — nothing
about story/flow/dialogue/quest content, so it doesn't belong under any existing subsystem.

Placement: a new top-level package, `com.dimalab.storymodengine.model`, split across `api`/`client`/
`common` exactly like every other subsystem (`cinematic`, `quest`, `dialogue`, `voxel`, ...) — not
nested under `resource`, whose current job (`AssetGenerator`: `ContentDescriptor` → generated
blockstate/model JSON for `@AutoContent`) is a different concern from importing an externally
authored asset. `model` also isn't shoehorned into the still-unbuilt `render`/`animation` slots from
ARCHITECTURE.md's "Planned package layout" table — it only needs the narrow slice of each that glTF
playback requires, not a general rendering or animation framework; those remain open for whatever
eventually needs the rest.

## Internal representation — a node graph, not a flat mesh list

Everything outside `common.model.gltf` never imports a glTF-shaped type — but the *shape* of the
representation follows glTF's own, because that shape is the model's actual structure rather than a
serialization detail:

```
ModelDefinition
├── List<ModelNode> roots            -- the hierarchy; parents always resolved before children
│   └── ModelNode                     -- index, name, children, parent,
│       │                                 bind TRS (translation/rotation/scale), mesh?, skin?
│       ├── Mesh?                      -- List<Primitive> (one Primitive = one material = one draw call)
│       │   └── Primitive               -- VertexData + MaterialData + int[] indices
│       │       ├── VertexData           -- positions/normals/uv/joints/weights, struct-of-arrays
│       │       └── MaterialData          -- raw base color image bytes + base color factor (RGBA)
│       └── Skin?                       -- int[] jointNodeIndices + Matrix4f[] inverseBindMatrices
└── List<AnimationClip>              -- name + Map<nodeIndex, AnimationData> + duration
    └── AnimationData                 -- up to three Tracks (translation / rotation / scale)
        └── Track<T>                   -- sorted times[] + values[], binary-searched
```

**Why the hierarchy is kept.** glTF's node graph carries three things nothing else does: where each
mesh sits, what every bone's rest pose is, and the parent chain skinning walks. Flattening it — as
the first version did, picking the single first node that owned a mesh — silently collapses every
multi-part model to the origin and makes skinning of anything but a one-node rig impossible. There is
no cheaper representation that stays correct.

**Definition vs. runtime.** `ModelNode` is shared and immutable: bind pose only, one instance per
loaded file. `client.model.RuntimeNode` mirrors it per rendered entity and owns the mutable current
pose plus a cached `globalMatrix`. Two entities showing one model pose independently — the same "one
definition, many runs" split `flow.Flow`/`FlowManager` already use. Posing a frame is always: reset
every node to bind, let each animation layer compose onto it, then one top-down pass computing
`global = parent.global × local`. Resetting first is what lets a bone that stopped being animated
fall back to rest instead of freezing at its last value.

This is deliberately *not* generalized further than glTF needs today (no attempt to also fit a future
FBX/Bedrock importer) — see Non-goals. The boundary exists so the renderer and the animation player
never have to know the data came from a `.glb`.

## Model space — why a synthetic root node

glTF's own convention puts a model's front at **+Z**; every vanilla Minecraft model faces **−Z**. A
model exported from Blender/Maya/3ds Max therefore renders backwards with no correction — and
Blockbench, which authors in Minecraft's orientation already, must *not* be corrected. `ModelSpace`
tells them apart by reading `asset.generator` (Blockbench stamps its name there), rather than making
the mod author configure it or guessing from geometry.

The correction is applied as a **synthetic root node** (`index = -1`) wrapping the real roots, never
baked into vertex data. Three consequences worth stating: the imported geometry stays exactly as
authored; skinning still works against the unmodified bind pose; and the correction reaches skinned
meshes for free, because the joints are themselves children of that root and so their global matrices
already include it.

## Animation — stored as deltas from the bind pose

A glTF rotation track says *"at time t this bone's local rotation **is** Q"*. What gets stored here is
*"at t this bone is rotated by `bindRotation⁻¹ · Q` away from its rest pose"* — and correspondingly
`value − bindTranslation` and `value / bindScale`. The absolute form is trivially recoverable
(`bind ⊕ delta`), so nothing is lost, but the delta form is what makes animation *composable*:

- two clips cross-fade by interpolating their deltas and applying the result once;
- a clip applies at partial weight by scaling its delta toward identity;
- an additive layer — a recoil, a lean, a breathing bob — stacks on top of whatever pose is already
  there, because a delta means something independently of what it's applied to.

With absolute values none of that works: applying a clip can only overwrite, so a second clip always
wins outright and blending is structurally impossible. This is the single most consequential change
from the first version, and `AnimationPose` (sample → mix → apply, with `OVERRIDE`/`ADDITIVE` modes
and an optional per-layer bone mask) is the consuming side of it.

## Physical presence — collision, and where its limits come from

A model that can be walked through isn't an object, it's a decal. Two representations exist, because
Minecraft itself offers exactly two and they are not equally capable:

| | Entity (`ModelEntity`) | Block (`ModelBlock`) |
|---|---|---|
| Collision | **One upright box** — `EntityDimensions` is a single width × height and there is no multi-box or mesh option for entities in 1.20.1 | **Full `VoxelShape`** — many boxes, following the mesh silhouette |
| Moves | yes | no |
| Derived from | `ModelBounds` (bind-pose extent) | `ModelVoxelizer` (rasterized mesh) |
| Command | `/storymodengine prop spawn <name>` | `/storymodengine prop place <name>` |

The single-box entity limit is vanilla's, not this engine's — stated here rather than left for
someone to discover as a bug report. Shaped collision on something that moves would mean moving
blocks or a custom physics pass, both well outside this package.

**Hitboxes come from the bind pose, never the current animated pose.** A hitbox that resized as a
character walked would read as broken collision rather than as fidelity, and it would also make
collision depend on client-side animation timing, which the server has no reason to trust.

### Rotation — where drawn and solid have to agree

Both representations turn, and both had a rotation bug that only became visible with `F3+B`:

- **Entity.** An entity's box is always world-axis-aligned; Minecraft has no rotated entity hitbox
  and nothing here changes that. What it can do is *size* the box to contain the turned model: a
  footprint of `x × z` rotated by θ spans `x·|cos θ| + z·|sin θ|` one way and `x·|sin θ| + z·|cos θ|`
  the other. Previously the box stayed sized for the unrotated model while the mesh was drawn turned,
  so its corners hung outside their own hitbox. A square footprint at 45° now correctly grows by √2.
- **Block.** The collision shape rotates through `voxel.ShapeRotation` (clockwise quarter-steps from
  `NORTH`), so the render must turn **−90° per step**. It was using `Direction.toYRot()`, which
  disagrees for *every* direction — 180° for `NORTH` where the shape doesn't turn at all, +270° for
  `EAST` where the shape turns −90°. The block was drawn facing one way and collided with facing
  another. `physics.ModelRotation` now owns that single conversion, is used by the block, its
  renderer and the self-test alike, and is deliberately free of any Minecraft `Block` reference so
  the test can run without bootstrapping registries.

### Voxelization

`ModelVoxelizer` rasterizes triangles into a grid (16³ by default — Minecraft's own model
resolution), flood-fills the outside so enclosed volume becomes solid rather than a shell a player
could stand inside, then greedily merges cells into as few boxes as possible. That last step isn't
cosmetic: a solid 16³ shape is 4096 cells, and handing `Shapes.or` 4096 boxes makes every collision
query against the block crawl; merging brings a filled cube down to ~16. Cell membership uses an
exact triangle-box separating-axis test — the cheap alternatives either over-fill (a sloped roof
collides as a solid wedge) or under-fill (a thin wall you can walk through).

The result is an ordinary `voxel.ShapeDefinition`, so it inherits that package's union, caching and
`rotated(facing)` support instead of growing a second shape system.

### Why the server can read a model at all

Collision only counts if the server agrees to it — the server is what accepts or rejects a player's
movement. But models live under `assets/`, which a server's `ResourceManager` never exposes. So
`ModelPhysics` resolves a model in two steps: the client's `ModelRegistry` first, then a direct read
from the mod jar's **classpath**, which works on both sides because the jar is installed on both.
`GltfBufferResolver` falls back to the same classpath for sibling `.bin`/texture files when handed a
null `ResourceManager`. The server parses geometry for itself and simply fails to resolve textures,
which costs nothing since it never draws. (HollowEngine solves the same problem the same way, with
its own `ModelResourceIO` and a `ModelSide.SERVER` that skips materials.)

Geometry still never crosses the network. Both `ModelEntity` and `ModelBlockEntity` sync only the
model's **name** — the same "send the id, not the content" principle `DialogueStepPacket` follows.

## `.smemeta` — what a model says about itself

A model's rig, and its forward axis when the exporter can't be trusted, live in a **sidecar file next
to the asset**: `player.glb` → `player.glb.smemeta`. JSON, because every other data file this engine
loads is JSON through Gson; adding a TOML parser for one file would buy nothing.

```json
{
  "facing": "auto",
  "rig": [
    { "name": "root", "anchor": "body" },
    { "name": "head", "parent": "root", "anchor": "golova",
      "members": ["golova", "Helmet", "leftBrow", "rightBrow"] }
  ]
}
```

**Why not in Java.** The rig used to be a Java class naming one specific asset's parts (`golova`,
`Helmet`, `leftHand_layer`) *inside engine code* — so a second model with different part names meant
editing and recompiling the engine. A description that belongs to an asset belongs with the asset.
What stays in Java is only the **vocabulary** (`HumanoidBones`: `head`, `body`, `leftArm`, …), so
`HumanoidPoser` can animate any model whose sidecar uses those names.

`facing` overrides the exporter sniff (`auto` | `gltf` | `minecraft`). The sniff is a heuristic over a
free-text `asset.generator` field; an unknown exporter, or one whose string a converter rewrote, can
be told the answer instead of guessed at.

A model with no sidecar loads exactly as before, and a **malformed** sidecar costs the model its rig,
not its geometry — the same "log and keep going per resource" rule `DialogueJsonLoader` follows.

Both load paths — the client reload and the classpath read the server uses for collision — go through
`ModelLoading`, so a model is rigged identically on both sides, at load. Rigging at load rather than
behind a render-time cache is what lets everything downstream stop knowing rigs exist.

## Rigging a flat model — `model.rig`

Real-world Blockbench exports are usually **not** what the glTF spec's animation machinery assumes:
the shipped player model is 46 separate mesh parts with **no hierarchy, no skin, and zero animation
clips**. Body parts do carry sensible pivots in their own node origins (the head part sits at the
neck, an arm at its shoulder), but parts that must move *together* don't know about each other, and
accessories — armour, face details — are exported at the world origin with geometry baked in absolute
coordinates. Rotating "the head" leaves the helmet, brows and eyes behind.

`ModelRig.apply(definition, bones)` fixes this by **rebuilding the model as a real hierarchy**: one
group node per `RigBone`, placed at that joint's pivot, with every member reparented underneath and
its translation made relative. The output is an ordinary `ModelDefinition` whose tree composes like a
natively-rigged model's, so `RuntimeNode`'s hierarchy pass, the renderer and bone lookup all work
unchanged — nothing downstream knows a rig was involved. Parts stay rigid (this is not skinning),
which is exactly right for blocky part-based models and costs none of the per-vertex work.

Two details worth stating:

- **Pivots come from geometry, not constants.** A bone names an *anchor* node whose own origin is the
  joint, so the same rig description survives the model being re-exported at a different offset or
  scale.
- **Duplicate node names are handled rather than tolerated.** The shipped model has two nodes named
  `leftHand_layer`, one of which actually belongs to the right arm (an export slip). A member name
  claims the next not-yet-claimed node with that name, in bone declaration order, so both arms keep
  their overlay; the ambiguity is logged so it can be fixed at the source.

`HumanoidPoser` then supplies walk, idle sway, head look and an attack swing procedurally, because a
model with no clips would otherwise stand frozen. Clip-driven animation still works and composes with
it: `ModelInstance.poseWith` runs reset → animation layers → procedural → matrix pass, so a head that
tracks the player can sit on top of a walk cycle rather than being overwritten by it.

## What already exists — reused, not reinvented

Verified directly against the real project files before designing around them:

| Already provided by | What it covers | Used instead of reinventing |
|---|---|---|
| `api.math.interp.Interpolators.VECTOR3F`/`QUATERNION_SLERP` | Correct per-type blending, quaternion slerp never averages components | Every animation channel — translation/scale interpolate via `VECTOR3F`, rotation via `QUATERNION_SLERP` |
| `api.math.transform.Transform` (T/R/S, `.compose(child)`, `.inverse()`) | Parent→child local-to-world composition | Walking the joint hierarchy each frame: a joint's world transform is its parent's world transform composed with its own local (bind or animated) transform — no hand-rolled matrix chain |
| `api.math.interp.Interpolators.step()` | Discrete "hold start until t=1" blending | glTF `STEP` sampler interpolation |
| `com.google.gson.Gson` (already a transitive Minecraft/Forge dependency, already used by `DialogueJsonLoader`/`QuestJsonLoader`) | JSON parsing | Deserializing the glTF JSON chunk/`.gltf` file into a plain Gson-mapped schema (`GltfDocument`) — no hand-rolled JSON tokenizer |
| `com.mojang.blaze3d.platform.NativeImage.read(InputStream)` | PNG/JPEG decoding, exactly the vanilla resource-pack texture path | Every texture source (external file, `data:` URI, GLB-embedded bufferView image) decodes through this — no custom image codec |
| `net.minecraft.server.packs.resources.SimplePreparableReloadListener` | The non-JSON reload-listener base class, already precedented by `scripting.reload.SmeReloadListener` for `.sme` files | `ModelReloadListener` — `.gltf`/`.glb`/`.bin` are not JSON either |
| `common.concurrent.Async.io()` / `AsyncTask<T>` | Off-main-thread work with one door back via `Async.main(...)` | **Not** the reload path (see Lifecycle below — a real crash, not a hypothetical, ruled this out there); used instead by the on-demand `/storymodengine model load` command, the one place in this subsystem where a server (and so `Async`'s pools) is guaranteed already running |
| `common.logging.EngineLog.channel(name)` | Structured, filterable logging | `EngineLog.channel("Model")` — every parse/texture/reload failure, matching `Dialogue`/`Quest`/`Async`/`Trigger`/`SME`'s own channel convention |
| `org.joml.Matrix4f`/`Vector3f`/`Quaternionf` (already the engine's vector/rotation types throughout `math`) | Inverse bind matrices, vertex transformation | Used directly; no new vector library |

## Render path: CPU skinning, not vanilla `ModelPart`

Confirmed by reading `ClassicModel`/`ClassicRenderer`: vanilla's `ModelPart`/`CubeListBuilder`/
`LayerDefinition` only express a rigid parent-child cube hierarchy — there is no per-vertex joint
index/weight concept anywhere in that API, so it structurally cannot render a skinned glTF mesh.
Two real options exist for skinning in Forge 1.20.1:

1. **GPU skinning** (upload joint matrices as a uniform/SSBO, skin in a vertex shader) — the
   technically "correct" long-term answer, but Forge 1.20.1's rendering pipeline has no first-class
   custom-shader hook for entity rendering without either a full `ShaderInstance`/`CoreShaders`
   rewrite of the render call or a Sodium/Iris-style pipeline this engine doesn't depend on. Real,
   but large, risk-heavy, and not something to gate a first working version on.
2. **CPU skinning** — walk the joint hierarchy once per rendered frame per model instance
   (`Transform.compose` down the parent chain, exactly as `math`'s own doc for `Transform.compose`
   describes: *"a limb attached to a rig"*), producing one world-space matrix per joint; transform
   each vertex by its up-to-4 joint weights on the CPU, then feed the already-skinned positions/
   normals straight into an ordinary `VertexConsumer`. This is exactly what vanilla does for
   `ModelPart` transforms already (just per-cube instead of per-vertex-with-weights), so it fits the
   existing rendering pipeline with zero new GL state, no custom shader, and no Forge version-specific
   shader risk.

**Decision: CPU skinning for v1.** It is the only path that needs nothing beyond `PoseStack`/
`VertexConsumer`/`MultiBufferSource`/`RenderType` — all already vanilla-idiomatic, all already used
elsewhere in this engine's client code. GPU skinning is called out explicitly as a future stretch
goal (a second `ModelRenderer` implementation behind the same interface, mirroring how `concurrent`
already keeps `ExecutorFactory` swappable for a future virtual-thread implementation) — not attempted
here, since it would require a shader pipeline this codebase has never touched and isn't needed to
prove the import pipeline itself works. Cost is the standard, accepted tradeoff for CPU skinning: work
scales with vertex count per rendered frame per visible instance; acceptable for the entity-scale
models this loader targets, not for a scene full of high-poly skinned meshes.

## Texture loading

`NativeImage.read(InputStream)` for every source glTF images can point at, resolved uniformly by
`GltfBufferResolver` regardless of which container flavor supplies the bytes:

- External file (`.gltf` referencing `image.png` by relative path, next to the model in the resource
  pack) — read via `ResourceManager`.
- `data:` URI (base64-embedded, common in single-file `.gltf` exports) — `java.util.Base64` decode,
  stdlib, no new dependency.
- GLB-embedded image (`bufferView`-referenced, common in `.glb`) — sliced straight out of the binary
  BIN chunk already loaded for geometry.

The resulting `NativeImage` is decoded during import (safe off-thread — see Lifecycle, decoding pixels
touches no GL state) and held on `MaterialData`, unmodified, until something actually needs to render
that material. `client.model.ModelTextureLoader.textureFor(material)` is what uploads it — lazily, the
first time any `ModelInstance` using that material is actually drawn, caching the resulting
`DynamicTexture` under a synthetic `ResourceLocation` (`storymodengine:model/dynamic_<n>`, a global
counter) so the same material is never uploaded twice. This upload only ever happens from `ModelRenderer
.render`, which is already running on the render thread by construction (nothing calls it from
anywhere else) — no `Async.main()` hop is needed to reach the render thread for it, unlike the
genuinely off-thread decode step during import. `ModelRenderer` then builds `RenderType
.entityCutoutNoCull(...)` against the resolved texture, exactly the way any vanilla entity texture is
referenced. An untextured material (or one whose image failed to decode) resolves to a shared 1×1 white
fallback texture instead of skipping the primitive — its `baseColorFactor` still tints it correctly via
the vertex color, so a missing/broken texture degrades to a flat-colored mesh rather than an invisible
one.

## Package layout

```
com.dimalab.storymodengine
├── api/model/
│   ├── ModelFormatException.java        -- unchecked, carries the failing resource id + a human message
│   └── LayerBlendMode.java              -- OVERRIDE / ADDITIVE, the animation-layer vocabulary
├── common/model/                         -- no client-only type anywhere in here (see MaterialData)
│   ├── ModelDefinition.java             -- roots + animations, flattened node list/index built once
│   ├── ModelNode.java                    -- hierarchy node: bind TRS, children, parent, mesh?, skin?
│   ├── Skin.java                          -- jointNodeIndices + inverseBindMatrices
│   ├── Mesh.java / Primitive.java / VertexData.java / MaterialData.java
│   ├── AnimationClip.java / AnimationData.java
│   ├── track/Track.java                  -- sorted times + binary search + local-t
│   │       Vec3Track.java / QuatTrack.java  -- LINEAR/STEP, delegating to api.math.interp.Interpolators
│   ├── ModelSpace.java                   -- synthetic correction root + exporter detection
│   ├── GeometryUtils.java                 -- flat normals when a file omits NORMAL (spec's own fallback)
│   ├── ModelRegistry.java                 -- id -> ModelDefinition; reset() fires a release callback
│   ├── reload/ModelReloadListener.java    -- SimplePreparableReloadListener<Map<ResourceLocation,byte[]>>
│   └── gltf/
│       ├── GltfDocument.java + nested schema classes -- raw Gson-mapped glTF JSON, incl. asset.generator
│       ├── GlbContainer.java             -- GLB header/JSON-chunk/BIN-chunk split; flavor sniffed by magic
│       ├── GltfBufferResolver.java        -- external file / data URI / GLB BIN chunk, one API
│       ├── GltfAccessorReader.java         -- accessor+bufferView -> float[]/int[]/Matrix4f[],
│       │                                       component-type and normalization aware
│       ├── GltfMeshImporter.java          -- primitives -> Mesh/Primitive/VertexData, mode checked
│       ├── GltfMaterialImporter.java       -- materials -> MaterialData (raw image bytes, no decode)
│       ├── GltfAnimationImporter.java       -- animations -> AnimationClip, converted to bind-pose deltas
│       └── GltfModelParser.java              -- orchestrator: container -> materials -> skins -> node
│                                                tree -> animations -> ModelSpace -> ModelDefinition
└── client/model/
    ├── RuntimeNode.java                  -- per-instance pose + globalMatrix, allocation-free update
    ├── ModelInstance.java                 -- definition + runtime tree + animator; pose() per frame
    ├── ModelAnimator.java / AnimationLayer.java  -- the layer stack (clip, time, weight, mode, bone mask)
    ├── AnimationPose.java / BonePose.java  -- sample -> mix -> apply(OVERRIDE|ADDITIVE)
    ├── CpuSkinner.java                      -- joint matrices + linear blend skinning, reused buffers
    ├── ModelRenderer.java                    -- walks the runtime tree -> VertexConsumer
    ├── ModelTextureLoader.java                -- decode + upload, cached, released on reload
    ├── ModelReloadBootstrap.java               -- Dist.CLIENT; registers the listener + the release hook
    └── debug/
        ├── ModelCommand.java                  -- /storymodengine model list|load|test|selftest — a
        │                                          RegisterClientCommandsEvent command (like
        │                                          math.example.MathUiCommand), not a server command:
        │                                          model loading/rendering is entirely client-side
        ├── ModelSelfTest.java                  -- the pure-JVM checks `selftest` runs
        └── ModelDebugRenderStage.java           -- RenderLevelStageEvent hook drawing the `/model test` anchor
```

~30 files — comparable in scale to `voxel`(9)/`raycast`(7) plus a runtime and renderer, well under
`cinematic`(66)/`quest`(43).

**Why `ModelReloadBootstrap` sits under `client` while the listener it registers stays in `common`**:
the listener only reads bytes and builds format-independent data, with nothing client-only in it; the
bootstrap additionally wires `ModelTextureLoader`'s release hook, which does touch client-only types.
Splitting them keeps `common.model` free of `@OnlyIn` reasoning entirely rather than arguing about
lazy class-loading in a comment — which is also why `MaterialData` holds *raw encoded image bytes*
instead of a decoded `NativeImage`.

## Lifecycle

**Verified against the real Forge source before writing anything here, and it overturned the first
assumption**: `AddReloadListenerEvent` — the event every existing loader (`DialogueJsonLoader`/
`QuestJsonLoader`/`CutsceneJsonLoader`/`SmeReloadListener`) hooks — is documented on the class itself
as *"for server-side resources"*, tied to `ReloadableServerResources`; it fires on both dedicated and
integrated servers, over `data/<ns>/...` content, and never touches the client's own resource-pack
reload at all. That's the right event for dialogue/quest/story text, which must exist identically on
both sides — it is the *wrong* one for a `.gltf`/`.glb` model, which is client-only rendering content
the same way `assets/storymodengine/textures/entity/classic.png` is. The actual hook, confirmed by
reading `net.minecraftforge.client.event.RegisterClientReloadListenersEvent`'s source: fired once
during `Minecraft`'s own construction, on the mod event bus, client-logical-side only, via
`event.registerReloadListener(...)` — a new event this engine hasn't needed before (the same category
of "first of its kind" as `dialogue`'s own `RegisterKeyMappingsEvent`). Models therefore live under `assets/<ns>/storymodengine/models/*.gltf`/`*.glb` (a resource pack path,
like any vanilla model/texture), not `data/`. Registration follows the exact same self-registering
shape `DialogueJsonBootstrap`/`SmeReloadBootstrap` already use — a small `@Mod.EventBusSubscriber`
class needs no explicit call from `EngineBootstrap` at all, since Forge's own annotation scan
discovers and registers it automatically at mod-construction time: `ModelReloadBootstrap`, annotated
`@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value =
Dist.CLIENT)`, calls `event.registerReloadListener(new ModelReloadListener())` on
`RegisterClientReloadListenersEvent`. No `EngineBootstrap` edit needed — this is the one subsystem so
far with zero dependency on anything else the engine initializes.

`ModelReloadListener.prepare(...)` lists every resource under `models/` (any namespace) ending in
`.gltf`/`.glb` and reads each one synchronously — not through `Async.io()`. **Corrected after a real
crash, not a design preference**: the first version fanned reads out through `Async.io()`, and it
brought down the client's very first launch with `AsyncShutdownException: Rejected — engine
concurrency is not accepting new work`. Root cause, confirmed against `concurrent.executor
.AsyncExecutors`'s own doc: `Async`'s thread pools aren't created until `ServerStartingEvent` fires —
`isAccepting()` is `false` before that — and the client's *own* initial resource-pack reload runs
during game boot, before any world/server has started, so `Async` correctly rejected the work every
single time, not just occasionally. This also explains why neither `DialogueJsonLoader` nor
`scripting.reload.SmeReloadListener` route their own reads through `Async` — `prepare()` already runs
on vanilla's own background executor (`PreparableReloadListener`'s own contract), which is the actual
"don't block the main thread" guarantee needed here; reading synchronously inside it is exactly what
those two established loaders already do. `apply(...)` runs `GltfModelParser` per file (also
synchronous, also already off the main thread — `apply` runs on the game thread, but only once
`prepare`'s future has resolved) and populates `ModelRegistry`; a single malformed model is caught,
logged via `EngineLog.channel("Model").error(...)`, and skipped — one bad file never aborts the whole
reload, matching `DialogueJsonLoader`'s established per-resource try/catch loop.

`Async` is still used in this subsystem — just not here. `client.model.debug.ModelCommand`'s `load`
subcommand (force-parsing one named file on demand, outside a full `/reload`) routes its read+parse
through `Async.io()...thenApply(...)...thenMain(...)`, because that command can only ever run while a
world is already open, well after `ServerStartingEvent` — `Async`'s pools are guaranteed to exist by
then, and a command handler genuinely does run synchronously on the render thread otherwise, so
offloading a potentially slow parse there is both safe and worth doing. Same primitive, two different
call sites, opposite conclusions — the deciding factor is always "has `ServerStartingEvent` fired yet
on this code path," not a blanket rule either way.

## Command surface

Mirrors `raycast test|debug|selftest` and `async selftest|stresstest`:

- `/storymodengine model list` — every currently loaded model id, mesh/skin/animation counts.
- `/storymodengine model load <name>` — force a single model through the parser immediately (outside
  a full `/reload`), reports geometry/skin/animation counts or the parse diagnostic on failure — the
  fast iteration loop for someone authoring a `.glb`. `<name>` is a short bare word (e.g. `test`), not
  a full resource path — resolved against `assets/storymodengine/storymodengine/models/<name>.gltf`,
  falling back to `.glb`, the same `StringArgumentType.word()` + "assume the `storymodengine`
  namespace" convention `trigger.example.TriggerDemoCommand`/`dialogue.example.DialogueDemoCommand`
  already use. **Corrected after first use**: the first version took a `ResourceLocationArgument`,
  which defaults an unqualified name to the `minecraft` namespace and treats it as a literal resource
  path — `/storymodengine model load test` failed with "Could not read minecraft:test" for exactly
  that reason, caught from real `runClient` log output, not a hypothetical.
- `/storymodengine model test <name> [animationName]` — spawns a client-side debug render anchor at the
  invoking player's position/looking direction, drawing the model (optionally looping the named
  animation, or bind pose if omitted) via `ModelDebugRenderStage`. Pure visual smoke test — no entity
  is spawned, nothing persists past logout, matching `raycast`'s debug-particle pattern of leaving no
  world state behind.
- `/storymodengine prop spawn|place|info|clear <name>` — the **physical** side, and a *server*
  command (the asset commands above are client-side): an entity only exists if the server spawns it,
  and collision is only real if the server agrees to it. `spawn` creates a solid `ModelEntity`,
  `place` sets down a `ModelBlock` with shaped collision, `info` reports the derived hitbox and box
  count without creating anything, and `clear` removes props within 32 blocks. Kept under a separate
  `prop` literal rather than extending `model` so the client and server command dispatchers never
  have to disambiguate the same path.
- `/storymodengine model selftest` — this project's established in-game JUnit substitute (same
  reasoning as `QuestCompilerTestCommand`/`RaycastSelfTestCommand`): hand-built minimal glTF JSON
  (a single triangle) parses correctly; a synthetic GLB byte buffer (header + JSON chunk + BIN chunk)
  round-trips; every accessor component type/normalization decodes to the expected values; a 2-joint
  synthetic skin's inverse bind matrices and vertex weights skin to the expected world position at a
  known pose; a 2-keyframe animation channel interpolates to the expected value at `t=0.5` via
  `Interpolators.VECTOR3F`/`QUATERNION_SLERP`.

## Explicit non-goals (restated from the request, plus what fell out of it)

FBX, Bedrock `geo.json`, Blockbench `.bbmodel`, Wavefront OBJ — not touched. Full PBR (metallic/
roughness/normal/occlusion/emissive maps) — only `baseColorTexture` + `baseColorFactor` are read;
every other glTF material field is ignored. `KHR_*` extensions — ignored entirely (a model using them
still imports, just without whatever the extension would have added). Morph targets, sparse
accessors, multiple UV sets, vertex colors (`COLOR_0`), cameras, lights, and glTF's own scene/node
transform graph beyond what skinning joints need — not read; a mod author's use case here is "one
skinned/animated mesh as an entity model," not general DCC-file interchange. `CUBICSPLINE` sampler
interpolation — detected and logged once per clip via `EngineLog`, falls back to `LINEAR` rather than
silently producing wrong tangent-driven motion or crashing. More than 4 joint influences per vertex
(`JOINTS_1`/`WEIGHTS_1` and beyond) — not read; `JOINTS_0`/`WEIGHTS_0` only. Network sync of geometry
or animation state — never; a model loads identically on every client from the resource pack, same as
any vanilla model. GPU/compute skinning — deferred stretch goal, not attempted (see Render path
above). No in-game format editor/viewer beyond the plain debug-render smoke test in `model test`.

## Verification

1. `./gradlew compileJava --rerun-tasks` after each implementation stage, matching this project's
   established per-stage compile discipline. **Status: passing.**
2. `/storymodengine model selftest` — twenty pure-JVM checks, run against the real compiled classes
   (not just "it compiles"). **Status: 20/20 passing.** Each targets an invariant that would otherwise
   fail silently:

   | Check | What would break without it |
   |---|---|
   | GLB container round-trip | header/chunk parsing |
   | Minimal glTF triangle parse | accessor decoding, identity indices, generated flat normals |
   | Node hierarchy preserved | parent links, and a child's composed global position `(1,2,0)` |
   | Node matrix decomposition | a `matrix`-form node silently rendering at the origin |
   | Blockbench facing detection | every non-Blockbench model facing backwards |
   | Animation stored as deltas | a keyframe equal to the bind pose must yield an *identity* delta |
   | Pose blending at weight 0.5 | override must land exactly halfway, not snap |
   | Additive layering | two additive layers must sum, not overwrite each other |
   | Skin + animation math | joint composition × inverse bind at a known pose |
   | Triangles as degenerate quads | vanilla entity render types are `Mode.QUADS`; emitting 3 vertices per triangle makes the buffer stitch faces across triangle boundaries |
   | Model bounds from bind pose | hitbox size, and the entity-dimensions collapse to width × height |
   | Voxelized cube fills the block | the rasterizer actually marking cells at all |
   | Greedy merge keeps box count sane | 4096 boxes instead of ~16 would make every collision query crawl |
   | Hollow mesh fills solid inside | without the flood fill a player can clip inside a closed model |
   | Hitbox grows for a rotated model | a square footprint at 45° must widen by √2 or the mesh hangs outside its own hitbox |
   | Block render yaw matches shape rotation | drawn orientation vs. collision orientation silently disagreeing |
   | Rig reparents flat nodes onto a joint | an accessory whose origin sits away from the joint must still swing with it |
   | Sidecar metadata drives the rig | the rig must come from the asset, and a broken sidecar must not cost the model its geometry |
   | Sparse accessor overrides applied | reading only the base array is silently wrong geometry, with no error |
   | Triangles emitted as triangles | padding to quads would submit a third more vertices than the mesh has |
   | Texture ids stable across loads | ids derived from an upload counter change every reload |

3. Still outstanding, and only the user can run it: a real skinned/animated `.gltf`/`.glb` dropped
   into `assets/storymodengine/storymodengine/models/`, loaded with `/storymodengine model load
   <name>` and viewed with `/storymodengine model test <name> [animation]` in a live `runClient` —
   confirming geometry, texture, orientation, and the skin actually deforming across frames. No
   amount of self-testing substitutes for looking at it.
