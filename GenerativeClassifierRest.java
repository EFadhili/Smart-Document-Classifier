package org.example.core;

import com.google.gson.*;
import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;


public class GenerativeClassifierRest {
    private final String modelId; 
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();
    private static final Gson GSON = new Gson();

    public GenerativeClassifierRest(String model) { 
        if (model == null) throw new IllegalArgumentException("model is required");
        this.modelId = model.startsWith("models/") ? model.substring("models/".length()) : model;
    }

    public Result classifyWithToken(String text, String[] allowedLabels, String accessToken) throws Exception {
        if (text == null) text = "";
        if (allowedLabels == null || allowedLabels.length == 0)
            throw new IllegalArgumentException("allowedLabels must not be empty");
        if (accessToken == null || accessToken.isBlank())
            throw new IllegalArgumentException("accessToken is required");

        // Build instruction
        String instruction =
                "Classify the document into exactly ONE of these labels: " + String.join(", ", allowedLabels) + ".\n" +
                        "Return ONLY compact JSON like {\"label\":\"<one_of_labels>\",\"reason\":\"<short why>\"}.\n" +
                        "Do not include code fences or extra commentary.";

        // v1beta request body
        JsonObject rootReq = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", instruction + "\n\nDocument:\n" + text);
        parts.add(part);
        userMsg.add("parts", parts);
        contents.add(userMsg);
        rootReq.add("contents", contents);

        // Endpoint
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modelId + ":generateContent";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(rootReq), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        String body = resp.body();

        if (code >= 400) {
            throw new RuntimeException("Gemini classify error: HTTP " + code + " -> " + body);
        }
        if (body == null || body.isBlank()) {
            return new Result(null, null, null); // nothing returned; caller keeps SVM label
        }

        // Safely extract the first candidate text
        String textOut = extractFirstText(body);

        if (textOut == null || textOut.isBlank()) {
            // Could be safety blocked or empty – caller should keep SVM label
            return new Result(null, null, body);
        }

        textOut = textOut.trim();

        // If the model followed instructions and returned JSON, parse it
        if (looksLikeJson(textOut)) {
            try {
                JsonObject obj = JsonParser.parseString(textOut).getAsJsonObject();
                String label = asString(obj, "label");
                String reason = asString(obj, "reason");
                // sanity-check label against allowed set
                if (label != null && !containsIgnoreCase(allowedLabels, label)) {
                  
                    String coerced = firstAllowedInText(allowedLabels, label);
                    if (coerced != null) label = coerced;
                }
                return new Result(label, reason, body);
            } catch (Exception ignore) {
                // fall through to plain-text matching
            }
        }

        // Fallback: find the first allowed label mentioned as a word
        String label = firstAllowedInText(allowedLabels, textOut);
        return new Result(label, null, body);
    }

    private static boolean looksLikeJson(String s) {
        String t = s.trim();
        return t.startsWith("{") && t.endsWith("}");
    }

    private static String asString(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : null;
    }

    private static boolean containsIgnoreCase(String[] arr, String v) {
        for (String a : arr) if (a.equalsIgnoreCase(v)) return true;
        return false;
    }

    private static String firstAllowedInText(String[] allowed, String haystack) {
        String lower = haystack.toLowerCase();
        for (String label : allowed) {
            String pattern = "\\b" + Pattern.quote(label.toLowerCase()) + "\\b";
            if (lower.matches(".*" + pattern + ".*")) return label;
        }
        // loose contains as last resort
        for (String label : allowed) {
            if (lower.contains(label.toLowerCase())) return label;
        }
        return null;
    }

    /** Pulls candidates[0].content.parts[0].text if present, otherwise null. */
    private static String extractFirstText(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("candidates")) return null;
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates.size() == 0) return null;
            JsonObject cand0 = candidates.get(0).getAsJsonObject();
            if (!cand0.has("content")) return null;
            JsonObject content = cand0.getAsJsonObject("content");
            if (!content.has("parts")) return null;
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts.size() == 0) return null;
            JsonObject p0 = parts.get(0).getAsJsonObject();
            if (!p0.has("text") || p0.get("text").isJsonNull()) return null;
            return p0.get("text").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    public static class Result {
        public final String label;
        public final String reason;
        public final String raw; // raw HTTP JSON body (for debugging)
        public Result(String label, String reason, String raw) {
            this.label = label; this.reason = reason; this.raw = raw;
        }
    }
}

