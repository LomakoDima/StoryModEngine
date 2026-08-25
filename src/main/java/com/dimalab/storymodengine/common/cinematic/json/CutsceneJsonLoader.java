package com.dimalab.storymodengine.common.cinematic.json;

import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import com.dimalab.storymodengine.common.cinematic.Shot;
import com.dimalab.storymodengine.api.cinematic.SkipPolicy;
import com.dimalab.storymodengine.common.cinematic.binding.LookAt;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.common.cinematic.state.Subtitle;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.Map;

import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.pos;
import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.rot;

/**
 * Loads {@code CutsceneDefinition}s from {@code data/<namespace>/storymodengine/cutscenes/*.json}
 * — the same {@code SimpleJsonResourceReloadListener} mechanism every vanilla data-driven system
 * (loot tables, recipes) already uses, registered via {@code AddReloadListenerEvent} in {@link
 * CinematicJsonBootstrap}. Every JSON file is parsed straight into the *same* {@code
 * CutsceneDefinition.Builder} a Java-authored cutscene already goes through — one validation path,
 * not a parallel one — then registered into {@code CutsceneRegistry} exactly like {@code
 * @AutoCutscene} does; from the rest of the system's point of view there is no difference between a
 * code-defined and a JSON-defined cutscene.
 *
 * <p><b>Not representable in JSON, by design</b>: {@code EventTrack} (arbitrary {@code
 * Supplier<Event>}) and {@code ActionTrack} (arbitrary {@code Consumer<ServerPlayer>>}) — both are
 * genuinely Java code, not data, and a JSON cutscene simply omits them. Everything else (shots,
 * actors, subtitles, sounds, fades, triggers, markers, skip policy) is plain data and fully
 * supported.
 *
 * <p>A malformed file is logged and skipped — one bad cutscene never prevents every other file (or
 * the reload itself) from loading, the same fail-soft discipline {@code CinematicManager#tick()}
 * already applies to a throwing cue.
 */
public final class CutsceneJsonLoader extends SimpleJsonResourceReloadListener {

