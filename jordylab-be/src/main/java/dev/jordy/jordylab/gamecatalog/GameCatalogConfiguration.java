package dev.jordy.jordylab.gamecatalog;

import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({GameCatalogProperties.class, AutoFillProperties.class, LibBotProperties.class})
class GameCatalogConfiguration {

    @Bean
    Ticker conversationTicker() {
        return Ticker.systemTicker();
    }
}
