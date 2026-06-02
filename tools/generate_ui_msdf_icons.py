"""
Builds the shared UI icon MSDF atlas used by the Phaze menus.

Pipeline:
1. Load the icon PNG and apply any explicit crop used by the runtime menu.
2. Convert the alpha mask into a clean monochrome raster.
3. Trace that raster into SVG with vtracer.
4. Merge all traced SVG paths into a single compound path so msdfgen
   reads the full icon instead of only the last path.
5. Generate a per-icon MSDF bitmap with the official msdfgen binary.
6. Pack all icon bitmaps into one atlas and write a small JSON manifest.

Dependencies:
- pillow
- vtracer
- svgelements

The script auto-downloads msdfgen into build/generated-ui-msdf/toolchain.
"""

from __future__ import annotations

import json
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path
from subprocess import run
import sys

try:
    from PIL import Image
except ImportError as exc:
    raise SystemExit("Missing dependency: pillow. Install with `python -m pip install pillow`.") from exc

try:
    import vtracer
except ImportError as exc:
    raise SystemExit("Missing dependency: vtracer. Install with `python -m pip install vtracer`.") from exc

try:
    from svgelements import SVG, Path as SvgPath
except ImportError as exc:
    raise SystemExit("Missing dependency: svgelements. Install with `python -m pip install svgelements`.") from exc


ROOT = Path(__file__).resolve().parents[1]
RES_ROOT = ROOT / "src" / "main" / "resources"
BUILD_ROOT = ROOT / "build" / "generated-ui-msdf"
RASTER_DIR = BUILD_ROOT / "raster"
SVG_DIR = BUILD_ROOT / "svg"
MSDF_DIR = BUILD_ROOT / "msdf"
TOOLCHAIN_DIR = BUILD_ROOT / "toolchain"
OUT_DIR = RES_ROOT / "assets" / "phaze" / "msdf"
OUT_PNG = OUT_DIR / "ui_icons.png"
OUT_JSON = OUT_DIR / "ui_icons.json"

MSDFGEN_VERSION = "1.13"
MSDFGEN_ZIP_URL = f"https://github.com/Chlumsky/msdfgen/releases/download/v{MSDFGEN_VERSION}/msdfgen-{MSDFGEN_VERSION}-win64.zip"
MSDFGEN_DIR = TOOLCHAIN_DIR / f"msdfgen-{MSDFGEN_VERSION}-win64"
MSDFGEN_EXE = MSDFGEN_DIR / "msdfgen" / "msdfgen.exe"

TARGET_LONGEST_EDGE = 80
MIN_OUTPUT_SIZE = 24
PX_RANGE = 6
TILE_PADDING = PX_RANGE + 2
ALPHA_THRESHOLD = 16
ATLAS_GAP = 8
ATLAS_MAX_WIDTH = 2048

SPECIAL_CROPS: dict[str, tuple[int, int, int, int]] = {}

MAIN_MENU_ICON_IDS = (
    "phaze:textures/menu/user.png",
    "phaze:textures/menu/users.png",
    "phaze:textures/menu/tab.png",
    "phaze:textures/menu/settings.png",
    "phaze:textures/menu/phaze_brand.png",
    "phaze:textures/menu/cube.png",
    "phaze:textures/menu/arrow_external_bold.png",
    "phaze:textures/menu/logout.png",
    "phaze:textures/menu/gem_alt_filled.png",
    "phaze:textures/menu/flashback.png",
    "phaze:textures/menu/paintbrush.png",
    "phaze:textures/menu/cross.png",
    "minecraft:textures/reset.png",
)

GUI_ICON_IDS = (
    "minecraft:textures/settings.png",
    "minecraft:textures/cross.png",
    "minecraft:textures/trash.png",
    "minecraft:textures/back_arrow.png",
    "minecraft:textures/search_lunar.png",
    "minecraft:textures/edit.png",
    "minecraft:textures/share.png",
    "minecraft:textures/file.png",
    "minecraft:textures/file_import.png",
    "minecraft:textures/size.png",
    "minecraft:textures/cloud.png",
    "minecraft:textures/clock.png",
    "minecraft:textures/reset.png",
)


@dataclass(frozen=True)
class IconSource:
    icon_id: str
    path: Path
    crop: tuple[int, int, int, int] | None = None


@dataclass
class BuiltIcon:
    icon_id: str
    image: Image.Image
    content_aspect_ratio: float


@dataclass(frozen=True)
class RasterInfo:
    layout_width: int
    layout_height: int
    content_left: int
    content_top: int
    content_width: int
    content_height: int


def resource_id(path: Path) -> str | None:
    parts = path.relative_to(RES_ROOT).parts
    if len(parts) < 4 or parts[0] != "assets":
        return None
    namespace = parts[1]
    rel = "/".join(parts[2:])
    return f"{namespace}:{rel}"


def resource_path(icon_id: str) -> Path:
    namespace, rel = icon_id.split(":", 1)
    return RES_ROOT / "assets" / namespace / rel