    public CutsceneJsonLoader() {
        super(new Gson(), "storymodengine/cutscenes");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        int loaded = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                CutsceneDefinition definition = parse(id, entry.getValue().getAsJsonObject());
                CutsceneRegistry.register(definition);
                loaded++;
            } catch (Exception e) {
                EngineLog.channel("Cinematic").error("Failed to parse cutscene '" + id + "' — skipping", e);
            }
        }
        EngineLog.channel("Cinematic").info("[JSON] Loaded {} cutscene(s) from data packs", loaded);
    }

    /**
     * {@code o.has(key)} alone is true even for an explicit JSON {@code null} (a natural way to
     * write "no value" by hand), which {@code GsonHelper}'s getters then reject with a confusing
     * {@code JsonSyntaxException} rather than treating it the same as the key being absent — this
     * treats the two identically everywhere an optional field is checked below.
     */
    private static boolean hasNonNull(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull();
    }

    /**
     * The parsing entry point — {@code public} (not just used internally by {@link #apply}) so
     * {@code cinematic.example.CutscenePlayJsonCommand} can parse a raw file straight off disk
     * (e.g. one written by {@code cinematic.client.CameraRecorderCommands}) without needing it to
     * sit inside a real data pack and go through a full {@code /reload} first — same parser, same
     * validation, just a different source for the {@code JsonObject}.
     */
    public static CutsceneDefinition parse(ResourceLocation id, JsonObject root) {
        CutsceneDefinition.Builder builder = CutsceneDefinition.define(id)
                .duration(GsonHelper.getAsInt(root, "duration"));

        if (hasNonNull(root, "skipPolicy")) {
            builder.skipPolicy(SkipPolicy.valueOf(GsonHelper.getAsString(root, "skipPolicy")));
        }
        if (hasNonNull(root, "defaultSkipMarker")) {
            builder.defaultSkipMarker(GsonHelper.getAsString(root, "defaultSkipMarker"));
        }

        for (JsonElement e : GsonHelper.getAsJsonArray(root, "shots", new JsonArray())) {
            parseShot(builder, e.getAsJsonObject());
        }
        for (JsonElement e : GsonHelper.getAsJsonArray(root, "actors", new JsonArray())) {
            parseActor(builder, e.getAsJsonObject());
        }
        for (JsonElement e : GsonHelper.getAsJsonArray(root, "subtitles", new JsonArray())) {
            parseSubtitle(builder, e.getAsJsonObject());
        }
        for (JsonElement e : GsonHelper.getAsJsonArray(root, "sounds", new JsonArray())) {
            parseSound(builder, e.getAsJsonObject());
        }
        for (JsonElement e : GsonHelper.getAsJsonArray(root, "fades", new JsonArray())) {
            parseFade(builder, e.getAsJsonObject());
        }
        for (JsonElement e : GsonHelper.getAsJsonArray(root, "triggers", new JsonArray())) {
            JsonObject o = e.getAsJsonObject();
            builder.trigger(GsonHelper.getAsInt(o, "tick"), new ResourceLocation(GsonHelper.getAsString(o, "id")));
        }
        for (JsonElement e : GsonHelper.getAsJsonArray(root, "markers", new JsonArray())) {
            JsonObject o = e.getAsJsonObject();
            builder.marker(GsonHelper.getAsString(o, "name"), GsonHelper.getAsInt(o, "tick"));
        }

        return builder.build();
    }

    private static void parseShot(CutsceneDefinition.Builder builder, JsonObject shot) {
        int start = GsonHelper.getAsInt(shot, "start");
        int duration = GsonHelper.getAsInt(shot, "duration");
        Shot.Transition transition = hasNonNull(shot, "transition")
                ? Shot.Transition.valueOf(GsonHelper.getAsString(shot, "transition")) : Shot.Transition.CUT;
        int transitionTicks = GsonHelper.getAsInt(shot, "transitionTicks", 0);

        builder.shot(start, duration, transition, transitionTicks, camera -> {
            for (JsonElement e : GsonHelper.getAsJsonArray(shot, "position", new JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                camera.position(GsonHelper.getAsInt(o, "tick"),
                        pos(GsonHelper.getAsFloat(o, "x"), GsonHelper.getAsFloat(o, "y"), GsonHelper.getAsFloat(o, "z")));
            }
            for (JsonElement e : GsonHelper.getAsJsonArray(shot, "rotation", new JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                camera.rotation(GsonHelper.getAsInt(o, "tick"),
                        rot(GsonHelper.getAsFloat(o, "yaw"), GsonHelper.getAsFloat(o, "pitch")));
            }
            for (JsonElement e : GsonHelper.getAsJsonArray(shot, "fov", new JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                camera.fov(GsonHelper.getAsInt(o, "tick"), GsonHelper.getAsFloat(o, "value"));
            }
            for (JsonElement e : GsonHelper.getAsJsonArray(shot, "roll", new JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                camera.roll(GsonHelper.getAsInt(o, "tick"), GsonHelper.getAsFloat(o, "value"));
            }
            if (hasNonNull(shot, "lookAt")) {
                camera.lookAt(parseLookAt(shot.getAsJsonObject("lookAt")));
            }
        });
    }

    private static LookAt parseLookAt(JsonObject o) {
        String type = GsonHelper.getAsString(o, "type");
        return switch (type) {
            case "entity" -> LookAt.entity(GsonHelper.getAsString(o, "binding"));
            case "position" -> LookAt.position(new Vector3f(
                    GsonHelper.getAsFloat(o, "x"), GsonHelper.getAsFloat(o, "y"), GsonHelper.getAsFloat(o, "z")));
            default -> throw new IllegalArgumentException("Unknown lookAt type '" + type + "' — expected 'entity' or 'position'");
        };
    }

    private static void parseActor(CutsceneDefinition.Builder builder, JsonObject actor) {
        String binding = GsonHelper.getAsString(actor, "binding");
        builder.actor(binding, track -> {
            if (GsonHelper.getAsBoolean(actor, "authoritative", false)) {
                track.authoritative();
            }
            for (JsonElement e : GsonHelper.getAsJsonArray(actor, "position", new JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                track.position(GsonHelper.getAsInt(o, "tick"),
                        pos(GsonHelper.getAsFloat(o, "x"), GsonHelper.getAsFloat(o, "y"), GsonHelper.getAsFloat(o, "z")));
            }
            for (JsonElement e : GsonHelper.getAsJsonArray(actor, "rotation", new JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                track.rotation(GsonHelper.getAsInt(o, "tick"),
                        rot(GsonHelper.getAsFloat(o, "yaw"), GsonHelper.getAsFloat(o, "pitch")));
            }
            for (JsonElement e : GsonHelper.getAsJsonArray(actor, "visible", new JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                track.visible(GsonHelper.getAsInt(o, "tick"), GsonHelper.getAsBoolean(o, "value"));
            }
        });
    }

    private static void parseSubtitle(CutsceneDefinition.Builder builder, JsonObject o) {
        String text = GsonHelper.getAsString(o, "text");
        int start = GsonHelper.getAsInt(o, "start");
        int duration = GsonHelper.getAsInt(o, "duration");
        String speaker = hasNonNull(o, "speaker") ? GsonHelper.getAsString(o, "speaker") : null;
        int fadeIn = GsonHelper.getAsInt(o, "fadeIn", 0);
        int fadeOut = GsonHelper.getAsInt(o, "fadeOut", 0);
        int color = hasNonNull(o, "color") ? parseColor(GsonHelper.getAsString(o, "color")) : 0xFFFFFF;
        Subtitle.Position position = hasNonNull(o, "position")
                ? Subtitle.Position.valueOf(GsonHelper.getAsString(o, "position")) : Subtitle.Position.BOTTOM;
        builder.subtitle(text, start, duration, speaker, fadeIn, fadeOut, color, position);
    }

    private static void parseSound(CutsceneDefinition.Builder builder, JsonObject o) {
        int tick = GsonHelper.getAsInt(o, "tick");
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(GsonHelper.getAsString(o, "sound")));
        if (sound == null) {
            throw new IllegalArgumentException("Unknown sound id '" + GsonHelper.getAsString(o, "sound") + "'");
        }
        float volume = GsonHelper.getAsFloat(o, "volume", 1f);
        float pitch = GsonHelper.getAsFloat(o, "pitch", 1f);
        if (hasNonNull(o, "position")) {
            JsonObject p = o.getAsJsonObject("position");
            builder.sound(tick, sound, volume, pitch,
                    new Vector3f(GsonHelper.getAsFloat(p, "x"), GsonHelper.getAsFloat(p, "y"), GsonHelper.getAsFloat(p, "z")));
        } else {
            builder.sound(tick, sound, volume, pitch);
        }
    }

    private static void parseFade(CutsceneDefinition.Builder builder, JsonObject o) {
        int start = GsonHelper.getAsInt(o, "start");
        int duration = GsonHelper.getAsInt(o, "duration");
        int color = hasNonNull(o, "color") ? parseColor(GsonHelper.getAsString(o, "color")) : 0x000000;
        float from = GsonHelper.getAsFloat(o, "from");
        float to = GsonHelper.getAsFloat(o, "to");
        builder.fade(start, duration, color, from, to);
    }

    private static int parseColor(String hex) {
        String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
        return Integer.parseInt(cleaned, 16);
    }
}
