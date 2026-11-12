package dev.jordy.jordylab.fna.util;

import dev.jordy.jordylab.fna.rest.controller.model.PriceHistoryPointDto;
import dev.jordy.jordylab.fna.rest.controller.model.PriceHistorySummaryDto;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/** Window statistics for the price history (spec 023, research section 5): one place computes every figure. */
@UtilityClass
public class PriceHistorySummaryCalculator {

    private static final int PERCENT_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /** Summary of ascending points, or {@code null} when there are none. */
    public static PriceHistorySummaryDto summarize(List<PriceHistoryPointDto> points) {
        if (points.isEmpty()) {
            return null;
        }
        PriceHistoryPointDto first = points.getFirst();
        PriceHistoryPointDto last = points.getLast();
        BigDecimal highest = points.stream().map(PriceHistoryPointDto::priceInEuro).max(Comparator.naturalOrder()).orElseThrow();
        BigDecimal lowest = points.stream().map(PriceHistoryPointDto::priceInEuro).min(Comparator.naturalOrder()).orElseThrow();
        if (points.size() == 1) {
            return new PriceHistorySummaryDto(first.date(), first.priceInEuro(), last.date(), last.priceInEuro(),
                    null, null, highest, lowest);
        }
        BigDecimal change = last.priceInEuro().subtract(first.priceInEuro());
        BigDecimal changePercent = change.multiply(ONE_HUNDRED)
                .divide(first.priceInEuro(), PERCENT_SCALE, RoundingMode.HALF_UP);

        return new PriceHistorySummaryDto(first.date(), first.priceInEuro(), last.date(), last.priceInEuro(),
                change, changePercent, highest, lowest);
    }
}