def cache_path(root: Path, icon_id: str, suffix: str) -> Path:
    namespace, rel = icon_id.split(":", 1)
    target = root / namespace / rel
    return target.with_suffix(suffix)


def append_icon(icons: list[IconSource], seen: set[str], icon_id: str) -> None:
    if icon_id in seen:
        return
    path = resource_path(icon_id)
    if not path.exists():
        return
    icons.append(IconSource(icon_id, path, SPECIAL_CROPS.get(icon_id)))
    seen.add(icon_id)


def discover_icons() -> list[IconSource]:
    icons: list[IconSource] = []
    seen: set[str] = set()

    for icon_id in MAIN_MENU_ICON_IDS:
        append_icon(icons, seen, icon_id)

    for icon_id in GUI_ICON_IDS:
        append_icon(icons, seen, icon_id)

    phaze_modules_dir = RES_ROOT / "assets" / "phaze" / "textures" / "modules"
    if phaze_modules_dir.exists():
        for path in sorted(phaze_modules_dir.glob("*.png")):
            icon_id = resource_id(path)
            if icon_id is not None:
                append_icon(icons, seen, icon_id)

    vanilla_modules_dir = RES_ROOT / "assets" / "minecraft" / "textures" / "modules"
    if vanilla_modules_dir.exists():
        for path in sorted(vanilla_modules_dir.glob("*.png")):
            icon_id = resource_id(path)
            if icon_id is not None:
                append_icon(icons, seen, icon_id)

    return icons


def ensure_msdfgen() -> Path:
    if MSDFGEN_EXE.exists():
        return MSDFGEN_EXE

    TOOLCHAIN_DIR.mkdir(parents=True, exist_ok=True)
    zip_path = TOOLCHAIN_DIR / f"msdfgen-{MSDFGEN_VERSION}-win64.zip"
    if not zip_path.exists():
        print(f"Downloading msdfgen {MSDFGEN_VERSION}...")
        urllib.request.urlretrieve(MSDFGEN_ZIP_URL, zip_path)

    if not MSDFGEN_DIR.exists():
        print("Extracting msdfgen...")
        with zipfile.ZipFile(zip_path) as archive:
            archive.extractall(MSDFGEN_DIR)

    if not MSDFGEN_EXE.exists():
        raise RuntimeError(f"msdfgen executable not found at {MSDFGEN_EXE}")
    return MSDFGEN_EXE


def load_icon_image(icon: IconSource) -> Image.Image:
    image = Image.open(icon.path).convert("RGBA")
    if icon.crop is not None:
        x, y, w, h = icon.crop
        cropped = image.crop((x, y, x + w, y + h))
        image.close()
        return cropped
    return image


def build_traceable_raster(icon: IconSource, raster_path: Path) -> RasterInfo:
    image = load_icon_image(icon)
    layout_width, layout_height = image.size
    alpha = image.getchannel("A").point(lambda a: 255 if a >= ALPHA_THRESHOLD else 0)
    bbox = alpha.getbbox()
    content = image.crop(bbox) if bbox is not None else image.copy()
    content_left = bbox[0] if bbox is not None else 0
    content_top = bbox[1] if bbox is not None else 0
    width, height = content.size
    alpha = content.getchannel("A").point(lambda a: 255 if a >= ALPHA_THRESHOLD else 0)
    raster = Image.new("RGBA", content.size, (255, 255, 255, 0))
    raster.putalpha(alpha)

    raster_path.parent.mkdir(parents=True, exist_ok=True)
    raster.save(raster_path)

    image.close()
    content.close()
    raster.close()
    return RasterInfo(
        layout_width=layout_width,
        layout_height=layout_height,
        content_left=content_left,
        content_top=content_top,
        content_width=width,
        content_height=height,
    )


def trace_png_to_svg(raster_path: Path, svg_path: Path) -> None:
    svg_path.parent.mkdir(parents=True, exist_ok=True)
    vtracer.convert_image_to_svg_py(str(raster_path), str(svg_path))


def merge_svg_paths(raw_svg_path: Path, compound_svg_path: Path, width: int, height: int) -> None:
    svg = SVG.parse(str(raw_svg_path))
    combined = SvgPath()
    for element in svg.elements():
        if isinstance(element, SvgPath):
            combined.extend(element * element.transform)

    if len(combined) == 0:
        raise RuntimeError(f"No SVG paths traced for {raw_svg_path}")

    compound_svg_path.parent.mkdir(parents=True, exist_ok=True)
    compound_svg_path.write_text(
        (
            f'<?xml version="1.0" encoding="UTF-8"?>\n'
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">\n'
            f'  <path d="{combined.d()}" fill="#ffffff"/>\n'
            f'</svg>\n'
        ),
        encoding="utf-8",
    )


