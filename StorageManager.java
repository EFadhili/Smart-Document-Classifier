package org.example.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;



/**
 * StorageManager (user-aware, consistent paths)
 * data/
 *   Users/<ownerId>/inputs/<YYYY-MM-DD>/<file>
 *   Users/<ownerId>/extracted/<label>/<file>.txt
 *   Users/<ownerId>/summaries/<label>/<file>.summary.json
 *   db/metadata.db
 */
public class StorageManager {
    private final Path baseDir;
    private final Path usersRoot;
    private final Path inputsDir;// data/Users
    private final Path dbPath;      // data/db/metadata.db
    private final String jdbcUrl;
    private final Path extractedDir;
    private final Path summariesDir;

    public StorageManager(String projectRoot) throws Exception {
        this.baseDir = Paths.get(projectRoot).toAbsolutePath();
        this.extractedDir = baseDir.resolve("data").resolve("extracted");
        this.summariesDir = baseDir.resolve("data").resolve("summaries");
        this.inputsDir = baseDir.resolve("data").resolve("inputs");
        this.usersRoot = baseDir.resolve("data").resolve("Users");
        this.dbPath = baseDir.resolve("data").resolve("db").resolve("metadata.db");
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;

        ensureDirs();
        initDb();
    }

    public String getJdbcUrl() {
        return this.jdbcUrl;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }


    private void ensureDirs() throws IOException {
        Files.createDirectories(usersRoot);          // data/Users
        Files.createDirectories(inputsDir);
        Files.createDirectories(extractedDir);
        Files.createDirectories(summariesDir);
        Files.createDirectories(dbPath.getParent());
    }

