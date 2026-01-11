package ssd.medivault.logserver.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class HmacService {
    private static final String ALGO = "HmacSHA256";
    private final byte[] key;

    public HmacService() {
        String keyMaterial = System.getenv("AUDIT_HMAC_KEY");
        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new IllegalStateException("AUDIT_HMAC_KEY not set");
        }
        this.key = keyMaterial.getBytes(StandardCharsets.UTF_8);
    }

    public String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(key, ALGO));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

