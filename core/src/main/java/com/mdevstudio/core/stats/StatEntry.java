package com.mdevstudio.core.stats;

import java.util.UUID;

/** One line of a leaderboard. {@code name} is the last known name, or "?" for players never seen. */
public record StatEntry(UUID id, String name, long value) {
}
