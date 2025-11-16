package ssd.medivault;

import org.junit.jupiter.api.Test;
import ssd.medivault.crypto.CryptoUtils;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.*;

public class CryptoUtilsTest {

    @Test
    public void testAesGcmEncryptDecrypt() throws GeneralSecurityException {
        SecretKey key = CryptoUtils.generateAESKey();
        String plain = "Test message — AES-GCM";

        String cipher = CryptoUtils.encryptAES(key, plain);
        assertNotNull(cipher);

        String dec = CryptoUtils.decryptAES(key, cipher);
        assertEquals(plain, dec);
    }

    @Test
    public void testAesGcmTamper() throws GeneralSecurityException {
        SecretKey key = CryptoUtils.generateAESKey();
        String plain = "Another message";
        String cipher = CryptoUtils.encryptAES(key, plain);

        // tamper with the ciphertext (flip a char)
        byte[] bytes = java.util.Base64.getDecoder().decode(cipher);
        bytes[bytes.length - 1] ^= 1; // flip last bit
        String tampered = java.util.Base64.getEncoder().encodeToString(bytes);

        assertThrows(GeneralSecurityException.class, () -> CryptoUtils.decryptAES(key, tampered));
    }

    @Test
    public void testAesGcmFileEncryptDecrypt() throws Exception {
        SecretKey key = CryptoUtils.generateAESKey();

        // read test resource file bytes
        java.nio.file.Path p = java.nio.file.Paths.get("src/test/resources/testdata/critical.txt");
        byte[] fileBytes = java.nio.file.Files.readAllBytes(p);

        String cipher = CryptoUtils.encryptAES(key, fileBytes);
        assertNotNull(cipher);

        byte[] decBytes = CryptoUtils.decryptAESToBytes(key, cipher);
        assertArrayEquals(fileBytes, decBytes);
    }

    @Test
    public void testRsaSignVerify() throws GeneralSecurityException {
        KeyPair kp = CryptoUtils.generateRSAKeyPair();
        String message = "Message to sign: " + System.currentTimeMillis();

        String sig = CryptoUtils.signRSA(kp.getPrivate(), message);
        assertNotNull(sig);

        boolean ok = CryptoUtils.verifyRSA(kp.getPublic(), message, sig);
        assertTrue(ok);
    }

    @Test
    public void testRsaVerifyFail() throws GeneralSecurityException {
        KeyPair kp1 = CryptoUtils.generateRSAKeyPair();
        KeyPair kp2 = CryptoUtils.generateRSAKeyPair();
        String message = "Will not verify with other key";

        String sig = CryptoUtils.signRSA(kp1.getPrivate(), message);
        boolean ok = CryptoUtils.verifyRSA(kp2.getPublic(), message, sig);
        assertFalse(ok);
    }
}
