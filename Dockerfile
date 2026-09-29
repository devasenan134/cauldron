# Website build
FROM node:20-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# API server, serving the built website
FROM python:3.12-slim
# ffmpeg: yt-dlp joins Instagram's separate video and sound streams with it (recipe imports)
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv
WORKDIR /app/server
COPY server/pyproject.toml server/uv.lock ./
RUN uv sync --frozen --no-dev --python /usr/local/bin/python3
COPY server/ ./
COPY --from=web /web/dist /app/web/dist
ENV CAULDRON_DB=/data/cauldron.db CAULDRON_WEB=/app/web/dist
EXPOSE 8000
CMD ["uv", "run", "--no-sync", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
