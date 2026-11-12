package dev.jordy.jordylab.fna.rest.controller.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One recorded day of a holding's history: the euro value of that day. */
public record PriceHistoryPointDto(LocalDate date, BigDecimal priceInEuro) {
}
