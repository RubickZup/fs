#!/usr/bin/env python3
"""
generate_golem_assets.py
Generates:
1. GeckoLib Geo Model: mossy_stone_golem.geo.json
2. GeckoLib Animations: mossy_stone_golem.animation.json
3. Blockbench Model: mossy_stone_golem.bbmodel
4. Baked Pixel-Art Texture: mossy_stone_golem.png (128x128, nearest-neighbor, 32-color quantized)
"""

import os
import json
import uuid
import math
import base64
from PIL import Image, ImageDraw

OUTPUT_DIR = "K:/Mods/mossy_stone_golem/src/main/resources/assets/mossystonegolem"
MODELS_DIR = f"{OUTPUT_DIR}/geo"
ANIMS_DIR = f"{OUTPUT_DIR}/animations"
TEXTURES_DIR = f"{OUTPUT_DIR}/textures/entity"

os.makedirs(MODELS_DIR, exist_ok=True)
os.makedirs(ANIMS_DIR, exist_ok=True)
os.makedirs(TEXTURES_DIR, exist_ok=True)

# -------------------------------------------------------------
# 1. MODEL DEFINITION
# -------------------------------------------------------------
# Elements: name, bone_name, parent_bone, size [dx, dy, dz], origin [x, y, z], pivot [x, y, z], rot [rx, ry, rz], tex_type ('stone', 'chain', 'bell')
# Origin in Blockbench is the minimum coordinate corner [min_x, min_y, min_z]

