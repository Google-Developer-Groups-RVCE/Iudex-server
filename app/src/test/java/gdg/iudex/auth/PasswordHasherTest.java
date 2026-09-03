package gdg.iudex.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  class PasswordHasherTest
 *  
 *  Tests for the PasswordHasher
 *  
 *  testHashOutput - test whether hash is non null and non empty.
 *  testVerifySuccess - verification of a correct password should return true.
 *  testVerifyFailure - verification of an incorrect password should return false.
 *  testSalting - make sure salting randomness exists.
 */

class PasswordHasherTest {

    private PasswordHasher passwordHasher;
    private static final String TEST_PASSWORD = "12345678";

    @Test
    void testHashOutput() {
        passwordHasher = new PasswordHasher();
        String hashedPassword = passwordHasher.hash(TEST_PASSWORD);

        // just verify the output here, not much else you can check
        assertNotNull(hashedPassword, "The hashed password should not be null");
        assertFalse(hashedPassword.trim().isEmpty(), "The hashed password should not be empty");
    }

    @Test
    void testVerifySuccess() {
        passwordHasher = new PasswordHasher();
        String hashedPassword = passwordHasher.hash(TEST_PASSWORD);
        
        boolean isVerified = passwordHasher.verify(TEST_PASSWORD, hashedPassword);
        
        assertTrue(isVerified, "Verification should return true for the correct password");
    }

    @Test
    void testVerifyFailure() {
        passwordHasher = new PasswordHasher();
        String hashedPassword = passwordHasher.hash(TEST_PASSWORD);
        String wrongPassword = "WrongPassword999!";
        
        boolean isVerified = passwordHasher.verify(wrongPassword, hashedPassword);
        
        assertFalse(isVerified, "Verification should return false for an incorrect password");
    }

    @Test
    void testSalting() {
        passwordHasher = new PasswordHasher();
        String hash1 = passwordHasher.hash(TEST_PASSWORD);
        String hash2 = passwordHasher.hash(TEST_PASSWORD);

        assertNotEquals(hash1, hash2, "Two hashes of the same password should differ due to the random salt");
        
        assertTrue(passwordHasher.verify(TEST_PASSWORD, hash1));
        assertTrue(passwordHasher.verify(TEST_PASSWORD, hash2));
    }
}