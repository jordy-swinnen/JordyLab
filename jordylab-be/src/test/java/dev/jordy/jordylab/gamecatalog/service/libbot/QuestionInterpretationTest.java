package dev.jordy.jordylab.gamecatalog.service.libbot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class QuestionInterpretationTest {

    private final BeanOutputConverter<QuestionInterpretation> converter = new BeanOutputConverter<>(
            QuestionInterpretation.class);

    @Test
    void acceptsTheLanguageValuesTheGeneratedSchemaAdvertises() {
        QuestionInterpretation interpretation = converter.convert("""
                {"intent":"LIBRARY_QUERY","language":"EN","standaloneQuestion":"Which games support local multiplayer?"}
                """);

        assertSoftly(softly -> {
            softly.assertThat(interpretation.intent()).isEqualTo(QuestionInterpretation.Intent.LIBRARY_QUERY);
            softly.assertThat(interpretation.language()).isEqualTo(Language.EN);
        });
    }

    @Test
    void acceptsTheLowercaseLanguageCodesTheRecordedGoldensUse() {
        QuestionInterpretation interpretation = converter.convert("""
                {"intent":"LIBRARY_QUERY","language":"nl","standaloneQuestion":"Welke games hebben lokale multiplayer?"}
                """);

        assertThat(interpretation.language()).isEqualTo(Language.NL);
    }

    @Test
    void anUnrecognisedLanguageFoldsIntoOther() {
        QuestionInterpretation interpretation = converter.convert("""
                {"intent":"OUT_OF_SCOPE","language":"fr","standaloneQuestion":"Quel temps fait-il?","reply":"Je ne peux répondre qu'aux questions sur ta ludothèque."}
                """);

        assertThat(interpretation.language()).isEqualTo(Language.OTHER);
    }
}
