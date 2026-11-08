#version 150

// One instance per batched GUI primitive, expanded into a 4-vertex triangle strip.
// Meta (Geom1.w): kind | texture slot << 3 | UV swap << 7.
// Kind: 0 = rounded rect, 1 = MSDF glyph or icon, 2 = plain texture.
//   rect:    ShapeA = rect base.xy + size.xy (framebuffer px), ShapeB = corner radii,
//            Params = thickness, softness, OutlineColor = outline color
//   msdf:    ShapeA = label fade left, right, width + atlas range,
//            ShapeB = fade strength left, right + thickness, smoothness
// Clip is the scissor box active at submission.

in vec4 Geom0;
in vec4 Geom1;
in vec4 Uv;
in vec4 Color0;
in vec4 Color1;
in vec4 Color2;
in vec4 Color3;
in vec4 ShapeA;
in vec4 ShapeB;
in vec2 Params;
in vec4 OutlineColor;
in vec4 Clip;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vColor;
out vec2 vTexCoord;
out vec2 vGlobalPos;
flat out vec4 vShapeA;
flat out vec4 vShapeB;
flat out vec4 vShapeC;
flat out vec4 vOutlineColor;
flat out vec4 vClip;

void main() {
    // Strip order (0,1) (0,0) (1,1) (1,0): both triangles share the (0,0)-(1,1) diagonal,
    // the split the indexed quads of the eager renderers use.
    vec2 st = vec2(float(gl_VertexID >> 1), float(1 - (gl_VertexID & 1)));
    vec2 position = Geom0.xy + st.x * Geom0.zw + st.y * Geom1.xy;

    int meta = int(Geom1.w + 0.5);
    vTexCoord = (meta & 128) != 0
            ? vec2(mix(Uv.x, Uv.z, st.y), mix(Uv.y, Uv.w, st.x))
            : mix(Uv.xy, Uv.zw, st);
    vColor = st.y < 0.5 ? (st.x < 0.5 ? Color0 : Color1) : (st.x < 0.5 ? Color2 : Color3);
    vGlobalPos = position;
    vShapeA = ShapeA;
    vShapeB = ShapeB;
    vShapeC = vec4(float(meta & 7), float((meta >> 3) & 15), Params);
    vOutlineColor = OutlineColor;
    vClip = Clip;
    gl_Position = ProjMat * ModelViewMat * vec4(position, Geom1.z, 1.0);
}
