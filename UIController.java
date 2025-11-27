package org.example.ui;

import com.google.api.client.auth.oauth2.Credential;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.example.auth.AuthManager;
import org.example.auth.TokenManager;
import org.example.core.*;
import org.example.storage.CreditService;
import org.example.storage.StorageManager;
import org.example.auth.AdminService;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class UIController {
    private MainWindowUI mainWindow;
    private final TokenManager tokenManager;
    private final AuthManager authManager;
    private final AdminService adminService;
    private final CreditService creditService;

    // Add service account path constant
    private static final String SERVICE_ACCOUNT_PATH = "C:\\Users\\EFM\\gcloud_key\\vision-service-account.json";

    // prevent concurrent sign-in flows
    private final AtomicBoolean signingIn = new AtomicBoolean(false);

    private static final double GEMINI_OVERRIDE_THRESHOLD = 0.65;
    private static final String[] ALLOWED_LABELS = new String[]{
            "Petition", "Ruling/Judgement/Order", "Contract", "Invoice", "Affidavit", "Memorandum", "Other", "Power of Attorney"};

    // backend collaborators
    private final StorageManager storage;
    private final ProcessRunner preprocessor;
    private final ProcessRunner classifier;
    private final TextExtractor extractor;
    private final SummarizationBridge summarizer;
    private final OcrBridge ocrBridge;
    private final SummarizationBridgeRest summarizerRest;

    private final Gson gson = new Gson();
    private final Type mapType = new TypeToken<Map<String, Object>>() {}.getType();

    // Updated constructor
    public UIController(TokenManager tokenManager, AuthManager authManager) throws Exception {
        this.tokenManager = tokenManager;
        this.authManager = authManager;
        this.adminService = new AdminService();

        // Initialize storage first (needed for credit service)
        StorageManager s = null;
        try {
            String projectRoot = System.getProperty("user.dir");
            s = new StorageManager(projectRoot);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize storage manager", e);
        }
        this.storage = s;

        // Initialize credit service with storage's JDBC URL
        this.creditService = new CreditService(storage.getJdbcUrl());

        // Ensure TokenManager and AuthManager point to the same on-disk token store.
        try {
            var tokenDir = authManager.getTokenStoreDirPath();
            if (tokenDir != null) {
                Files.createDirectories(tokenDir);
                this.tokenManager.setTokenStoreDir(tokenDir.toString());
            }
        } catch (Exception ignored) {}

        // Verify service account file exists
        File saFile = new File(SERVICE_ACCOUNT_PATH);
        if (!saFile.exists()) {
            throw new RuntimeException("Service account file not found: " + SERVICE_ACCOUNT_PATH);
        }
        System.out.println("✅ Service account file verified: " + SERVICE_ACCOUNT_PATH);

        // configure python & script paths
        String python = "python"; // Use system Python since packages are installed
        String preprocessScript = "python/preprocess.py";
        String classifierScript = "python/evaluate_single.py";
        String ocrScript = "python/ocr_google.py";
        String summarizerScript = "python/summarize_gemini.py";

        // Initialize OCR bridge with service account
        this.ocrBridge = new OcrBridge(python, ocrScript, SERVICE_ACCOUNT_PATH);

        // Then initialize TextExtractor with the OCR bridge
        this.extractor = new TextExtractor(ocrBridge);

        // Initialize other components
        this.preprocessor = new ProcessRunner(python, preprocessScript);
        this.classifier = new ProcessRunner(python, classifierScript);
        this.summarizer = new SummarizationBridge(python, summarizerScript, null);

        // Initialize REST summarizer with Vertex AI configuration
        String projectId = "legal-ocr-project-474912";
        String location = "us-central1";
        String modelId = "gemini-2.5-flash";
        this.summarizerRest = new SummarizationBridgeRest(projectId, location, modelId);

        System.out.println("✅ UIController initialized successfully with credit system");
    }

    // === CREDIT SYSTEM METHODS ===

    /**
     * OPTION 2: User clicks "Get Free Credits" button
     */
    public boolean addFreeCredits() {
        try {
            String userId = getCurrentUserId();
            boolean success = creditService.addFreeCredits(userId);
            if (success) {
                System.out.println("✅ Added 100 free credits to user: " + userId);
            }
            return success;
        } catch (Exception e) {
            System.err.println("❌ Failed to add free credits: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * OPTION 1: Admin adds credits to user
     */
    public boolean adminAddCredits(String targetUserId, int amount, String reason) {
        try {
            String adminId = getCurrentUserId();
            boolean success = creditService.adminAddCredits(adminId, targetUserId, amount, reason);
            if (success) {
                System.out.println("✅ Admin " + adminId + " added " + amount + " credits to user: " + targetUserId);
            }
            return success;
        } catch (Exception e) {
            System.err.println("❌ Failed to add admin credits: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get user's current credit info
     */
    public CreditService.UserCreditInfo getUserCreditInfo() {
        try {
            String userId = getCurrentUserId();
            return creditService.getUserCreditInfo(userId);
        } catch (Exception e) {
            System.err.println("❌ Failed to get user credit info: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if user can process documents (has credits and not suspended)
     */
    public boolean canProcessDocuments() {
        try {
            CreditService.UserCreditInfo info = getUserCreditInfo();
            return info != null && !info.isSuspended() && info.getCreditsBalance() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get user's credit balance
     */
    public int getUserCredits() {
        try {
            CreditService.UserCreditInfo info = getUserCreditInfo();
            return info != null ? info.getCreditsBalance() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Check if user is suspended
     */
    public boolean isUserSuspended() {
        try {
            CreditService.UserCreditInfo info = getUserCreditInfo();
            return info != null && info.isSuspended();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get suspension reason if user is suspended
     */
    public String getSuspensionReason() {
        try {
            CreditService.UserCreditInfo info = getUserCreditInfo();
            return info != null && info.isSuspended() ? info.getSuspensionReason() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Admin: Suspend a user
     */
    public boolean suspendUser(String targetUserId, String reason) {
        try {
            String adminId = getCurrentUserId();
            boolean success = creditService.suspendUser(adminId, targetUserId, reason);
            if (success) {
                System.out.println("✅ Admin " + adminId + " suspended user: " + targetUserId + " - Reason: " + reason);
            }
            return success;
        } catch (Exception e) {
            System.err.println("❌ Failed to suspend user: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Admin: Unsuspend a user
     */
    public boolean unsuspendUser(String targetUserId) {
        try {
            String adminId = getCurrentUserId();
            boolean success = creditService.unsuspendUser(adminId, targetUserId);
            if (success) {
                System.out.println("✅ Admin " + adminId + " unsuspended user: " + targetUserId);
            }
            return success;
        } catch (Exception e) {
            System.err.println("❌ Failed to unsuspend user: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Admin: Get all users for management
     */
    public List<CreditService.UserCreditInfo> getAllUsersForAdmin() {
        try {
            return creditService.getAllUsers();
        } catch (Exception e) {
            System.err.println("❌ Failed to get all users: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Admin: Get user's transaction history
     */
    public List<CreditService.CreditTransaction> getUserTransactions(String userId, int limit) {
        try {
            return creditService.getUserTransactions(userId, limit);
        } catch (Exception e) {
            System.err.println("❌ Failed to get user transactions: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Calculate credits needed for document processing
     */
    public int calculateProcessingCredits(File file, String extractedText) {
        try {
            int pageCount = getPageCount(file);
            int wordCount = extractedText.length() / 5; // rough word count estimate
            return creditService.calculateProcessingCredits(pageCount, wordCount);
        } catch (Exception e) {
            System.err.println("❌ Failed to calculate processing credits: " + e.getMessage());
            return 10; // default fallback
        }
    }

    /**
     * Deduct credits for service usage
     */
    public boolean deductCredits(int amount, String service, String documentId) {
        try {
            String userId = getCurrentUserId();
            boolean success = creditService.deductCredits(userId, amount, service, documentId);
            if (success) {
                System.out.println("✅ Deducted " + amount + " credits from user " + userId + " for " + service);
            }
            return success;
        } catch (CreditService.CreditException e) {
            throw e; // Re-throw credit exceptions (insufficient credits, suspended, etc.)
        } catch (Exception e) {
            System.err.println("❌ Failed to deduct credits: " + e.getMessage());
            return false;
        }
    }

    // === ADMIN AUTHENTICATION ===

    /**
     * Admin: Validate admin login
     */
    public boolean validateAdminLogin(String email, String password) {
        try {
            return creditService.validateAdminLogin(email, password);
        } catch (Exception e) {
            System.err.println("❌ Failed to validate admin login: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if current user is admin
     */
    public boolean isCurrentUserAdmin() {
        try {
            String email = tokenManager.getCurrentUserId();
            return creditService.isAdmin(email);
        } catch (Exception e) {
            return false;
        }
    }

    // === EXISTING METHODS (UPDATED WITH CREDIT CHECKS) ===

    public List<String> getSystemStatistics() {
        if (!isCurrentUserAdmin()) {
            throw new SecurityException("Admin access required");
        }

        List<String> stats = new ArrayList<>();
        try {
            // User statistics
            int totalUsers = storage.getTotalUserCount();
            int activeUsers = storage.getActiveUserCount();
            stats.add("Total Users: " + totalUsers);
            stats.add("Active Users: " + activeUsers);

            // Credit statistics
            List<CreditService.UserCreditInfo> allUsers = getAllUsersForAdmin();
            int suspendedUsers = (int) allUsers.stream().filter(CreditService.UserCreditInfo::isSuspended).count();
            int totalCredits = allUsers.stream().mapToInt(CreditService.UserCreditInfo::getCreditsBalance).sum();
            stats.add("Suspended Users: " + suspendedUsers);
            stats.add("Total Credits in System: " + totalCredits);

            // Document statistics
            int totalDocuments = storage.getTotalDocumentCount();
            int processedDocuments = storage.getProcessedDocumentCount();
            stats.add("Total Documents: " + totalDocuments);
            stats.add("Processed Documents: " + processedDocuments);

            // Storage statistics
            long storageUsed = storage.getTotalStorageUsed();
            stats.add("Storage Used: " + (storageUsed / (1024 * 1024)) + " MB");

            // Recent activity
            stats.add("Last System Update: " + LocalDateTime.now());

        } catch (Exception e) {
            stats.add("Error gathering statistics: " + e.getMessage());
        }
        return stats;
    }

    public List<Map<String, Object>> getAllUsersWithStats() {
        if (!isCurrentUserAdmin()) {
            throw new SecurityException("Admin access required");
        }

        try {
            return storage.getAllUsersWithStats();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch users: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getAllDocumentsForAdmin() {
        if (!isCurrentUserAdmin()) {
            throw new SecurityException("Admin access required");
        }

        try {
            return storage.getAllDocuments();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch documents: " + e.getMessage(), e);
        }
    }

    public void setMainWindow(MainWindowUI w) { this.mainWindow = w; }

    public void signOut() {
        try { authManager.clearTokenCache(); } catch (Exception ignored) {}
        tokenManager.signOut();
        signingIn.set(false);
        if (mainWindow != null) {
            SwingUtilities.invokeLater(() -> mainWindow.onSignedOut());
        }
    }

    public void signIn(JFrame parent) {
        // single-flight: ignore clicks while a sign-in is already running
        if (!signingIn.compareAndSet(false, true)) {
            return;
        }

        new Thread(() -> {
            try {
                // Ensure token dir exists right before launching browser
                try {
                    var dir = authManager.getTokenStoreDirPath();
                    if (dir != null) Files.createDirectories(dir);
                    // Keep TokenManager in sync (in case AppLauncher didn't wire it)
                    if (dir != null) tokenManager.setTokenStoreDir(dir.toString());
                } catch (Exception ignored) {}

                Credential cred = null;
                Exception lastErr = null;

                // Try once, and on a token-store error, clear & retry once.
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        cred = authManager.signInInteractive("user");
                        lastErr = null;
                        break;
                    } catch (Exception e) {
                        lastErr = e;
                        // If the first attempt had a token-store problem, clear and retry
                        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                        boolean tokenStoreIssue =
                                msg.contains("token") || msg.contains("store") || msg.contains("file") || msg.contains("directory");
                        if (attempt == 0 && tokenStoreIssue) {
                            try {
                                authManager.clearTokenCache();
                                var dir = authManager.getTokenStoreDirPath();
                                if (dir != null) Files.createDirectories(dir);
                            } catch (Exception ignored) {}
                            continue;
                        }
                        break;
                    }
                }

                if (cred == null) {
                    Exception err = lastErr != null ? lastErr : new RuntimeException("Unknown sign-in failure");
                    throw err;
                }

                tokenManager.setCredential(cred);
                String email = tokenManager.fetchUserEmail();
                tokenManager.setCurrentUserId((email != null && !email.isBlank()) ? email : "unknown");

                // Initialize user's credit account if it doesn't exist
                try {
                    CreditService.UserCreditInfo creditInfo = getUserCreditInfo();
                    System.out.println("✅ User credit account ready. Balance: " + creditInfo.getCreditsBalance() + " credits");
                } catch (Exception e) {
                    System.err.println("⚠️ Failed to initialize user credit account: " + e.getMessage());
                }

                // UI: notify success
                if (mainWindow != null) {
                    SwingUtilities.invokeLater(() -> mainWindow.onSignedIn(email));
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(parent, "Sign-in failed: " + e.getMessage()));
            } finally {
                signingIn.set(false);
            }
        }, "signin-thread").start();
    }

    public Path uploadFile(File f) throws Exception {
        ensureSignedIn();
        if (storage == null) throw new IllegalStateException("Storage not available");
        String owner = tokenManager.getCurrentUserId();

        Path stored = storage.storeOriginalForUserUnlabeled(f, owner);

        String sha = StorageManager.sha256(stored.toFile());

        Map<String,Object> rec = new HashMap<>();
        rec.put("filename", f.getName());
        rec.put("original_path", stored.toString());
        rec.put("stored_input_path", stored.toString());
        rec.put("extracted_path", null);
        rec.put("summary_path", null);
        rec.put("predicted_label", "Unlabeled");
        rec.put("confidence", null);
        rec.put("sha256", sha);
        rec.put("processed", 0);
        rec.put("notes", "Uploaded");
        rec.put("owner_id", owner);
        storage.upsertDocumentRecord(rec);

        return stored;
    }

    private String extractiveFallback(String text, int maxSentences) {
        if (text == null) return "";
        String[] sentences = text.replace("\r", " ").split("(?<=[.!?])\\s+");
        StringBuilder sb = new StringBuilder("[Auto-extractive summary]\n");
        int count = 0;
        for (String s : sentences) {
            if (s.isBlank()) continue;
            sb.append("- ").append(s.trim()).append("\n");
            if (++count >= maxSentences) break;
        }
        if (count == 0) sb.append(text.substring(0, Math.min(300, text.length())));
        return sb.toString().trim();
    }

    /** Run pipeline asynchronously. Requires a signed-in session and credits. */
    public void runPipeline(File file, Consumer<Map<String,Object>> onComplete, Consumer<Exception> onError) {
        new Thread(() -> {
            try {
                ensureSignedIn();

                // Check credits and account status before processing
                if (!canProcessDocuments()) {
                    CreditService.UserCreditInfo info = getUserCreditInfo();
                    if (info.isSuspended()) {
                        throw new IllegalStateException("Account suspended: " + info.getSuspensionReason());
                    } else {
                        throw new IllegalStateException("Insufficient credits. Available: " + info.getCreditsBalance() + " credits");
                    }
                }

                // 1) extract
                String extracted = extractor.extract(file);
                if (extracted == null) extracted = "";

                // Calculate credits needed for this processing
                int creditsNeeded = calculateProcessingCredits(file, extracted);
                System.out.println("🔢 Processing will use approximately " + creditsNeeded + " credits");

                // 2) preprocess
                Map<String,Object> prePayload = new HashMap<>();
                prePayload.put("mode", "preprocess");
                prePayload.put("text", extracted);

                String accessToken = null;
                try {
                    // Force token refresh if needed
                    accessToken = tokenManager.getValidAccessToken();
                    System.out.println("🔑 Pipeline Access Token Length: " +
                            (accessToken != null ? accessToken.length() : "null"));
                } catch (Exception ignored) {
                    System.err.println("⚠️ Failed to get access token");
                }

                Map<String, String> env = new HashMap<>();
                if (accessToken != null && !accessToken.isBlank()) {
                    env.put("GOOGLE_OAUTH_ACCESS_TOKEN", accessToken);
                    System.out.println("🔑 Token set for preprocessing/classification (length: " + accessToken.length() + ")");
                }

                String preRaw = preprocessor.callBridge(prePayload, env);
                String preprocessed = preRaw;
                try {
                    Map<String,Object> preResp = gson.fromJson(preRaw, mapType);
                    if (preResp != null && preResp.get("normalized") != null) {
                        preprocessed = preResp.get("normalized").toString();
                    }
                } catch (Exception ignore) {}

                // 3) classify
                Map<String,Object> classPayload = new HashMap<>();
                classPayload.put("text", preprocessed);
                String classRaw = classifier.callBridge(classPayload, env);
                Map<String,Object> classResp = null;
                try { classResp = gson.fromJson(classRaw, mapType); } catch (Exception ignore) {}
                String prediction = classResp != null && classResp.get("prediction") != null ? classResp.get("prediction").toString() : "Unknown";
                double confidence = 0.0;
                if (classResp != null && classResp.get("confidence") != null) {
                    Object c = classResp.get("confidence");
                    confidence = (c instanceof Number) ? ((Number)c).doubleValue() : Double.parseDouble(c.toString());
                }

                // 3b) optional Gemini override
                String finalLabel = prediction;
                String decisionSource = "svm";
                try {
                    accessToken = tokenManager.getAccessToken();
                    if (confidence < GEMINI_OVERRIDE_THRESHOLD && accessToken != null && !accessToken.isBlank()) {
                        GenerativeClassifierRest gRest = new GenerativeClassifierRest("gemini-2.5-flash");
                        GenerativeClassifierRest.Result gRes = gRest.classifyWithToken(extracted, ALLOWED_LABELS, accessToken);
                        if (gRes != null && gRes.label != null && !gRes.label.isBlank()) {
                            finalLabel = gRes.label.trim();
                            decisionSource = "gemini_override";
                        }
                    }
                } catch (Exception ignored) {}

                // 4) summarize (REST first)
                String summary;
                try {
                    String at = tokenManager.getAccessToken();


                    // Use long-text summarization for large documents
                    if (extracted.length() > 8000) {
                        System.out.println("📚 Using long-text summarization for " + extracted.length() + " characters");
                        summary = summarizeLongText(extracted, at);
                    } else {
                        summary = summarizerRest.smartSummarize(extracted, at, 16000, 120);
                    }
                } catch (Exception restEx) {
                    System.err.println("❌ Vertex AI summarization failed: " + restEx.getMessage());

                    // Check if it's a permission error
                    if (restEx.getMessage() != null && restEx.getMessage().contains("PERMISSION_DENIED")) {
                        System.err.println("🔒 Vertex AI permission denied. Please ensure your account has 'Vertex AI User' role.");
                        summary = "[Summarization unavailable: Permission denied. Please contact administrator to grant Vertex AI permissions.]";
                    } else {
                        summary = "[Summarization failed: " + restEx.getMessage() + "]";
                    }

                    // Fallback to extractive summary
                    if (summary.startsWith("[")) {
                        summary = extractiveFallback(extracted, 5);
                    }
                }

                // Deduct credits after successful processing
                boolean creditsDeducted = deductCredits(creditsNeeded, "document_processing", file.getName());
                if (!creditsDeducted) {
                    throw new IllegalStateException("Failed to deduct credits for processing");
                }

                int remainingCredits = getUserCredits();

                Map<String,Object> out = new HashMap<>();
                out.put("extracted", extracted);
                out.put("preprocessed", preprocessed);
                out.put("prediction", finalLabel);
                out.put("confidence", confidence);
                out.put("decision_source", decisionSource);
                out.put("summary", summary == null ? "" : summary);
                out.put("credits_used", creditsNeeded);
                out.put("remaining_credits", remainingCredits);

                System.out.println("✅ Processing complete. Used " + creditsNeeded + " credits. Remaining: " + remainingCredits);
                onComplete.accept(out);

            } catch (Exception e) {
                onError.accept(e);
            }
        }, "pipeline-thread").start();
    }

    /** Synchronous variant with credit checks */
    public Map<String,Object> runPipelineSync(File file) throws Exception {
        ensureSignedIn();

        // Check credits and account status
        if (!canProcessDocuments()) {
            CreditService.UserCreditInfo info = getUserCreditInfo();
            if (info.isSuspended()) {
                throw new IllegalStateException("Account suspended: " + info.getSuspensionReason());
            } else {
                throw new IllegalStateException("Insufficient credits. Available: " + info.getCreditsBalance() + " credits");
            }
        }

        String extracted = extractor.extract(file);
        if (extracted == null) extracted = "";

        // Calculate and deduct credits
        int creditsNeeded = calculateProcessingCredits(file, extracted);
        boolean creditsDeducted = deductCredits(creditsNeeded, "document_processing", file.getName());
        if (!creditsDeducted) {
            throw new IllegalStateException("Failed to deduct credits for processing");
        }

        Map<String,Object> prePayload = new HashMap<>();
        prePayload.put("mode", "preprocess");
        prePayload.put("text", extracted);

        String accessToken = null;
        try { accessToken = tokenManager.getAccessToken(); } catch (Exception ignored) {}
        Map<String,String> env = new HashMap<>();
        if (accessToken != null && !accessToken.isBlank()) env.put("GOOGLE_OAUTH_ACCESS_TOKEN", accessToken);

        String preRaw = preprocessor.callBridge(prePayload, env);
        String preprocessed = preRaw;
        try {
            Map<String,Object> preResp = gson.fromJson(preRaw, mapType);
            if (preResp != null && preResp.get("normalized") != null) preprocessed = preResp.get("normalized").toString();
        } catch (Exception ignore) {}

        Map<String,Object> classPayload = new HashMap<>();
        classPayload.put("text", preprocessed);
        String classRaw = classifier.callBridge(classPayload, env);
        Map<String,Object> classResp = null;
        try { classResp = gson.fromJson(classRaw, mapType); } catch (Exception ignore) {}
        String prediction = (classResp != null && classResp.get("prediction") != null) ? classResp.get("prediction").toString() : "Unknown";
        double confidence = 0.0;
        if (classResp != null && classResp.get("confidence") != null) {
            Object c = classResp.get("confidence");
            confidence = (c instanceof Number) ? ((Number)c).doubleValue() : Double.parseDouble(c.toString());
        }

        // optional override
        String finalLabel = prediction;
        try {
            if (confidence < GEMINI_OVERRIDE_THRESHOLD && accessToken != null && !accessToken.isBlank()) {
                GenerativeClassifierRest gRest = new GenerativeClassifierRest("gemini-2.5-flash");
                GenerativeClassifierRest.Result gRes = gRest.classifyWithToken(extracted, ALLOWED_LABELS, accessToken);
                if (gRes != null && gRes.label != null && !gRes.label.isBlank()) {
                    finalLabel = gRes.label.trim();
                }
            }
        } catch (Exception ignored) {}

        // summarize
        String summary = "";
        try {
            if (accessToken != null && !accessToken.isBlank()) {
                if (extracted.length() > 8000) {
                    System.out.println("📚 Using long-text summarization for " + extracted.length() + " characters");
                    summary = summarizeLongText(extracted, accessToken);
                } else {
                    summary = summarizerRest.smartSummarize(extracted, accessToken, 16000, 120);
                }
            } else {
                // Fallback to Python summarizer if no OAuth token
                Map<String,Object> sumPayload = new HashMap<>();
                sumPayload.put("text", extracted);
                sumPayload.put("model", "gemini-2.5-flash");
                sumPayload.put("max_tokens", 8192);
                String sumRaw = new ProcessRunner(summarizer.getPythonExe(), summarizer.getScriptPath()).callBridge(sumPayload, Map.of());
                Map<String,Object> sumResp = gson.fromJson(sumRaw, mapType);
                summary = (sumResp != null && sumResp.get("summary") != null) ? sumResp.get("summary").toString() : (sumRaw == null ? "" : sumRaw);
            }
        } catch (Exception e) {
            e.printStackTrace();
            summary = "[Summarization failed]";
        }

        int remainingCredits = getUserCredits();

        Map<String,Object> out = new HashMap<>();
        out.put("extracted", extracted);
        out.put("preprocessed", preprocessed);
        out.put("prediction", finalLabel);
        out.put("confidence", confidence);
        out.put("summary", summary);
        out.put("credits_used", creditsNeeded);
        out.put("remaining_credits", remainingCredits);
        return out;
    }

    /** Batch process with credit checks */
    public void runPipelineBatch(
            List<File> files,
            Consumer<BatchProgress> onProgress,
            Consumer<List<BatchResult>> onDone,
            Consumer<Exception> onError
    ) {
        new Thread(() -> {
            List<BatchResult> results = new ArrayList<>();
            try {
                ensureSignedIn();

                // Check initial credits
                if (!canProcessDocuments()) {
                    CreditService.UserCreditInfo info = getUserCreditInfo();
                    if (info.isSuspended()) {
                        throw new IllegalStateException("Account suspended: " + info.getSuspensionReason());
                    } else {
                        throw new IllegalStateException("Insufficient credits. Available: " + info.getCreditsBalance() + " credits");
                    }
                }

                int total = files.size();
                int idx = 0;
                for (File f : files) {
                    idx++;
                    BatchProgress prog = new BatchProgress(idx, total, f.getName(), "Processing…");
                    onProgress.accept(prog);

                    try {
                        // Check if we still have credits for this document
                        if (!canProcessDocuments()) {
                            onProgress.accept(new BatchProgress(idx, total, f.getName(), "Skipped (insufficient credits)"));
                            results.add(BatchResult.skipped(f));
                            continue;
                        }

                        String sha = StorageManager.sha256(f);
                        Map<String,Object> existing = storage.getBySha256(sha);
                        if (existing != null && Integer.valueOf(1).equals(existing.get("processed"))) {
                            onProgress.accept(new BatchProgress(idx, total, f.getName(), "Skipped (already processed)"));
                            results.add(BatchResult.skipped(f));
                            continue;
                        }

                        Map<String,Object> res = runPipelineSync(f);
                        saveResults(f, res);

                        onProgress.accept(new BatchProgress(idx, total, f.getName(), "Done"));
                        results.add(BatchResult.success(f, res));
                    } catch (Exception exOne) {
                        onProgress.accept(new BatchProgress(idx, total, f.getName(), "Failed: " + exOne.getMessage()));
                        results.add(BatchResult.failure(f, exOne));
                    }
                }
                onDone.accept(results);
            } catch (Exception e) {
                onError.accept(e);
            }
        }, "batch-thread").start();
    }

    // === HELPER METHODS ===

    private int getPageCount(File file) {
        // Simple implementation - you might have a better way
        // For now, return 1 page as default
        return 1;
    }

    /**
     * Batch process files - this is the method called from the UI
     */
    public void runBatch(
            List<File> files,
            Consumer<String> onProgress,
            Consumer<Map<File, Exception>> onDone
    ) {
        System.out.println("🚀 runBatch called with " + files.size() + " files");

        // Convert the progress format for runPipelineBatch
        Consumer<BatchProgress> progressConsumer = progress -> {
            String message = String.format("Processing %d/%d: %s - %s",
                    progress.index, progress.total, progress.filename, progress.status);
            onProgress.accept(message);
        };

        // Convert the completion format for runPipelineBatch
        Consumer<List<BatchResult>> doneConsumer = results -> {
            Map<File, Exception> failures = new HashMap<>();
            for (BatchResult result : results) {
                if (!result.ok && result.error != null) {
                    failures.put(result.file, result.error);
                }
            }
            onDone.accept(failures);
        };

        // Error handler
        Consumer<Exception> errorConsumer = error -> {
            Map<File, Exception> failures = new HashMap<>();
            for (File file : files) {
                failures.put(file, error);
            }
            onDone.accept(failures);
        };

        // Call your existing runPipelineBatch method
        runPipelineBatch(files, progressConsumer, doneConsumer, errorConsumer);
    }

    /**
     * Alternative batch processing that processes files directly without complex progress tracking
     */
    public void runBatchDirect(
            List<File> files,
            Consumer<String> onProgress,
            Consumer<Map<File, Exception>> onDone
    ) {
        new Thread(() -> {
            Map<File, Exception> failures = new HashMap<>();
            int processed = 0;
            int total = files.size();

            System.out.println("🔄 Starting direct batch processing for " + total + " files");

            for (File file : files) {
                processed++;
                String progressMsg = String.format("Processing %d/%d: %s", processed, total, file.getName());
                onProgress.accept(progressMsg);
                System.out.println(progressMsg);

                try {
                    // Check if we still have credits
                    if (!canProcessDocuments()) {
                        CreditService.UserCreditInfo info = getUserCreditInfo();
                        String errorMsg = info.isSuspended() ?
                                "Account suspended: " + info.getSuspensionReason() :
                                "Insufficient credits: " + info.getCreditsBalance();
                        failures.put(file, new IllegalStateException(errorMsg));
                        continue;
                    }

                    // Check if file already processed
                    String sha = StorageManager.sha256(file);
                    Map<String,Object> existing = storage.getBySha256(sha);
                    if (existing != null && Integer.valueOf(1).equals(existing.get("processed"))) {
                        System.out.println("⏭️ Skipping already processed file: " + file.getName());
                        continue;
                    }

                    // Process the file
                    Map<String, Object> result = runPipelineSync(file);
                    System.out.println("✅ Successfully processed: " + file.getName());

                    // Save results
                    saveResults(file, result);

                } catch (Exception e) {
                    System.err.println("❌ Failed to process " + file.getName() + ": " + e.getMessage());
                    failures.put(file, e);
                }
            }

            System.out.println("✅ Batch processing completed. Failures: " + failures.size());
            onDone.accept(failures);

        }, "batch-direct-thread").start();
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    /** Tiny POJOs for progress & results */
    public static class BatchProgress {
        public final int index, total;
        public final String filename;
        public final String status;
        public BatchProgress(int index, int total, String filename, String status) {
            this.index = index; this.total = total; this.filename = filename; this.status = status;
        }
    }
    public static class BatchResult {
        public final File file;
        public final boolean ok;           // This field exists
        public final boolean success;      // Add this field for compatibility
        public final Map<String,Object> data;
        public final Exception error;

        private BatchResult(File f, boolean ok, Map<String,Object> data, Exception e) {
            this.file = f;
            this.ok = ok;
            this.success = ok;  // Map ok to success
            this.data = data;
            this.error = e;
        }

        public static BatchResult success(File f, Map<String,Object> data){
            return new BatchResult(f, true, data, null);
        }

        public static BatchResult failure(File f, Exception e){
            return new BatchResult(f, false, null, e);
        }

        public static BatchResult skipped(File f){
            return new BatchResult(f, true, Map.of("skipped", true), null);
        }
    }

    // === Long-text summarization helper ===
    private String summarizeLongText(String text, String accessToken) throws Exception {
        if (text == null) text = "";
        text = text.strip();
        if (text.isEmpty()) return "";

        // REDUCE CHUNK SIZE to avoid token limits
        final int CHUNK = 4000;  // Reduced from 8000

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += CHUNK) {
            parts.add(text.substring(i, Math.min(text.length(), i + CHUNK)));
        }

        List<String> partials = new ArrayList<>();
        for (String p : parts) {
            // SIMPLIFY THE PROMPT to use fewer tokens
            String prompt = "Summarize this legal document clearly. Focus on:\n"
                    + "• Parties involved\n• Main purpose\n• Key facts\n• Important dates\n\n"
                    + p;

            // REDUCE MAX TOKENS for response
            String s = summarizerRest.smartSummarize(prompt, accessToken, 6144, 120);
            if (s == null || s.isEmpty() || s.contains("MAX_TOKENS")) {
                // Fallback: extract first few sentences
                s = extractiveFallback(p, 3);
            }
            partials.add(s);
        }

        if (partials.size() == 1) {
            return partials.get(0);
        }

        // For multiple parts, combine with a simpler prompt
        String combined = String.join("\n\n", partials);
        String finalPrompt = "Combine these summaries into one concise summary:\n\n" + combined;

        // Use even fewer tokens for final combination
        return summarizerRest.smartSummarize(finalPrompt, accessToken, 6144, 120);
    }

    public boolean saveResults(File storedFile, Map<String,Object> pipelineResult) throws Exception {
        if (storage == null) throw new IllegalStateException("Storage not available");

        String owner = tokenManager != null ? tokenManager.getCurrentUserId() : "anonymous";
        String pred  = String.valueOf(pipelineResult.getOrDefault("prediction","Unlabeled")).trim();
        if (pred.isEmpty()) pred = "Unlabeled";

        String extracted = String.valueOf(pipelineResult.getOrDefault("extracted",""));
        String summary   = String.valueOf(pipelineResult.getOrDefault("summary",""));
        summary = unescapeIfNeeded(summary);

        Path supposed = storedFile.toPath();
        Path existing = storage.ensureStoredInputExists(owner, supposed, storedFile.getName());
        if (existing == null) {
            throw new IOException("Stored file is missing and could not be recovered: " + supposed);
        }

        Path labeled = storage.moveOriginalToLabel(existing, owner, pred);
        Path extractedPath = storage.storeExtracted(owner, pred, storedFile.getName(), extracted);
        Path summaryPath   = storage.storeSummary(owner, pred, storedFile.getName(), summary);

        String sha = StorageManager.sha256(labeled.toFile());
        double conf = 0.0;
        try {
            Object c = pipelineResult.get("confidence");
            if (c instanceof Number) conf = ((Number)c).doubleValue();
            else if (c != null) conf = Double.parseDouble(c.toString());
        } catch (Exception ignored) {}

        Map<String,Object> rec = new HashMap<>();
        rec.put("filename", storedFile.getName());
        rec.put("original_path", labeled.toString());
        rec.put("stored_input_path", labeled.toString());
        rec.put("extracted_path", extractedPath.toString());
        rec.put("summary_path", summaryPath.toString());
        rec.put("predicted_label", pred);
        rec.put("confidence", conf);
        rec.put("sha256", sha);
        rec.put("processed", 1);
        rec.put("notes", "Saved from UI");
        rec.put("owner_id", owner);

        storage.upsertDocumentRecord(rec);
        return true;
    }

    public StorageManager getStorage() { return storage; }
    public CreditService getCreditService() { return creditService; }

    public boolean isSignedIn() { return tokenManager.isSignedIn(); }
    public String getCurrentUserId() { return tokenManager != null ? tokenManager.getCurrentUserId() : null; }
    public String debugTokenInfo(){ return tokenManager != null ? tokenManager.debugTokenInfo() : null; }

    private static String unescapeIfNeeded(String s) {
        if (s == null) return "";
        if (s.contains("\n") && !s.contains("\\n")) return s;
        return s.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t");
    }

    /** Guard used by upload/run/batch to ensure a session is active. */
    private void ensureSignedIn() {
        if (!isSignedIn()) {
            throw new IllegalStateException("You must sign in to use this feature.");
        }
    }

    // Add this method to your UIController for testing
    public void testOcrIntegration(File testFile) {
        try {
            System.out.println("🧪 Testing OCR Integration with: " + testFile.getName());

            String extractedText = extractor.extract(testFile);

            System.out.println("✅ Extraction successful!");
            System.out.println("📊 Text length: " + extractedText.length() + " characters");

            if (extractedText.length() > 0) {
                String preview = extractedText.substring(0, Math.min(200, extractedText.length()));
                System.out.println("📖 Preview: " + preview + "...");
            }

        } catch (Exception e) {
            System.err.println("❌ OCR Integration test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get all credit transactions for admin panel
     */
    public List<CreditService.CreditTransaction> getAllCreditTransactions(int limit) {
        try {
            return creditService.getAllTransactions(limit);
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    public void verifyCreditSystem() {
        try {
            creditService.verifyDatabase();
        } catch (SQLException e) {
            System.err.println("❌ Credit system verification failed: " + e.getMessage());
        }
    }
}