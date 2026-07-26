package vorga.phazeclient.api.system.font.entry;

import vorga.phazeclient.api.system.font.glyph.Glyph;

public record DrawEntry(float atX, float atY, int color, Glyph toDraw) {
}
