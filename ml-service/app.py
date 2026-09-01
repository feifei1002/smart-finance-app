from fastapi import FastAPI
from pydantic import BaseModel
import torch
import re
from transformers import pipeline

# Initialize the FastAPI app
app = FastAPI(title="Transaction Classifier API")

EXACT_BRAND_LOOKUP = [
    # 1. Food Delivery & Specific Local Food Brands
    (r"\b(UBER EATS|DELIVEROO|JUST EAT|HAPPY MYMENU)\b", "Food & Dining"),
    (r"\b(PIZZA EXPRESS|ASDA EXPRESS|TESCO EXPRESS|SAINSBURY'S LOCAL)\b", "Food & Dining"),

    # 2. Supermarkets, Cafes, Bakeries & Restaurants
    (r"\b(TESCO|SAINSBURY|SAINSBURY'S|MARKS & SPENCER|MARKS&SPENCER|M&S|WAITROSE|MORRISONS|ALDI|LIDL|COSTCO|ASDA|SPAR|WHOLE FOODS|TAZAKI|CHUANGLEE|OSEYO|WANA HONG|WANAHONG|TIAN TIAN|COSTA|CAFFE NERO|GAIL|GAIL'S|PRET|PRET A MANGER|STARBUCKS|MCDONALD|KFC|WASABI|SHORYU|HAIDILAO|FLAT IRON|LOUNGERS|CHINATOWN BAKERY|MILLE|SHAKE SHACK|WINGSTOP|BURGER & LOBSTER|PAUL|EAT TOKYO|KINEYA|KIKI & MIUMIU|LA MARITXU|DOUGLAS BAKERY|BENTO|RAMEN|SUSHI|BAKERY|BISTRO|PUB|BAR|TAVERN|RESTAURANT|CAFÉ|CAFE|COFFEE|SUPERMARKET|GROCERY|GROCERIES|CASH & CARRY|CASH AND CARRY|RESTORANAS|KRCMA)\b", "Food & Dining"),

    # 3. Transportation Networks (Negative lookahead excludes Uber Eats)
    (r"\b(TFL|TRANSPORT FOR LONDON|TRANSPORT FOR WALES|EMT MADRID|RATP|TRAINLINE|RAILCARD|GRAB|FLIXBUS|REGIOJET|ARRIVA|BEE NETWORK|BKK|DOPRAVNI PODNIK|JUDU|LUX EXPRESS|DUBLIN EXPRESS|POSTAJA|TALLINNA|AVTOBUSNA|TRAINPAL|NEXTBIKE|UBER(?! EATS)|BOLT|METRO|BUS|BUSES|TRAIN|RAILWAY|RAIL|AIRLINE|AIRWAYS?|FLIGHT|TAXI|CAB|PARKING|TRANSIT|STATION)\b", "Transportation"),

    # 4. Shopping, Retail & Personal Care
    (r"\b(JOHN LEWIS|SELFRIDGES|PRIMARK|HOLLAND & BARRETT|HOLLAND AND BARRETT|TK MAXX|AMAZON|SHEIN|PULL&BEAR|PULL AND BEAR|JELLYCAT|MOSS BROS|WHSMITH|BOOTS|WATERSTONES|MUJI|H&M|DR\.MAX|ZEEMAN|UNIQLO|ZARA|ASOS|BACK MARKET|VISION EXPRESS|FREE PRINTS|SEA BARBER|SEABARBER|MOOMIN|SIZE\?|MAC STRATFORD|PHARMACY|BARBER|SALON)\b", "Shopping & Personal"),

    # 5. Bills & Housing
    (r"\b(LEBARA|VOXI|OCTOPUS ENERGY|ROYAL GREENWICH|LOVESPACE|SAFESTORE|AIRWALLET|EDF|(?!THE )O2(?! VENUE)|EE|VODAFONE|BRITISH GAS|UTILITIES|COUNCIL|RENT|MOBILE|TELECOM)\b", "Bills & Housing"),

    # 6. Entertainment, Lodging & Subscriptions
    (r"\b(NETFLIX|SPOTIFY|BOOKING\.COM|AGODA|TRIP\.COM|NORDVPN|EXPRESSVPN|OPENAI|ANTHROPIC|GITHUB|LINKEDIN|UDEMY|THE GYM GROUP|GLL BETTER|TRADING 212|GOOGLE PLAY|NINTENDO|AXS TICKETS|TICKETMASTER|ODEON|CINEWORLD|VUE|SHOWCASE|VICTORIA AND ALBERT MUSEUM|NATIONAL MARITIME MUSEUM|MUSEUM|CASTLE|CATHEDRAL|PALACE|GUINNESS STOREHOUSE|LIVRARIA LELLO|CONWY VISITORS|AIRBNB|HILTON|IBIS|TRAVELODGE|HOTEL|HOSTEL|GUESTHOUSE|ZOO|COLOSSEUM|BLUETICKET|JEGYMESTER)\b", "Entertainment & Subscriptions")
]


def get_exact_brand_match(description: str):
    for pattern, category in EXACT_BRAND_LOOKUP:
        if re.search(pattern, description, re.IGNORECASE):
            return category
    return None

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

    if not clean_desc:
        return {"category": "Miscellaneous"}

    exact_category = get_exact_brand_match(req.description) or get_exact_brand_match(clean_desc)
    if exact_category is not None:
        return {
            "category": exact_category
        }

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

    return {
        "category": mapped_category
    }
