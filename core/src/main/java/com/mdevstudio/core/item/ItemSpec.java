package com.mdevstudio.core.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mdevstudio.core.text.Text;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/**
 * An item described in config. Either the short form {@code "diamond 3"} or a section:
 * <pre>
 * material: nether_star      # or another plugin's item: nexo:id, itemsadder:ns:id, craftengine:ns:id
 * amount: 1
 * name: "&lt;accent&gt;Key"
 * lore: ["line"]
 * model: "mdev:epic_key"
 * glow: true
 * enchantments: {sharpness: 5}
 * </pre>
 */
public record ItemSpec(
        Material material,
        int amount,
        String name,
        List<String> lore,
        NamespacedKey model,
        boolean glow,
        Map<Enchantment, Integer> enchantments,
        String custom) {

    public static ItemSpec parse(String shortForm) {
        String[] parts = shortForm.trim().split("\\s+");
        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        if (CustomItems.isCustom(parts[0])) {
            CustomItems.validate(parts[0]);
            return new ItemSpec(null, amount, null, List.of(), null, false, Map.of(), parts[0]);
        }
        return new ItemSpec(material(parts[0]), amount, null, List.of(), null, false, Map.of(), null);
    }

    public static ItemSpec parse(ConfigurationSection section) {
        String materialName = section.getString("material", "");
        String custom = null;
        Material material = null;
        if (CustomItems.isCustom(materialName)) {
            CustomItems.validate(materialName);
            custom = materialName;
        } else {
            material = material(materialName);
        }
        NamespacedKey model = null;
        String modelKey = section.getString("model");
        if (modelKey != null) {
            model = NamespacedKey.fromString(modelKey.toLowerCase(Locale.ROOT));
            if (model == null) {
                throw new IllegalArgumentException("Invalid model key '" + modelKey + "'");
            }
        }

        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
        if (enchantSection != null) {
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
            for (String id : enchantSection.getKeys(false)) {
                NamespacedKey key = NamespacedKey.fromString(id.toLowerCase(Locale.ROOT));
                Enchantment enchantment = key == null ? null : registry.get(key);
                if (enchantment == null) {
                    throw new IllegalArgumentException("Unknown enchantment '" + id + "'");
                }
                enchantments.put(enchantment, enchantSection.getInt(id, 1));
            }
        }

        return new ItemSpec(
                material,
                Math.max(1, section.getInt("amount", 1)),
                section.getString("name"),
                new ArrayList<>(section.getStringList("lore")),
                model,
                section.getBoolean("glow", false),
                enchantments,
                custom);
    }

    /** Accepts both a string and a section under {@code path}. */
    public static ItemSpec parse(ConfigurationSection parent, String path) {
        if (parent.isConfigurationSection(path)) {
            return parse(parent.getConfigurationSection(path));
        }
        String value = parent.getString(path);
        if (value == null) {
            throw new IllegalArgumentException("Missing item '" + path + "'");
        }
        return parse(value);
    }

    public ItemStack create(Text text, int amount, TagResolver... placeholders) {
        ItemStack item = custom != null
                ? CustomItems.create(custom, amount)
                : ItemStack.of(material, Math.max(1, amount));
        item.editMeta(meta -> {
            if (name != null) {
                meta.displayName(text.parseItemLine(name, placeholders));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(line -> text.parseItemLine(line, placeholders)).toList());
            }
            if (model != null) {
                meta.setItemModel(model);
            }
            enchantments.forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true));
            if (glow && enchantments.isEmpty()) {
                meta.setEnchantmentGlintOverride(true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return item;
    }

    public ItemStack create(Text text, TagResolver... placeholders) {
        return create(text, amount, placeholders);
    }

    private static Material material(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Unknown item material '" + name + "'");
        }
        return material;
    }
}
