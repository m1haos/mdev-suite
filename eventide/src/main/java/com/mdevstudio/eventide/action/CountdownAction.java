package com.mdevstudio.eventide.action;

import java.time.Duration;

import com.mdevstudio.core.scenario.ActionFactory;
import com.mdevstudio.core.scenario.ScenarioAction;
import com.mdevstudio.core.util.Durations;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.running.ActiveEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

final class CountdownAction implements ActionFactory {

    static final String SLOT = "countdown";

    private final EventidePlugin plugin;

    CountdownAction(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ScenarioAction create(ConfigurationSection settings) {
        Duration duration = Durations.parse(settings.getString("duration", "5m"));
        String title = settings.getString("title", "<event> <gray>— <white><time>");
        // The old flat keys still work so event files from 0.1 keep loading.
        BarStyle style = settings.isConfigurationSection("bar")
                ? BarStyle.parse(settings, "yellow", 0)
                : new BarStyle(EventActions.enumValue(BossBar.Color.class, settings.getString("color", "yellow")),
                        EventActions.enumValue(BossBar.Overlay.class, settings.getString("overlay", "progress")), 0);

        return EventActions.forEvent(event -> {
            if (!plugin.settings().modules().countdownBar()) {
                return;
            }
            BossBar bar = style.create(render(title, event, duration), 1f);
            event.bars().show(SLOT, bar, style.radius());
            Countdown countdown = new Countdown(event, bar, title, duration);
            countdown.task = event.every(20, countdown);
        });
    }

    private Component render(String template, ActiveEvent event, Duration left) {
        return plugin.text().parse(template,
                TagResolver.resolver(event.placeholders(), Placeholder.unparsed("time", Durations.clock(left))));
    }

    private final class Countdown implements Runnable {

        private final ActiveEvent event;
        private final BossBar bar;
        private final String title;
        private final long totalSeconds;
        private long elapsed;
        private BukkitTask task;

        private Countdown(ActiveEvent event, BossBar bar, String title, Duration duration) {
            this.event = event;
            this.bar = bar;
            this.title = title;
            this.totalSeconds = Math.max(1, duration.toSeconds());
        }

        @Override
        public void run() {
            long left = totalSeconds - ++elapsed;
            if (left <= 0 || !event.bars().shows(SLOT, bar)) {
                event.bars().hide(SLOT, bar);
                task.cancel();
                return;
            }
            bar.progress(left / (float) totalSeconds);
            bar.name(render(title, event, Duration.ofSeconds(left)));
        }
    }
}
