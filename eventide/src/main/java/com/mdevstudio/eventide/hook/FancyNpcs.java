package com.mdevstudio.eventide.hook;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Player NPCs of FancyNpcs. Called through reflection: FancyNpcs is built for Java 25, and a compile
 * dependency on it would stop this jar from running on the Java 21 servers it supports.
 */
public final class FancyNpcs {

    public static final String PLUGIN = "FancyNpcs";
    private static final String API = "de.oliver.fancynpcs.api.";
    // FancyNpcs wants an owner for every NPC; event NPCs belong to nobody.
    private static final UUID NO_CREATOR = new UUID(0, 0);

    private FancyNpcs() {
    }

    /**
     * Shows a player NPC that FancyNpcs never writes to its own file, so a crash leaves nothing behind.
     * Clicks are handed to {@code onClick} on the main thread. Returns the action that removes the NPC.
     */
    public static Runnable spawn(Plugin owner, Location at, String displayName, String skin, NamedTextColor glow,
            Consumer<Player> onClick) {
        try {
            ClassLoader loader = owner.getServer().getPluginManager().getPlugin(PLUGIN).getClass().getClassLoader();
            Class<?> pluginType = Class.forName(API + "FancyNpcsPlugin", true, loader);
            Class<?> dataType = Class.forName(API + "NpcData", true, loader);
            Class<?> npcType = Class.forName(API + "Npc", true, loader);
            Class<?> managerType = Class.forName(API + "NpcManager", true, loader);

            Object data = dataType.getConstructor(String.class, UUID.class, Location.class)
                    .newInstance("eventide-" + UUID.randomUUID(), NO_CREATOR, at);
            dataType.getMethod("setDisplayName", String.class).invoke(data, displayName);
            dataType.getMethod("setTurnToPlayer", boolean.class).invoke(data, true);
            Consumer<Player> click = player -> owner.getServer().getScheduler().runTask(owner,
                    () -> onClick.accept(player));
            dataType.getMethod("setOnClick", Consumer.class).invoke(data, click);
            if (skin != null && !skin.isBlank()) {
                dataType.getMethod("setSkin", String.class).invoke(data, skin);
            }
            if (glow != null) {
                dataType.getMethod("setGlowing", boolean.class).invoke(data, true);
                dataType.getMethod("setGlowingColor", NamedTextColor.class).invoke(data, glow);
            }

            Object fancy = pluginType.getMethod("get").invoke(null);
            @SuppressWarnings("unchecked")
            Function<Object, Object> adapter = (Function<Object, Object>) pluginType.getMethod("getNpcAdapter")
                    .invoke(fancy);
            Object npc = adapter.apply(data);
            Object manager = pluginType.getMethod("getNpcManager").invoke(fancy);
            npcType.getMethod("setSaveToFile", boolean.class).invoke(npc, false);
            managerType.getMethod("registerNpc", npcType).invoke(manager, npc);
            npcType.getMethod("create").invoke(npc);
            npcType.getMethod("spawnForAll").invoke(npc);
            return () -> {
                try {
                    npcType.getMethod("removeForAll").invoke(npc);
                    managerType.getMethod("removeNpc", npcType).invoke(manager, npc);
                } catch (ReflectiveOperationException e) {
                    owner.getSLF4JLogger().error("Could not remove a FancyNpcs captain", unwrap(e));
                }
            };
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FancyNpcs refused the captain NPC: " + unwrap(e), unwrap(e));
        }
    }

    private static Throwable unwrap(ReflectiveOperationException e) {
        return e instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : e;
    }
}
