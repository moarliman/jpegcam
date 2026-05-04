package com.github.ma1co.pmcademo.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;

public class FilmPresetManager {
    private static JSONArray sPresets;
    private static String[] sNames;

    public static void init(Context ctx) {
        try {
            InputStream is = ctx.getAssets().open("film_presets.json");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            sPresets = new JSONArray(new String(buf, "UTF-8"));

            sNames = new String[sPresets.length() + 1];
            sNames[0] = "NONE";
            for (int i = 0; i < sPresets.length(); i++) {
                sNames[i + 1] = sPresets.getJSONObject(i).getString("name");
            }
        } catch (Exception e) {
            sPresets = new JSONArray();
            sNames = new String[]{"NONE"};
        }
    }

    public static String[] getNames() {
        return sNames != null ? sNames : new String[]{"NONE"};
    }

    public static void applyPreset(RTLProfile p, String name) {
        if (name == null || name.equals("NONE") || sPresets == null) {
            p.filmSimulation = "NONE";
            return;
        }
        try {
            for (int i = 0; i < sPresets.length(); i++) {
                JSONObject o = sPresets.getJSONObject(i);
                if (o.getString("name").equals(name)) {
                    p.filmSimulation      = name;
                    p.colorMode           = o.optString("colorMode", p.colorMode);
                    p.contrast            = o.optInt("contrast", p.contrast);
                    p.saturation          = o.optInt("saturation", p.saturation);
                    p.sharpness           = o.optInt("sharpness", p.sharpness);
                    p.sharpnessGain       = o.optInt("sharpnessGain", p.sharpnessGain);
                    p.colorDepthRed       = o.optInt("colorDepthRed", p.colorDepthRed);
                    p.colorDepthGreen     = o.optInt("colorDepthGreen", p.colorDepthGreen);
                    p.colorDepthBlue      = o.optInt("colorDepthBlue", p.colorDepthBlue);
                    p.colorDepthCyan      = o.optInt("colorDepthCyan", p.colorDepthCyan);
                    p.colorDepthMagenta   = o.optInt("colorDepthMagenta", p.colorDepthMagenta);
                    p.colorDepthYellow    = o.optInt("colorDepthYellow", p.colorDepthYellow);
                    p.dro                 = o.optString("dro", p.dro);
                    p.wbShift             = o.optInt("wbShift", p.wbShift);
                    p.wbShiftGM           = o.optInt("wbShiftGM", p.wbShiftGM);
                    JSONArray mx = o.optJSONArray("advMatrix");
                    if (mx != null && mx.length() == 9) {
                        for (int j = 0; j < 9; j++) p.advMatrix[j] = mx.getInt(j);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {}
    }
}
