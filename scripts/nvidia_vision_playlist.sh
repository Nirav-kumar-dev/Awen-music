#!/usr/bin/env bash
# NVIDIA DiffusionGemma 26B Vision Chat Completion Script
# Generates music playlist recommendations based on an image

stream=false
if [ "$stream" = true ]; then
    accept_header='Accept: text/event-stream'
else
    accept_header='Accept: application/json'
fi

NVIDIA_API_KEY="${NVIDIA_API_KEY:-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}"
IMAGE_URL="${1:-https://assets.ngc.nvidia.com/products/api-catalog/phi-3-5-vision/example1b.jpg}"

cat > /tmp/payload.json <<JSON
{
  "messages": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "Analyze the scene, lighting, mood, and aesthetic of this image. Create an evocative music playlist that perfectly fits this visual vibe. Output valid JSON in the format: {\"title\": \"Playlist Name\", \"mood\": \"Mood Description\", \"songs\": [\"Song Title - Artist\", ...]}"
        },
        {
          "type": "image_url",
          "image_url": {
            "url": "$IMAGE_URL"
          }
        }
      ]
    }
  ],
  "model": "google/diffusiongemma-26b-a4b-it",
  "chat_template_kwargs": {
    "enable_thinking": true
  },
  "max_tokens": 4096,
  "stream": false,
  "temperature": 1,
  "top_p": 0.95
}
JSON

curl https://integrate.api.nvidia.com/v1/chat/completions \
  -H "Authorization: Bearer $NVIDIA_API_KEY" \
  -H "Content-Type: application/json" \
  -H "$accept_header" \
  -d @/tmp/payload.json
