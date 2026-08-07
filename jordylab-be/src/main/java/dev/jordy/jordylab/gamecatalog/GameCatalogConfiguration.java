package dev.jordy.jordylab.gamecatalog;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GameCatalogProperties.class)
class GameCatalogConfiguration {
}
