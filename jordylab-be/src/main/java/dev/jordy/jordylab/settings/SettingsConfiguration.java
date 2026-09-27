package dev.jordy.jordylab.settings;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SettingsProperties.class)
class SettingsConfiguration {
}
