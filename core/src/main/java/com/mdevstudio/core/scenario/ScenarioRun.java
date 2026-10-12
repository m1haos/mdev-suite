package com.mdevstudio.core.scenario;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.scheduler.BukkitTask;

public final class ScenarioRun {

    private final Scenario scenario;
    private final ScenarioContext context;
    private final Runnable onComplete;
    // Signals can arrive before the stage that waits for them, e.g. waves cleared during a pause.
    // They are counted, so three quick wave-cleared signals still let three stages through.
    private final Map<String, Integer> received = new HashMap<>();
    private BukkitTask pending;
    private Scenario.Stage waiting;
    private int nextStage;
    private boolean cancelled;

    ScenarioRun(Scenario scenario, ScenarioContext context, Runnable onComplete) {
        this.scenario = scenario;
        this.context = context;
        this.onComplete = onComplete;
        scheduleNext();
    }

    public void cancel() {
        cancelled = true;
        if (pending != null) {
            pending.cancel();
        }
    }

    public int currentStage() {
        return nextStage;
    }

    /** Lets a stage with {@code wait-for: <name>} start. Each signal is used by one stage. */
    public void signal(String name) {
        if (cancelled) {
            return;
        }
        String signal = name.toLowerCase(Locale.ROOT);
        if (waiting != null && signal.equals(waiting.waitFor())) {
            Scenario.Stage stage = waiting;
            waiting = null;
            if (pending != null) {
                pending.cancel();
            }
            runLater(stage);
        } else {
            received.merge(signal, 1, Integer::sum);
        }
    }

    private void scheduleNext() {
        if (cancelled) {
            return;
        }
        if (nextStage >= scenario.stages().size()) {
            onComplete.run();
            return;
        }
        Scenario.Stage stage = scenario.stages().get(nextStage);
        if (stage.waitFor() == null || consume(stage.waitFor())) {
            runLater(stage);
            return;
        }
        waiting = stage;
        pending = null;
        if (stage.timeoutTicks() <= 0) {
            return;
        }
        pending = schedule(() -> {
            waiting = null;
            if (stage.runOnTimeout()) {
                runLater(stage);
            } else {
                cancelled = true;
                onComplete.run();
            }
        }, stage.timeoutTicks());
    }

    private boolean consume(String signal) {
        Integer count = received.get(signal);
        if (count == null) {
            return false;
        }
        if (count == 1) {
            received.remove(signal);
        } else {
            received.put(signal, count - 1);
        }
        return true;
    }

    private void runLater(Scenario.Stage stage) {
        pending = schedule(() -> {
            nextStage++;
            for (ScenarioAction action : stage.actions()) {
                if (cancelled) {
                    return;
                }
                try {
                    action.run(context);
                } catch (RuntimeException e) {
                    // One broken action must not strand the rest of the scenario half-finished.
                    context.plugin().getSLF4JLogger().error("Scenario action failed in stage {}", nextStage, e);
                }
            }
            scheduleNext();
        }, stage.delayTicks());
    }

    private BukkitTask schedule(Runnable task, long delay) {
        return context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), task, Math.max(0, delay));
    }
}
