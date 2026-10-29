package io.jenkins.plugins.parameterfoundry.provider;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Data-only JavaScript String conversion for JSON values; never evaluates JavaScript. */
final class SourceJsonText {
    private SourceJsonText() {}

    static String javascript(Object value) {
        if (value == null) return "null";
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> item == null ? "" : javascript(item))
                    .collect(Collectors.joining(","));
        }
        if (value instanceof Map<?, ?> map) {
            // JSON cannot supply callable conversion hooks. A shadowed toString therefore
            // fails the source browser's primitive conversion, including inside arrays.
            if (map.containsKey("toString")) throw new IllegalArgumentException("JSON label cannot be coerced.");
            return "[object Object]";
        }
        if (value instanceof Number number) return number(number.doubleValue());
        return String.valueOf(value);
    }

    private static String number(double value) {
        if (Double.isNaN(value)) return "NaN";
        if (Double.isInfinite(value)) return value < 0 ? "-Infinity" : "Infinity";
        if (value == 0) return "0";
        boolean negative = value < 0;
        double absolute = Math.abs(value);
        BigDecimal exact = new BigDecimal(absolute);
        BigDecimal shortest = null;
        for (int precision = 1; precision <= 17 && shortest == null; precision++) {
            BigDecimal rounded = exact.round(new MathContext(precision, RoundingMode.HALF_EVEN));
            BigDecimal unit = BigDecimal.ONE.scaleByPowerOfTen(-rounded.scale());
            for (BigDecimal candidate : List.of(rounded, rounded.subtract(unit), rounded.add(unit))) {
                if (Double.doubleToLongBits(candidate.doubleValue()) != Double.doubleToLongBits(absolute)) continue;
                if (shortest == null
                        || candidate
                                        .subtract(exact)
                                        .abs()
                                        .compareTo(shortest.subtract(exact).abs())
                                < 0
                        || candidate
                                                .subtract(exact)
                                                .abs()
                                                .compareTo(
                                                        shortest.subtract(exact).abs())
                                        == 0
                                && !candidate.unscaledValue().testBit(0)) shortest = candidate;
            }
        }
        if (shortest == null) throw new IllegalArgumentException("JSON number cannot be formatted.");
        shortest = shortest.stripTrailingZeros();
        int exponent = shortest.precision() - shortest.scale() - 1;
        String text;
        if (exponent >= -6 && exponent < 21) {
            text = shortest.toPlainString();
        } else {
            String digits = shortest.unscaledValue().toString();
            text = digits.charAt(0) + (digits.length() == 1 ? "" : "." + digits.substring(1)) + "e"
                    + (exponent >= 0 ? "+" : "") + exponent;
        }
        return negative ? "-" + text : text;
    }
}
