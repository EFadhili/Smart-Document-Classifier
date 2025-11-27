package org.example.core;

import com.google.gson.*;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SummarizationBridgeRest {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();
    private static final Gson GSON = new Gson();

    private final String projectId;
    private final String location;
    private final String modelId;

    public SummarizationBridgeRest(String projectId, String location, String modelId) {
        this.projectId = projectId;
        this.location = location;
        this.modelId = modelId;
    }

    public String smartSummarize(String text, String accessToken, int maxOutTokens, int timeoutSec) throws Exception {
        if (text == null) text = "";
        text = text.trim();
        if (text.isEmpty()) return "[No input text]";

        // If text is short, process in one go
        if (text.length() <= 12000) {
            String prompt = "As a legal expert, provide a comprehensive summary of this legal document. " +
                    "Include: Parties involved, Purpose/Intent, Key obligations and rights, Important dates/deadlines, " +
                    "Financial amounts if any, Termination clauses, and Overall significance.\n\n" + text;

            return generateOnce(prompt, accessToken, maxOutTokens, timeoutSec);
        }

        // For longer documents, use chunking with better overlap
        final int CHUNK_SIZE = 4000;
        final int OVERLAP = 500;
        List<String> chunks = splitChunksWithOverlap(text, CHUNK_SIZE, OVERLAP);
        List<String> partials = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String prompt = String.format("Summarize this PART %d/%d of a legal document. Focus on key legal points, " +
                            "parties, obligations, and important clauses:\n\n%s",
                    i + 1, chunks.size(), chunk);

            String s = generateOnce(prompt, accessToken, Math.min(2000, maxOutTokens), timeoutSec);
            if (s != null && !s.trim().isEmpty()) {
                partials.add(s.trim());
            }

            // Small delay to avoid rate limiting
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        if (partials.size() == 1) return partials.get(0);

        // Merge partial summaries
        String join = String.join("\n\n", partials);
        String mergePrompt = "Combine the following partial summaries into one cohesive, comprehensive legal document summary. " +
                "Remove repetition but preserve all important legal details, parties, obligations, dates, and clauses. " +
                "Ensure the final summary flows naturally as a single document:\n\n" + join;

        String merged = generateOnce(mergePrompt, accessToken, maxOutTokens, timeoutSec);
        return (merged == null || merged.isBlank()) ? String.join("\n\n", partials) : merged.trim();
    }

    private static List<String> splitChunksWithOverlap(String s, int chunkSize, int overlap) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int end = Math.min(i + chunkSize, s.length());

            // Try to break at sentence boundary if possible
            if (end < s.length()) {
                int sentenceEnd = findSentenceBoundary(s, end - 100, end + 100);
                if (sentenceEnd > end - 500) { // Only use if it's reasonably close
                    end = sentenceEnd;
                }
            }

            out.add(s.substring(i, end));

            if (end >= s.length()) break;

            // Move forward with overlap
            i = Math.max(i + 1, end - overlap);
        }
        return out;
    }

    private static int findSentenceBoundary(String text, int start, int end) {
        if (end > text.length()) end = text.length();
        String segment = text.substring(start, end);

        // Look for sentence endings
        int lastPeriod = segment.lastIndexOf('.');
        int lastQuestion = segment.lastIndexOf('?');
        int lastExclamation = segment.lastIndexOf('!');
        int lastNewline = segment.lastIndexOf('\n');

        int boundary = Math.max(Math.max(lastPeriod, lastQuestion), Math.max(lastExclamation, lastNewline));
        if (boundary != -1) {
            return start + boundary + 1; // +1 to include the punctuation
        }

        // Fallback: look for paragraph break
        int doubleNewline = segment.lastIndexOf("\n\n");
        if (doubleNewline != -1) {
            return start + doubleNewline + 2;
        }

        return end; // No good boundary found
    }

  
    private String generateOnce(String userText, String accessToken, int maxTokens, int timeoutSec) throws Exception {
        // Use Vertex AI endpoint instead of direct Gemini API
        String url = String.format("https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
                location, projectId, location, modelId);

        JsonObject cfg = new JsonObject();
        if (maxTokens > 0) {
            cfg.addProperty("maxOutputTokens", maxTokens);
        }
        cfg.addProperty("temperature", 0.2); // Lower temperature for more consistent legal summaries

        JsonObject part = new JsonObject();
        part.addProperty("text", userText);

        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(userMsg);

        JsonObject body = new JsonObject();
        body.add("contents", contents);
        body.add("generationConfig",cfg);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (resp.statusCode() != 200) {
            System.err.println("[Vertex AI DEBUG] HTTP " + resp.statusCode() + " body:\n" + resp.body());
            throw new java.io.IOException("Vertex AI API error: HTTP " + resp.statusCode() + " -> " + resp.body());
        }

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();

        // Handle safety filters
        if (root.has("promptFeedback")) {
            JsonObject pf = root.getAsJsonObject("promptFeedback");
            if (pf.has("blockReason") && !pf.get("blockReason").isJsonNull()) {
                String blockReason = pf.get("blockReason").getAsString();
                System.err.println("[Vertex AI DEBUG] Content blocked: " + blockReason);
                return "[Summary blocked for safety: " + blockReason + "]";
            }
        }

        if (!root.has("candidates")) {
            System.err.println("[Vertex AI DEBUG] No candidates. Raw:\n" + resp.body());
            return "[No candidates returned]";
        }

        JsonArray cands = root.getAsJsonArray("candidates");
        if (cands.size() == 0) {
            System.err.println("[Vertex AI DEBUG] Empty candidates array. Raw:\n" + resp.body());
            return "[No candidates returned]";
        }

        // Process first candidate
        JsonObject cand = cands.get(0).getAsJsonObject();

        // Check finish reason
        if (cand.has("finishReason") && !cand.get("finishReason").isJsonNull()) {
            String finishReason = cand.get("finishReason").getAsString();
            if (!"STOP".equals(finishReason)) {
                System.err.println("[Vertex AI DEBUG] Non-STOP finish reason: " + finishReason);
            }
        }

        // Extract text content
        if (cand.has("content")) {
            JsonObject content = cand.getAsJsonObject("content");
            if (content.has("parts")) {
                JsonArray outParts = content.getAsJsonArray("parts");
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < outParts.size(); j++) {
                    JsonObject p = outParts.get(j).getAsJsonObject();
                    if (p.has("text") && !p.get("text").isJsonNull()) {
                        sb.append(p.get("text").getAsString());
                    }
                }
                String result = sb.toString().trim();
                if (!result.isEmpty()) return result;
            }
        }

        System.err.println("[Vertex AI DEBUG] No valid content found. Raw:\n" + resp.body());
        return "[No valid content in response]";
    }

    private static List<String> splitChunks(String s, int chunkSize) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int end = Math.min(i + chunkSize, s.length());
            out.add(s.substring(i, end));
            i = end;
        }
        return out;
    }

    // Helper method to maintain backward compatibility
    public String summarizeWithToken(String text, String accessToken, int maxOutputTokens, int timeoutSeconds) throws Exception {
        return smartSummarize(text, accessToken, maxOutputTokens, timeoutSeconds);
    }
}