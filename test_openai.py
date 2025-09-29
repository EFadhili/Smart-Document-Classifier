from openai import OpenAI
from dotenv import load_dotenv
import os

load_dotenv()  # Load from .env

client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

resp = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[{"role": "user", "content": "Hello, world! Can you summarize yourself in one sentence?"}]
)

print("OpenAI Response:")
print(resp.choices[0].message.content)
