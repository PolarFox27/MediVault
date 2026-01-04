package ssd.medivault.logserver.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class HmacService {
    private static final String ALGO = "HmacSHA256";
    private final byte[] key;

    public HmacService() {
        // TODO: In practice: load from file or env
        this.key = "CHANGE_ME_SECURE_KEY".getBytes();
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

