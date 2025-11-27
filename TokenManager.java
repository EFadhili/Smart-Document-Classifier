package org.example.auth;

import com.google.api.client.auth.oauth2.Credential;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Objects;

/**
 * TokenManager manages the active OAuth credential and basic user identity.
 * Provides access tokens with auto-refresh and simple sign-in/out helpers.
 */
public class TokenManager {
    private Credential activeCredential;
    private String currentUserId;
    private Path tokenStoreDir;  // set from AppLauncher so signOut can clear cached tokens

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final Gson GSON = new Gson();

    public TokenManager() {}

    /* ---------- Session / identity ---------- */

    public void setCredential(Credential credential) {
        this.activeCredential = Objects.requireNonNull(credential);
    }


    public Credential getCredential() {
        return activeCredential;
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public boolean hasCredential() {
        return activeCredential != null;
    }

    public boolean isSignedIn() {
        return activeCredential != null && currentUserId != null && !currentUserId.isBlank();
    }

    /* ---------- Token storage dir (for clearing on sign-out) ---------- */


    public void setTokenStoreDir(String dir) {
        setTokenStoreDir(dir == null || dir.isBlank() ? null : Paths.get(dir));
    }

    public void setTokenStoreDir(Path dir) {
        this.tokenStoreDir = dir;
        if (dir != null) {
            try {
                Files.createDirectories(dir);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create token store dir: " + dir, e);
            }
        }
    }

    public Path getTokenStoreDir() {
        return tokenStoreDir;
    }

    /* ---------- Access token with auto-refresh ---------- */


    public String getAccessToken() {
        if (activeCredential == null) {
            return null;
        }

        String token = activeCredential.getAccessToken();
        System.out.println("🔑 [TokenManager] Returning token, length: " +
                (token != null ? token.length() : "null"));

        if (token != null && token.length() < 100) {
            System.err.println("⚠️ [TokenManager] WARNING: Token is suspiciously short!");
        }

        return token;
    }

    public String getValidAccessToken() throws Exception {
        if (activeCredential == null) {
            throw new IllegalStateException("Not authenticated");
        }

        // Check if token needs refresh
        if (activeCredential.getExpiresInSeconds() != null &&
                activeCredential.getExpiresInSeconds() < 300) { // 5 minutes
            System.out.println("🔄 Refreshing access token...");
            boolean refreshed = activeCredential.refreshToken();
            if (!refreshed) {
                throw new Exception("Failed to refresh access token");
            }
        }

        return activeCredential.getAccessToken();
    }

    /* ---------- Convenience: fetch user email ---------- */

    public String fetchUserEmail() {
        try {
            String token = getAccessToken();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            JsonObject j = GSON.fromJson(resp.body(), JsonObject.class);
            if (j != null && j.has("email")) {
                return j.get("email").getAsString();
            }
        } catch (Exception e) {
            // ignore, return null
        }
        return null;
    }

    /* ---------- Sign out: clear memory + delete cached tokens ---------- */


    public void signOut() {
        clearCredential();
        // Delete files but keep the directory so the next OAuth run can write into it.
        if (tokenStoreDir != null) {
            try {
                if (Files.isDirectory(tokenStoreDir)) {
                    try (var stream = Files.list(tokenStoreDir)) {
                        stream.forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
                    }
                }
            } catch (Exception ignored) {}
        }
    }
    public void clearCredential() {
        this.activeCredential = null;
        this.currentUserId = null;
    }

    public String debugTokenInfo() {
        try {
            String token = getAccessToken();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?access_token=" + token))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body(); // look for "scope": "... generative-language ..."
        } catch (Exception e) {
            return "tokeninfo error: " + e.getMessage();
        }
    }

}
