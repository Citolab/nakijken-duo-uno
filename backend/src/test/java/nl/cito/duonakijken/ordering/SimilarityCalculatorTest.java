package nl.cito.duonakijken.ordering;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SimilarityCalculatorTest {

    @Test
    void tokenJaccardHandlesEmptySets() {
        assertThat(SimilarityCalculator.tokenJaccard(Set.of(), Set.of())).isEqualTo(1d);
        assertThat(SimilarityCalculator.tokenJaccard(Set.of("a"), Set.of())).isEqualTo(0d);
    }

    @Test
    void tokenJaccardOfIdenticalSetsIsOne() {
        assertThat(SimilarityCalculator.tokenJaccard(Set.of("a", "b"), Set.of("a", "b"))).isEqualTo(1d);
    }

    @Test
    void tokenJaccardOfPartialOverlap() {
        assertThat(SimilarityCalculator.tokenJaccard(Set.of("a", "b"), Set.of("b", "c")))
            .isCloseTo(1d / 3d, offset(1e-9));
    }

    @Test
    void charNGramCosineOfIdenticalVectorsIsOne() {
        var grams = Map.of("abc", 2, "bcd", 1);
        assertThat(SimilarityCalculator.charNGramCosine(grams, grams)).isCloseTo(1d, offset(1e-9));
    }

    @Test
    void charNGramCosineOfDisjointVectorsIsZero() {
        assertThat(SimilarityCalculator.charNGramCosine(Map.of("abc", 1), Map.of("xyz", 1))).isEqualTo(0d);
    }

    @Test
    void normalizedEditDistanceIsZeroForEqualStrings() {
        assertThat(SimilarityCalculator.normalizedEditDistance("glucose", "glucose")).isEqualTo(0d);
        assertThat(SimilarityCalculator.normalizedEditDistance("", "")).isEqualTo(0d);
    }

    @Test
    void normalizedEditDistanceIsOneForCompletelyDifferentStrings() {
        assertThat(SimilarityCalculator.normalizedEditDistance("abc", "xyz")).isEqualTo(1d);
    }

    @Test
    void normalizedEditDistanceCountsTranspositionAsSingleEdit() {
        // Damerau-Levenshtein: "ab" -> "ba" is one transposition.
        assertThat(SimilarityCalculator.normalizedEditDistance("ab", "ba")).isCloseTo(0.5d, offset(1e-9));
    }
}
