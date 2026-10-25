"""Generate the sounds listed in docs/sfx-prompts.md with the ElevenLabs sound-generation API.

Usage: ELEVENLABS_API_KEY=... python -I tools/generate_sfx.py
Writes art/sfx/<name>.mp3, skipping names that already exist. One request at a time with a pause between
them; on a rate limit it backs off, and it stops on any other error. The key is read from the environment only.
"""
import json
import os
import pathlib
import re
import sys
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "art" / "sfx"
KEY = os.environ.get("ELEVENLABS_API_KEY")
if not KEY:
    sys.exit("Set ELEVENLABS_API_KEY")

rows = re.findall(r"^\| ([a-z_.]+) \| ([\d.]+)s \| (.+?) \|$", (ROOT / "docs" / "sfx-prompts.md").read_text(encoding="utf-8"), re.M)
OUT.mkdir(parents=True, exist_ok=True)
made = 0
for name, secs, prompt in rows:
    dest = OUT / f"{name}.mp3"
    if dest.exists():
        continue
    body = json.dumps({"text": prompt + ", single one-shot sound effect, no music, no voice",
                       "duration_seconds": float(secs), "prompt_influence": 0.6}).encode()
    for attempt in range(4):
        req = urllib.request.Request("https://api.elevenlabs.io/v1/sound-generation", data=body, method="POST",
                                     headers={"xi-api-key": KEY, "Content-Type": "application/json", "Accept": "audio/mpeg"})
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                dest.write_bytes(r.read())
            made += 1
            print(f"ok   {name}", flush=True)
            break
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")[:300]
            if e.code == 429 and attempt < 3:
                wait = 30 * (attempt + 1)
                print(f"429  {name}: waiting {wait}s", flush=True)
                time.sleep(wait)
                continue
            sys.exit(f"stop {name}: HTTP {e.code} {detail}")
    time.sleep(4)
print(f"done: {made} generated")
