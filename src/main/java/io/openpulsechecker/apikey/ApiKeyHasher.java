package io.openpulsechecker.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyHasher {

    public String hash(String rawSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(rawSecret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash API key", e);
        }
    }
}
