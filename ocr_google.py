import sys
import json
import io
import os
from google.cloud import vision
from pdf2image import convert_from_path

def extract_text_from_image(image_path):
    client = vision.ImageAnnotatorClient()
    with io.open(image_path, 'rb') as image_file:
        content = image_file.read()

    image = vision.Image(content=content)
    response = client.text_detection(image=image)

    if response.error.message:
        return {"error": f"OCR error: {response.error.message}"}

    texts = response.text_annotations
    return {"text": texts[0].description if texts else ""}


def extract_text_from_pdf(pdf_path):
    # Convert PDF pages to images
    try:
        images = convert_from_path(pdf_path)
    except Exception as e:
        return {"error": f"OCR error: Unable to convert PDF ({str(e)})"}


    all_text = ""
    for img in images:
        temp_img = "temp_page.png"
        img.save(temp_img, 'PNG')
        result = extract_text_from_image(temp_img)
        if "text" in result:
            all_text += result["text"] + "\n"
        os.remove(temp_img)
    return {"text": all_text.strip()}


if __name__ == "__main__":
    try:
        raw_input = sys.stdin.read().strip()
        if not raw_input:
            print(json.dumps({"error": "No input received"}))
            sys.exit(1)

        payload = json.loads(raw_input)
        path = payload.get("path")

        if not path or not os.path.exists(path):
            print(json.dumps({"error": f"File not found: {path}"}))
            sys.exit(1)

        ext = os.path.splitext(path)[1].lower()
        result = {}

        if ext in [".jpg", ".jpeg", ".png", ".tiff", ".bmp"]:
            result = extract_text_from_image(path)
        elif ext == ".pdf":
            result = extract_text_from_pdf(path)
        else:
            result = {"error": f"Unsupported file format: {ext}"}

        # ✅ Print final result as JSON (for Java to parse)
        print(json.dumps(result, ensure_ascii=False))

    except Exception as e:
        # Catch any unexpected error and return as JSON
        print(json.dumps({"error": f"OCR fatal error: {str(e)}"}))
        sys.exit(1)
