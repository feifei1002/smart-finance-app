from fastapi import FastAPI
from pydantic import BaseModel
import torch
import re
from transformers import pipeline

# Initialize the FastAPI app
app = FastAPI(title="Transaction Classifier API")

# 1. Load DeBERTa (Runs once when the server starts)
device = 0 if torch.cuda.is_available() else -1
print(f"Loading DeBERTa-v3 Large on device index: {device}...")

classifier = pipeline(
    "zero-shot-classification",
    model="MoritzLaurer/deberta-v3-large-zeroshot-v2.0",
    device=device,
    torch_dtype=torch.float16 if device == 0 else torch.float32,
)

# 2. Setup Verbalizers (Exactly as you wrote them)
taxonomy_verbalizers = {
    "Food & Dining": "supermarkets, groceries, restaurants, cafes, or food delivery",
    "Shopping & Personal": "clothing stores, retail shopping, electronics, or personal care",
    "Bills & Housing": "mobile phone bills, electricity, gas, utilities, or housing expenses",
    "Entertainment & Subscriptions": "digital subscriptions, streaming, hotels, museums, or event tickets",
    "Transportation": "public transport, train tickets, flights, bus fares, or ride-hailing"
}
verbalizer_to_category = {v: k for k, v in taxonomy_verbalizers.items()}
candidate_verbalizers = list(taxonomy_verbalizers.values())
hypothesis_template = "This purchase was for {}."

# Define the expected JSON payload
class TransactionRequest(BaseModel):
    description: str

@app.post("/classify")
def classify_transaction(req: TransactionRequest):
    # 3. Clean the description (Mimicking your Pandas split/strip logic)
    clean_desc = re.split(r"[#\-\(]", req.description)[0].strip()

    # 4. Augment text
    augmented_text = f"Merchant name: {clean_desc}"

    # 5. Run the model
    res = classifier(
        augmented_text,
        candidate_labels=candidate_verbalizers,
        hypothesis_template=hypothesis_template,
        multi_label=False,
    )

    # 6. Map back to your category
    top_verbalizer = res["labels"][0]
    mapped_category = verbalizer_to_category.get(top_verbalizer, "Miscellaneous")

    return {"category": mapped_category}
