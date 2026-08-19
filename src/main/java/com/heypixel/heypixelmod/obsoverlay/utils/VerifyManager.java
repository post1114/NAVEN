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

   public static String fetchServerIdentity() {
      try {
         HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(Naven.VERIFY_SERVER + "/health"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();
         HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
         if (resp.statusCode() == 200) {
            String body = resp.body().trim();
            String encrypted = extractJsonString(body, "identity");
            if (encrypted != null && !encrypted.isEmpty()) {
               try {
                  return CryptoUtils.decrypt(encrypted);
               } catch (Exception e) {
                  return null;
               }
            }
         }
      } catch (Exception e) {
         // ignore
      }
      return null;
   }

   public static boolean verifyIdentity(String serverIdentity) {
      if (serverIdentity == null || serverIdentity.isEmpty()) return false;
      return serverIdentity.equals(Naven.VERIFY_IDENTITY);
   }

   public static void verifyCredentials(String username, String password) {
      Naven.verifyStatus = "Verifying...";
      Naven.verified = false;
      Naven.verifyToken = "";

      try {
         String payload = CryptoUtils.encrypt(username + "\n" + password);
         String body = "{\"data\":\"" + escapeJson(payload) + "\"}";

         HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(Naven.VERIFY_SERVER + "/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(10))
            .build();

         HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
         int code = resp.statusCode();
         String respBody = resp.body().trim();

         if (code == 200) {
            String encrypted = extractJsonString(respBody, "token");
            if (encrypted != null) {
               String token = CryptoUtils.decrypt(encrypted);
               Naven.verified = true;
               Naven.verifyToken = token;
               Naven.verifyStatus = "Verified!";
            } else {
               Naven.verifyStatus = "Invalid server response";
            }
         } else if (code == 401) {
            String error = extractJsonString(respBody, "error");
            Naven.verifyStatus = error != null ? error : "Invalid credentials";
         } else if (code == 403) {
            String error = extractJsonString(respBody, "error");
            Naven.verifyStatus = error != null ? error : "Account expired";
         } else {
            Naven.verifyStatus = "Server error (" + code + ")";
         }
      } catch (Exception e) {
         Naven.verifyStatus = "Error: " + e.getMessage();
      }
   }

   private static String extractJsonString(String json, String key) {
      String search = "\"" + key + "\"";
      int idx = json.indexOf(search);
      if (idx < 0) return null;
      int colon = json.indexOf(':', idx + search.length());
      if (colon < 0) return null;
      int start = json.indexOf('"', colon + 1);
      if (start < 0) return null;
      int end = json.indexOf('"', start + 1);
      if (end < 0) return null;
      return json.substring(start + 1, end);
   }

   private static String escapeJson(String s) {
      return s.replace("\\", "\\\\").replace("\"", "\\\"");
   }
}
