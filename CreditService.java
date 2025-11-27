package org.example.storage;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.apache.commons.lang3.SystemUtils.getUserName;

public class CreditService {
    private final String jdbcUrl;

    public CreditService(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    // === USER CREDIT MANAGEMENT ===

    /**
     * Get user's current credit balance and account status
     */
    public UserCreditInfo getUserCreditInfo(String userId) throws SQLException {
        String sql = "SELECT credits_balance, is_suspended, suspension_reason, suspended_at " +
                "FROM user_credits WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new UserCreditInfo(
                            userId,
                            rs.getInt("credits_balance"),
                            rs.getBoolean("is_suspended"),
                            rs.getString("suspension_reason"),
                            rs.getString("suspended_at")
                    );
                } else {
                    // User doesn't exist in credits table, create with default 100 credits
                    createUserCreditAccount(userId);
                    return new UserCreditInfo(userId, 100, false, null, null);
                }
            }
        }
    }

    /**
     * Create a new credit account for user with 100 free credits
     */
    private void createUserCreditAccount(String userId) throws SQLException {
        String sql = "INSERT INTO user_credits (user_id, credits_balance) VALUES (?, 100)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.executeUpdate();
        }

        // Log the initial free credits
        logTransaction(userId, 100, "initial_credits", "Initial 100 free credits");
    }

    /**
     * OPTION 2: One-click free top-up - adds 100 credits
     */
    public boolean addFreeCredits(String userId) throws SQLException {
        String sql = "UPDATE user_credits SET credits_balance = credits_balance + 100, " +
                "last_updated = ? WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, LocalDateTime.now().toString());
            stmt.setString(2, userId);
            int affected = stmt.executeUpdate();

            if (affected > 0) {
                logTransaction(userId, 100, "free_topup", "User clicked free top-up button");
                return true;
            }
            return false;
        }
    }

    /**
     * OPTION 1: Admin manually adds credits to user
     */
    public boolean adminAddCredits(String adminUserId, String targetUserId, int amount, String reason) throws SQLException {
        String sql = "UPDATE user_credits SET credits_balance = credits_balance + ?, " +
                "last_updated = ? WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, amount);
            stmt.setString(2, LocalDateTime.now().toString());
            stmt.setString(3, targetUserId);
            int affected = stmt.executeUpdate();

            if (affected > 0) {
                logTransaction(targetUserId, amount, "admin_add",
                        "Admin " + adminUserId + ": " + reason);
                return true;
            }
            return false;
        }
    }

    /**
     * Deduct credits for service usage
     */
    public boolean deductCredits(String userId, int amount, String service, String documentId) throws SQLException {
        // First check if user has enough credits and is not suspended
        UserCreditInfo userInfo = getUserCreditInfo(userId);

        if (userInfo.isSuspended()) {
            throw new CreditException("Account suspended: " + userInfo.getSuspensionReason());
        }

        if (userInfo.getCreditsBalance() < amount) {
            throw new CreditException("Insufficient credits. Available: " +
                    userInfo.getCreditsBalance() + ", Required: " + amount);
        }

        String sql = "UPDATE user_credits SET credits_balance = credits_balance - ?, " +
                "last_updated = ? WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, amount);
            stmt.setString(2, LocalDateTime.now().toString());
            stmt.setString(3, userId);
            int affected = stmt.executeUpdate();

            if (affected > 0) {
                logTransaction(userId, -amount, "usage", service + " - Document: " + documentId);
                return true;
            }
            return false;
        }
    }

    /**
     * Calculate credits needed for a document processing operation
     */
    public int calculateProcessingCredits(int pageCount, int wordCount) {
        int credits = 0;
        credits += pageCount * 10;           // 10 credits per page for OCR
        credits += wordCount / 100;          // 1 credit per 100 words for AI
        credits += 5;                        // Fixed cost for classification
        return Math.max(credits, 1);         // Minimum 1 credit
    }

    // === ADMIN ACCOUNT MANAGEMENT ===

    /**
     * Suspend a user account
     */
    public boolean suspendUser(String adminUserId, String targetUserId, String reason) throws SQLException {
        String sql = "UPDATE user_credits SET is_suspended = 1, suspension_reason = ?, " +
                "suspended_at = ? WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reason);
            stmt.setString(2, LocalDateTime.now().toString());
            stmt.setString(3, targetUserId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Unsuspend a user account
     */
    public boolean unsuspendUser(String adminUserId, String targetUserId) throws SQLException {
        String sql = "UPDATE user_credits SET is_suspended = 0, suspension_reason = NULL, " +
                "suspended_at = NULL WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetUserId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Get all users for admin dashboard
     */
    public List<UserCreditInfo> getAllUsers() throws SQLException {
        String sql = "SELECT user_id, credits_balance, is_suspended, suspension_reason, suspended_at " +
                "FROM user_credits ORDER BY last_updated DESC";

        List<UserCreditInfo> users = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                users.add(new UserCreditInfo(
                        rs.getString("user_id"),
                        rs.getInt("credits_balance"),
                        rs.getBoolean("is_suspended"),
                        rs.getString("suspension_reason"),
                        rs.getString("suspended_at")
                ));
            }
        }
        return users;
    }

    /**
     * Get user's transaction history
     */
    public List<CreditTransaction> getUserTransactions(String userId, int limit) throws SQLException {

        printTableStructure();

        String sql = "SELECT " +
                "id, user_id, user_name, amount, transaction_type, description, document_id, created_at " +
                "FROM credit_transactions WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";

        List<CreditTransaction> transactions = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(new CreditTransaction(
                            rs.getInt("id"),  // Add ID
                            rs.getString("user_id"),
                            rs.getInt("amount"),
                            rs.getString("transaction_type"),
                            rs.getString("description"),
                            rs.getString("created_at"),
                            rs.getString("user_name"),
                            rs.getInt("document_id")
                    ));
                }
            }
        }
        return transactions;
    }

    // === ADMIN AUTHENTICATION ===

    /**
     * Validate admin login
     */
    public boolean validateAdminLogin(String email, String password) throws SQLException {
        String sql = "SELECT password_hash FROM admin_users WHERE email = ? AND is_active = 1";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    // Simple password check - in production use proper hashing
                    return storedHash.equals(password);
                }
            }
        }
        return false;
    }

    public boolean isAdmin(String email) throws SQLException {
        String sql = "SELECT 1 FROM admin_users WHERE email = ? AND is_active = 1";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // === PRIVATE HELPER METHODS ===

    private void logTransaction(String userId, int amount, String type, String description) throws SQLException {
        String sql = "INSERT INTO credit_transactions (user_id, user_name, amount, transaction_type, description) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, getUserName(userId));
            stmt.setInt(3, amount);
            stmt.setString(4, type);
            stmt.setString(5, description);
            stmt.executeUpdate();
        }
        catch (SQLException e) {
            System.err.println("❌ Error logging transaction: " + e.getMessage());
            throw e;
    }
    }

    // === DATA CLASSES ===

    public static class UserCreditInfo {
        private final String userId;
        private final int creditsBalance;
        private final boolean isSuspended;
        private final String suspensionReason;
        private final String suspendedAt;

        public UserCreditInfo(String userId, int creditsBalance, boolean isSuspended,
                              String suspensionReason, String suspendedAt) {
            this.userId = userId;
            this.creditsBalance = creditsBalance;
            this.isSuspended = isSuspended;
            this.suspensionReason = suspensionReason;
            this.suspendedAt = suspendedAt;
        }

        // Getters
        public String getUserId() { return userId; }
        public int getCreditsBalance() { return creditsBalance; }
        public boolean isSuspended() { return isSuspended; }
        public String getSuspensionReason() { return suspensionReason; }
        public String getSuspendedAt() { return suspendedAt; }
    }

    public static class CreditTransaction {
        private final int id;
        private final String userId;
        private final int amount;
        private final String type;
        private final String description;
        private final String createdAt;
        private final String userName;
        private final int documentId;

        public CreditTransaction(int id, String userId, int amount, String type, String description, String createdAt, String userName, int documentId) {
            this.id = id;
            this.userId = userId;
            this.amount = amount;
            this.type = type;
            this.description = description;
            this.createdAt = createdAt;
            this.userName = userName;
            this.documentId = documentId;
        }

        // Getters
        public int getId() { return id; }
        public String getUserId() { return userId; }
        public int getAmount() { return amount; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public String getCreatedAt() { return createdAt; }
        public String getUserName() { return userName; }
        public int getDocumentId() { return documentId; }
    }

    public static class CreditException extends RuntimeException {
        public CreditException(String message) {
            super(message);
        }
    }

    /**
     * Extract username from user ID (email)
     */
    private String getUserName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "Unknown User";
        }

        // If userId is an email, extract the username part
        if (userId.contains("@")) {
            return userId.substring(0, userId.indexOf("@"));
        }

        // Return the userId as fallback
        return userId;
    }

    /**
     * Get all transactions for admin panel (includes all users)
     */
    public List<CreditTransaction> getAllTransactions(int limit) throws SQLException {
        String sql =  "SELECT id, user_id, user_name, amount, transaction_type, description, document_id, created_at " +
                "FROM credit_transactions ORDER BY created_at DESC LIMIT ?";

        List<CreditTransaction> transactions = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(new CreditTransaction(
                            rs.getInt("id"),
                            rs.getString("user_id"),
                            rs.getInt("amount"),
                            rs.getString("transaction_type"),
                            rs.getString("description"),
                            rs.getString("created_at"),
                            rs.getString("user_name"),
                            rs.getInt("document_id")
                    ));
                }
            }
        }
        return transactions;
    }

    /**
     * Debug method to check table structure
     */
    public void printTableStructure() throws SQLException {
        String sql = "PRAGMA table_info(credit_transactions)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            System.out.println("=== credit_transactions table structure ===");
            while (rs.next()) {
                System.out.println("Column: " + rs.getString("name") +
                        ", Type: " + rs.getString("type"));
            }
        }
    }
    /**
     * Verify database connection and table structure
     */
    public void verifyDatabase() throws SQLException {
        try (Connection conn = getConnection()) {
            System.out.println("✅ Database connection successful");

            // Test query to verify table access
            String testSql = "SELECT COUNT(*) as count FROM credit_transactions";
            try (PreparedStatement stmt = conn.prepareStatement(testSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("✅ credit_transactions table accessible, count: " + rs.getInt("count"));
                }
            }

            // Test specific column access
            String columnTestSql = "SELECT amount FROM credit_transactions LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(columnTestSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("✅ amount column accessible, value: " + rs.getInt("amount"));
                } else {
                    System.out.println("ℹ️  No records in credit_transactions table");
                }
            }
        }
    }
}