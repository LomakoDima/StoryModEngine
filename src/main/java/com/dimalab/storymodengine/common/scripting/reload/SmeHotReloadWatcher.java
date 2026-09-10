package com.dimalab.storymodengine.common.scripting.reload;

import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Watches every {@code data/<ns>/storymodengine/stories/} directory this mod's own dev build knows
 * about and triggers a full {@code MinecraftServer#reloadResources} — the exact call {@code /reload}
 * itself makes — the moment a {@code .sme} file changes on disk, no command needed. Reuses the
 * existing reload pipeline entirely: {@link SmeReloadListener} runs exactly as it does for a manual
 * {@code /reload}, so this file adds no second reload mechanism, only the trigger.
 *
 * <p><b>Dev environment only</b> ({@code FMLEnvironment.production == false}) — a shipped, packaged
 * mod never spins up a filesystem watcher a player didn't ask for; this is purely a development-loop
 * convenience, the same spirit as this project's own live-testing workflow.
 *
 * <p>Watches two roots when both exist: {@code src/main/resources/...} (what you actually edit) and
 * {@code build/resources/main/...} (what the running game's {@code ResourceManager} actually reads,
 * via the mod's own classpath entry). A change under {@code src} is copied to the matching path under
 * {@code build} before the reload fires, so this works correctly whether or not Gradle's own {@code
 * processResources} has re-synced the file yet — no dependency on an IDE's "build on save" setting.
 *
 * <p>Registered via the same {@code AtomicBoolean REGISTERED} + {@code
 * MinecraftForge.EVENT_BUS.register(Class)} idiom {@code concurrent.integration.AsyncLifecycle}
 * already uses — not {@code @Mod.EventBusSubscriber}, since engine code isn't tied to one modid.
 */
public final class SmeHotReloadWatcher {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final long DEBOUNCE_MILLIS = 300;

    private static volatile WatchService watchService;
    private static volatile Thread watcherThread;
    private static volatile boolean running;
    private static volatile MinecraftServer currentServer;

    private SmeHotReloadWatcher() {
    }

    public static void registerOnce() {
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.register(SmeHotReloadWatcher.class);
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        currentServer = event.getServer();
        start();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        stop();
        currentServer = null;
    }

    private static void start() {
        WatchRoots roots = resolveWatchRoots();
        List<Path> storyDirs = new ArrayList<>();
        storyDirs.addAll(findStoryDirs(roots.buildResourcesMain()));
        storyDirs.addAll(findStoryDirs(roots.srcResourcesMain()));
        if (storyDirs.isEmpty()) {
            EngineLog.channel("SME").warn("Hot reload watcher: no 'storymodengine/stories' directory found under src or build resources — not starting");
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            Map<WatchKey, Path> keyToDir = new HashMap<>();
            for (Path dir : storyDirs) {
                keyToDir.put(dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE), dir);
            }
            running = true;
            watcherThread = new Thread(() -> watchLoop(keyToDir, roots), "SME-HotReload-Watcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
            EngineLog.channel("SME").info("Hot reload watcher started — {} .sme file(s) will auto-reload on save: {}",
                    storyDirs.size(), storyDirs);
        } catch (IOException e) {
            EngineLog.channel("SME").warn("Hot reload watcher failed to start: {}", e.toString());
        }
    }

    private static void stop() {
        running = false;
        Thread thread = watcherThread;
        if (thread != null) {
            thread.interrupt();
        }
        WatchService service = watchService;
        if (service != null) {
            try {
                service.close();
            } catch (IOException ignored) {
                // Best-effort — the daemon thread will exit on its own ClosedWatchServiceException.
            }
        }
        watcherThread = null;
        watchService = null;
    }

    private static void watchLoop(Map<WatchKey, Path> keyToDir, WatchRoots roots) {
        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            }
            if (key == null) {
                continue;
            }

            Path dir = keyToDir.get(key);
            boolean smeChanged = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == OVERFLOW) {
                    continue;
                }
                Path changedName = (Path) event.context();
                if (!changedName.toString().endsWith(".sme")) {
                    continue;
                }
                smeChanged = true;
                mirrorSourceToBuildIfNeeded(roots, dir, changedName);
            }
            boolean stillValid = key.reset();
            if (!stillValid) {
                keyToDir.remove(key);
            }

            if (smeChanged) {
                debounceRemainingEvents();
                triggerReload();
            }
        }
    }

    /** Drains any further watch events for ~{@link #DEBOUNCE_MILLIS} so one editor save (often several rapid filesystem events) triggers exactly one reload. */
    private static void debounceRemainingEvents() {
        try {
            Thread.sleep(DEBOUNCE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        WatchKey extra;
        while ((extra = watchService.poll()) != null) {
            extra.pollEvents();
            extra.reset();
        }
    }

    private static void mirrorSourceToBuildIfNeeded(WatchRoots roots, Path changedDir, Path changedName) {
        if (roots.srcResourcesMain() == null || !changedDir.startsWith(roots.srcResourcesMain())) {
            return; // the change was already under build/resources/main — nothing to mirror
        }
        if (roots.buildResourcesMain() == null) {
            return;
        }
        try {
            Path relative = roots.srcResourcesMain().relativize(changedDir.resolve(changedName));
            Path target = roots.buildResourcesMain().resolve(relative);
            Path source = changedDir.resolve(changedName);
            if (Files.exists(source)) {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else if (Files.exists(target)) {
                Files.delete(target);
            }
        } catch (IOException e) {
            EngineLog.channel("SME").warn("Hot reload watcher: failed to mirror {} to build/resources/main: {}", changedName, e.toString());
        }
    }

    private static void triggerReload() {
        MinecraftServer server = currentServer;
        if (server == null) {
            return;
        }
        server.execute(() -> {
            EngineLog.channel("SME").info("Hot reload: .sme change detected, reloading resources...");
            server.reloadResources(server.getPackRepository().getSelectedIds());
        });
    }

    private static List<Path> findStoryDirs(Path resourcesMain) {
        List<Path> found = new ArrayList<>();
        if (resourcesMain == null) {
            return found;
        }
        Path dataDir = resourcesMain.resolve("data");
        if (!Files.isDirectory(dataDir)) {
            return found;
        }
        try (var namespaces = Files.list(dataDir)) {
            for (Path namespace : (Iterable<Path>) namespaces::iterator) {
                Path stories = namespace.resolve("storymodengine").resolve("stories");
                if (Files.isDirectory(stories)) {
                    found.add(stories);
                }
            }
        } catch (IOException e) {
            EngineLog.channel("SME").warn("Hot reload watcher: failed to scan {}: {}", dataDir, e.toString());
        }
        return found;
    }

    private static WatchRoots resolveWatchRoots() {
        Path buildResourcesMain = null;
        Path srcResourcesMain = null;
        try {
            Path codeSource = Paths.get(SmeHotReloadWatcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // Dev environment layout: .../build/classes/java/main/... — walk up looking for a
            // directory literally named "build", rather than assuming a fixed nesting depth (which
            // varies across Gradle/ForgeGradle versions and run tasks and previously crashed with an
            // NPE when the actual code source turned out shallower than assumed).
            Path buildDir = findAncestorNamed(codeSource, "build");
            if (buildDir != null) {
                buildResourcesMain = buildDir.resolve("resources").resolve("main");
                Path projectRoot = buildDir.getParent();
                if (projectRoot != null) {
                    Path candidate = projectRoot.resolve("src").resolve("main").resolve("resources");
                    if (Files.isDirectory(candidate)) {
                        srcResourcesMain = candidate;
                    }
                }
            }
        } catch (URISyntaxException e) {
            EngineLog.channel("SME").warn("Hot reload watcher: could not resolve dev resource roots: {}", e.toString());
        }
        return new WatchRoots(buildResourcesMain, srcResourcesMain);
    }

    /** Walks {@code start}'s ancestors (starting at itself) looking for one whose file name is exactly {@code name} — {@code null} if none is found before reaching the filesystem root. */
    private static Path findAncestorNamed(Path start, String name) {
        for (Path current = start; current != null; current = current.getParent()) {
            Path fileName = current.getFileName();
            if (fileName != null && fileName.toString().equals(name)) {
                return current;
            }
        }
        return null;
    }

    private record WatchRoots(Path buildResourcesMain, Path srcResourcesMain) {
    }
}
