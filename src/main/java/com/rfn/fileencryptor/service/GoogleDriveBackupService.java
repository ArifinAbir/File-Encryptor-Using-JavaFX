package com.rfn.fileencryptor.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

/**
 * Service to back up encrypted files to Google Drive.
 */
public class GoogleDriveBackupService {

    private static final String BACKUP_FOLDER_NAME = "FileEncryptor Backups";
    // Keys for Drive appProperties to carry metadata needed to reconstruct DB entries on restore
    public static final String PROP_IV = "iv";
    public static final String PROP_SALT = "salt";
    public static final String PROP_ALGO = "algo";
    public static final String PROP_COMPRESSED = "compressed"; // Y/N
    public static final String PROP_ORIGINAL_NAME = "originalName";
    public static final String PROP_FILE_SIZE = "fileSize"; // original size in bytes
    public static final String PROP_OWNER_ID = "ownerId"; // optional for validation

    private final Drive drive;

    public GoogleDriveBackupService(Drive drive) {
        this.drive = drive;
    }

    public interface Progress {
        void onFileStart(Path file, long size);
        void onProgress(Path file, long bytes, long total);
        void onFileDone(Path file, String driveFileId);
    }

    /**
     * Upload every file within the provided directory to the app folder in Drive.
     */
    public void backupDirectory(Path encryptedDir, Progress progress) throws IOException {
        if (encryptedDir == null) throw new IllegalArgumentException("encryptedDir is null");
        if (!Files.isDirectory(encryptedDir)) {
            throw new IllegalArgumentException("Not a directory: " + encryptedDir);
        }

        String folderId = ensureBackupFolder();

        try (Stream<Path> s = Files.list(encryptedDir)) {
            List<Path> files = s.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path p : files) {
                uploadSingle(p, folderId, progress);
            }
        }
    }

    /** Upload only the provided files. */
    public void backupSpecificFiles(List<Path> files, Progress progress) throws IOException {
        if (files == null || files.isEmpty()) return;
        String folderId = ensureBackupFolder();
        for (Path p : files) {
            if (p != null && Files.isRegularFile(p)) {
                uploadSingle(p, folderId, progress);
            }
        }
    }

    /** Upload provided files with Drive appProperties metadata per file. */
    public void backupSpecificFilesWithProps(List<Path> files,
                                             Map<Path, Map<String, String>> appPropsByPath,
                                             Progress progress) throws IOException {
        if (files == null || files.isEmpty()) return;
        String folderId = ensureBackupFolder();
        for (Path p : files) {
            if (p != null && Files.isRegularFile(p)) {
                Map<String, String> props = appPropsByPath == null ? null : appPropsByPath.get(p);
                uploadSingleWithProps(p, folderId, progress, props);
            }
        }
    }

    /** Update existing Drive files (by id) with new content and appProperties. */
    public void updateSpecificFilesWithProps(Map<Path, File> existingByPath,
                                             Map<Path, Map<String, String>> appPropsByPath,
                                             Progress progress) throws IOException {
        if (existingByPath == null || existingByPath.isEmpty()) return;
        for (Map.Entry<Path, File> e : existingByPath.entrySet()) {
            Path path = e.getKey();
            File existing = e.getValue();
            updateSingleWithProps(path, existing.getId(), progress, appPropsByPath == null ? null : appPropsByPath.get(path));
        }
    }

    private String ensureBackupFolder() throws IOException {
        String query = "name = '" + BACKUP_FOLDER_NAME.replace("'", "\\'")
                + "' and mimeType = 'application/vnd.google-apps.folder' and trashed = false";

        FileList list = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setPageSize(1)
                .execute();

        if (list.getFiles() != null && !list.getFiles().isEmpty()) {
            return list.getFiles().get(0).getId();
        }

        File folderMeta = new File();
        folderMeta.setName(BACKUP_FOLDER_NAME);
        folderMeta.setMimeType("application/vnd.google-apps.folder");
        File created = drive.files().create(folderMeta)
                .setFields("id")
                .execute();
        return created.getId();
    }

    private void uploadSingle(Path path, String parentId, Progress progress) throws IOException {
        long size = Files.size(path);
        if (progress != null) progress.onFileStart(path, size);

        File meta = new File();
        meta.setName(path.getFileName().toString());
        meta.setParents(Collections.singletonList(parentId));

        FileContent media = new FileContent("application/octet-stream", path.toFile());
        Drive.Files.Create req = drive.files().create(meta, media)
                .setFields("id, name, size, modifiedTime, parents");

        MediaHttpUploader uploader = req.getMediaHttpUploader();
        uploader.setDirectUploadEnabled(false); // resumable
        uploader.setChunkSize(10 * 1024 * 1024); // 10MB
        uploader.setProgressListener(u -> {
            if (progress == null || size <= 0) return;
            switch (u.getUploadState()) {
                case MEDIA_IN_PROGRESS -> progress.onProgress(path, (long)(u.getProgress() * size), size);
                case MEDIA_COMPLETE -> { /* handled after execute */ }
                default -> { }
            }
        });

        File uploaded = req.execute();
        if (progress != null) {
            progress.onProgress(path, size, size);
            progress.onFileDone(path, uploaded.getId());
        }
    }

    private void uploadSingleWithProps(Path path, String parentId, Progress progress, Map<String, String> appProps) throws IOException {
        long size = Files.size(path);
        if (progress != null) progress.onFileStart(path, size);

        File meta = new File();
        meta.setName(path.getFileName().toString());
        meta.setParents(Collections.singletonList(parentId));
        if (appProps != null && !appProps.isEmpty()) {
            meta.setAppProperties(appProps);
        }

        FileContent media = new FileContent("application/octet-stream", path.toFile());
        Drive.Files.Create req = drive.files().create(meta, media)
                .setFields("id, name, size, modifiedTime, parents, appProperties");

        MediaHttpUploader uploader = req.getMediaHttpUploader();
        uploader.setDirectUploadEnabled(false);
        uploader.setChunkSize(10 * 1024 * 1024);
        uploader.setProgressListener(u -> {
            if (progress == null || size <= 0) return;
            switch (u.getUploadState()) {
                case MEDIA_IN_PROGRESS -> progress.onProgress(path, (long)(u.getProgress() * size), size);
                case MEDIA_COMPLETE -> { }
                default -> { }
            }
        });

        File uploaded = req.execute();
        if (progress != null) {
            progress.onProgress(path, size, size);
            progress.onFileDone(path, uploaded.getId());
        }
    }

    private void updateSingleWithProps(Path path, String fileId, Progress progress, Map<String, String> appProps) throws IOException {
        long size = Files.size(path);
        if (progress != null) progress.onFileStart(path, size);

        File meta = new File();
        if (appProps != null && !appProps.isEmpty()) {
            meta.setAppProperties(appProps);
        }

        FileContent media = new FileContent("application/octet-stream", path.toFile());
        Drive.Files.Update req = drive.files().update(fileId, meta, media)
                .setFields("id, name, size, modifiedTime, appProperties");

        MediaHttpUploader uploader = req.getMediaHttpUploader();
        uploader.setDirectUploadEnabled(false);
        uploader.setChunkSize(10 * 1024 * 1024);
        uploader.setProgressListener(u -> {
            if (progress == null || size <= 0) return;
            switch (u.getUploadState()) {
                case MEDIA_IN_PROGRESS -> progress.onProgress(path, (long)(u.getProgress() * size), size);
                case MEDIA_COMPLETE -> { }
                default -> { }
            }
        });

        File updated = req.execute();
        if (progress != null) {
            progress.onProgress(path, size, size);
            progress.onFileDone(path, updated.getId());
        }
    }

    /** List all files currently in the backup folder on Drive. */
    public List<File> listBackupFiles() throws IOException {
        String folderId = ensureBackupFolder();
        String q = String.format("'%s' in parents and trashed = false", folderId);
        String pageToken = null;
        new File();
        java.util.ArrayList<File> results = new java.util.ArrayList<>();
    do {
        FileList resp = drive.files().list()
            .setQ(q)
            .setSpaces("drive")
            .setFields("nextPageToken, files(id, name, size, modifiedTime, md5Checksum, appProperties)")
            .setPageToken(pageToken)
            .setPageSize(1000)
            .execute();
            if (resp.getFiles() != null) results.addAll(resp.getFiles());
            pageToken = resp.getNextPageToken();
        } while (pageToken != null);
        return results;
    }

    /** Convenience: return a Set of existing backup filenames in Drive folder. */
    public java.util.Set<String> listBackupFileNames() throws IOException {
        return listBackupFiles().stream()
                .map(File::getName)
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
    }

    /** Download all backup files to the destination directory. */
    public void downloadAll(Path destDir, boolean overwrite, Progress progress) throws IOException {
        if (destDir == null) throw new IllegalArgumentException("destDir is null");
        Files.createDirectories(destDir);
        List<File> files = listBackupFiles();
        for (File f : files) {
            downloadSingle(f, destDir, overwrite, progress);
        }
    }

    /** Download only files whose names are in 'namesToFetch' to the destination directory. */
    public void downloadByNames(Set<String> namesToFetch, Path destDir, boolean overwrite, Progress progress) throws IOException {
        if (namesToFetch == null || namesToFetch.isEmpty()) return;
        Files.createDirectories(destDir);
        // Build quick lookup of name -> file (if duplicates, keep the latest modified)
        Map<String, File> latestByName = new HashMap<>();
        for (File f : listBackupFiles()) {
            String name = f.getName();
            if (!namesToFetch.contains(name)) continue;
            File prev = latestByName.get(name);
            if (prev == null) {
                latestByName.put(name, f);
            } else {
                // pick the newer one
                if (f.getModifiedTime() != null && (prev.getModifiedTime() == null
                        || f.getModifiedTime().getValue() > prev.getModifiedTime().getValue())) {
                    latestByName.put(name, f);
                }
            }
        }
        for (File f : latestByName.values()) {
            downloadSingle(f, destDir, overwrite, progress);
        }
    }

    private void downloadSingle(File driveFile, Path destDir, boolean overwrite, Progress progress) throws IOException {
        String name = driveFile.getName();
        long size = driveFile.getSize() == null ? -1L : driveFile.getSize();
        Path outPath = destDir.resolve(name);
        if (!overwrite && Files.exists(outPath)) {
            // skip existing
            if (progress != null) {
                progress.onFileStart(outPath, size);
                progress.onProgress(outPath, size, size);
                progress.onFileDone(outPath, driveFile.getId());
            }
            return;
        }

        if (progress != null) progress.onFileStart(outPath, size);

        try (OutputStream os = Files.newOutputStream(outPath);
             InputStream is = drive.files().get(driveFile.getId()).executeMediaAsInputStream()) {
            byte[] buf = new byte[1024 * 1024]; // 1MB chunks
            long total = 0;
            int r;
            while ((r = is.read(buf)) != -1) {
                os.write(buf, 0, r);
                total += r;
                if (progress != null && size > 0) {
                    progress.onProgress(outPath, total, size);
                }
            }
        }

        if (progress != null) {
            progress.onProgress(outPath, size, size);
            progress.onFileDone(outPath, driveFile.getId());
        }
    }
}
