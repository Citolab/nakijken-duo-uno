package nl.cito.duonakijken.ordering;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizationTest {

    @Test
    void normalizeLowercasesAndCollapsesWhitespace() {
        assertThat(TextNormalization.normalize("  Fotosynthese   maakt\tGLUCOSE  ", true))
            .isEqualTo("fotosynthese maakt glucose");
    }

    @Test
    void normalizeFoldsDiacriticsWhenEnabled() {
        assertThat(TextNormalization.normalize("coördinatie één", true))
            .isEqualTo("coordinatie een");
    }

    @Test
    void normalizeKeepsDiacriticsWhenDisabled() {
        assertThat(TextNormalization.normalize("coördinatie", false))
            .isEqualTo("coördinatie");
    }

    @Test
    void normalizeReplacesPunctuationWithSpaces() {
        assertThat(TextNormalization.normalize("glucose, zuurstof & water!", true))
            .isEqualTo("glucose zuurstof water");
    }

    @Test
    void normalizeHandlesNullAndBlank() {
        assertThat(TextNormalization.normalize(null, true)).isEmpty();
        assertThat(TextNormalization.normalize("   ", true)).isEmpty();
    }

    @Test
    void tokenizeSplitsOnWhitespace() {
        assertThat(TextNormalization.tokenize("glucose en zuurstof"))
            .containsExactly("glucose", "en", "zuurstof");
        assertThat(TextNormalization.tokenize("")).isEmpty();
        assertThat(TextNormalization.tokenize(null)).isEmpty();
    }

    @Test
    void extractCharNGramsCountsOverlappingGrams() {
        assertThat(TextNormalization.extractCharNGrams("abab", 3))
            .isEqualTo(Map.of("aba", 1, "bab", 1));
    }

    @Test
    void extractCharNGramsIgnoresSpacesAndHandlesShortInput() {
        assertThat(TextNormalization.extractCharNGrams("a b", 3)).isEqualTo(Map.of("ab", 1));
        assertThat(TextNormalization.extractCharNGrams("", 3)).isEmpty();
    }

    @Test
    void isJunkDetectsEmptyAndSymbolOnlyResponses() {
        assertThat(TextNormalization.isJunk("", "")).isTrue();
        assertThat(TextNormalization.isJunk("???", TextNormalization.normalize("???", true))).isTrue();
        assertThat(TextNormalization.isJunk(null, "iets")).isTrue();
        assertThat(TextNormalization.isJunk("echt antwoord", "echt antwoord")).isFalse();
    }
}
