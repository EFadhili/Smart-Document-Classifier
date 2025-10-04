package org.example.core;

import java.io.File;

public interface OcrBridge {
    String ocr(File file) throws Exception;
}
