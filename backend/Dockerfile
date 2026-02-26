FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy backend code
COPY backend/ .

# Create data directory
RUN mkdir -p /app/data

ENV WECHATMEM_DATA_DIR=/app/data
ENV WECHATMEM_DEBUG=false

EXPOSE 9721

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "9721"]
