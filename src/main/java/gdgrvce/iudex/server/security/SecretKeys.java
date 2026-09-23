package gdgrvce.iudex.server.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Resolves the base64 secrets the application signs and encrypts with.
 *
 * <p>An unset secret mints a throwaway key so a local run starts without any
 * configuration. That key lives only for the life of the process, so every
 * restart invalidates whatever it protected. A deployment must set a real
 * value; the warning below says so.</p>
 */
public final class SecretKeys {

    private static final Logger log = LoggerFactory.getLogger(SecretKeys.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretKeys() {
    }

    /**
     * Decodes {@code configured}, or generates {@code requiredBytes} of randomness
     * when it is blank.
     *
     * @throws IllegalStateException if a value was supplied but is not usable, so
     *         a misconfigured deployment fails at startup rather than at the
     *         first request
     */
    public static byte[] resolve(String configured, int requiredBytes, String propertyName) {
        if (configured == null || configured.isBlank()) {
            byte[] generated = new byte[requiredBytes];
            RANDOM.nextBytes(generated);
            log.warn("""
                    No {} configured -- generated a random one for this run.
                    Anything it protects stops working when the process restarts.
                    Set it to a base64 value of at least {} bytes before deploying, e.g.
                      openssl rand -base64 {}""", propertyName, requiredBytes, requiredBytes);
            return generated;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(propertyName + " must be base64-encoded", exception);
        }
        if (decoded.length < requiredBytes) {
            throw new IllegalStateException(propertyName + " must decode to at least " + requiredBytes
                    + " bytes, but decoded to " + decoded.length);
        }
        return decoded;
    }
}
