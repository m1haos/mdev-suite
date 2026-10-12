package com.mdevstudio.core.reward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns config lines like {@code "command: eco give <player> 100"} into rewards.
 * Other plugins add their own types, e.g. {@code register("mdev-key", ...)}.
 */
public final class RewardRegistry {

    private final Map<String, Function<String, Reward>> types = new HashMap<>();

    public void register(String type, Function<String, Reward> parser) {
        types.put(type.toLowerCase(Locale.ROOT), parser);
    }

    public boolean has(String type) {
        return types.containsKey(type.toLowerCase(Locale.ROOT));
    }

    public Reward parse(String line) {
        int separator = line.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("Reward '" + line + "' must look like 'type: value'");
        }
        String type = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        Function<String, Reward> parser = types.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("Unknown reward type '" + type + "'. Known: " + types.keySet());
        }
        return parser.apply(line.substring(separator + 1).trim());
    }

    public List<Reward> parseAll(List<String> lines) {
        List<Reward> rewards = new ArrayList<>(lines.size());
        for (String line : lines) {
            rewards.add(parse(line));
        }
        return rewards;
    }
}
