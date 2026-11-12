package dev.jordy.jordylab.fna.util;

import dev.jordy.jordylab.fna.rest.controller.model.PriceHistoryPointDto;
import dev.jordy.jordylab.fna.rest.controller.model.PriceHistorySummaryDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class PriceHistorySummaryCalculatorTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2025, 11, 7);
    private static final LocalDate MIDDLE_DAY = LocalDate.of(2026, 3, 2);
    private static final LocalDate LAST_DAY = LocalDate.of(2026, 11, 6);

    @Test
    void noPointsGiveNoSummary() {
        assertThat(PriceHistorySummaryCalculator.summarize(List.of())).isNull();
    }

    @Test
    void aSinglePointHasNoChangeFiguresInsteadOfZero() {
        PriceHistorySummaryDto summary = PriceHistorySummaryCalculator.summarize(List.of(point(FIRST_DAY, "1012.30")));

        assertSoftly(softly -> {
            softly.assertThat(summary.startDate()).isEqualTo(FIRST_DAY);
            softly.assertThat(summary.endDate()).isEqualTo(FIRST_DAY);
            softly.assertThat(summary.endPriceInEuro()).isEqualByComparingTo("1012.30");
            softly.assertThat(summary.changeInEuro()).isNull();
            softly.assertThat(summary.changePercent()).isNull();
        });
    }

    @Test
    void changeIsTheEndMinusTheStartAndThePercentIsRoundedToTwoDecimals() {
        PriceHistorySummaryDto summary = PriceHistorySummaryCalculator.summarize(List.of(
                point(FIRST_DAY, "1012.30"),
                point(LAST_DAY, "1250.00")));

        assertSoftly(softly -> {
            softly.assertThat(summary.startPriceInEuro()).isEqualByComparingTo("1012.30");
            softly.assertThat(summary.endPriceInEuro()).isEqualByComparingTo("1250.00");
            softly.assertThat(summary.changeInEuro()).isEqualByComparingTo("237.70");
            softly.assertThat(summary.changePercent()).isEqualByComparingTo("23.48");
        });
    }

    @Test
    void highestAndLowestAreTakenOverEveryPointOfTheWindow() {
        PriceHistorySummaryDto summary = PriceHistorySummaryCalculator.summarize(List.of(
                point(FIRST_DAY, "1012.30"),
                point(MIDDLE_DAY, "990.00"),
                point(LAST_DAY, "1300.00")));

        assertSoftly(softly -> {
            softly.assertThat(summary.highestPriceInEuro()).isEqualByComparingTo("1300.00");
            softly.assertThat(summary.lowestPriceInEuro()).isEqualByComparingTo("990.00");
        });
    }

    @Test
    void theFirstAndLastPointsGiveTheStartAndEndDates() {
        PriceHistorySummaryDto summary = PriceHistorySummaryCalculator.summarize(List.of(
                point(FIRST_DAY, "1012.30"),
                point(MIDDLE_DAY, "1100.00"),
                point(LAST_DAY, "1250.00")));

        assertSoftly(softly -> {
            softly.assertThat(summary.startDate()).isEqualTo(FIRST_DAY);
            softly.assertThat(summary.endDate()).isEqualTo(LAST_DAY);
        });
    }

    private static PriceHistoryPointDto point(LocalDate date, String priceInEuro) {
        return new PriceHistoryPointDto(date, new BigDecimal(priceInEuro));
    }
}
