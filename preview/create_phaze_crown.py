"""Create the first Blockbench preview of the Phaze crown.

The client intentionally consumes the embedded-PNG subset of Generic Model
projects, so this keeps the preview self-contained and opens directly in
Blockbench without any companion texture files.
"""

from __future__ import annotations

import base64
import json
import struct
import uuid
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parent
OUT = ROOT / "cosmetics" / "Phaze Crown Hat.bbmodel"


def png_rgba(width: int, height: int, pixels: list[tuple[int, int, int, int]]) -> bytes:
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        start = y * width
        for r, g, b, a in pixels[start:start + width]:
            rows.extend((r, g, b, a))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(rows), 9))
            + chunk(b"IEND", b""))


def texture_png() -> str:
    width = height = 16
    pixels = [(8, 6, 18, 255)] * (width * height)

    def swatch(x0: int, y0: int, x1: int, y1: int,
               colors: tuple[tuple[int, int, int], tuple[int, int, int], tuple[int, int, int]]) -> None:
        """Fill a clean 4x4 swatch: highlight, body, then shadow."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                row = y - y0
                if row == 0:
                    color = colors[2]
                elif row == y1 - y0 - 1:
                    color = colors[0]
                else:
                    color = colors[1]
                pixels[y * width + x] = (*color, 255)

    # Bright blocky palette from the reference. Each material is a clean 4x4
    # swatch: no speckles, highlights or decorative pixels on the jewels.
    swatch(0, 0, 4, 4, ((116, 75, 4), (195, 146, 0), (238, 232, 0)))
    swatch(4, 0, 8, 4, ((98, 69, 2), (178, 154, 0), (238, 211, 0)))
    swatch(8, 0, 12, 4, ((112, 29, 2), (166, 35, 5), (235, 57, 7)))
    swatch(12, 0, 16, 4, ((74, 166, 215), (84, 178, 220), (170, 208, 226)))
    swatch(0, 4, 4, 8, ((55, 160, 55), (38, 207, 36), (99, 232, 101)))
    # Warm brown inset used for the small dark blocks in the reference.
    swatch(4, 4, 8, 8, ((65, 37, 2), (130, 82, 8), (174, 112, 18)))
    swatch(8, 4, 12, 8, ((90, 18, 3), (142, 30, 6), (235, 57, 7)))
    swatch(12, 4, 16, 8, ((57, 122, 185), (74, 166, 215), (170, 208, 226)))

    # Keep the complete 16x16 file clean as well; the lower half mirrors the
    # same reference palette instead of containing unrelated placeholder pixels.
    for y in range(8, 16):
        for x in range(width):
            pixels[y * width + x] = pixels[(y - 8) * width + x]

    return "data:image/png;base64," + base64.b64encode(png_rgba(width, height, pixels)).decode()


def uid() -> str:
    return str(uuid.uuid4())


def face_map(uv: list[float]) -> dict[str, dict[str, object]]:
    return {
        side: {"uv": uv, "texture": 0}
        for side in ("north", "east", "south", "west", "up", "down")
    }


def cube(name: str, start: tuple[float, float, float], end: tuple[float, float, float],
         uv: list[float], rotation: tuple[float, float, float] | None = None,
         origin: tuple[float, float, float] | None = None) -> dict[str, object]:
    item: dict[str, object] = {
        "name": name,
        "box_uv": False,
        "from": list(start),
        "to": list(end),
        "autouv": 0,
        "color": 7,
        "faces": face_map(uv),
        "type": "cube",
        "uuid": uid(),
    }
    if rotation is not None:
        item["rotation"] = list(rotation)
        item["origin"] = list(origin or start)
    return item


def beam(name: str, x: float, y: float, length: float, thickness: float,
         z: float, depth: float, angle: float, uv: list[float]) -> dict[str, object]:
    """Place a chunky rotated gold ornament in the front plane."""
    return cube(
        name,
        (x - length / 2, y - thickness / 2, z),
        (x + length / 2, y + thickness / 2, z + depth),
        uv,
        rotation=(0.0, 0.0, angle),
        origin=(x, y, z + depth / 2),
    )


def jewel_on_face(name: str, x: float, y: float, z: float, size: float,
                  gem_uv: list[float], axis: int,
                  direction: int, surface: float) -> list[dict[str, object]]:
    """Make one plain colored cube mounted directly to a wall face.

    ``surface`` is the outside coordinate of the wall and ``direction`` points
    out of the crown. The cube slightly enters the wall, so it cannot float;
    there is no separate gold setting or decorative geometry around it.
    """
    gem_center = surface + direction * 0.08
    gem_half_depth = 0.12

    def bounds(center: float, half_depth: float, transverse: float) -> tuple[list[float], list[float]]:
        center_point = [x, y, z]
        half = [transverse / 2, transverse / 2, transverse / 2]
        half[axis] = half_depth
        center_point[axis] = center
        return (
            [center_point[index] - half[index] for index in range(3)],
            [center_point[index] + half[index] for index in range(3)],
        )

    gem_from, gem_to = bounds(gem_center, gem_half_depth, size)
    return [cube(name, tuple(gem_from), tuple(gem_to), gem_uv)]


def crystal_stack(name: str, x: float, z: float, width: float, height: float,
                  body_uv: list[float], edge_uv: list[float]) -> list[dict[str, object]]:
    """Build one new broad crystal tooth from separated cubic facets."""
    gap = 0.08
    levels = (
        (width, 1.42, 2.35, body_uv),
        (width - 0.50, 2.35 + gap, height * 0.57, body_uv),
        (width - 0.95, height * 0.57 + gap, height * 0.80, edge_uv),
        (max(0.62, width - 1.25), height * 0.80 + gap, height, edge_uv),
    )
    result: list[dict[str, object]] = []
    for index, (level_width, y0, y1, uv) in enumerate(levels):
        half = level_width / 2
        result.append(cube(
            f"{name} facet {index + 1}",
            (x - half, y0, z - half),
            (x + half, y1, z + half),
            uv,
        ))
    return result


def main() -> None:
    elements: list[dict[str, object]] = []

    gold = [0, 0, 4, 4]
    gold_shadow = [4, 0, 8, 4]
    ruby = [8, 0, 12, 4]
    sapphire = [12, 0, 16, 4]
    emerald = [0, 4, 4, 8]
    brown = [4, 4, 8, 8]
    ruby_shadow = [8, 4, 12, 8]
    sapphire_shadow = [12, 4, 16, 8]

    # Minecraft fortress-crown silhouette from the reference: a square open
    # ring, thick walls, four corner towers, crenellations, colored jewels and
    # a central cross. All parts are cuboids; there are no flat planes.
    elements.extend([
        # Lower square base and the raised front/back/side walls.
        cube("Square base front", (-5.40, 0.0, -5.40), (5.40, 1.05, -4.15), gold_shadow),
        cube("Square base back", (-5.40, 0.0, 4.15), (5.40, 1.05, 5.40), gold_shadow),
        cube("Square base left", (-5.40, 0.0, -4.19), (-4.15, 1.05, 4.19), gold_shadow),
        cube("Square base right", (4.15, 0.0, -4.19), (5.40, 1.05, 4.19), gold),
        cube("Front crown wall", (-4.85, 1.01, -5.25), (4.85, 2.45, -4.05), gold),
        cube("Back crown wall", (-4.85, 1.01, 4.05), (4.85, 2.45, 5.25), gold_shadow),
        cube("Left crown wall", (-5.25, 1.01, -4.09), (-4.05, 2.45, 4.09), gold_shadow),
        cube("Right crown wall", (4.05, 1.01, -4.09), (5.25, 2.45, 4.09), gold),
        # Connected ledges use a tiny hidden overlap instead of a visible gap.
        cube("Front upper ledge", (-5.05, 2.42, -5.34), (5.05, 2.88, -4.04), gold),
        cube("Back upper ledge", (-5.05, 2.42, 4.04), (5.05, 2.88, 5.34), gold_shadow),
        cube("Left upper ledge", (-5.34, 2.42, -4.08), (-4.04, 2.88, 4.08), gold_shadow),
        cube("Right upper ledge", (4.04, 2.42, -4.08), (5.34, 2.88, 4.08), gold),
    ])

    # Large square stones mounted into the wall panels on all four sides.
    jewel_row = (
        (-4.15, emerald), (-2.05, ruby_shadow), (0.0, sapphire),
        (2.05, ruby_shadow), (4.15, emerald),
    )
    for index, (x, uv) in enumerate(jewel_row):
        elements.extend(jewel_on_face(
            f"Front square jewel {index + 1}", x, 1.72, 0.0, 0.82,
            uv, axis=2, direction=-1, surface=-5.25,
        ))
        elements.extend(jewel_on_face(
            f"Back square jewel {index + 1}", x, 1.72, 0.0, 0.82,
            uv, axis=2, direction=1, surface=5.25,
        ))

    for index, (z, uv) in enumerate(jewel_row):
        elements.extend(jewel_on_face(
            f"Left square jewel {index + 1}", 0.0, 1.72, z, 0.82,
            uv, axis=0, direction=-1, surface=-5.25,
        ))
        elements.extend(jewel_on_face(
            f"Right square jewel {index + 1}", 0.0, 1.72, z, 0.82,
            uv, axis=0, direction=1, surface=5.25,
        ))

    # Four corner towers: chunky, square and intentionally castle-like.
    elements.extend([
        cube("Front left tower", (-5.20, 2.84, -5.20), (-4.05, 5.20, -4.05), gold),
        cube("Front right tower", (4.05, 2.84, -5.20), (5.20, 5.20, -4.05), gold),
        cube("Back left tower", (-5.20, 2.84, 4.05), (-4.05, 5.20, 5.20), gold_shadow),
        cube("Back right tower", (4.05, 2.84, 4.05), (5.20, 5.20, 5.20), gold_shadow),
        # Small crenels sit above each tower with clear seams.
        cube("Front left merlon", (-5.30, 5.16, -5.18), (-4.55, 6.28, -4.05), gold),
        cube("Front left inner merlon", (-4.20, 5.16, -5.18), (-3.95, 6.75, -4.05), gold),
        cube("Front right merlon", (4.55, 5.16, -5.18), (5.30, 6.28, -4.05), gold),
        cube("Front right inner merlon", (3.95, 5.16, -5.18), (4.20, 6.75, -4.05), gold),
        cube("Back left merlon", (-5.30, 5.16, 4.05), (-4.55, 6.28, 5.18), gold_shadow),
        cube("Back right merlon", (4.55, 5.16, 4.05), (5.30, 6.28, 5.18), gold_shadow),
    ])

    # Cross-shaped center ornament on the far wall, like the reference crown.
    elements.extend([
        cube("Back cross lower stem", (-0.52, 2.84, 4.10), (0.52, 5.26, 4.78), gold),
        cube("Back cross arms", (-1.50, 5.22, 4.10), (1.50, 6.02, 4.78), gold),
        cube("Back cross upper stem", (-0.52, 5.98, 4.10), (0.52, 6.30, 4.78), gold),
        cube("Front inner lower stem", (-0.45, 2.84, -4.78), (0.45, 4.24, -4.35), gold_shadow),
        cube("Front inner cross arms", (-1.15, 4.20, -4.78), (1.15, 4.88, -4.35), gold_shadow),
        cube("Front inner upper stem", (-0.45, 4.84, -4.78), (0.45, 5.05, -4.35), gold_shadow),
    ])

    # Brown square cap blocks are a characteristic detail in the supplied model.
    for index, (x, z) in enumerate((
        (-3.25, -5.34), (3.25, -5.34), (-3.25, 5.34), (3.25, 5.34),
        (-5.34, 0.0), (5.34, 0.0),
    )):
        if abs(x) > abs(z):
            start = (x - 0.24, 2.84, z - 0.55)
            end = (x + 0.24, 3.65, z + 0.55)
        else:
            start = (x - 0.55, 2.84, z - 0.24)
            end = (x + 0.55, 3.65, z + 0.24)
        elements.append(cube(f"Brown crown cap {index + 1}", start, end, brown))

    model = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": "phaze_crown",
        "model_identifier": "phaze:crown",
        "resolution": {"width": 16, "height": 16},
        "elements": elements,
        "outliner": [],
        "textures": [{
            "name": "phaze_crown.png",
            "id": 0,
            "uuid": uid(),
            "width": 16,
            "height": 16,
            "source": texture_png(),
        }],
    }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(model, separators=(",", ":")), encoding="utf-8")
    print(OUT)


if __name__ == "__main__":
    main()
