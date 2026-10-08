# Transaction categorisation service (FastAPI + DeBERTa zero-shot)
# Build context: the ml-service/ folder
#   docker build -t smart-finance-ml ml-service
# On Railway: set Root Directory to /ml-service (the Dockerfile is then found automatically)

FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    HF_HOME=/models

WORKDIR /app

# CPU-only PyTorch first. The default wheel bundles CUDA (several GB) and
# Railway has no GPU. requirements.txt's torch>=2.0 is then already satisfied.
RUN pip install --index-url https://download.pytorch.org/whl/cpu torch

COPY requirements.txt .
RUN pip install -r requirements.txt

# Bake the model into the image so containers start without downloading ~900 MB
# from Hugging Face on every deploy/restart. Only the PyTorch weights + tokenizer
# are fetched; the repo's ONNX folder (~1.7 GB) is skipped.
# The model name must match the one in app.py — change both if you switch models.
RUN python -c "from huggingface_hub import snapshot_download; \
snapshot_download('MoritzLaurer/deberta-v3-large-zeroshot-v2.0', \
allow_patterns=['*.json', '*.safetensors', 'spm.model'], ignore_patterns=['onnx/*'])"

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