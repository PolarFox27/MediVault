package ssd.medivault.utils;

import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.exception.HexException;

public class EncodingUtils {

    /**
     * Utils function that encodes a byte array into hexadecimal.
     *
     * @param bytes the bytes to encode.
     * @return the Base64 URL encoding.
     */
    public static String toHex(byte[] bytes) {
        return new ByteArray(bytes).getHex();
    }

    /**
     * Utils function that decodes a byte array from a hexadecimal encoding.
     *
     * @param hex the hex encoding
     * @return the decoded byte array
     * @throws HexException if the encoding was invalid
     */
    public static byte[] fromHex(String hex) throws HexException {
        return ByteArray.fromHex(hex).getBytes();
    }
}
