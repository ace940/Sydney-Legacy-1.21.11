package me.aidan.sydney.debug;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.events.SubscribeEvent;
import me.aidan.sydney.events.impl.TickEvent;
import me.aidan.sydney.modules.Module;
import me.aidan.sydney.utils.IMinecraft;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Development-only harness that toggles every registered module on and off inside a loaded world
 * so that tick and render handlers actually execute.
 *
 * <p>Enabled with {@code -Dsydney.stressTest=<output dir>} and optionally
 * {@code -Dsydney.stressTest.start=<module index>} to resume after a crash. The harness writes
 * {@code current.json} before each module (so a crash can be attributed) and appends one line per
 * module to {@code results.txt}:
 *
 * <pre>index<TAB>module<TAB>OK|EXCEPTION|SKIPPED<TAB>detail</pre>
 *
 * <p>Modules that would end the session (AutoLog and friends) are skipped. Every module's original
 * toggle state is restored when the run finishes, then the client stops itself.
 */
public final class StressHarness implements IMinecraft {
    public static final String PROP_DIR = "sydney.stressTest";
    public static final String PROP_START = "sydney.stressTest.start";
    public static final String PROP_HOLD = "sydney.stressTest.hold";

    /** Modules whose enable path disconnects or logs out mid-run. */
    private static final List<String> SKIP = List.of("AutoLog", "AutoReconnect", "AutoLogin", "SelfBow");

    /** Modules forced off while the run is active: they disconnect, respawn or spam chat. */
    private static final List<String> SESSION_MODULES =
            List.of("AutoLog", "AutoReconnect", "AutoLogin", "AutoRespawn", "Spammer", "KillSay", "Announcer");

    private static StressHarness instance;

    private final Path dir;
    private final Path results;
    private final int holdTicks;
    private final List<Module> modules = new ArrayList<>();
    private final List<Boolean> originalState = new ArrayList<>();

    private int index;
    private int phase; // 0 = waiting for world, 1 = enabling, 2 = holding, 3 = finished
    private int hold;
    private Module current;

    private StressHarness(Path dir, int start, int holdTicks) {
        this.dir = dir;
        this.results = dir.resolve("results.txt");
        this.holdTicks = holdTicks;
        this.index = start;
    }

    public static void init() {
        String dir = System.getProperty(PROP_DIR);
        if (dir == null || dir.isBlank()) return;

        int start = parseInt(System.getProperty(PROP_START), 0);
        int hold = parseInt(System.getProperty(PROP_HOLD), 20);

        try {
            Path path = Paths.get(dir);
            Files.createDirectories(path);
            instance = new StressHarness(path, start, hold);
            instance.modules.addAll(Sydney.MODULE_MANAGER.getModules());
            instance.modules.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            // originalState is deliberately NOT captured here: ConfigManager only applies the
            // user's saved config in onPostInitialize(), after this init hook. Capturing toggle
            // states here records module defaults, and restoreStates() at the end of the run
            // would then overwrite the loaded config with defaults on the next quit-save.
            // The snapshot is taken at the first tick instead (see onTick phase 0).

            Sydney.EVENT_HANDLER.subscribe(instance);
            Sydney.LOGGER.info("[stress] harness armed: dir={} start={} hold={} modules={}",
                    path, start, hold, instance.modules.size());
        } catch (IOException exception) {
            Sydney.LOGGER.error("[stress] could not initialise harness", exception);
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (phase == 3) return;
        if (mc.player == null || mc.world == null) return;

        if (phase == 0) {
            // Snapshot the config-loaded toggle state now that ConfigManager has run; restoring
            // these values afterwards keeps the user's saved config intact.
            if (originalState.isEmpty()) {
                for (Module module : modules) originalState.add(module.isToggled());
            }

            phase = 1;
            // Ticks (and therefore this harness) stop while a singleplayer game is paused, and
            // the window is never focused when the run is driven from a terminal.
            mc.options.pauseOnLostFocus = false;
            silenceSessionModules();
            Sydney.LOGGER.info("[stress] world loaded, starting at module index {}", index);
        }

        // AutoLog (or a config that re-enables it) would disconnect the moment the world loads.
        silenceSessionModules();

        switch (phase) {
            case 1 -> beginModule();
            case 2 -> {
                if (++hold >= holdTicks) finishModule();
            }
            default -> {
            }
        }
    }

    /** Keeps modules that would end or spam the session turned off for the duration of the run. */
    private void silenceSessionModules() {
        for (String name : SESSION_MODULES) {
            for (Module module : modules) {
                if (!module.getName().equalsIgnoreCase(name) || !module.isToggled()) continue;
                try {
                    module.setToggled(false, false);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void beginModule() {
        if (index >= modules.size()) {
            complete();
            return;
        }

        current = modules.get(index);
        hold = 0;

        String name = current.getName();
        String meta = current.getCategory().getName();

        if (SKIP.contains(name)) {
            append(index, name, "SKIPPED", "disconnects the session");
            index++;
            return;
        }

        writeCurrent(index, name, "enabling");

        try {
            current.setToggled(false, false);
            current.setToggled(true, false);
        } catch (Throwable throwable) {
            append(index, name, "EXCEPTION", describe(throwable) + " @enable");
            safeDisable();
            index++;
            return;
        }

        phase = 2;
        Sydney.LOGGER.info("[stress] {}/{} {} [{}] enabled", index + 1, modules.size(), name, meta);
    }

    private void finishModule() {
        String name = current.getName();
        writeCurrent(index, name, "disabling");

        try {
            current.setToggled(false, false);
            append(index, name, "OK", "");
        } catch (Throwable throwable) {
            append(index, name, "EXCEPTION", describe(throwable) + " @disable");
        }

        index++;
        phase = 1;
        current = null;
    }

    private void complete() {
        phase = 3;
        restoreStates();
        append(index, "<end>", "DONE", "modules=" + modules.size());
        Sydney.LOGGER.info("[stress] complete, stopping client");
        mc.scheduleStop();
    }

    private void restoreStates() {
        for (int i = 0; i < modules.size(); i++) {
            try {
                modules.get(i).setToggled(originalState.get(i), false);
            } catch (Throwable ignored) {
            }
        }
    }

    private void safeDisable() {
        try {
            current.setToggled(false, false);
        } catch (Throwable ignored) {
        }
    }

    private void writeCurrent(int i, String name, String phaseName) {
        try {
            Files.writeString(dir.resolve("current.json"),
                    "{\"index\":" + i + ",\"module\":\"" + name + "\",\"phase\":\"" + phaseName + "\"}",
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private void append(int i, String name, String status, String detail) {
        String line = i + "\t" + name + "\t" + status + "\t" + detail.replace('\n', ' ') + System.lineSeparator();
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(results,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            writer.print(line);
        } catch (IOException exception) {
            Sydney.LOGGER.error("[stress] could not append result", exception);
        }
    }

    private static String describe(Throwable throwable) {
        StringBuilder builder = new StringBuilder(throwable.getClass().getSimpleName());
        if (throwable.getMessage() != null) builder.append(": ").append(throwable.getMessage());

        StackTraceElement[] trace = throwable.getStackTrace();
        for (StackTraceElement element : trace) {
            if (element.getClassName().startsWith("me.aidan.sydney")) {
                builder.append(" @ ").append(element.getClassName().substring(element.getClassName().lastIndexOf('.') + 1))
                        .append('.').append(element.getMethodName()).append(':').append(element.getLineNumber());
                break;
            }
        }
        return builder.toString();
    }
}
