package com.mdevstudio.eventide.action;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.mdevstudio.core.item.ItemSpec;
import com.mdevstudio.core.text.Text;
import com.mdevstudio.eventide.running.ActiveEvent;
import com.mdevstudio.eventide.running.MobGlow;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;

/**
 * A mob as written in an event file: type, name, stats, equipment and glow. Zero or missing stats keep
 * the vanilla value. Used by guards, waves and the boss.
 */
record MobSpec(
        EntityType type,
        String name,
        double health,
        double damage,
        double speed,
        double scale,
        Map<EquipmentSlot, ItemSpec> equipment,
        NamedTextColor glow) {

    // Event mobs spawn in the open and must find players who are still some way off.
    private static final double MIN_FOLLOW_RANGE = 32;
    private static final Map<String, EquipmentSlot> SLOTS = Map.of(
            "main-hand", EquipmentSlot.HAND,
            "off-hand", EquipmentSlot.OFF_HAND,
            "head", EquipmentSlot.HEAD,
            "chest", EquipmentSlot.CHEST,
            "legs", EquipmentSlot.LEGS,
            "feet", EquipmentSlot.FEET);

    /**
     * Reads name, health, damage, speed, scale, equipment and glow from {@code section} for the given type.
     * {@code defaultGlow} is used when the file does not say; null means no glow.
     */
    static MobSpec parse(ConfigurationSection section, EntityType type, NamedTextColor defaultGlow) {
        Map<EquipmentSlot, ItemSpec> equipment = new EnumMap<>(EquipmentSlot.class);
        ConfigurationSection gear = section.getConfigurationSection("equipment");
        if (gear != null) {
            for (String key : gear.getKeys(false)) {
                EquipmentSlot slot = SLOTS.get(key.toLowerCase(Locale.ROOT));
                if (slot == null) {
                    throw new IllegalArgumentException("equipment: '" + key + "' is not one of " + SLOTS.keySet());
                }
                equipment.put(slot, ItemSpec.parse(gear, key));
            }
        }
        return new MobSpec(
                type,
                section.getString("name"),
                section.getDouble("health", 0),
                section.getDouble("damage", 0),
                section.getDouble("speed", 0),
                section.getDouble("scale", 0),
                Map.copyOf(equipment),
                parseGlow(section, defaultGlow));
    }

    static NamedTextColor parseGlow(ConfigurationSection section, NamedTextColor fallback) {
        if (!section.contains("glow")) {
            return fallback;
        }
        if (section.isBoolean("glow")) {
            return section.getBoolean("glow") ? (fallback == null ? NamedTextColor.WHITE : fallback) : null;
        }
        try {
            return MobGlow.parseColor(section.getString("glow", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("glow: " + e.getMessage(), e);
        }
    }

    static EntityType entityType(String name) {
        String id = name.trim().toLowerCase(Locale.ROOT);
        EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(id));
        if (type == null || !type.isAlive() || type.getEntityClass() == null || type == EntityType.PLAYER) {
            throw new IllegalArgumentException("'" + id + "' is not a mob");
        }
        return type;
    }

    MobSpec withType(EntityType other) {
        return new MobSpec(other, name, health, damage, speed, scale, equipment, glow);
    }

    MobSpec withoutGlow() {
        return new MobSpec(type, name, health, damage, speed, scale, equipment, null);
    }

    LivingEntity spawn(ActiveEvent event, Location at, Text text) {
        return (LivingEntity) at.getWorld().spawnEntity(at, type, CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
            entity.setPersistent(false);
            if (name != null && !name.isBlank()) {
                entity.customName(text.parse(name, event.placeholders()));
                entity.setCustomNameVisible(true);
            }
            if (entity instanceof LivingEntity living) {
                prepare(living, text, event);
            }
            if (glow != null && event.eventide().settings().modules().mobGlow()) {
                MobGlow.apply(entity, glow);
            }
        });
    }

    private void prepare(LivingEntity living, Text text, ActiveEvent event) {
        living.setRemoveWhenFarAway(false);
        setBase(living, Attribute.MAX_HEALTH, health);
        AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
        if (health > 0 && maxHealth != null) {
            // The server caps max health (1024 by default); asking for more would throw here.
            living.setHealth(Math.min(health, maxHealth.getValue()));
        }
        setBase(living, Attribute.ATTACK_DAMAGE, damage);
        setBase(living, Attribute.MOVEMENT_SPEED, speed);
        setBase(living, Attribute.SCALE, scale);
        AttributeInstance follow = living.getAttribute(Attribute.FOLLOW_RANGE);
        if (follow != null && follow.getBaseValue() < MIN_FOLLOW_RANGE) {
            follow.setBaseValue(MIN_FOLLOW_RANGE);
        }

        EntityEquipment gear = living.getEquipment();
        if (gear != null) {
            equipment.forEach((slot, item) -> {
                gear.setItem(slot, item.create(text, event.placeholders()));
                gear.setDropChance(slot, 0);
            });
        }
        // Guards spawn in the open at any time of day; burning ones would be gone before anyone arrives.
        if (living instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        } else if (living instanceof AbstractSkeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
        } else if (living instanceof Phantom phantom) {
            phantom.setShouldBurnInDay(false);
        }
        // A baby zombie is a different, much harder fight than the one written in the file.
        if (living instanceof Ageable ageable) {
            ageable.setAdult();
        }
    }

    private static void setBase(LivingEntity living, Attribute attribute, double value) {
        if (value <= 0) {
            return;
        }
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
