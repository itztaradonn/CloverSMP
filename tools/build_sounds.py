"""Build resourcepack/assets/cloverblessing/sounds.json (and converted .ogg files).

Usage: python -I tools/build_sounds.py

For each sound name, if art/sfx/<name>.(mp3|wav|ogg|flac) exists it is converted with ffmpeg to a mono Ogg
Vorbis file in the pack (Minecraft only plays .ogg, and only mono sounds are positional); otherwise the name
plays a vanilla placeholder. Run it, then ./gradlew resourcePack, whenever files in art/sfx change.
"""
import json
import pathlib
import shutil
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "art" / "sfx"
PACK = ROOT / "resourcepack" / "assets" / "cloverblessing"

# name -> (vanilla placeholder event, pitch, volume)
SOUNDS = {
    "blessing.thornlash": ("entity.player.attack.crit", 1.4, 1.0),
    "blessing.stinger": ("entity.bee.sting", 1.2, 1.0),
    "blessing.mossguard": ("block.moss.place", 0.8, 1.0),
    "blessing.last_leaf": ("item.totem.use", 1.6, 0.5),
    "blessing.springleaf": ("entity.slime.jump", 1.2, 1.0),
    "blessing.quiet_dew": ("block.amethyst_block.chime", 1.8, 1.0),
    "blessing.leechvine": ("entity.generic.drink", 0.7, 1.0),
    "blessing.tangle_seed": ("block.cave_vines.place", 0.8, 1.0),
    "blessing.hobble": ("item.armor.equip_chain", 0.8, 1.0),
    "blessing.waystem_plant": ("block.azalea.place", 1.0, 1.0),
    "blessing.waystem_recall": ("entity.enderman.teleport", 1.2, 0.8),
    "blessing.glass_bloom": ("block.glass.hit", 1.6, 0.6),
    "blessing.wager_root_win": ("entity.player.levelup", 1.8, 0.4),
    "blessing.wager_root_loss": ("block.wood.break", 0.6, 1.0),
    "blessing.turning_year": ("block.amethyst_block.resonate", 1.0, 1.0),
    "curse.bloodroot": ("entity.warden.heartbeat", 1.2, 1.0),
    "curse.thorncrown": ("enchant.thorns.hit", 0.8, 1.0),
    "curse.hollow_bell": ("block.bell.use", 0.5, 1.0),
    "curse.hungering_clover": ("entity.player.burp", 0.5, 1.0),
    "clover.leaf_open": ("entity.player.levelup", 1.4, 0.6),
    "clover.leaf_wilt": ("block.grass.break", 0.6, 1.0),
    "clover.pact_take": ("entity.elder_guardian.curse", 0.8, 0.5),
    "clover.pact_break": ("block.glass.break", 0.6, 1.0),
    "craft.press": ("block.composter.fill_success", 1.0, 1.0),
    "craft.graft": ("block.smithing_table.use", 1.2, 1.0),
    "craft.prune": ("entity.sheep.shear", 1.0, 1.0),
    "charge.gain": ("entity.experience_orb.pickup", 1.6, 0.3),
    "ui.blessing_ready": ("block.note_block.bell", 2.0, 0.4),
}

out_dir = PACK / "sounds"
if out_dir.exists():
    shutil.rmtree(out_dir)
entries, custom = {}, 0
for name, (event, pitch, volume) in SOUNDS.items():
    src = next((p for ext in ("mp3", "wav", "ogg", "flac") if (p := SRC / f"{name}.{ext}").exists()), None)
    if src:
        rel = name.replace(".", "/")
        dest = out_dir / f"{rel}.ogg"
        dest.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(src), "-ac", "1", "-ar", "44100",
                        "-c:a", "libvorbis", "-q:a", "5", str(dest)], check=True)
        entries[name] = {"sounds": [f"cloverblessing:{rel}"]}
        custom += 1
    else:
        entries[name] = {"sounds": [{"name": f"minecraft:{event}", "type": "event", "pitch": pitch, "volume": volume}]}

(PACK / "sounds.json").write_text(json.dumps(entries, indent=2) + "\n", encoding="utf-8")
print(f"sounds.json: {len(entries)} sounds, {custom} custom, {len(entries) - custom} vanilla placeholders")
