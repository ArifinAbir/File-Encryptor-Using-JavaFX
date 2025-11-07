package com.rfn.fileencryptor.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressionService {

    /**
     * Compress data using GZIP
     * @param data Uncompressed data
     * @return Compressed data
     */
    public byte[] compress(byte[] data) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzos = new GZIPOutputStream(baos)) {

            gzos.write(data);
            gzos.finish();
            return baos.toByteArray();

        } catch (Exception e) {
            System.err.println("Compression error: " + e.getMessage());
            throw new Exception("Compression failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decompress GZIP data
     * @param compressedData Compressed data
     * @return Decompressed data
     */
    public byte[] decompress(byte[] compressedData) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();

        } catch (Exception e) {
            System.err.println("Decompression error: " + e.getMessage());
            throw new Exception("Decompression failed: " + e.getMessage(), e);
        }
    }
}
