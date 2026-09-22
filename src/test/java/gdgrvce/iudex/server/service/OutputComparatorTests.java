package gdgrvce.iudex.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputComparatorTests {

    @Test
    void identicalOutputsMatch() {
        assertTrue(OutputComparator.matches("42", "42"));
    }

    @Test
    void trailingNewlineIsIgnored() {
        assertTrue(OutputComparator.matches("42\n", "42"));
        assertTrue(OutputComparator.matches("1\n2\n\n", "1\n2"));
    }

    @Test
    void trailingWhitespaceOnEachLineIsIgnored() {
        assertTrue(OutputComparator.matches("1 \n2\t", "1\n2"));
    }

    @Test
    void carriageReturnsAreNormalized() {
        assertTrue(OutputComparator.matches("1\r\n2", "1\n2"));
    }

    @Test
    void interiorDifferenceFails() {
        assertFalse(OutputComparator.matches("1\n5", "1\n4"));
    }

    @Test
    void nullActualMatchesEmptyExpected() {
        assertTrue(OutputComparator.matches(null, ""));
        assertFalse(OutputComparator.matches(null, "42"));
    }
}
