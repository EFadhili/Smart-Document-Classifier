package org.example.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Hardened StorageManager
 * - getBySha256 returns Map<String,Object>
 * - avoids Java 17-only APIs
 * - safer upsert handling
 */
public class StorageManager {
    private final Path baseDir;
    private final Path inputsDir;
    private final Path extractedDir;
    private final Path summariesDir;
    private final Path dbPath;
    private final String jdbcUrl;

    public StorageManager(String projectRoot) throws Exception {
        this.baseDir = Paths.get(projectRoot).toAbsolutePath();
        this.inputsDir = baseDir.resolve("data").resolve("inputs");
        this.extractedDir = baseDir.resolve("data").resolve("extracted");
        this.summariesDir = baseDir.resolve("data").resolve("summaries");
        this.dbPath = baseDir.resolve("data").resolve("db").resolve("metadata.db");
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toString();

        ensureDirs();
        initDb();
    }

    private void ensureDirs() throws IOException {
        Files.createDirectories(inputsDir);
        Files.createDirectories(extractedDir);
        Files.createDirectories(summariesDir);
        Files.createDirectories(dbPath.getParent());
    }

    private void initDb() throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            try (Statement s = c.createStatement()) {
                s.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS documents (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                "filename TEXT NOT NULL," +
                                "original_path TEXT NOT NULL," +
                                "stored_input_path TEXT NOT NULL," +
                                "extracted_path TEXT," +
                                "summary_path TEXT," +
                                "predicted_label TEXT," +
                                "confidence REAL," +
                                "sha256 TEXT UNIQUE," +
                                "processed INTEGER DEFAULT 0," +
                                "uploaded_at TEXT," +
                                "processed_at TEXT," +
                                "notes TEXT" +
                                ");"
                );
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_documents_sha256 ON documents(sha256);");
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_documents_label ON documents(predicted_label);");
            }
        }
    }

    // compute SHA-256 hex of file bytes (manual hex conversion to avoid HexFormat dependency)
    public static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(f.toPath())) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                md.update(buf, 0, read);
            }
        }
        byte[] digest = md.digest();
        return bytesToHex(digest);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    // store original file into inputs/date/ and return stored path
    public Path storeOriginal(File original) throws Exception {
        String date = java.time.LocalDate.now().toString();
        Path dateDir = inputsDir.resolve(date);
        Files.createDirectories(dateDir);
        Path target = dateDir.resolve(original.getName());
        target = uniquePath(target);
        Files.copy(original.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    // store extracted text (string) in extracted/<label>/filename.txt
    public Path storeExtracted(String label, String filename, String extractedText) throws IOException {
        String safeLabel = label == null || label.trim().isEmpty() ? "unknown" : sanitizeLabel(label);
        Path labelDir = extractedDir.resolve(safeLabel);
        Files.createDirectories(labelDir);
        String safeName = filename + ".txt";
        Path p = labelDir.resolve(safeName);
        p = uniquePath(p);
        Files.writeString(p, extractedText == null ? "" : extractedText, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return p;
    }

    // store summary JSON/text in summaries/<label>/filename.summary.json
    public Path storeSummary(String label, String filename, String jsonContent) throws IOException {
        String safeLabel = label == null || label.trim().isEmpty() ? "unknown" : sanitizeLabel(label);
        Path labelDir = summariesDir.resolve(safeLabel);
        Files.createDirectories(labelDir);
        String safeName = filename + ".summary.json";
        Path p = labelDir.resolve(safeName);
        p = uniquePath(p);
        Files.writeString(p, jsonContent == null ? "" : jsonContent, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return p;
    }

    // store metadata record (insert or update by sha256)
    public void upsertDocumentRecord(Map<String, Object> fields) throws SQLException {
        if (fields == null) throw new IllegalArgumentException("fields map required");
        String sha = toStr(fields.get("sha256"));
        if (sha == null) throw new IllegalArgumentException("sha256 required in fields");

        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            c.setAutoCommit(false);
            String checkSql = "SELECT id FROM documents WHERE sha256 = ?";
            try (PreparedStatement ps = c.prepareStatement(checkSql)) {
                ps.setString(1, sha);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt("id");
                        String updateSql = "UPDATE documents SET filename=?, original_path=?, stored_input_path=?, extracted_path=?, summary_path=?, predicted_label=?, confidence=?, processed=?, processed_at=?, notes=? WHERE id=?";
                        try (PreparedStatement up = c.prepareStatement(updateSql)) {
                            up.setString(1, toStr(fields.get("filename")));
                            up.setString(2, toStr(fields.get("original_path")));
                            up.setString(3, toStr(fields.get("stored_input_path")));
                            up.setString(4, toStr(fields.getOrDefault("extracted_path", null)));
                            up.setString(5, toStr(fields.getOrDefault("summary_path", null)));
                            up.setString(6, toStr(fields.getOrDefault("predicted_label", null)));
                            Double conf = toDouble(fields.get("confidence"));
                            if (conf == null) up.setNull(7, Types.REAL); else up.setDouble(7, conf);
                            Integer processed = toInt(fields.getOrDefault("processed", 0));
                            up.setInt(8, processed);
                            up.setString(9, Instant.now().toString());
                            up.setString(10, toStr(fields.getOrDefault("notes", null)));
                            up.setInt(11, id);
                            up.executeUpdate();
                        }
                    } else {
                        String insertSql = "INSERT INTO documents(filename, original_path, stored_input_path, extracted_path, summary_path, predicted_label, confidence, sha256, processed, uploaded_at, processed_at, notes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
                        try (PreparedStatement ins = c.prepareStatement(insertSql)) {
                            ins.setString(1, toStr(fields.get("filename")));
                            ins.setString(2, toStr(fields.get("original_path")));
                            ins.setString(3, toStr(fields.get("stored_input_path")));
                            ins.setString(4, toStr(fields.getOrDefault("extracted_path", null)));
                            ins.setString(5, toStr(fields.getOrDefault("summary_path", null)));
                            ins.setString(6, toStr(fields.getOrDefault("predicted_label", null)));
                            Double conf = toDouble(fields.get("confidence"));
                            if (conf == null) ins.setNull(7, Types.REAL); else ins.setDouble(7, conf);
                            ins.setString(8, sha);
                            Integer processed = toInt(fields.getOrDefault("processed", 0));
                            ins.setInt(9, processed);
                            String now = Instant.now().toString();
                            ins.setString(10, now);
                            ins.setString(11, now);
                            ins.setString(12, toStr(fields.getOrDefault("notes", null)));
                            ins.executeUpdate();
                        }
                    }
                }
            }
            c.commit();
        }
    }

    // retrieve a doc record by sha256 (returns Map and closes connection)
    public Map<String, Object> getBySha256(String sha256) throws SQLException {
        String q = "SELECT * FROM documents WHERE sha256 = ?";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, sha256);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rowToMap(rs);
            }
        }
    }

    // helper: if path exists, add suffix to avoid overwrite
    private Path uniquePath(Path p) {
        if (!Files.exists(p)) return p;
        String name = p.getFileName().toString();
        Path parent = p.getParent();
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int i = 1;
        Path candidate;
        do {
            candidate = parent.resolve(base + "-" + i + ext);
            i++;
        } while (Files.exists(candidate));
        return candidate;
    }

    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            m.put(md.getColumnName(i), rs.getObject(i));
        }
        return m;
    }

    private String sanitizeLabel(String label) {
        return label.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim();
    }

    private static String toStr(Object o) {
        return o == null ? null : o.toString();
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
    }

    private static Integer toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }
}
