package com.rfn.fileencryptor.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

/**
 * Handles Google OAuth 2.0 for installed apps and builds an authenticated Drive client.
 */
public final class GoogleDriveAuth {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final Collection<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);

    private static final String TOKENS_DIR = System.getProperty("user.home")
            + File.separator + ".fileencryptor" + File.separator + "google-tokens";

    // Place your client JSON here: src/main/resources/google/credentials.json
    private static final String CREDENTIALS_RESOURCE = "/google/credentials.json";

    private GoogleDriveAuth() {}

    /** Returns true if a valid stored credential exists (no browser login needed). */
    public static boolean isAuthenticated() {
        try {
            return getStoredCredential() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Builds an authenticated Drive service (will prompt browser on first run). */
    public static Drive getDriveService() throws Exception {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = getStoredCredential();
        if (credential == null) {
            credential = authorize();
        }
        return new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("FileEncryptor")
                .build();
    }

    /** Deletes cached tokens to force next run to re-authenticate. */
    public static void signOut() {
        File dir = new File(TOKENS_DIR);
        if (dir.exists()) {
            Optional.ofNullable(dir.listFiles()).ifPresent(files -> {
                for (File f : files) {
                    try { f.delete(); } catch (Exception ignore) {}
                }
            });
            try { dir.delete(); } catch (Exception ignore) {}
        }
    }

    private static Credential authorize() throws IOException, GeneralSecurityException {
        GoogleClientSecrets clientSecrets = loadClientSecrets();
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var dataStoreFactory = new FileDataStoreFactory(new File(TOKENS_DIR));

        var flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(dataStoreFactory)
                .setAccessType("offline")
                .build();

        var receiver = new LocalServerReceiver.Builder()
                .setHost("127.0.0.1")
                .setPort(0) // random available port
                .build();

        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private static Credential getStoredCredential() throws IOException, GeneralSecurityException {
        GoogleClientSecrets clientSecrets = loadClientSecrets();
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var dataStoreFactory = new FileDataStoreFactory(new File(TOKENS_DIR));

        var flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(dataStoreFactory)
                .setAccessType("offline")
                .build();

        Credential cred = flow.loadCredential("user");
        if (cred == null) return null;
        Long remaining = cred.getExpiresInSeconds();
        if (remaining != null && remaining <= 60) {
            if (!cred.refreshToken()) {
                return null;
            }
        }
        return cred;
    }

    private static GoogleClientSecrets loadClientSecrets() throws IOException {
        try (InputStream in = GoogleDriveAuth.class.getResourceAsStream(CREDENTIALS_RESOURCE)) {
            if (in == null) {
                throw new FileNotFoundException("Missing " + CREDENTIALS_RESOURCE + ". Place your OAuth client JSON there.");
            }
            return GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }
}
