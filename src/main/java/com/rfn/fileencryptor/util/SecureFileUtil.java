package com.rfn.fileencryptor.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.SecureRandom;

import com.rfn.fileencryptor.config.ConfigManager;

public class SecureFileUtil {

    /**
     * Read entire file into byte array
     */
    public static byte[] readFile(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    /**
     * Write byte array to file
     */
    public static void writeFile(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
            fos.flush();
        }
    }

    /**
     * Securely delete file by overwriting before deletion
     */
    public static boolean secureDelete(File file) {
        try {
            if (!file.exists()) {
                return false;
            }

            // Fast delete mode: just delete without overwrite
            String mode = ConfigManager.getDeleteMode();
            if ("fast".equalsIgnoreCase(mode)) {
                return file.delete();
            }

            long fileSize = file.length();
            int passes = ConfigManager.getDeletePasses();
            int bufSize = Math.max(64 * 1024, ConfigManager.getStreamBufferSizeBytes());

            // Overwrite file efficiently in large blocks
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                byte[] zeroBuf = new byte[bufSize]; // zero-filled
                byte[] oneBuf = new byte[bufSize];
                java.util.Arrays.fill(oneBuf, (byte) 0xFF);
                byte[] randomBuf = new byte[bufSize];
                SecureRandom sr = new SecureRandom();

                for (int pass = 0; pass < passes; pass++) {
                    raf.seek(0);
                    long remaining = fileSize;
                    while (remaining > 0) {
                        int len = (int) Math.min(bufSize, remaining);
                        if (pass == 0) {
                            raf.write(zeroBuf, 0, len);
                        } else if (pass == 1) {
                            raf.write(oneBuf, 0, len);
                        } else {
                            sr.nextBytes(randomBuf);
                            raf.write(randomBuf, 0, len);
                        }
                        remaining -= len;
                    }
                }
            }

            // Finally delete the file
            return file.delete();

        } catch (Exception e) {
            System.err.println("Secure delete failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if file exists
     */
    public static boolean fileExists(String path) {
        return new File(path).exists();
    }

    /**
     * Create directory if not exists
     */
    public static void createDirectoryIfNotExists(String path) throws IOException {
        File dir = new File(path);
        if (!dir.exists()) {
            Files.createDirectories(dir.toPath());
        }
    }
}
