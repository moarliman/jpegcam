package com.github.ma1co.pmcademo.app;

public class RTLProfile {
    public String profileName;

    // Color & Tone (Hardware)
    public String whiteBalance = "AUTO";
    public int wbShift = 0;
    public int wbShiftGM = 0;
    public String dro = "OFF";
    public int contrast = 0;
    public int saturation = 0;
    public int sharpness = 0;
    public int sharpnessGain = 0;
    public String colorMode = "standard";

    // 6-axis color depth (-7 to +7)
    public int colorDepthRed = 0;
    public int colorDepthGreen = 0;
    public int colorDepthBlue = 0;
    public int colorDepthCyan = 0;
    public int colorDepthMagenta = 0;
    public int colorDepthYellow = 0;

    // RGB matrix (identity = 100% on diagonal)
    public int[] advMatrix = {100, 0, 0,  0, 100, 0,  0, 0, 100};

    // Picture effects
    public String proColorMode = "off";
    public String pictureEffect = "off";
    public String peToyCameraTone = "normal";
    public int softFocusLevel = 1;
    public int vignetteHardware = 0;

    // Lens shading
    public int shadingRed = 0;
    public int shadingBlue = 0;

    public RTLProfile(int slotIndex) {
        this.profileName = "RECIPE " + (slotIndex + 1);
    }

    public RTLProfile() {
        this.profileName = "RECIPE";
    }
}
