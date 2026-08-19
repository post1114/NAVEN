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

   public static void verify(String username, String password) {
      Naven.verifyStatus = "Verifying...";
      Naven.verified = false;
      Naven.verifyToken = "";

      try {
         String requestBody = "{\"username\":\"" + escapeJson(username)
            + "\",\"password\":\"" + escapeJson(password) + "\"}";
         HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(Naven.VERIFY_SERVER))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(10))
            .build();

         client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
               int code = response.statusCode();
               String body = response.body().trim();
               return new VerifyResult(code, body);
            })
            .thenAccept(result -> {
               if (result.code == 200 && !result.body.isEmpty()) {
                  Naven.verified = true;
                  Naven.verifyToken = result.body;
                  Naven.verifyStatus = "Verified!";
               } else if (result.code == 401 || result.code == 403) {
                  Naven.verified = false;
                  Naven.verifyStatus = "Invalid credentials";
               } else {
                  Naven.verified = false;
                  Naven.verifyStatus = "Server error (" + result.code + ")";
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

   private static String escapeJson(String s) {
      return s.replace("\\", "\\\\").replace("\"", "\\\"");
   }

   private static class VerifyResult {
      final int code;
      final String body;

      VerifyResult(int code, String body) {
         this.code = code;
         this.body = body;
      }
   }
}
