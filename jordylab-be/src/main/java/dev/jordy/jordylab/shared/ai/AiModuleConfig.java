package dev.jordy.jordylab.shared.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "jordylab.ai")
record AiModuleConfig(
        int healthCheckTtlSeconds,
        int healthCheckTimeoutSeconds,
        Map<String, ModuleProvider> modules
) {
    record ModuleProvider(String provider, String model) {
    }

    public ModuleProvider getModuleConfig(String moduleName) {
        return modules.get(moduleName);
    }
}
