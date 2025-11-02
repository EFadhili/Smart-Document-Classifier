from google.cloud import vision
import io
import json
import os

# Optionally print the credential path to confirm
print("Using credentials:", os.getenv("GOOGLE_APPLICATION_CREDENTIALS"))

def ocr_image(path):
    client = vision.ImageAnnotatorClient()
    with io.open(path, "rb") as image_file:
        content = image_file.read()
    image = vision.Image(content=content)
    # For general OCR use text_detection()
    response = client.text_detection(image=image)
    texts = response.text_annotations
    if response.error.message:
        raise Exception(response.error.message)

    if texts:
        return texts[0].description  # full text
    return ""

if __name__ == "__main__":
    print(ocr_image("C:/Users/EFM/Pictures/Screenshots/Screenshot 2025-09-20 225247.png"))
