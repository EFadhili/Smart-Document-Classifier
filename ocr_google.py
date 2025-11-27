#!/usr/bin/env python3
# ocr_google.py - Service Account only for Vision API
import sys
import json
import os
import io
from pdf2image import convert_from_path
from google.cloud import vision


def setup_vision_client():
    """Set up Vision client using service account credentials only"""
    try:
        # DEBUG: Extensive environment checking
        creds_path = os.environ.get('GOOGLE_APPLICATION_CREDENTIALS')
        # Debug info is now only printed to stderr, not mixed with stdout

        if not creds_path:
            error_msg = "GOOGLE_APPLICATION_CREDENTIALS environment variable not set"
            print(f"ERROR: {error_msg}", file=sys.stderr)
            return {"error": error_msg}

        if not os.path.exists(creds_path):
            error_msg = f"Service account file not found: {creds_path}"
            print(f"ERROR: {error_msg}", file=sys.stderr)
            return {"error": error_msg}

        # Verify it's a service account file
        try:
            with open(creds_path, 'r') as f:
                creds_data = json.load(f)

            if creds_data.get('type') != 'service_account':
                error_msg = f"Wrong credentials type. Expected 'service_account', got '{creds_data.get('type')}'"
                print(f"ERROR: {error_msg}", file=sys.stderr)
                return {"error": error_msg}

        except Exception as e:
            error_msg = f"Invalid credentials file: {e}"
            print(f"ERROR: {error_msg}", file=sys.stderr)
            return {"error": error_msg}

        # Create client
        client = vision.ImageAnnotatorClient()
        return {"client": client, "status": "success"}

    except ImportError as e:
        error_msg = "google-cloud-vision not installed. Run: pip install google-cloud-vision"
        print(f"ERROR: {error_msg} - {e}", file=sys.stderr)
        return {"error": error_msg}

    except Exception as e:
        error_msg = f"Failed to create Vision client: {e}"
        print(f"ERROR: {error_msg}", file=sys.stderr)
        return {"error": error_msg}


def ocr_pdf_with_service_account(path):
    """OCR PDF using service account"""
    result = setup_vision_client()
    if "error" in result:
        return result

    client = result["client"]

    try:
        images = convert_from_path(path)
        all_text = []

        for i, img in enumerate(images):
            try:
                buf = io.BytesIO()
                img.save(buf, format="PNG")
                buf.seek(0)
                image_content = buf.read()

                image = vision.Image(content=image_content)
                response = client.document_text_detection(image=image)

                if response.error.message:
                    all_text.append(f"\n--- Page {i+1} Error: {response.error.message} ---\n")
                    continue

                if response.full_text_annotation:
                    text = response.full_text_annotation.text
                    if text.strip():
                        all_text.append(f"\n--- Page {i+1} ---\n{text}")
                    else:
                        all_text.append(f"\n--- Page {i+1} No Text ---\n")
                else:
                    all_text.append(f"\n--- Page {i+1} No Text ---\n")

            except Exception as e:
                all_text.append(f"\n--- Page {i+1} Error: {str(e)} ---\n")
                continue

        full_text = "".join(all_text).strip()
        return {
            "text": full_text if full_text else "[No text extracted from PDF]",
            "method": "vision_service_account",
            "pages": len(images)
        }

    except Exception as e:
        return {"error": f"PDF OCR failed: {str(e)}"}


def ocr_image_with_service_account(path):
    """OCR image using service account"""
    result = setup_vision_client()
    if "error" in result:
        return result

    client = result["client"]

    try:
        with open(path, "rb") as f:
            content = f.read()

        image = vision.Image(content=content)
        response = client.document_text_detection(image=image)

        if response.error.message:
            return {"error": f"Vision API error: {response.error.message}"}

        text = response.full_text_annotation.text if response.full_text_annotation else ""

        return {
            "text": text if text.strip() else "[No text found in image]",
            "method": "vision_service_account"
        }

    except Exception as e:
        return {"error": f"Image OCR failed: {str(e)}"}


def extract_text_directly(path):
    """Extract text from text files"""
    try:
        encodings = ['utf-8', 'latin-1', 'windows-1252']
        for encoding in encodings:
            try:
                with open(path, 'r', encoding=encoding) as f:
                    content = f.read().strip()
                if content:
                    return {
                        "text": content,
                        "method": f"direct_text_{encoding}"
                    }
            except UnicodeDecodeError:
                continue

        # Fallback
        with open(path, 'rb') as f:
            content = f.read().decode('utf-8', errors='ignore').strip()

        return {
            "text": content if content else "[Empty file]",
            "method": "binary_fallback"
        }

    except Exception as e:
        return {"error": f"Text extraction failed: {str(e)}"}


def main():
    try:
        # Read input
        raw_input = sys.stdin.read().strip()
        if not raw_input:
            print(json.dumps({"error": "No input provided"}))
            return

        payload = json.loads(raw_input)
        path = payload.get("path")

        if not path or not os.path.exists(path):
            print(json.dumps({"error": "File not found"}))
            return

        # Set poppler path if provided
        poppler_path = payload.get("poppler_path")
        if poppler_path:
            os.environ["POPPLER_PATH"] = poppler_path

        ext = os.path.splitext(path)[1].lower()

        # Process based on file type
        if ext in ['.txt', '.text', '.md']:
            result = extract_text_directly(path)
        elif ext == '.pdf':
            result = ocr_pdf_with_service_account(path)
        elif ext in ['.png', '.jpg', '.jpeg', '.tiff', '.bmp']:
            result = ocr_image_with_service_account(path)
        else:
            result = {"error": f"Unsupported file type: {ext}"}

        # ONLY output JSON to stdout - this is critical!
        print(json.dumps(result, ensure_ascii=False))

    except Exception as e:
        print(json.dumps({"error": f"OCR failed: {str(e)}"}))


if __name__ == "__main__":
    main()