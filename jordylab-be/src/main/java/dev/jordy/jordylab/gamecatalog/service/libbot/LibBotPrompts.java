package dev.jordy.jordylab.gamecatalog.service.libbot;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * LibBot's two system prompts, loaded from {@code prompts/gamecatalog/} (AI rule: prompts are resources, not Java
 * strings). Placeholders use {@code <name>} so the JSON-ish text in the prompts needs no escaping.
 */
@Component
public class LibBotPrompts {

    @Value("classpath:prompts/gamecatalog/libbot-interpret.st")
    Resource interpretResource;

    @Value("classpath:prompts/gamecatalog/libbot-answer.st")
    Resource answerResource;

    private String interpretTemplate;
    private String answerTemplate;

    @PostConstruct
    void init() throws IOException {
        this.interpretTemplate = interpretResource.getContentAsString(StandardCharsets.UTF_8);
        this.answerTemplate = answerResource.getContentAsString(StandardCharsets.UTF_8);
    }

    public String interpret(String platforms, String places, String attached) {
        return render(interpretTemplate, Map.of("platforms", platforms, "places", places, "attached", attached));
    }

    public String answer(String language, String applied, int maxRecommendations, String rows) {
        return render(answerTemplate, Map.of("language", language, "applied", applied,
                "maxRecommendations", String.valueOf(maxRecommendations), "rows", rows));
    }

    private String render(String template, Map<String, Object> variables) {
        return PromptTemplate.builder()
                .template(template)
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .variables(variables)
                .build()
                .render();
    }
}
