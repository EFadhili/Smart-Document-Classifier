package org.example.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.*;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class TextExtractor {

    private final OcrBridge ocrBridge;  // placeholder for OCR

    // Constructor allows injecting OCR bridge (null if unavailable now)
    public TextExtractor(OcrBridge ocrBridge) {
        this.ocrBridge = ocrBridge;
    }

    public String extract(File file) throws Exception {
        String name = file.getName().toLowerCase();

        if (name.endsWith(".docx")) {
            return extractFromDocx(file);
        } else if (name.endsWith(".pdf")) {
            String text = extractFromPdf(file);
            if (text.trim().length() > 100) {
                return text; // machine-readable PDF
            } else if (ocrBridge != null) {
                // fallback to OCR when integrated
                return ocrBridge.ocr(file);
            } else {
                return "[Scanned PDF detected, OCR not yet enabled]";
            }
        } else if (name.endsWith(".txt")) {
            try {
                // Read all bytes
                byte[] bytes = Files.readAllBytes(file.toPath());

                // First, try UTF-8
                return new String(bytes, StandardCharsets.UTF_8);

            } catch (MalformedInputException mie) {
                try {
                    // Fallback: system default charset (often Windows-1252 on Windows)
                    return new String(Files.readAllBytes(file.toPath()));
                } catch (Exception e) {
                    e.printStackTrace();
                    return "";
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }

        } else if (isImage(name)) {
            if (ocrBridge != null) {
                return ocrBridge.ocr(file);
            } else {
                return "[Image file detected, OCR not yet enabled]";
            }
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + name);
        }
    }

    private String extractFromPdf(File file) {
        try (PDDocument doc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (Exception e) {
            return "";
        }
    }

    private String extractFromDocx(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {

            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));

            return sb.toString();

        }
             catch (Exception e) {
            return "";
        }
    }

    private boolean isImage(String name) {
        return name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".tiff")
                || name.endsWith(".tif");
    }
}
