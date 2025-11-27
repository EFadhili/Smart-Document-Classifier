# list_models_safe.py
import google.generativeai as genai
import json

def print_model(m):
    # Try dict-like access first
    try:
        if isinstance(m, dict):
            name = m.get("name") or m.get("model") or m.get("id") or "<no-name>"
            display = m.get("displayName") or m.get("display_name") or m.get("title") or ""
            methods = m.get("supported_generation_methods") or m.get("supported_methods") or m.get("features") or ""
            print(f"- NAME: {name}")
            if display:
                print(f"    display: {display}")
            if methods:
                print(f"    methods: {methods}")
            return
    except Exception:
        pass

    # Try attribute access (protobuf / SDK objects)
    try:
        name = getattr(m, "name", None) or getattr(m, "model", None) or getattr(m, "id", None)
        display = getattr(m, "display_name", None) or getattr(m, "displayName", None) or getattr(m, "title", None)
        methods = getattr(m, "supported_generation_methods", None) or getattr(m, "supported_methods", None)
        print(f"- NAME: {name}")
        if display:
            print(f"    display: {display}")
        if methods:
            print(f"    methods: {methods}")
        return
    except Exception:
        pass

    # Fallback: print raw object
    try:
        print("- RAW:", json.dumps(m, default=str, indent=2))
    except Exception:
        print("- RAW:", str(m))

def main():
    print("Listing available models:\n")
    try:
        models_gen = genai.list_models()
    except Exception as e:
        print("Error calling list_models():", e)
        return

    try:
        count = 0
        for m in models_gen:
            count += 1
            print_model(m)
        if count == 0:
            print("No models returned (count=0).")
        else:
            print(f"\nTotal models listed: {count}")
    except TypeError:
        # Not iterable? try printing directly
        print("Returned object not iterable, raw output:")
        print(models_gen)

if __name__ == "__main__":
    main()
