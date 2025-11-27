# python/ai_bridge.py
import os
import sys
import json
import io
import logging
import re

# Optional imports (Google Vision, pdf2image)
try:
    from google.cloud import vision
    HAVE_VISION = True
except Exception:
    HAVE_VISION = False

try:
    from pdf2image import convert_from_path
    HAVE_PDF2IMAGE = True
except Exception:
    HAVE_PDF2IMAGE = False

# Optional NLTK for better preprocessing; fallback otherwise
try:
    import nltk
    from nltk.corpus import stopwords
    from nltk.tokenize import word_tokenize
    from nltk.stem import WordNetLemmatizer
    HAVE_NLTK = True
except Exception:
    HAVE_NLTK = False

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ai_bridge")

# Initialize Google Vision client lazily
_vision_client = None
def _get_vision_client():
    global _vision_client
    if _vision_client is None:
        if not HAVE_VISION:
            raise RuntimeError("google-cloud-vision not installed in this environment.")
        _vision_client = vision.ImageAnnotatorClient()
    return _vision_client

# OCR for image bytes using Google Vision
def ocr_image_bytes(image_bytes: bytes) -> str:
    client = _get_vision_client()
    image = vision.Image(content=image_bytes)
    resp = client.document_text_detection(image=image)
    if resp.error and resp.error.message:
        raise RuntimeError(f"Vision API error: {resp.error.message}")
    return resp.full_text_annotation.text or ""

# OCR for files (images or pdfs)
def ocr_file(path: str, poppler_path: str = None) -> str:
    path = os.path.abspath(path)
    ext = path.lower().rsplit(".", 1)[-1]
    if ext in ("png", "jpg", "jpeg", "tiff", "tif", "bmp"):
        with open(path, "rb") as f:
            data = f.read()
        return ocr_image_bytes(data)
    elif ext == "pdf":
        # If google vision supports direct PDF via storage, that's complicated --
        # for local usage convert pages to images and OCR each page.
        if not HAVE_PDF2IMAGE:
            raise RuntimeError("pdf2image not installed. Install pdf2image and poppler.")
        # Optionally pass poppler_path via environment variable POPPLER_PATH or arg
        poppler_option = poppler_path or os.getenv("POPPLER_PATH")
        images = convert_from_path(path, dpi=300, poppler_path=poppler_option) if poppler_option else convert_from_path(path, dpi=300)
        texts = []
        for page in images:
            buf = io.BytesIO()
            page.save(buf, format="JPEG")
            buf.seek(0)
            texts.append(ocr_image_bytes(buf.read()))
        return "\n\n".join(t for t in texts if t)
    else:
        raise ValueError("Unsupported file extension for OCR: " + ext)

# TEXT PREPROCESSING
# If NLTK available use it; otherwise do a lighter-weight normalize
def _init_nltk():
    try:
        nltk.data.find('tokenizers/punkt')
    except:
        nltk.download('punkt', quiet=True)
    try:
        nltk.data.find('corpora/stopwords')
    except:
        nltk.download('stopwords', quiet=True)
    try:
        nltk.data.find('corpora/wordnet')
    except:
        nltk.download('wordnet', quiet=True)

def normalize_text(text: str) -> str:
    if not text:
        return ""
    text = text.replace('\r\n', '\n')
    text = text.strip()
    # Basic cleaning
    text = re.sub(r'\s+', ' ', text)
    # Lowercase
    text = text.lower()
    # Remove undesired characters but keep digits (dates) and letters
    text = re.sub(r'[^a-z0-9\s\-\/\.,]', ' ', text)
    # If NLTK available use tokenization, stopword removal, lemmatization
    if HAVE_NLTK:
        try:
            _init_nltk()
            stop_words = set(stopwords.words('english'))
            lemmatizer = WordNetLemmatizer()
            tokens = word_tokenize(text)
            clean_tokens = []
            for t in tokens:
                if t.isalpha() and t not in stop_words and len(t) > 1:
                    clean_tokens.append(lemmatizer.lemmatize(t))
            return " ".join(clean_tokens)
        except Exception as e:
            logger.warning("NLTK path failed, falling back to simple normalizer: %s", e)
    # Fallback simple approach: remove short tokens and stopwords list approximation
    # small built-in stopword set
    small_stop = {"the","and","for","with","that","this","from","are","was","is","by","on","of","in","to","a","an"}
    tokens = text.split()
    tokens = [t for t in tokens if t not in small_stop and len(t) > 2]
    return " ".join(tokens)

# Request handler: supports ocr_vision, preprocess, ocr_and_preprocess
def handle_request(payload: dict) -> dict:
    mode = payload.get("mode")
    if mode == "ocr_vision":
        path = payload.get("path")
        if not path:
            return {"ok": False, "error": "missing path"}
        try:
            poppler = payload.get("poppler_path")
            txt = ocr_file(path, poppler_path=poppler)
            return {"ok": True, "text": txt}
        except Exception as e:
            return {"ok": False, "error": str(e)}
    elif mode == "preprocess":
        txt = payload.get("text", "")
        try:
            norm = normalize_text(txt)
            return {"ok": True, "normalized": norm}
        except Exception as e:
            return {"ok": False, "error": str(e)}
    elif mode == "ocr_and_preprocess":
        path = payload.get("path")
        poppler = payload.get("poppler_path")
        try:
            raw = ocr_file(path, poppler_path=poppler)
            norm = normalize_text(raw)
            return {"ok": True, "text": raw, "normalized": norm}
        except Exception as e:
            return {"ok": False, "error": str(e)}
    else:
        return {"ok": False, "error": "unknown_mode"}

def main_from_stdin():
    try:
        in_str = sys.stdin.read()
        if not in_str:
            # nothing passed
            print(json.dumps({"ok": False, "error": "no_input"}))
            return
        payload = json.loads(in_str)
        out = handle_request(payload)
        print(json.dumps(out, ensure_ascii=False))
    except Exception as e:
        print(json.dumps({"ok": False, "error": str(e)}))

if __name__ == "__main__":
    main_from_stdin()