    private void initDb() throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement s = c.createStatement()) {

            // Your existing documents table
            s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS documents (\n" +
                            "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                            "  filename TEXT NOT NULL,\n" +
                            "  original_path TEXT NOT NULL,\n" +
                            "  stored_input_path TEXT NOT NULL,\n" +
                            "  extracted_path TEXT,\n" +
                            "  summary_path TEXT,\n" +
                            "  predicted_label TEXT,\n" +
                            "  confidence REAL,\n" +
                            "  sha256 TEXT UNIQUE,\n" +
                            "  processed INTEGER DEFAULT 0,\n" +
                            "  uploaded_at TEXT,\n" +
                            "  processed_at TEXT,\n" +
                            "  notes TEXT,\n" +
                            "  owner_id TEXT\n" +
                            ");"
            );

            // NEW: User credits and account status table
            s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS user_credits (\n" +
                            "  user_id TEXT PRIMARY KEY,\n" +
                            "  credits_balance INTEGER DEFAULT 100,\n" +
                    "  is_suspended BOOLEAN DEFAULT 0,\n" +
                            "  suspension_reason TEXT,\n" +
                            "  suspended_at TEXT,\n" +
                            "  created_at TEXT DEFAULT CURRENT_TIMESTAMP,\n" +
                            "  last_updated TEXT DEFAULT CURRENT_TIMESTAMP\n" +
                            ");"
            );

            // NEW: Credit transactions log
            s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS credit_transactions (\n" +
                            "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                            "  user_id TEXT,\n" +
                            "  user_name TEXT NOT NULL,\n" +
                            "  amount INTEGER,\n" +
            "  transaction_type TEXT,\n" +
            "  description TEXT,\n" +
                    "  document_id INTEGER,\n" +
            "  created_at TEXT DEFAULT CURRENT_TIMESTAMP,\n" +
                    "  FOREIGN KEY (user_id) REFERENCES user_credits(user_id)\n" +
                    ");"
        );

            // NEW: Admin users table
            s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS admin_users (\n" +
                            "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                            "  email TEXT UNIQUE,\n" +
                            "  password_hash TEXT,\n" +
                            "  role TEXT DEFAULT 'admin',\n" +
                    "  is_active BOOLEAN DEFAULT 1,\n" +
                            "  created_at TEXT DEFAULT CURRENT_TIMESTAMP\n" +
                            ");"
            );

            // Create indexes
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_documents_owner ON documents(owner_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_documents_sha256 ON documents(sha256)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_documents_label ON documents(predicted_label)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_credit_transactions_user ON credit_transactions(user_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_credit_transactions_type ON credit_transactions(transaction_type)");

            // Insert default admin user (password: admin123)
            s.executeUpdate(
                    "INSERT OR IGNORE INTO admin_users (email, password_hash, role) VALUES " +
                            "('elton.mumalasi@strathmore.edu', 'admin123', 'super_admin')"
            );
            try {
                // Add user_name column to credit_transactions if it doesn't exist
                s.executeUpdate("ALTER TABLE credit_transactions ADD COLUMN user_name TEXT");

                // Update existing records with user names
                s.executeUpdate("UPDATE credit_transactions SET user_name = " +
                        "SUBSTR(user_id, 1, INSTR(user_id, '@') - 1) " +
                        "WHERE user_name IS NULL AND user_id LIKE '%@%'");

                s.executeUpdate("UPDATE credit_transactions SET user_name = user_id " +
                        "WHERE user_name IS NULL");
            } catch (SQLException e) {
                // Column might already exist, ignore the error
                System.out.println("Note: user_name column setup completed (might already exist)");
            }
        }
    }

    public int getTotalUserCount() throws SQLException {
        String sql = "SELECT COUNT(DISTINCT owner_id) FROM documents";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int getActiveUserCount() throws SQLException {
        String sql = "SELECT COUNT(DISTINCT owner_id) FROM documents WHERE processed = 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int getTotalDocumentCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM documents";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int getProcessedDocumentCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM documents WHERE processed = 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public long getTotalStorageUsed() throws SQLException {
        return 0L;
    }
    public List<Map<String, Object>> getAllUsersWithStats() throws SQLException {
        String sql = """
            SELECT 
                owner_id as user_id,
                owner_id as email,
                COUNT(*) as document_count,
                MAX(created_at) as last_active
            FROM documents 
            GROUP BY owner_id 
            ORDER BY document_count DESC
            """;

        List<Map<String, Object>> users = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", rs.getString("user_id"));
                user.put("email", rs.getString("email"));
                user.put("document_count", rs.getInt("document_count"));
                user.put("last_active", rs.getTimestamp("last_active"));
                users.add(user);
            }
        }
        return users;
    }

    public List<Map<String, Object>> getAllDocuments() throws SQLException {
        String sql = """
            SELECT 
                id as doc_id, filename, owner_id, predicted_label, 
                confidence, processed_at as processed_date
            FROM documents 
            WHERE processed = 1
            ORDER BY processed_at DESC
            """;

        List<Map<String, Object>> documents = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("doc_id", rs.getString("doc_id"));
                doc.put("filename", rs.getString("filename"));
                doc.put("owner_id", rs.getString("owner_id"));
                doc.put("predicted_label", rs.getString("predicted_label"));
                doc.put("confidence", rs.getDouble("confidence"));

                // Handle the timestamp parsing manually
                String dateString = rs.getString("processed_date");
                if (dateString != null) {
                    try {
                        // Parse the ISO timestamp manually
                        java.time.Instant instant = java.time.Instant.parse(dateString);
                        java.sql.Timestamp timestamp = java.sql.Timestamp.from(instant);
                        doc.put("processed_date", timestamp);
                    } catch (Exception e) {
                        // If parsing fails, just use the string
                        doc.put("processed_date", dateString);
                    }
                } else {
                    doc.put("processed_date", null);
                }

                documents.add(doc);
            }
        }
        return documents;
    }


    // ===== Path helpers =====
    private static String safeOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) return "anonymous";
        return ownerId.replaceAll("[^a-zA-Z0-9_.@-]", "_");
    }
    private static String safeLabel(String label) {
        if (label == null || label.isBlank()) return "unknown";
        return label.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim();
    }
    private Path userRoot(String ownerId) {
        return usersRoot.resolve(safeOwner(ownerId));
    }
    private Path inputsDir(String ownerId, String date) {
        return userRoot(ownerId).resolve("inputs").resolve(date);
    }
    private Path extractedDir(String ownerId, String label) {
        return userRoot(ownerId).resolve("extracted").resolve(safeLabel(label));
    }
    private Path summariesDir(String ownerId, String label) {
        return userRoot(ownerId).resolve("summaries").resolve(safeLabel(label));
    }

    // ===== SHA-256 =====
    public static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(f.toPath())) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) md.update(buf, 0, read);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    // ===== Store originals =====
    /** Store original under data/Users/<owner>/inputs/<YYYY-MM-DD>/ */
    public Path storeOriginalForUser(File original, String ownerId) throws Exception {
        String date = java.time.LocalDate.now().toString();
        String owner = (ownerId == null || ownerId.isBlank()) ? "anonymous" : ownerId;
        Path dateDir = inputsDir.resolve(owner).resolve("Unlabeled").resolve(date);
        Files.createDirectories(dateDir);
        Path target = uniquePath(dateDir.resolve(original.getName()));
        Files.copy(original.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /** Back-compat shim: anonymous owner */
    public Path storeOriginal(File original) throws Exception {
        return storeOriginalForUser(original, "anonymous");
    }
    public Path storeOriginalForUserUnlabeled(File original, String ownerId) throws Exception {
        String date = java.time.LocalDate.now().toString();
        Path targetDir = baseDir.resolve("data")
                .resolve("inputs")
                .resolve(ownerId == null ? "anonymous" : ownerId)
                .resolve("Unlabeled")
                .resolve(date);
        Files.createDirectories(targetDir);
        Path target = uniquePath(targetDir.resolve(original.getName()));
        Files.copy(original.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }



    // ===== Store extracted & summaries (user-aware) =====
    /** data/Users/<owner>/extracted/<label>/<filename>.txt */
    public Path storeExtracted(String ownerId, String label, String filename, String extractedText) throws IOException {
        String date = java.time.LocalDate.now().toString();
        String owner = (ownerId == null || ownerId.isBlank()) ? "anonymous" : ownerId;
        String safeLabel = sanitizeLabel(label);
        Path dir = extractedDir.resolve(owner).resolve(safeLabel).resolve(date);
        Files.createDirectories(dir);
        Path p = uniquePath(dir.resolve(filename + ".txt"));
        Files.writeString(p, extractedText == null ? "" : extractedText, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return p;
    }

    /** data/Users/<owner>/summaries/<label>/<filename>.summary.json */
    // summaries/<owner>/<label>/<date>/<filename>.summary.json
    /** Store summary as a readable .txt file under summaries/<label>/filename_summary.txt */
    public Path storeSummary(String ownerId, String label, String filename, String summaryText) throws IOException {
        String safeLabel = (label == null || label.trim().isEmpty()) ? "unknown" : sanitizeLabel(label);
        String date = java.time.LocalDate.now().toString();

        Path labelDir = summariesDir
                .resolve(ownerId == null ? "anonymous" : ownerId)
                .resolve(safeLabel)
                .resolve(date);
        Files.createDirectories(labelDir);

        String base = filename;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);

        Path p = uniquePath(labelDir.resolve(base + "_summary.txt"));
        Files.writeString(p, summaryText == null ? "" : summaryText, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return p;
    }



    // ===== Optional: move original into a labeled subfolder under inputs (after classification) =====
    public Path moveOriginalToLabel(String storedInputPath, String ownerId, String label) throws Exception {
        if (storedInputPath == null || label == null) return storedInputPath == null ? null : Paths.get(storedInputPath);
        Path src = Paths.get(storedInputPath);
        if (!Files.exists(src)) return src;

        String date = java.time.LocalDate.now().toString();
        String owner = (ownerId == null || ownerId.isBlank()) ? "anonymous" : ownerId;
        String safeLabel = sanitizeLabel(label);

        Path targetDir = inputsDir.resolve(owner).resolve(safeLabel).resolve(date);
        Files.createDirectories(targetDir);
        Path dst = uniquePath(targetDir.resolve(src.getFileName()));
        Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE documents SET stored_input_path=?, predicted_label=? WHERE stored_input_path=?")) {
            ps.setString(1, dst.toString());
            ps.setString(2, safeLabel);
            ps.setString(3, storedInputPath);
            ps.executeUpdate();
        }
        return dst;
    }
    // ===== DB upsert & queries =====
    public void upsertDocumentRecord(Map<String, Object> fields) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            c.setAutoCommit(false);
            String checkSql = "SELECT id FROM documents WHERE sha256 = ?";
            try (PreparedStatement ps = c.prepareStatement(checkSql)) {
                ps.setString(1, toStr(fields.get("sha256")));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt("id");
                        String updateSql =
                                "UPDATE documents SET filename=?, original_path=?, stored_input_path=?, extracted_path=?, summary_path=?, " +
                                        "predicted_label=?, confidence=?, processed=?, processed_at=?, notes=?, owner_id=? WHERE id=?";
                        try (PreparedStatement up = c.prepareStatement(updateSql)) {
                            up.setString(1, toStr(fields.get("filename")));
                            up.setString(2, toStr(fields.get("original_path")));
                            up.setString(3, toStr(fields.get("stored_input_path")));
                            up.setString(4, toStr(fields.get("extracted_path")));
                            up.setString(5, toStr(fields.get("summary_path")));
                            up.setString(6, toStr(fields.get("predicted_label")));
                            Double conf = toDouble(fields.get("confidence"));
                            if (conf == null) up.setNull(7, Types.REAL); else up.setDouble(7, conf);
                            up.setInt(8, toInt(fields.get("processed")));
                            up.setString(9, Instant.now().toString());
                            up.setString(10, toStr(fields.get("notes")));
                            up.setString(11, toStr(fields.get("owner_id")));
                            up.setInt(12, id);
                            up.executeUpdate();
                        }
                    } else {
                        String insertSql =
                                "INSERT INTO documents(filename, original_path, stored_input_path, extracted_path, summary_path, " +
                                        "predicted_label, confidence, sha256, processed, uploaded_at, processed_at, notes, owner_id) " +
                                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
                        try (PreparedStatement ins = c.prepareStatement(insertSql)) {
                            ins.setString(1, toStr(fields.get("filename")));
                            ins.setString(2, toStr(fields.get("original_path")));
                            ins.setString(3, toStr(fields.get("stored_input_path")));
                            ins.setString(4, toStr(fields.get("extracted_path")));
                            ins.setString(5, toStr(fields.get("summary_path")));
                            ins.setString(6, toStr(fields.get("predicted_label")));
                            Double conf = toDouble(fields.get("confidence"));
                            if (conf == null) ins.setNull(7, Types.REAL); else ins.setDouble(7, conf);
                            ins.setString(8, toStr(fields.get("sha256")));
                            ins.setInt(9, toInt(fields.get("processed")));
                            ins.setString(10, Instant.now().toString());
                            ins.setString(11, Instant.now().toString());
                            ins.setString(12, toStr(fields.get("notes")));
                            ins.setString(13, toStr(fields.get("owner_id")));
                            ins.executeUpdate();
                        }
                    }
                }
            }
            c.commit();
        }
    }

    public Map<String, Object> getBySha256(String sha256) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement("SELECT * FROM documents WHERE sha256 = ?")) {
            ps.setString(1, sha256);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rowToMap(rs);
            }
        }
    }

    public ResultSet listByOwner(String ownerId) throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        if (ownerId == null) {
            PreparedStatement ps = c.prepareStatement("SELECT * FROM documents ORDER BY uploaded_at DESC");
            return ps.executeQuery();
        } else {
            PreparedStatement ps = c.prepareStatement("SELECT * FROM documents WHERE owner_id = ? ORDER BY uploaded_at DESC");
            ps.setString(1, ownerId);
            return ps.executeQuery();
        }
    }

    // In StorageManager.java
    public java.util.List<Path> listUnprocessedByOwner(String ownerId) throws SQLException {
        String sql = (ownerId == null)
                ? "SELECT stored_input_path FROM documents WHERE processed = 0 OR processed IS NULL ORDER BY uploaded_at DESC"
                : "SELECT stored_input_path FROM documents WHERE owner_id = ? AND (processed = 0 OR processed IS NULL) ORDER BY uploaded_at DESC";

        java.util.List<Path> out = new java.util.ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (ownerId != null) ps.setString(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String p = rs.getString(1);
                    if (p != null && !p.isBlank()) out.add(Paths.get(p));
                }
            }
        }
        return out;
    }

    public List<Path> listStoredInputsByOwner(String ownerId) throws SQLException {
        List<Path> out = new ArrayList<>();
        String sql = (ownerId == null)
                ? "SELECT stored_input_path FROM documents ORDER BY uploaded_at DESC"
                : "SELECT stored_input_path FROM documents WHERE owner_id = ? ORDER BY uploaded_at DESC";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (ownerId != null) ps.setString(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String p = rs.getString(1);
                    if (p != null && !p.isBlank()) out.add(Paths.get(p));
                }
            }
        }
        return out;
    }

    public void updateNotesByPath(String storedInputPath, String notes) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement("UPDATE documents SET notes=? WHERE stored_input_path=?")) {
            ps.setString(1, notes);
            ps.setString(2, storedInputPath);
            ps.executeUpdate();
        }
    }

    public void renameFile(Path oldPath, String newName) throws SQLException, IOException {
        Path newPath = oldPath.resolveSibling(newName);
        Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE documents SET filename=?, stored_input_path=? WHERE stored_input_path=?")) {
            ps.setString(1, newName);
            ps.setString(2, newPath.toString());
            ps.setString(3, oldPath.toString());
            ps.executeUpdate();
        }
    }

    public void deleteByPath(String storedInputPath) throws SQLException, IOException {
        String sel = "SELECT extracted_path, summary_path FROM documents WHERE stored_input_path=?";
        String del = "DELETE FROM documents WHERE stored_input_path=?";
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            String extractedPath = null, summaryPath = null;
            try (PreparedStatement ps = c.prepareStatement(sel)) {
                ps.setString(1, storedInputPath);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        extractedPath = toStr(rs.getObject("extracted_path"));
                        summaryPath = toStr(rs.getObject("summary_path"));
                    }
                }
            }
            safeDelete(extractedPath);
            safeDelete(summaryPath);
            safeDelete(storedInputPath);
            try (PreparedStatement ps2 = c.prepareStatement(del)) {
                ps2.setString(1, storedInputPath);
                ps2.executeUpdate();
            }
        }
    }

    public void clearDatabase() throws SQLException, IOException {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM documents");
        }
        // wipe only under data/Users (keep db structure)
        deleteDirectory(usersRoot);
        ensureDirs();
    }

    // ===== helpers =====
    private void safeDelete(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) return;
        try { Files.deleteIfExists(Paths.get(pathStr)); } catch (Exception ignored) {}
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    private Path uniquePath(Path p) {
        if (!Files.exists(p)) return p;
        String name = p.getFileName().toString();
        Path parent = p.getParent();
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        int i = 1;
        Path candidate;
        do { candidate = parent.resolve(base + "-" + i + ext); i++; }
        while (Files.exists(candidate));
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

    private static String toStr(Object o) { return o == null ? null : o.toString(); }
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

    // Optional getters
    public Path getBaseDir() { return baseDir; }
    public Path getDbPath() { return dbPath; }
    // Backward-compatible overloads (default owner = "anonymous")
    public Path storeExtracted(String label, String filename, String text) throws IOException {
        return storeExtracted("anonymous", label, filename, text);
    }

    public Path storeSummary(String label, String filename, String json) throws IOException {
        return storeSummary("anonymous", label, filename, json);
    }
    private String sanitizeLabel(String label) {
        if (label == null) return "unknown";
        return label.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim();
    }

    // Try to find the current stored_input_path by sha256
    public String findStoredInputPathBySha(String sha256) throws SQLException {
        String q = "SELECT stored_input_path FROM documents WHERE sha256=? ORDER BY processed_at DESC, uploaded_at DESC LIMIT 1";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, sha256);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // Find the current stored_input_path for this owner + filename
    public String findStoredInputPathByOwnerAndFilename(String ownerId, String filename) throws SQLException {
        String owner = (ownerId == null || ownerId.isBlank()) ? "anonymous" : ownerId;
        String sql = "SELECT stored_input_path FROM documents " +
                "WHERE owner_id=? AND filename=? " +
                "ORDER BY processed_at DESC, uploaded_at DESC LIMIT 1";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, filename);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // Optional: get sha256 already stored for this path (if you ever need it)
    public String getShaByStoredPath(String storedInputPath) throws SQLException {
        String sql = "SELECT sha256 FROM documents WHERE stored_input_path=? " +
                "ORDER BY processed_at DESC, uploaded_at DESC LIMIT 1";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, storedInputPath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // Try to find a stored input by owner + filename anywhere under data/inputs/<owner>
    public Path findStoredInput(String ownerId, String filename) throws IOException {
        Path root = baseDir.resolve("data").resolve("inputs").resolve(ownerId == null ? "anonymous" : ownerId);
        if (!Files.exists(root)) return null;
        try (var walk = Files.walk(root)) {
            return walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equalsIgnoreCase(filename))
                    .findFirst().orElse(null);
        }
    }

    // If 'supposed' doesn't exist, try to locate an existing copy by filename; update DB path.
    public Path ensureStoredInputExists(String ownerId, Path supposed, String filename) throws Exception {
        if (supposed != null && Files.exists(supposed)) return supposed;
        Path found = findStoredInput(ownerId, filename);
        if (found != null) {
            try (Connection c = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement ps = c.prepareStatement("UPDATE documents SET stored_input_path=?, original_path=? WHERE filename=? AND owner_id=?")) {
                ps.setString(1, found.toString());
                ps.setString(2, found.toString());
                ps.setString(3, filename);
                ps.setString(4, ownerId);
                ps.executeUpdate();
            }
        }
        return found;
    }

    // Move original from current location into inputs/<owner>/<label>/<date>/filename and update DB.
    public Path moveOriginalToLabel(Path currentPath, String ownerId, String label) throws Exception {
        if (currentPath == null || !Files.exists(currentPath)) return currentPath;
        String date = java.time.LocalDate.now().toString();
        String safeLabel = label == null || label.isBlank() ? "Unlabeled" : label.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim();
        Path targetDir = baseDir.resolve("data").resolve("inputs")
                .resolve(ownerId == null ? "anonymous" : ownerId)
                .resolve(safeLabel)
                .resolve(date);
        Files.createDirectories(targetDir);
        Path dst = uniquePath(targetDir.resolve(currentPath.getFileName()));
        Files.move(currentPath, dst, StandardCopyOption.REPLACE_EXISTING);

        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement("UPDATE documents SET stored_input_path=?, original_path=?, predicted_label=? WHERE stored_input_path=?")) {
            ps.setString(1, dst.toString());
            ps.setString(2, dst.toString());
            ps.setString(3, safeLabel);
            ps.setString(4, currentPath.toString());
            ps.executeUpdate();
        }
        return dst;
    }

    // --- STORAGE BROWSER HELPERS ---

    /** New: list extracted_path for a user. */
    public java.util.List<Path> listExtractedByOwner(String ownerId) throws SQLException {
        java.util.List<Path> out = new java.util.ArrayList<>();
        String sql = (ownerId == null)
                ? "SELECT extracted_path FROM documents WHERE extracted_path IS NOT NULL ORDER BY uploaded_at DESC"
                : "SELECT extracted_path FROM documents WHERE owner_id = ? AND extracted_path IS NOT NULL ORDER BY uploaded_at DESC";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (ownerId != null) ps.setString(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String p = rs.getString(1);
                    if (p != null && !p.isBlank()) out.add(Paths.get(p));
                }
            }
        }
        return out;
    }

    /** New: list summary_path for a user. */
    public java.util.List<Path> listSummariesByOwner(String ownerId) throws SQLException {
        java.util.List<Path> out = new java.util.ArrayList<>();
        String sql = (ownerId == null)
                ? "SELECT summary_path FROM documents WHERE summary_path IS NOT NULL ORDER BY uploaded_at DESC"
                : "SELECT summary_path FROM documents WHERE owner_id = ? AND summary_path IS NOT NULL ORDER BY uploaded_at DESC";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (ownerId != null) ps.setString(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String p = rs.getString(1);
                    if (p != null && !p.isBlank()) out.add(Paths.get(p));
                }
            }
        }
        return out;
    }

    // --- Search for Database table (rows) ---
    public ResultSet searchDocumentsByOwner(String ownerId, String query) throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        String like = "%" + (query == null ? "" : query.trim()) + "%";
        String sqlAll =
                "SELECT * FROM documents " +
                        "WHERE (filename LIKE ? OR IFNULL(predicted_label,'') LIKE ? OR IFNULL(notes,'') LIKE ?) " +
                        "ORDER BY uploaded_at DESC";
        String sqlByOwner =
                "SELECT * FROM documents " +
                        "WHERE owner_id = ? AND (filename LIKE ? OR IFNULL(predicted_label,'') LIKE ? OR IFNULL(notes,'') LIKE ?) " +
                        "ORDER BY uploaded_at DESC";

        PreparedStatement ps;
        if (ownerId == null) {
            ps = c.prepareStatement(sqlAll);
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
        } else {
            ps = c.prepareStatement(sqlByOwner);
            ps.setString(1, ownerId);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
        }
        return ps.executeQuery(); // caller reads and lets conn close when result set is closed by GC
    }

    // --- Search for Storage tabs (paths) ---
    public java.util.List<Path> searchStoredInputsByOwner(String ownerId, String query) throws SQLException {
        String like = "%" + (query == null ? "" : query.trim()) + "%";
        String sqlAll =
                "SELECT stored_input_path FROM documents " +
                        "WHERE stored_input_path IS NOT NULL AND (filename LIKE ? OR IFNULL(predicted_label,'') LIKE ? OR IFNULL(notes,'') LIKE ?) " +
                        "ORDER BY uploaded_at DESC";
        String sqlByOwner =
                "SELECT stored_input_path FROM documents " +
                        "WHERE owner_id = ? AND stored_input_path IS NOT NULL AND (filename LIKE ? OR IFNULL(predicted_label,'') LIKE ? OR IFNULL(notes,'') LIKE ?) " +
                        "ORDER BY uploaded_at DESC";
        return selectPathColumn(ownerId, like, sqlAll, sqlByOwner);
    }

    public java.util.List<Path> searchExtractedByOwner(String ownerId, String query) throws SQLException {
        String like = "%" + (query == null ? "" : query.trim()) + "%";
        String sqlAll =
                "SELECT extracted_path FROM documents " +
                        "WHERE extracted_path IS NOT NULL AND (filename LIKE ? OR IFNULL(predicted_label,'') LIKE ? OR IFNULL(notes,'') LIKE ?) " +
                        "ORDER BY uploaded_at DESC";
        String sqlByOwner =
                "SELECT extracted_path FROM documents " +
                        "WHERE owner_id = ? AND extracted_path IS NOT NULL AND (filename LIKE ? OR IFNULL(predicted_label,'') LIKE ? OR IFNULL(notes,'') LIKE ?) " +
                        "ORDER BY uploaded_at DESC";
        return selectPathColumn(ownerId, like, sqlAll, sqlByOwner);
    }

    public java.util.List<Path> searchSummariesByOwner(String ownerId, String query) throws SQLException {
        String like = "%" + (query == null ? "" : query.trim()) + "%";
        String sqlAll =
                "SELECT summary_path FROM documents " +
                        "WHERE summary_path IS NOT NULL AND (filename LIKE ? OR IFNULL(predicted_label,'') LIKE ? OR IFNULL(notes,'') LIKE ?) " +
                        "ORDER BY uploaded_at DESC";
        String sqlByOwner =
                "SELECT summary_path FROM documents " +
                        "WHERE owner_id = ? AND summary_path IS NOT NULL AND (filename LIKE ? OR IFNULL(predicted_label,'') LIKE ? OR IFNULL(notes,'') LIKE ?) " +
                        "ORDER BY uploaded_at DESC";
        return selectPathColumn(ownerId, like, sqlAll, sqlByOwner);
    }

    // Small helper used by the three search* path methods above
    private java.util.List<Path> selectPathColumn(String ownerId, String like,
                                                  String sqlAll, String sqlByOwner) throws SQLException {
        java.util.List<Path> out = new java.util.ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = (ownerId == null)
                     ? c.prepareStatement(sqlAll)
                     : c.prepareStatement(sqlByOwner)) {
            int i = 1;
            if (ownerId != null) ps.setString(i++, ownerId);
            ps.setString(i++, like);
            ps.setString(i++, like);
            ps.setString(i, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String p = rs.getString(1);
                    if (p != null && !p.isBlank()) out.add(Paths.get(p));
                }
            }
        }
        return out;
    }

    /**
     * Format ISO timestamp to user-friendly date/time
     */
    public static String formatUserFriendlyDateTime(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.trim().isEmpty()) {
            return "N/A";
        }

        try {
            java.time.Instant instant = java.time.Instant.parse(isoTimestamp);
            java.time.LocalDateTime localDateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());

            // Format: "Nov 24, 2025 10:07 AM"
            return localDateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a"));
        } catch (Exception e) {
            // If parsing fails, return the original string or a simplified version
            try {
                // Try to extract just the date part
                return isoTimestamp.substring(0, 10); // Returns "2025-11-24"
            } catch (Exception ex) {
                return isoTimestamp;
            }
        }
    }

    /**
     * Format file size to human-readable format (KB, MB, GB)
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";

        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));

        // Limit to available units
        digitGroups = Math.min(digitGroups, units.length - 1);

        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }



}