package com.mdevstudio.eventide.action;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

import com.mdevstudio.core.scenario.ActionRegistry;
import com.mdevstudio.core.scenario.ScenarioAction;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.running.ActiveEvent;

/** Registers Eventide's stage actions in the core, next to broadcast, title, effect and the rest. */
public final class EventActions {

    private EventActions() {
    }

    public static void registerAll(ActionRegistry registry, EventidePlugin plugin) {
        registry.register("countdown", new CountdownAction(plugin));
        registry.register("marker", new MarkerAction(plugin));
        registry.register("spawn-loot", new SpawnLootAction(plugin));
        registry.register("spawn-guards", new SpawnGuardsAction(plugin));
        registry.register("airdrop", new AirdropAction(plugin));
        registry.register("capture-zone", new CaptureZoneAction(plugin));
        registry.register("waves", new WavesAction(plugin));
        registry.register("boss", new BossAction(plugin));
    }

    /** Wraps an action that only makes sense inside a running Eventide event. */
    static ScenarioAction forEvent(Consumer<ActiveEvent> body) {
        return context -> {
            if (context instanceof ActiveEvent event && !event.isFinished()) {
                body.accept(event);
            }
        };
    }

    static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + name + "' is not one of "
                    + Arrays.toString(type.getEnumConstants()).toLowerCase(Locale.ROOT));
        }
    }
}
