#!/usr/bin/env bash
# NVIDIA Nemotron 3.5 Lightning 30B A3B Music Assistant Script
# Generates music recommendations, mood playlists, and answers music queries

stream=false
if [ "$stream" = true ]; then
    accept_header='Accept: text/event-stream'
else
    accept_header='Accept: application/json'
fi

NVIDIA_API_KEY="${NVIDIA_API_KEY:-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}"
USER_PROMPT="${1:-Create a 5-song late night drive mood playlist}"
USER_NAME="${USER_NAME:-User}"
USER_HANDLE="${USER_HANDLE:-user}"
USER_PREFS="${USER_PREFS:-AI Sync: enabled, Dolby Atmos: enabled, Equalizer: enabled, Custom Playlists: enabled}"

cat > /tmp/nemotron_payload.json <<JSON
{
  "model": "nvidia/nemotron-3.5-lightning-30b-a3b",
  "messages": [
    {
      "role": "system",
      "content": "You are Awen AI, an intelligent, personalized music curator with full access to Awen music app and the user profile (Name: $USER_NAME, Handle: @$USER_HANDLE, Preferences: $USER_PREFS). Analyze the user's mood, query, or genre request. Respond in valid raw JSON with keys: 'title' (evocative playlist title), 'vibe' (concise description of the mood/sound), 'message' (warm, personalized 1-2 sentence response to user), and 'songs' (array of 5 to 8 real song objects, each with 'title' and 'artist'). Do not include markdown or reasoning."
    },
    {
      "role": "user",
      "content": "$USER_PROMPT"
    }
  ],
  "chat_template_kwargs": {
    "enable_thinking": false
  },
  "max_tokens": 2048,
  "temperature": 0.7,
  "top_p": 0.95,
  "stream": false
}
JSON

curl https://integrate.api.nvidia.com/v1/chat/completions \
  -H "Authorization: Bearer $NVIDIA_API_KEY" \
  -H "Content-Type: application/json" \
  -H "$accept_header" \
  -d @/tmp/nemotron_payload.json
