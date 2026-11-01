package dev.jordy.jordylab.shared.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AiProperties.class, LangfuseProperties.class})
class AiConfiguration {
}
