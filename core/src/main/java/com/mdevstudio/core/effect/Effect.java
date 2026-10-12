package com.mdevstudio.core.effect;

import java.util.List;

/** A named set of elements played together. Its length is the end of the longest element. */
public record Effect(String id, List<EffectElement> elements, int length) {

    public Effect(String id, List<EffectElement> elements) {
        this(id, List.copyOf(elements), elements.stream().mapToInt(element -> element.timing().end()).max().orElse(0));
    }
}
