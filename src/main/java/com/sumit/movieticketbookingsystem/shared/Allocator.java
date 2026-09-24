package com.sumit.movieticketbookingsystem.shared;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public final class Allocator {

    private Allocator() {
    }

    /**
     * Splits total in proportion to weights so that the parts always add up to the total.
     * Each part gets its floor share first; the leftover paise go one each to the parts with the largest
     * remainders (earlier index wins a tie).
     */
    public static long[] largestRemainder(long total, List<Long> weights) {
        if (total < 0) {
            throw new IllegalArgumentException("Total cannot be negative: " + total);
        }
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("Need at least one weight");
        }
        if (weights.stream().anyMatch(w -> w < 0)) {
            throw new IllegalArgumentException("Weights cannot be negative: " + weights);
        }
        long weightSum = weights.stream().mapToLong(Long::longValue).reduce(0, Math::addExact);
        if (weightSum == 0) {
            throw new IllegalArgumentException("Weights cannot all be zero");
        }

        long[] parts = new long[weights.size()];
        long[] remainders = new long[weights.size()];
        long allocated = 0;
        for (int i = 0; i < parts.length; i++) {
            long scaled = Math.multiplyExact(total, weights.get(i));
            parts[i] = scaled / weightSum;
            remainders[i] = scaled % weightSum;
            allocated += parts[i];
        }

        long leftover = total - allocated;
        IntStream.range(0, parts.length)
                .boxed()
                .sorted(Comparator.comparingLong((Integer i) -> remainders[i]).reversed())
                .limit(leftover)
                .forEach(i -> parts[i]++);
        return parts;
    }
}
