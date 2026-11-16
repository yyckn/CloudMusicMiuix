// Adapted from HaoZai000/softglass 0.1.1 (Apache-2.0); see miuix-controls/NOTICE.
package top.yukonga.miuix.kmp.material.softglass

internal val GLASS_SDF = """
float radiusAt(float2 c, float4 r) {
    if (c.x >= 0.0) {
        return c.y <= 0.0 ? r.y : r.z;
    } else {
        return c.y <= 0.0 ? r.x : r.w;
    }
}

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float2 roundRectGrad(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    float2 g;
    if (q.x > 0.0 && q.y > 0.0) {
        g = q / max(length(q), 1e-4);
    } else {
        g = (q.x > q.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
    }
    return sign(p) * g;
}
"""

/** 先模糊后调色：gcr 与 glassColor 的输入都取自重模糊后的背景色。 */
internal val GLASS_COLOR_PROCESS = """
uniform float3 uDarkerColor;

float glassLuma(float3 c) { return dot(c, GLASS_LUMA); }

float3 glassDarker(float3 c) {
    float k = mix(0.0, uDarker, smoothstep(uDarkerRange.x, uDarkerRange.y, glassLuma(c)));
    return mix(c, uDarkerColor, k);
}

float3 glassAdjust(float3 c, float sat, float bri) {
    float l = dot(c, float3(0.2125, 0.7153, 0.0721));
    return mix(float3(l), c, sat) + bri;
}

float4 processGlassColor(float3 refractCol, float3 bigCol) {
    float3 c = glassDarker(refractCol);
    float l = clamp(glassLuma(c), 0.0, 1.0);
    float lc = ((uLuminance.x * l + uLuminance.y) * l + uLuminance.z) * l + uLuminance.w;
    float curve = clamp(lc, 0.0, 1.0) / max(l, 0.01) * smoothstep(0.0, 0.1, l);
    c = c * mix(1.0, curve, uLuminanceAmount);
    c = glassAdjust(c, uSaturation, uBrightness);

    float3 b = glassDarker(bigCol);
    b = glassAdjust(b, uBgSat, uBgBri);
    b = mix(b, float3(1.0), uInnerWhite);

    float lumin = clamp(glassLuma(b), 0.0, 1.0);
    float tBurn = (uBurn <= 0.0) ? 1.0 : pow(lumin, uBurn);
    tBurn -= 0.5;
    float ratio = 0.8 * mix(lumin, 1.0, 1.587 * tBurn * tBurn * tBurn + 0.5);

    float3 gcr = mix(float3(1.0), b, ratio);
    float m = (gcr.r + gcr.g + gcr.b) / 3.0;
    float gmask = smoothstep(0.0, 0.4, length(b - float3(m)));
    gcr = mix(float3(m), gcr, gmask);
    gcr = mix(gcr, uGlassColor.rgb, uGlassColor.a);

    float im = uInnerMix * ratio;
    c = mix(c * gcr, gcr, im);
    return float4(clamp(c, float3(0.0), float3(1.0)), 1.0);
}
"""
