package ru.pyshkov.llm.day05;

/** Описание модели и её цены за 1M токенов. */
public record Day05Model(
        String id,
        String provider,
        String label,
        String resource,
        Pricing pricing
) {
    public record Pricing(
            double inputOffPeak,
            double inputPeak,
            double outputOffPeak,
            double outputPeak
    ) {
    }
}
