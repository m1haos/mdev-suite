package com.mdevstudio.eventide.running;

/** Something an action started that has to be cleaned up when the event ends. */
@FunctionalInterface
public interface EventPart {

    void stop();
}
