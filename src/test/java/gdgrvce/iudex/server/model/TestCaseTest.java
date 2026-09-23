package gdgrvce.iudex.server.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Covers the identity a submission result uses to name the test case it ran. */
class TestCaseTest {

    @Test
    void assignsAnIdentifierWhenNoneIsSupplied() {
        assertNotNull(new TestCase("1 2", "3").id());
    }

    @Test
    void keepsASuppliedIdentifier() {
        UUID id = UUID.randomUUID();
        assertEquals(id, new TestCase(id, "1 2", "3").id());
    }

    @Test
    void givesSeparatelyBuiltCasesDistinctIdentifiers() {
        assertNotEquals(new TestCase("1 2", "3").id(), new TestCase("1 2", "3").id());
    }

    @Test
    void rejectsMissingInputOrOutput() {
        assertThrows(NullPointerException.class, () -> new TestCase(null, "3"));
        assertThrows(NullPointerException.class, () -> new TestCase("1 2", null));
    }
}
