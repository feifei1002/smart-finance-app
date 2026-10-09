# Transaction categorisation service (FastAPI + DeBERTa zero-shot)
# Build context: the ml-service/ folder
#   docker build -t smart-finance-ml ml-service
# On Railway: set Root Directory to /ml-service (the Dockerfile is then found automatically)

# Base image pinned by digest so rebuilds use exactly the image we tested
# (tag kept for readability; the digest is what Docker uses).
FROM python:3.12-slim@sha256:05cda9777409a9c3ffddd94a4c476b79f0769a0b4857f0c7ed9226b6800b0d6f

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    HF_HOME=/models

WORKDIR /app

# Python dependencies are pinned:
#   requirements.txt - direct dependencies (exact versions)
#   constraints.txt  - every indirect dependency too (full lock from the tested image)
COPY requirements.txt constraints.txt ./

# CPU-only PyTorch from the PyTorch index. The default PyPI wheel bundles CUDA
# (several GB) and Railway has no GPU. The version is read from requirements.txt
# so it's defined in one place. --no-deps: torch's own dependencies are installed
# in the next step from PyPI, pinned by constraints.txt.
RUN TORCH_VERSION="$(tr -d '\r' < requirements.txt | sed -n 's/^torch==//p')" \
 && test -n "$TORCH_VERSION" \
 && pip install --no-deps --index-url https://download.pytorch.org/whl/cpu "torch==${TORCH_VERSION}"

# Everything else from PyPI at the pinned versions (torch==X is already satisfied
# by the X+cpu build above). pip check fails the build if anything is inconsistent.
RUN pip install -r requirements.txt -c constraints.txt \
 && pip check

# Pin the model to an exact commit so rebuilds are reproducible.
# app.py reads these same variables, so the model is defined in one place.
# To switch or update the model, change these two values and rebuild.
ENV MODEL_ID=MoritzLaurer/deberta-v3-large-zeroshot-v2.0 \
    MODEL_REVISION=cf44676c28ba7312e5c5f8f8d2c22b3e0c9cdae2

# Bake the model into the image so containers start without downloading ~900 MB
# from Hugging Face on every deploy/restart. Only the PyTorch weights + tokenizer
# are fetched; the repo's ONNX folder (~1.7 GB) is skipped.
# chmod lets the non-root "app" user read the whole cache (avoids a permission warning).
RUN python -c "import os; from huggingface_hub import snapshot_download; \
snapshot_download(os.environ['MODEL_ID'], revision=os.environ['MODEL_REVISION'], \
allow_patterns=['*.json', '*.safetensors', 'spm.model'], ignore_patterns=['onnx/*'])" \
 && chmod -R a+rX /models

# From here on, never try to reach Hugging Face at runtime.
ENV HF_HUB_OFFLINE=1 \
    TRANSFORMERS_OFFLINE=1

COPY app.py .

RUN useradd --system --uid 10001 --no-create-home app
USER app

# Binds 0.0.0.0 by default. If Railway's private network can't reach it,
# set the service variable HOST=:: (IPv6) on Railway.
ENV PORT=8000
EXPOSE 8000
CMD ["sh", "-c", "exec uvicorn app:app --host ${HOST:-0.0.0.0} --port ${PORT} --workers 1"]