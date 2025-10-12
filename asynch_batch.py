from google.cloud import vision_v1 as vision
from google.cloud import storage
import json
import re
import time

def google_pdf_ocr(gcs_input_uri, gcs_output_uri):
    """Extract text from a multi-page PDF in GCS using Google Vision OCR."""
    client = vision.ImageAnnotatorClient()

    # Input and output configuration
    gcs_source = vision.GcsSource(uri=gcs_input_uri)
    gcs_destination = vision.GcsDestination(uri=gcs_output_uri)

    input_config = vision.InputConfig(gcs_source=gcs_source, mime_type="application/pdf")
    output_config = vision.OutputConfig(gcs_destination=gcs_destination, batch_size=5)

    feature = vision.Feature(type_=vision.Feature.Type.DOCUMENT_TEXT_DETECTION)
    request = vision.AsyncAnnotateFileRequest(
        features=[feature], input_config=input_config, output_config=output_config
    )

    # Start OCR job
    operation = client.async_batch_annotate_files(requests=[request])
    print("📄 OCR started... waiting for completion.")
    operation.result(timeout=600)
    print("✅ OCR completed.")

    # Fetch OCR output JSON from GCS
    storage_client = storage.Client()
    match = re.match(r"gs://([^/]+)/(.+)", gcs_output_uri)
    bucket_name, prefix = match.groups()
    bucket = storage_client.bucket(bucket_name)
    blob_list = list(bucket.list_blobs(prefix=prefix))

    text_output = ""
    for blob in blob_list:
        data = json.loads(blob.download_as_text())
        for resp in data["responses"]:
            annotation = resp.get("fullTextAnnotation")
            if annotation:
                text_output += annotation["text"] + "\n"
    return text_output

if __name__ == "__main__":
    input_pdf = "gs://legal-ocr-storage/inputs/test1_006.pdf"
    output_json_prefix = "gs://legal-ocr-storage/outputs/test1_006/"

    text = google_pdf_ocr(input_pdf, output_json_prefix)
    print("\nExtracted text:\n", text[:1000])  # print first 1000 characters
