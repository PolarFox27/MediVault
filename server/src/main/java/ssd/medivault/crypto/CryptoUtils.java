package ssd.medivault.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Base64;

public final class CryptoUtils {

    private CryptoUtils() {}

    // RSA settings
    public static final int RSA_KEY_SIZE = 2048;

    // AES settings
    public static final int AES_KEY_SIZE = 128; // bits
    public static final int GCM_IV_LENGTH = 12; // bytes
    public static final int GCM_TAG_LENGTH = 128; // bits

    // --- Key generation ---

    public static KeyPair generateRSAKeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(new RSAKeyGenParameterSpec(RSA_KEY_SIZE, RSAKeyGenParameterSpec.F4), new SecureRandom());
        return kpg.generateKeyPair();
    }

    public static SecretKey generateAESKey() throws GeneralSecurityException {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(AES_KEY_SIZE, new SecureRandom());
        return kg.generateKey();
    }

    // --- AES-GCM encryption ---
   
    public static String encryptAES(SecretKey key, byte[] plaintextBytes) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] ciphertext = cipher.doFinal(plaintextBytes);

        // store iv + ciphertext
        ByteBuffer bb = ByteBuffer.allocate(iv.length + ciphertext.length);
        bb.put(iv);
        bb.put(ciphertext);
        return Base64.getEncoder().encodeToString(bb.array());
    }

    public static String encryptAES(SecretKey key, String plaintext) throws GeneralSecurityException {
        return encryptAES(key, plaintext.getBytes(StandardCharsets.UTF_8));
    }

    // --- AES-GCM Decryption ---

    public static byte[] decryptAESToBytes(SecretKey key, String base64IvCipher) throws GeneralSecurityException {
        byte[] ivCipher = Base64.getDecoder().decode(base64IvCipher);
        ByteBuffer bb = ByteBuffer.wrap(ivCipher);

        byte[] iv = new byte[GCM_IV_LENGTH];
        bb.get(iv);
        byte[] cipherBytes = new byte[bb.remaining()];
        bb.get(cipherBytes);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        return cipher.doFinal(cipherBytes);
    }

    public static String decryptAES(SecretKey key, String base64IvCipher) throws GeneralSecurityException {
        byte[] bytes = decryptAESToBytes(key, base64IvCipher);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // --- RSA signature / verification ---

    public static String signRSA(PrivateKey priv, String message) throws GeneralSecurityException {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(priv, new SecureRandom());
        sig.update(message.getBytes(StandardCharsets.UTF_8));
        byte[] s = sig.sign();
        return Base64.getEncoder().encodeToString(s);
    }

    public static boolean verifyRSA(PublicKey pub, String message, String base64Signature) throws GeneralSecurityException {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(pub);
        sig.update(message.getBytes(StandardCharsets.UTF_8));
        byte[] s = Base64.getDecoder().decode(base64Signature);
        return sig.verify(s);
    }

    // Helper to encode keys (for printing only)
    public static String base64EncodeKey(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}
