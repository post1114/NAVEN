package com.heypixel.heypixelmod.obsoverlay.utils;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class VerifyManager {
   private static final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

   public static void verify(String token) {
      Naven.verifyStatus = "Verifying...";
      Naven.verified = false;

      try {
         String requestBody = "{\"token\":\"" + escapeJson(token) + "\"}";
         HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(Naven.VERIFY_SERVER))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(10))
            .build();

         client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body)
            .thenAccept(body -> {
               String trimmed = body.trim();
               if (trimmed.equals(Naven.VERIFY_UUID)) {
                  Naven.verified = true;
                  Naven.verifyStatus = "Verified!";
               } else {
                  Naven.verified = false;
                  Naven.verifyStatus = "Invalid response";
               }
            })
            .exceptionally(ex -> {
               Naven.verified = false;
               Naven.verifyStatus = "Connection failed: " + ex.getMessage();
               return null;
            })
            .join();
      } catch (Exception e) {
         Naven.verified = false;
         Naven.verifyStatus = "Error: " + e.getMessage();
      }
   }

   public static void verifyLocal(String token) {
      Naven.verifyStatus = "Verifying...";
      Naven.verified = false;

      if (token != null && token.equals(Naven.VERIFY_UUID)) {
         Naven.verified = true;
         Naven.verifyStatus = "Verified!";
      } else {
         Naven.verified = false;
         Naven.verifyStatus = "Invalid token";
      }
   }

   private static String escapeJson(String s) {
      return s.replace("\\", "\\\\").replace("\"", "\\\"");
   }
}
