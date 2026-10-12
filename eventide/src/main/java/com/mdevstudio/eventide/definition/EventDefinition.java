package com.mdevstudio.eventide.definition;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mdevstudio.core.reward.Reward;
import com.mdevstudio.core.scenario.Scenario;

public record EventDefinition(
        String id,
        String displayName,
        List<LocalTime> schedule,
        int minOnline,
        LocationRule location,
        RegionRule region,
        Optional<StructureSpec> structure,
        String lootTable,
        Map<Integer, List<Reward>> placeRewards,
        List<Reward> participationRewards,
        String finishEffect,
        Set<String> finishOn,
        Scenario scenario) {
}
