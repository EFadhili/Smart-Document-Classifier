package org.example.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.*;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class TextExtractor {

    private final OcrBridge ocrBridge;

    public TextExtractor(OcrBridge ocrBridge) {
        this.ocrBridge = ocrBridge;
    }

    public String extract(File file) throws Exception {
        String name = file.getName().toLowerCase();

        System.out.println("📄 Extracting text from: " + file.getName());
        System.out.println("🔍 File type: " + name);

        if (name.endsWith(".docx")) {
            System.out.println("📝 Using DOCX extractor");
            return extractFromDocx(file);
        } else if (name.endsWith(".pdf")) {
            // First try direct text extraction
            String text = extractFromPdf(file);
            System.out.println("📊 Direct PDF extraction result: " + text.length() + " characters");

            // If it's a scanned PDF (little text extracted), use OCR
            if (text.trim().length() < 100) {
                System.out.println("🔍 Low text count, using OCR for scanned PDF");
                if (ocrBridge != null) {
                    String ocrResult = ocrBridge.ocr(file);
                    System.out.println("📊 OCR result: " + ocrResult.length() + " characters");
                    return ocrResult;
                } else {
                    return "[Scanned PDF detected, OCR bridge not available]";
                }
            } else {
                System.out.println("✅ Machine-readable PDF, using direct extraction");
                return text;
            }
        } else if (name.endsWith(".txt")) {
            System.out.println("📝 Using text file extractor");
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String content = new String(bytes, StandardCharsets.UTF_8);
                System.out.println("📊 Text file content: " + content.length() + " characters");
                return content;
            } catch (MalformedInputException mie) {
                try {
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
            System.out.println("🖼️ Using OCR for image file");
            if (ocrBridge != null) {
                String ocrResult = ocrBridge.ocr(file);
                System.out.println("📊 OCR result: " + ocrResult.length() + " characters");
                return ocrResult;
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
            String text = stripper.getText(doc);
            System.out.println("📊 PDFBox extracted: " + text.length() + " characters");
            return text;
        } catch (Exception e) {
            System.err.println("❌ PDF extraction failed: " + e.getMessage());
            return "";
        }
    }

    private String extractFromDocx(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {

            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            String text = sb.toString();
            System.out.println("📊 DOCX extracted: " + text.length() + " characters");
            return text;

        } catch (Exception e) {
            System.err.println("❌ DOCX extraction failed: " + e.getMessage());
            return "";
        }
    }

    private boolean isImage(String name) {
        return name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".tiff")
                || name.endsWith(".tif") || name.endsWith(".bmp");
    }
}