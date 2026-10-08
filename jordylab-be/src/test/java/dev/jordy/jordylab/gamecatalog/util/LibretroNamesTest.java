package dev.jordy.jordylab.gamecatalog.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LibretroNamesTest {

    @Test
    void theTitleItselfComesFirstThenRegionTaggedForms() {
        assertThat(LibretroNames.variants("Super Mario World")).startsWith("Super Mario World",
                "Super Mario World (USA)", "Super Mario World (Europe)", "Super Mario World (World)");
    }

    @Test
    void aTitleThatAlreadyCarriesATagIsNotTaggedAgain() {
        assertThat(LibretroNames.variants("Super Mario World (USA)")).containsExactly("Super Mario World (USA)");
    }

    @Test
    void aColonBecomesAUnderscoreAndAlsoADash() {
        assertThat(LibretroNames.variants("Zelda: A Link to the Past")).contains("Zelda_ A Link to the Past",
                "Zelda - A Link to the Past (USA)");
    }

    @Test
    void aLeadingArticleMovesBehindTheFirstPartWithACommaBeforeADash() {
        assertThat(LibretroNames.variants("The Legend of Zelda: A Link to the Past")).contains(
                "Legend of Zelda, The - A Link to the Past (USA)");
        assertThat(LibretroNames.variants("The Hobbit")).contains("Hobbit, The (Europe)");
    }

    @Test
    void ampersandsAndOtherForbiddenCharactersAreReplaced() {
        assertThat(LibretroNames.variants("Ratchet & Clank")).first().isEqualTo("Ratchet _ Clank");
    }

    @Test
    void theNumberOfRequestsIsCappedAndBlankTitlesHaveNoVariants() {
        assertThat(LibretroNames.variants("The Legend of Zelda: A Link to the Past")).hasSizeLessThanOrEqualTo(16);
        assertThat(LibretroNames.variants("  ")).isEmpty();
        assertThat(LibretroNames.variants(null)).isEmpty();
    }
}