ELEMENTS_SPEC = [
    # TORSO & HUMP
    {
        "name": "torso",
        "bone": "body",
        "parent": "root",
        "size": [22, 18, 20],
        "origin": [-11, 10, -10],
        "pivot": [0, 16, 0],
        "rotation": [8, 0, 0],
        "tex": "stone"
    },
    {
        "name": "hump",
        "bone": "body",
        "parent": "root",
        "size": [18, 8, 16],
        "origin": [-9, 24, -7],
        "pivot": [0, 24, 0],
        "rotation": [0, 0, 0],
        "tex": "stone"
    },
    # HEAD & NOSE (sunken into shoulders)
    {
        "name": "head",
        "bone": "head",
        "parent": "body",
        "size": [12, 12, 12],
        "origin": [-6, 16, -18],
        "pivot": [0, 20, -10],
        "rotation": [-6, 0, 0],
        "tex": "stone"
    },
    {
        "name": "nose",
        "bone": "head",
        "parent": "body",
        "size": [4, 7, 4],
        "origin": [-2, 15, -22],
        "pivot": [0, 18, -18],
        "rotation": [0, 0, 0],
        "tex": "stone"
    },
    # FRONT ARMS (LONG, MASSIVE, RESTING ON GROUND)
    {
        "name": "right_arm_upper",
        "bone": "right_arm",
        "parent": "body",
        "size": [8, 14, 8],
        "origin": [-18, 14, -8],
        "pivot": [-14, 26, -4],
        "rotation": [-10, 0, 6],
        "tex": "stone"
    },
    {
        "name": "right_fist",
        "bone": "right_arm",
        "parent": "body",
        "size": [10, 15, 11],
        "origin": [-19, 0, -11],
        "pivot": [-14, 14, -4],
        "rotation": [2, 0, 0],
        "tex": "stone"
    },
    {
        "name": "left_arm_upper",
        "bone": "left_arm",
        "parent": "body",
        "size": [8, 14, 8],
        "origin": [10, 14, -8],
        "pivot": [14, 26, -4],
        "rotation": [-10, 0, -6],
        "tex": "stone"
    },
    {
        "name": "left_fist",
        "bone": "left_arm",
        "parent": "body",
        "size": [10, 15, 11],
        "origin": [9, 0, -11],
        "pivot": [14, 14, -4],
        "rotation": [2, 0, 0],
        "tex": "stone"
    },
    # HIND LEGS (SHORT, WIDE, MASSIVE PILLARS)
    {
        "name": "right_leg",
        "bone": "right_leg",
        "parent": "root",
        "size": [8, 12, 10],
        "origin": [-11, 0, 5],
        "pivot": [-7, 12, 10],
        "rotation": [0, 0, 0],
        "tex": "stone"
    },
    {
        "name": "left_leg",
        "bone": "left_leg",
        "parent": "root",
        "size": [8, 12, 10],
        "origin": [3, 0, 5],
        "pivot": [7, 12, 10],
        "rotation": [0, 0, 0],
        "tex": "stone"
    },
    # BACK MOUNT (FORGED IRON RING / BRACKET)
    {
        "name": "back_bracket",
        "bone": "back_mount",
        "parent": "body",
        "size": [6, 5, 4],
        "origin": [-3, 26, 7],
        "pivot": [0, 28, 8],
        "rotation": [-15, 0, 0],
        "tex": "chain"
    },
    # CHAIN LINKS 1 TO 7 (PHYSICAL VERLET CHAIN)
    {
        "name": "link_1",
        "bone": "chain_1",
        "parent": "root",
        "size": [3, 4, 3],
        "origin": [-1.5, 23.5, 8.5],
        "pivot": [0, 26, 9.5],
        "rotation": [0, 0, 0],
        "tex": "chain"
    },
    {
        "name": "link_2",
        "bone": "chain_2",
        "parent": "root",
        "size": [4, 4, 2],
        "origin": [-2, 20, 9.5],
        "pivot": [0, 23.5, 10.5],
        "rotation": [0, 0, 0],
        "tex": "chain"
    },
    {
        "name": "link_3",
        "bone": "chain_3",
        "parent": "root",
        "size": [3, 4, 3],
        "origin": [-1.5, 16.5, 10.5],
        "pivot": [0, 20, 11.5],
        "rotation": [0, 0, 0],
        "tex": "chain"
    },
    {
        "name": "link_4",
        "bone": "chain_4",
        "parent": "root",
        "size": [4, 4, 2],
        "origin": [-2, 13, 11.5],
        "pivot": [0, 16.5, 12.5],
        "rotation": [0, 0, 0],
        "tex": "chain"
    },
    {
        "name": "link_5",
        "bone": "chain_5",
        "parent": "root",
        "size": [3, 4, 3],
        "origin": [-1.5, 9.5, 12.5],
        "pivot": [0, 13, 13.5],
        "rotation": [0, 0, 0],
        "tex": "chain"
    },
    {
        "name": "link_6",
        "bone": "chain_6",
        "parent": "root",
        "size": [4, 4, 2],
        "origin": [-2, 6, 13.5],
        "pivot": [0, 9.5, 14.5],
        "rotation": [0, 0, 0],
        "tex": "chain"
    },
    {
        "name": "link_7",
        "bone": "chain_7",
        "parent": "root",
        "size": [3, 4, 3],
        "origin": [-1.5, 2.5, 14.5],
        "pivot": [0, 6, 15.5],
        "rotation": [0, 0, 0],
        "tex": "chain"
    },
    # ANCIENT BRONZE BELL (RESTING ON GROUND AT Y=0)
    {
        "name": "bell_loop",
        "bone": "bell_bone",
        "parent": "root",
        "size": [4, 3, 4],
        "origin": [-2, 9, 15],
        "pivot": [0, 3, 17],
        "rotation": [0, 0, 0],
        "tex": "bell"
    },
    {
        "name": "bell_body",
        "bone": "bell_bone",
        "parent": "root",
        "size": [10, 6, 10],
        "origin": [-5, 4, 12],
        "pivot": [0, 3, 17],
        "rotation": [0, 0, 0],
        "tex": "bell"
    },
    {
        "name": "bell_skirt",
        "bone": "bell_bone",
        "parent": "root",
        "size": [13, 4, 13],
        "origin": [-6.5, 0, 10.5],
        "pivot": [0, 3, 17],
        "rotation": [0, 0, 0],
        "tex": "bell"
    },
    {
        "name": "bell_clapper",
        "bone": "bell_bone",
        "parent": "root",
        "size": [3, 5, 3],
        "origin": [-1.5, 0.5, 15.5],
        "pivot": [0, 3, 17],
        "rotation": [0, 0, 0],
        "tex": "bell"
    }
]

