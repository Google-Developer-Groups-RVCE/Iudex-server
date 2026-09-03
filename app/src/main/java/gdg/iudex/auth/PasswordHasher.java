package gdg.iudex.auth;

import com.password4j.Password;

/**
 *  class PasswordHasher
 *  
 *  Methods:
 *  hash - hash a plaintext password
 *  verify - verify a plaintext password against a hashed one
 */

public class PasswordHasher {

    public String hash(String plainTextPassword) {
        return Password.hash(plainTextPassword)
                .addRandomSalt()
                .withArgon2()
                .getResult();
    }

    public boolean verify(String plainTextPassword, String hashedPassword) {
        return Password.check(plainTextPassword, hashedPassword)
                .withArgon2();
    }
}