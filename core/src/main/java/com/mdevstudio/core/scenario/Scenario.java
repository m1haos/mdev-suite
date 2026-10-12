package com.mdevstudio.core.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mdevstudio.core.util.Durations;
import com.mdevstudio.core.util.Sections;
import org.bukkit.configuration.ConfigurationSection;

/**
 * An ordered list of stages. A stage starts {@code after} the previous one started, or, with
 * {@code wait-for}, that long after an action sent the named signal:
 * <pre>
 * stages:
 *   - actions:
 *       - type: broadcast
 *         message: "..."
 *   - after: 5m
 *     actions: [...]
 *   - wait-for: boss-killed
 *     timeout: 15m        # required; the scenario ends when nobody sends the signal in time
 *     on-timeout: finish  # or run: start the stage anyway
 *     actions: [...]
 * </pre>
 */
public record Scenario(List<Stage> stages) {

    public record Stage(long delayTicks, String waitFor, long timeoutTicks, boolean runOnTimeout,
            List<ScenarioAction> actions) {
    }

    public static Scenario parse(List<Map<?, ?>> rawStages, ActionRegistry actions) {
        List<Stage> stages = new ArrayList<>();
        for (int i = 0; i < rawStages.size(); i++) {
            ConfigurationSection stage = Sections.fromMap(rawStages.get(i));
            try {
                stages.add(parseStage(stage, actions));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("stage " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return new Scenario(List.copyOf(stages));
    }

    private static Stage parseStage(ConfigurationSection stage, ActionRegistry actions) {
        String waitFor = stage.getString("wait-for");
        if (waitFor != null && !stage.contains("timeout")) {
            throw new IllegalArgumentException("wait-for needs a timeout, e.g. 'timeout: 15m', "
                    + "otherwise a signal that never comes keeps the event running forever");
        }
        String onTimeout = stage.getString("on-timeout", "finish").toLowerCase(Locale.ROOT);
        if (!onTimeout.equals("finish") && !onTimeout.equals("run")) {
            throw new IllegalArgumentException("on-timeout must be finish or run, not '" + onTimeout + "'");
        }
        List<ScenarioAction> stageActions = new ArrayList<>();
        for (Map<?, ?> rawAction : stage.getMapList("actions")) {
            stageActions.add(actions.create(Sections.fromMap(rawAction)));
        }
        return new Stage(
                ticks(stage, "after"),
                waitFor == null || waitFor.isBlank() ? null : waitFor.trim().toLowerCase(Locale.ROOT),
                ticks(stage, "timeout"),
                onTimeout.equals("run"),
                List.copyOf(stageActions));
    }

    private static long ticks(ConfigurationSection stage, String key) {
        return stage.contains(key) ? Durations.toTicks(Durations.parse(stage.getString(key))) : 0;
    }

    public ScenarioRun start(ScenarioContext context, Runnable onComplete) {
        return new ScenarioRun(this, context, onComplete);
    }
}
