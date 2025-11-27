package org.example.auth;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;

/**
 * AuthManager: desktop OAuth helper.
 *
 * Usage:
 *   AuthManager am = new AuthManager("/credentials.json", List.of(SCOPES...), "tokens");
 *   Credential cred = am.authorizeInstalledApp("user");
 */
public class AuthManager {
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private final NetHttpTransport httpTransport;
    private final GoogleClientSecrets clientSecrets;
    private final List<String> scopes;

    private final FileDataStoreFactory tokenStoreFactory;
    private final Path tokenDirPath;

    // Keep one flow instance so the same data store is consistently used
    private GoogleAuthorizationCodeFlow flow;

    public AuthManager(String credentialsPath, List<String> scopes, String tokenStoreDir) throws Exception {
        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        this.scopes = Objects.requireNonNull(scopes, "scopes required");

        // Load client secrets (classpath or filesystem)
        if (credentialsPath.startsWith("/")) {
            try (InputStream in = AuthManager.class.getResourceAsStream(credentialsPath)) {
                if (in == null) throw new IllegalArgumentException("Resource not found: " + credentialsPath);
                this.clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
            }
        } else {
            File f = new File(credentialsPath);
            if (!f.exists()) throw new IllegalArgumentException("Credentials file not found: " + credentialsPath);
            try (FileReader fr = new FileReader(f)) {
                this.clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, fr);
            }
        }

        // Ensure token dir exists and wire FileDataStoreFactory
        String dirName = (tokenStoreDir == null || tokenStoreDir.isBlank()) ? "tokens" : tokenStoreDir;
        Path dir = Paths.get(dirName);
        Files.createDirectories(dir);                 // <— crucial so first write never fails
        this.tokenDirPath = dir;
        this.tokenStoreFactory = new FileDataStoreFactory(dir.toFile());

        // Build and keep a single flow that uses the factory above
        this.flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, this.scopes)
                .setDataStoreFactory(this.tokenStoreFactory)
                .setAccessType("offline")
                .build();
    }

    /** Directory where the Google client library stores tokens. */
    public Path getTokenStoreDirPath() {
        return tokenDirPath;
    }

    /**
     * Clears cached tokens safely: removes files inside the directory,
     * then recreates the directory and reinitializes the flow to the same location.
     */
    public void clearTokenCache() throws IOException {
        try {
            // Clear Google’s StoredCredential store
            var ds = tokenStoreFactory.getDataStore("StoredCredential");
            ds.clear();
        } catch (Exception ignored) {}

        // Remove files but keep (or recreate) the directory
        try {
            if (Files.exists(tokenDirPath)) {
                try (var paths = Files.list(tokenDirPath)) {
                    paths.forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
                }
            } else {
                Files.createDirectories(tokenDirPath);
            }
            // Make sure directory exists for the next run
            Files.createDirectories(tokenDirPath);
        } catch (Exception ignored) {}

        // Rebuild flow to ensure it uses a valid, existing data store
        this.flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, this.scopes)
                .setDataStoreFactory(this.tokenStoreFactory)
                .setAccessType("offline")
                .build();
    }

    /**
     * Performs the installed-app auth flow (opens browser and listens on loopback).
     * Returns a ready-to-use Credential (refresh token persisted under tokenDirPath).
     */
    public Credential authorizeInstalledApp(String userId) throws Exception {
        // Extra safety: ensure dir exists just before auth
        Files.createDirectories(tokenDirPath);

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(0).build();

        AuthorizationCodeInstalledApp app = new AuthorizationCodeInstalledApp(flow, receiver) {
            @Override
            protected void onAuthorization(AuthorizationCodeRequestUrl authorizationUrl) throws IOException {
                // Always show consent (helps avoid stale/partial token states)
                authorizationUrl.set("prompt", "consent");
                super.onAuthorization(authorizationUrl);
            }
        };

        return app.authorize((userId == null || userId.isBlank()) ? "user" : userId);
    }

    /** Expose the flow if you still need it elsewhere. */
    public GoogleAuthorizationCodeFlow getFlow() {
        return this.flow;
    }

    /** Convenience alias if some code still calls this. */
    public Credential signInInteractive(String userId) throws Exception {
        return authorizeInstalledApp(userId);
    }
}