# -------------------------------------------------------------
# 2. UV RECTANGLE PACKER (128 x 128 canvas)
# -------------------------------------------------------------
def pack_uvs(elements, tex_w=128, tex_h=256):
    # Sort elements by required UV area descending
    boxes = []
    for elem in elements:
        dx, dy, dz = elem["size"]
        w = 2 * (dz + dx)
        h = dz + dy
        boxes.append({
            "elem": elem,
            "w": w,
            "h": h,
            "dx": dx, "dy": dy, "dz": dz
        })

    # Sort largest first
    boxes.sort(key=lambda b: (b["h"], b["w"]), reverse=True)

    # Shelf packing algorithm
    shelves = [] # list of dicts: {'y': y, 'h': h, 'current_x': x}
    current_y = 0

    for b in boxes:
        placed = False
        for s in shelves:
            if s["current_x"] + b["w"] <= tex_w and b["h"] <= s["h"]:
                b["uv"] = [s["current_x"], s["y"]]
                s["current_x"] += b["w"]
                placed = True
                break
        if not placed:
            # Start new shelf
            if current_y + b["h"] > tex_h:
                raise ValueError(f"UV space exceeded! Cannot fit box {b['elem']['name']} of size {b['w']}x{b['h']}")
            shelf_h = b["h"]
            shelves.append({"y": current_y, "h": shelf_h, "current_x": b["w"]})
            b["uv"] = [0, current_y]
            current_y += shelf_h

    # Assign UV back to elements
    for b in boxes:
        b["elem"]["uv"] = b["uv"]
        b["elem"]["uv_size"] = [b["w"], b["h"]]
        print(f"Packed {b['elem']['name']:<18}: size={b['elem']['size']}, UV={b['uv']}, uv_wh=[{b['w']}, {b['h']}]")

pack_uvs(ELEMENTS_SPEC, 128, 256)

# -------------------------------------------------------------
# 3. BUILD GECKOLIB GEO JSON
# -------------------------------------------------------------
bones_map = {}
for elem in ELEMENTS_SPEC:
    b_name = elem["bone"]
    if b_name not in bones_map:
        bones_map[b_name] = {
            "name": b_name,
            "parent": elem["parent"] if elem["parent"] != "root" else None,
            "pivot": elem["pivot"],
            "rotation": elem.get("rotation", [0, 0, 0]),
            "cubes": []
        }
    bones_map[b_name]["cubes"].append({
        "origin": elem["origin"],
        "size": elem["size"],
        "uv": elem["uv"]
    })

# Root bone
bones_list = [
    {
        "name": "root",
        "pivot": [0, 0, 0],
        "cubes": []
    }
]

# Order bones logically
bone_order = [
    "body", "head", "right_arm", "left_arm", "right_leg", "left_leg",
    "back_mount", "chain_1", "chain_2", "chain_3", "chain_4", "chain_5", "chain_6", "chain_7",
    "bell_bone"
]

for b_name in bone_order:
    if b_name in bones_map:
        b_data = bones_map[b_name]
        bone_entry = {
            "name": b_data["name"],
            "pivot": b_data["pivot"]
        }
        if b_data["parent"]:
            bone_entry["parent"] = b_data["parent"]
        if b_data["rotation"] and any(r != 0 for r in b_data["rotation"]):
            bone_entry["rotation"] = b_data["rotation"]
        bone_entry["cubes"] = b_data["cubes"]
        bones_list.append(bone_entry)

