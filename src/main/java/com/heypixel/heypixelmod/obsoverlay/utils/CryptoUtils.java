package com.heypixel.heypixelmod.obsoverlay.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
   private static final String ALGORITHM = "AES";
   private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
   private static final byte[] KEY_BYTES = "NavenSecure2024!".getBytes(StandardCharsets.UTF_8);

   public static String encrypt(String plainText) {
      try {
         SecretKeySpec keySpec = new SecretKeySpec(KEY_BYTES, ALGORITHM);
         Cipher cipher = Cipher.getInstance(TRANSFORMATION);
         cipher.init(Cipher.ENCRYPT_MODE, keySpec);
         byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
         return Base64.getEncoder().encodeToString(encrypted);
      } catch (Exception e) {
         throw new RuntimeException("Encryption failed", e);
      }
   }

   public static String decrypt(String cipherText) {
      try {
         SecretKeySpec keySpec = new SecretKeySpec(KEY_BYTES, ALGORITHM);
         Cipher cipher = Cipher.getInstance(TRANSFORMATION);
         cipher.init(Cipher.DECRYPT_MODE, keySpec);
         byte[] decoded = Base64.getDecoder().decode(cipherText);
         return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
      } catch (Exception e) {
         throw new RuntimeException("Decryption failed", e);
      }
   }
}