def compute_output_dimensions(width: int, height: int) -> tuple[int, int, float]:
    longest = max(width, height)
    shortest = max(1, min(width, height))
    scale = TARGET_LONGEST_EDGE / float(longest)
    if shortest * scale < MIN_OUTPUT_SIZE:
        scale = MIN_OUTPUT_SIZE / float(shortest)
    out_w = max(1, int(round(width * scale)))
    out_h = max(1, int(round(height * scale)))
    return out_w, out_h, scale


def build_msdf(icon: IconSource, msdfgen_exe: Path) -> BuiltIcon:
    raster_path = cache_path(RASTER_DIR, icon.icon_id, ".png")
    raw_svg_path = cache_path(SVG_DIR / "raw", icon.icon_id, ".svg")
    compound_svg_path = cache_path(SVG_DIR / "compound", icon.icon_id, ".svg")
    msdf_png_path = cache_path(MSDF_DIR, icon.icon_id, ".png")

    raster_info = build_traceable_raster(icon, raster_path)
    trace_png_to_svg(raster_path, raw_svg_path)
    merge_svg_paths(raw_svg_path, compound_svg_path, raster_info.content_width, raster_info.content_height)

    content_w, content_h, scale = compute_output_dimensions(raster_info.layout_width, raster_info.layout_height)
    scale_x = scale
    scale_y = scale
    out_w = content_w + TILE_PADDING * 2
    out_h = content_h + TILE_PADDING * 2
    translate_x = raster_info.content_left + TILE_PADDING / scale
    translate_y = raster_info.content_top + TILE_PADDING / scale

    msdf_png_path.parent.mkdir(parents=True, exist_ok=True)
    run(
        [
            str(msdfgen_exe),
            "msdf",
            "-svg",
            str(compound_svg_path),
            "-o",
            str(msdf_png_path),
            "-dimensions",
            str(out_w),
            str(out_h),
            "-ascale",
            f"{scale_x}",
            f"{scale_y}",
            "-translate",
            f"{translate_x}",
            f"{translate_y}",
            "-pxrange",
            str(PX_RANGE),
            "-guesswinding",
            "-scanline",
        ],
        check=True,
    )

    image = Image.open(msdf_png_path).convert("RGBA")
    return BuiltIcon(icon.icon_id, image, raster_info.layout_width / float(max(1, raster_info.layout_height)))


def pack_icons(icons: list[BuiltIcon]) -> tuple[Image.Image, dict[str, tuple[int, int, int, int]]]:
    placements: dict[str, tuple[int, int, int, int]] = {}
    atlas_width = 0
    x = ATLAS_GAP
    y = ATLAS_GAP
    row_height = 0

    for icon in sorted(icons, key=lambda item: (item.image.size[1], item.image.size[0]), reverse=True):
        w, h = icon.image.size
        if x > ATLAS_GAP and x + w + ATLAS_GAP > ATLAS_MAX_WIDTH:
            x = ATLAS_GAP
            y += row_height + ATLAS_GAP
            row_height = 0
        placements[icon.icon_id] = (x, y, w, h)
        x += w + ATLAS_GAP
        row_height = max(row_height, h)
        atlas_width = max(atlas_width, x)

    atlas_height = y + row_height + ATLAS_GAP
    atlas = Image.new("RGBA", (atlas_width, atlas_height), (0, 0, 0, 0))
    for icon in icons:
        px, py, _, _ = placements[icon.icon_id]
        atlas.alpha_composite(icon.image, (px, py))
    return atlas, placements


def write_outputs(atlas: Image.Image, placements: dict[str, tuple[int, int, int, int]], icons: list[BuiltIcon]) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    atlas.save(OUT_PNG)

    payload = {
        "atlas": {
            "type": "msdf",
            "texture": "phaze:msdf/ui_icons.png",
            "distanceRange": PX_RANGE,
            "width": atlas.width,
            "height": atlas.height,
        },
        "icons": [],
    }

    for icon in sorted(icons, key=lambda item: item.icon_id):
        x, y, w, h = placements[icon.icon_id]
        payload["icons"].append(
            {
                "id": icon.icon_id,
                "contentAspectRatio": icon.content_aspect_ratio,
                "atlasBounds": {
                    "left": x,
                    "top": y,
                    "right": x + w,
                    "bottom": y + h,
                },
            }
        )

    with OUT_JSON.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=True, separators=(",", ":"))


def main() -> None:
    msdfgen_exe = ensure_msdfgen()
    icons = discover_icons()
    print(f"Discovered {len(icons)} icons")

    built_icons: list[BuiltIcon] = []
    for index, icon in enumerate(icons, 1):
        print(f"[{index}/{len(icons)}] {icon.icon_id}")
        built_icons.append(build_msdf(icon, msdfgen_exe))

    atlas, placements = pack_icons(built_icons)
    write_outputs(atlas, placements, built_icons)

    for icon in built_icons:
        icon.image.close()
    atlas.close()

    print(f"Wrote {OUT_PNG}")
    print(f"Wrote {OUT_JSON}")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(exc, file=sys.stderr)
        raise