geo_json = {
    "format_version": "1.12.0",
    "minecraft:geometry": [
        {
            "description": {
                "identifier": "geometry.mossy_stone_golem",
                "texture_width": 128,
                "texture_height": 256,
                "visible_bounds_width": 4.5,
                "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.5, 0]
            },
            "bones": bones_list
        }
    ]
}

geo_path = f"{MODELS_DIR}/mossy_stone_golem.geo.json"
with open(geo_path, "w", encoding="utf-8") as f:
    json.dump(geo_json, f, indent=2)
print(f"[OK] Saved GeckoLib Geo Model: {geo_path}")

# -------------------------------------------------------------
# 4. BUILD GECKOLIB ANIMATIONS JSON
# -------------------------------------------------------------
anim_json = {
    "format_version": "1.8.0",
    "animations": {
        "animation.mossy_stone_golem.idle": {
            "loop": True,
            "animation_length": 4.0,
            "bones": {
                "body": {
                    "position": {
                        "0.0": [0, 0, 0],
                        "2.0": [0, -0.6, 0.2],
                        "4.0": [0, 0, 0]
                    },
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "2.0": [2.5, 0, 0],
                        "4.0": [0, 0, 0]
                    }
                },
                "head": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "2.0": [-2.0, 0, 0],
                        "4.0": [0, 0, 0]
                    }
                },
                "right_arm": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "2.0": [-2.0, 0, 1.5],
                        "4.0": [0, 0, 0]
                    }
                },
                "left_arm": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "2.0": [-2.0, 0, -1.5],
                        "4.0": [0, 0, 0]
                    }
                }
            }
        },
        "animation.mossy_stone_golem.walk": {
            "loop": True,
            "animation_length": 2.0,
            "bones": {
                "body": {
                    "position": {
                        "0.0": [0, 0, 0],
                        "0.5": [0.4, 0.8, 0],
                        "1.0": [0, 0, 0],
                        "1.5": [-0.4, 0.8, 0],
                        "2.0": [0, 0, 0]
                    },
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "0.5": [3.0, 2.0, 4.0],
                        "1.0": [0, 0, 0],
                        "1.5": [3.0, -2.0, -4.0],
                        "2.0": [0, 0, 0]
                    }
                },
                "right_arm": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "0.5": [-18.0, 0, 5.0],
                        "1.0": [0, 0, 0],
                        "1.5": [14.0, 0, 2.0],
                        "2.0": [0, 0, 0]
                    },
                    "position": {
                        "0.0": [0, 0, 0],
                        "0.5": [0, 2.0, 3.0],
                        "1.0": [0, 0, 0],
                        "1.5": [0, 0, -2.0],
                        "2.0": [0, 0, 0]
                    }
                },
                "left_arm": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "0.5": [14.0, 0, -2.0],
                        "1.0": [0, 0, 0],
                        "1.5": [-18.0, 0, -5.0],
                        "2.0": [0, 0, 0]
                    },
                    "position": {
                        "0.0": [0, 0, 0],
                        "0.5": [0, 0, -2.0],
                        "1.0": [0, 0, 0],
                        "1.5": [0, 2.0, 3.0],
                        "2.0": [0, 0, 0]
                    }
                },
                "right_leg": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "0.5": [16.0, 0, 0],
                        "1.0": [0, 0, 0],
                        "1.5": [-16.0, 0, 0],
                        "2.0": [0, 0, 0]
                    }
                },
                "left_leg": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "0.5": [-16.0, 0, 0],
                        "1.0": [0, 0, 0],
                        "1.5": [16.0, 0, 0],
                        "2.0": [0, 0, 0]
                    }
                }
            }
        },
        "animation.mossy_stone_golem.attack": {
            "loop": False,
            "animation_length": 1.4,
            "bones": {
                "body": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "0.4": [-22.0, 0, 0],
                        "0.7": [28.0, 0, 0],
                        "1.0": [10.0, 0, 0],
                        "1.4": [0, 0, 0]
                    },
                    "position": {
                        "0.0": [0, 0, 0],
                        "0.4": [0, 4.0, -2.0],
                        "0.7": [0, -2.5, 4.0],
                        "1.0": [0, -1.0, 2.0],
                        "1.4": [0, 0, 0]
                    }
                },
                "right_arm": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "0.4": [-75.0, -10.0, 12.0],
                        "0.7": [35.0, 5.0, -4.0],
                        "1.0": [12.0, 0, 0],
                        "1.4": [0, 0, 0]
                    },
                    "position": {
                        "0.0": [0, 0, 0],
                        "0.4": [0, 5.0, 0],
                        "0.7": [0, -4.0, 6.0],
                        "1.0": [0, -1.0, 2.0],
                        "1.4": [0, 0, 0]
                    }
                },
                "left_arm": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "0.4": [-75.0, 10.0, -12.0],
                        "0.7": [35.0, -5.0, 4.0],
                        "1.0": [12.0, 0, 0],
                        "1.4": [0, 0, 0]
                    },
                    "position": {
                        "0.0": [0, 0, 0],
                        "0.4": [0, 5.0, 0],
                        "0.7": [0, -4.0, 6.0],
                        "1.0": [0, -1.0, 2.0],
                        "1.4": [0, 0, 0]
                    }
                },
                "head": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "0.4": [-15.0, 0, 0],
                        "0.7": [25.0, 0, 0],
                        "1.4": [0, 0, 0]
                    }
                }
            }
        }
    }
}

