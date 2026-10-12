package com.mdevstudio.core.scenario;

@FunctionalInterface
public interface ScenarioAction {

    void run(ScenarioContext context);
}