anim_path = f"{ANIMS_DIR}/mossy_stone_golem.animation.json"
with open(anim_path, "w", encoding="utf-8") as f:
    json.dump(anim_json, f, indent=2)
print(f"[OK] Saved GeckoLib Animations: {anim_path}")

# -------------------------------------------------------------
# 5. PIXEL-ART TEXTURE BAKING (128x128 Canvas)
# -------------------------------------------------------------
raw_stone_path = "K:/Mods/mossy_stone_golem/assets_raw/mossy_stone.jpg"
raw_chain_path = "K:/Mods/mossy_stone_golem/assets_raw/chain_metal.jpg"
raw_bell_path  = "K:/Mods/mossy_stone_golem/assets_raw/bronze_bell.jpg"
raw_atlas_path = "K:/Mods/mossy_stone_golem/assets_raw/mossy_stone_golem_atlas.jpg"

img_stone = Image.open(raw_stone_path).convert("RGB")
img_chain = Image.open(raw_chain_path).convert("RGB")
img_bell  = Image.open(raw_bell_path).convert("RGB")
img_atlas = Image.open(raw_atlas_path).convert("RGB")

canvas = Image.new("RGBA", (128, 256), (0, 0, 0, 0))

# For each element, slice texture patch from corresponding source
for elem in ELEMENTS_SPEC:
    uv_x, uv_y = elem["uv"]
    uv_w, uv_h = elem["uv_size"]
    tex_type = elem["tex"]

    if tex_type == "stone":
        src = img_stone
    elif tex_type == "chain":
        src = img_chain
    else:
        src = img_bell

    # Resize source texture patch using strict NEAREST
    patch = src.resize((uv_w, uv_h), Image.Resampling.NEAREST).convert("RGBA")

    # Custom details for head: add glowing eyes and carved nose shading
    if elem["name"] == "head":
        # Draw eyes on North face:
        # North face UV in standard box UV: [u + dz, v + dz] to [u + dz + dx, v + dz + dy]
        # dx=12, dy=12, dz=12
        north_u = uv_x + 12
        north_v = uv_y + 12
        # Draw on canvas later or directly on patch
        # Relative coordinates in patch: x from 12 to 24, y from 12 to 24
        patch_draw = ImageDraw.Draw(patch)
        # Left eye [rel_x: 14..16, rel_y: 16..17]
        # Right eye [rel_x: 20..22, rel_y: 16..17]
        eye_color = (235, 75, 45, 255) # deep glowing red/amber eye
        pupil_color = (255, 200, 80, 255)
        patch_draw.rectangle([14, 15, 16, 17], fill=eye_color)
        patch_draw.point((15, 16), fill=pupil_color)
        patch_draw.rectangle([20, 15, 22, 17], fill=eye_color)
        patch_draw.point((21, 16), fill=pupil_color)

    canvas.paste(patch, (uv_x, uv_y))

# Palette Quantization to 32 colors with dither=NONE as per CLAUDE.md standards
quantized = canvas.convert("RGB").quantize(colors=32, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGBA")

# Ensure alpha is crisp 1-bit
pixels = quantized.load()
for y in range(256):
    for x in range(128):
        orig_a = canvas.getpixel((x, y))[3]
        if orig_a < 128:
            pixels[x, y] = (0, 0, 0, 0)
        else:
            r, g, b, _ = pixels[x, y]
            pixels[x, y] = (r, g, b, 255)

out_tex_path = f"{TEXTURES_DIR}/mossy_stone_golem.png"
quantized.save(out_tex_path, "PNG")
print(f"[OK] Saved Quantized Pixel-Art Texture: {out_tex_path}")

# -------------------------------------------------------------
# 6. BLOCKBENCH (.bbmodel) PROJECT FILE GENERATION
# -------------------------------------------------------------
# Include embedded base64 texture for instant rendering in Blockbench
with open(out_tex_path, "rb") as f:
    tex_b64 = "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")

bb_elements = []
for elem in ELEMENTS_SPEC:
    bb_elements.append({
        "name": elem["name"],
        "box_uv": True,
        "uv_offset": elem["uv"],
        "from": elem["origin"],
        "to": [elem["origin"][0] + elem["size"][0], elem["origin"][1] + elem["size"][1], elem["origin"][2] + elem["size"][2]],
        "origin": elem["pivot"],
        "rotation": elem.get("rotation", [0, 0, 0]),
        "color": 0,
        "uuid": str(uuid.uuid4())
    })

# Outliner hierarchy
bb_outliner = [
    {
        "name": "root",
        "origin": [0, 0, 0],
        "uuid": str(uuid.uuid4()),
        "children": []
    }
]

# Map outliner groups
bb_groups = {"root": bb_outliner[0]}
for b_name in bone_order:
    parent_name = bones_map[b_name]["parent"] or "root"
    grp = {
        "name": b_name,
        "origin": bones_map[b_name]["pivot"],
        "uuid": str(uuid.uuid4()),
        "children": []
    }
    bb_groups[b_name] = grp
    bb_groups[parent_name]["children"].append(grp)

# Add element uuids to their bone groups
for elem in ELEMENTS_SPEC:
    bb_groups[elem["bone"]]["children"].append(elem["name"])

bbmodel_data = {
    "meta": {
        "format_version": "4.10",
        "model_format": "geckolib_model",
        "box_uv": True
    },
    "name": "mossy_stone_golem",
    "geometry_name": "mossy_stone_golem",
    "visible_box": [4.5, 3.5, 0],
    "resolution": {
        "width": 128,
        "height": 256
    },
    "elements": bb_elements,
    "outliner": bb_outliner,
    "textures": [
        {
            "path": "mossy_stone_golem.png",
            "name": "mossy_stone_golem",
            "folder": "textures",
            "namespace": "mossystonegolem",
            "id": "0",
            "particle": False,
            "render_mode": "default",
            "visible": True,
            "mode": "bitmap",
            "saved": True,
            "uuid": str(uuid.uuid4()),
            "source": tex_b64
        }
    ]
}

bbmodel_path = f"K:/Mods/mossy_stone_golem/mossy_stone_golem.bbmodel"
with open(bbmodel_path, "w", encoding="utf-8") as f:
    json.dump(bbmodel_data, f, indent=2)
print(f"[OK] Saved Blockbench Project: {bbmodel_path}")
print("ALL 3D ASSETS SUCCESSFULLY CREATED!")
