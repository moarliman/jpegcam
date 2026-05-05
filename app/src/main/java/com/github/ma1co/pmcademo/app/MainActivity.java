package com.github.ma1co.pmcademo.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;

import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.sysutil.ScalarInput;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements SurfaceHolder.Callback,
    SonyCameraManager.CameraEventListener, InputManager.InputListener,
    ConnectivityManager.StatusUpdateListener,
    MenuController.HostCallback, HudController.HostCallback, HttpServer.Callback {

    public static final boolean DEBUG_MODE = false;

    static {
        try {
            String[] paths = { "/mnt/sdcard/JPEGCAM_BOOT.TXT", "/sdcard/JPEGCAM_BOOT.TXT",
                               "/storage/sdcard0/JPEGCAM_BOOT.TXT", "/storage/sdcard1/JPEGCAM_BOOT.TXT" };
            for (String p : paths) {
                try {
                    java.io.FileOutputStream f = new java.io.FileOutputStream(p);
                    f.write("MainActivity class loaded OK\n".getBytes());
                    f.close();
                } catch (Exception ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private SonyCameraManager cameraManager;
    private InputManager inputManager;
    private RecipeManager recipeManager;
    private MatrixManager matrixManager;
    private ConnectivityManager connectivityManager;

    private Typeface digitalFont;

    private SurfaceView mSurfaceView;
    private boolean hasSurface = false;

    private FrameLayout mainUIContainer;
    private LinearLayout llBottomBar;

    private TextView tvTopStatus;
    private TextView tvBattery;
    private TextView tvValShutter;
    private TextView tvValAperture;
    private TextView tvValIso;
    private TextView tvValEv;
    private TextView tvMode;
    private TextView tvFocusMode;
    private HudController hudController;

    private int matrixToPercent(int hardwareValue) {
        return Math.round((hardwareValue / 1024.0f) * 100.0f);
    }
    private int percentToMatrix(int percentValue) {
        return Math.round((percentValue / 100.0f) * 1024.0f);
    }

    private BatteryView batteryIcon;
    private MenuController menuController;
    private boolean isReady = true;
    private int displayState = 0;

    private boolean prefShowFocusMeter = true;
    private boolean prefShowCinemaMattes = false;
    private boolean prefShowGridLines = false;

    private boolean hasPhysicalPasmDial = false;
    private boolean isFullFrame = false;
    private BroadcastReceiver hardwareStateReceiver;

    private boolean cachedIsManualFocus = false;
    private float cachedAperture = 2.8f;
    private float cachedFocusRatio = 0.5f;

    private GridLinesView gridLines;
    private CinemaMatteView cinemaMattes;
    private AdvancedFocusMeterView focusMeter;
    private ProReticleView afOverlay;

    private Handler uiHandler = new Handler();

    public static final int DIAL_MODE_SHUTTER  = 0;
    public static final int DIAL_MODE_APERTURE = 1;
    public static final int DIAL_MODE_ISO      = 2;
    public static final int DIAL_MODE_EXPOSURE = 3;
    public static final int DIAL_MODE_REVIEW   = 4;
    public static final int DIAL_MODE_RTL      = 5;
    public static final int DIAL_MODE_PASM     = 6;
    public static final int DIAL_MODE_FOCUS    = 7;

    private int mDialMode = DIAL_MODE_RTL;

    private final String[] MATRIX_PRESET_NAMES = {"STANDARD", "GOLDEN HOUR", "PNW GREEN", "CINEMATIC", "BLEACH BYPASS", "AEROCHROME", "CUSTOM"};
    private final int[][] MATRIX_PRESET_VALUES = {
        {100, 0, 0, 0, 100, 0, 0, 0, 100},
        {115, 5, 0, 5, 105, 0, 0, 0, 95},
        {95, 0, 0, 0, 110, 5, 0, 15, 105},
        {110, -10, 0, -5, 100, 10, 0, 5, 115},
        {130, 0, 0, 0, 130, 0, 0, 0, 130},
        {0, 140, 0, 100, 0, 0, 0, 0, 100}
    };
    private final String[] MATRIX_PRESET_NOTES = {
        "Identity Matrix. Zero color shift.",
        "Broadens the yellow spectrum.",
        "Fuji-style vintage teals.",
        "Professional Teal/Orange separation.",
        "High color density. WARNING: May clip highlights.",
        "False-color Infrared swap.",
        "Manual matrix override active."
    };

    private Runnable applySettingsRunnable = new Runnable() {
        @Override
        public void run() { applyHardwareRecipe(); }
    };

    private Runnable liveUpdater = new Runnable() {
        @Override
        public void run() {
            if (displayState == 0 && !menuController.isOpen() && hasSurface) {
                if (cameraManager != null && cameraManager.getCamera() != null) {
                    boolean s1_1_free = ScalarInput.getKeyStatus(ScalarInput.ISV_KEY_S1_1).status == 0;
                    boolean s1_2_free = ScalarInput.getKeyStatus(ScalarInput.ISV_KEY_S1_2).status == 0;
                    if (s1_1_free && s1_2_free) {
                        if (afOverlay != null && afOverlay.isPolling()) {
                            afOverlay.stopFocus(cameraManager.getCamera());
                            requestHudUpdate();
                        }
                        if (tvTopStatus != null && tvTopStatus.getVisibility() != View.VISIBLE) {
                            setHUDVisibility(View.VISIBLE);
                        }
                    }
                }
            }
            uiHandler.postDelayed(this, 500);
        }
    };

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                final int pct = (level * 100) / scale;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (tvBattery != null) tvBattery.setText(pct + "%");
                        if (batteryIcon != null) batteryIcon.setLevel(pct);
                    }
                });
            }
        }
    };

    private void factoryBurnMatrices() {
        if (matrixManager != null && matrixManager.getCount() == 0) {
            matrixManager.saveMatrix("STANDARD",      new int[]{100, 0, 0, 0, 100, 0, 0, 0, 100}, "Identity Matrix. Zero color shift.");
            matrixManager.saveMatrix("GOLDEN HOUR",   new int[]{115, 5, 0, 5, 105, 0, 0, 0, 95},   "Broadens yellow spectrum.");
            matrixManager.saveMatrix("PAC. NW GREEN", new int[]{95, 0, 0, 0, 110, 5, 0, 15, 105},  "Vintage teals.");
            matrixManager.saveMatrix("CINEMATIC",     new int[]{110, -10, 0, -5, 100, 10, 0, 5, 115}, "Teal/Orange separation.");
            matrixManager.saveMatrix("BLEACH BYPASS", new int[]{130, 0, 0, 0, 130, 0, 0, 0, 130}, "High density. WARNING: May clip highlights.");
            matrixManager.saveMatrix("AEROCHROME",    new int[]{0, 140, 0, 100, 0, 0, 0, 0, 100},  "False-color Infrared swap.");
            matrixManager.scanMatrices();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable ex) {
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("=== CRASH ===\n");
                    sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append("\n");
                    for (StackTraceElement e : ex.getStackTrace()) sb.append("  at ").append(e).append("\n");
                    byte[] data = sb.toString().getBytes("UTF-8");
                    String[] paths = { "/mnt/sdcard/JPEGCAM_CRASH.TXT", "/sdcard/JPEGCAM_CRASH.TXT",
                                       "/storage/sdcard0/JPEGCAM_CRASH.TXT", "/storage/sdcard1/JPEGCAM_CRASH.TXT" };
                    for (String p : paths) {
                        try { java.io.FileOutputStream fos = new java.io.FileOutputStream(p, true); fos.write(data); fos.close(); } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });

        super.onCreate(savedInstanceState);

        hasPhysicalPasmDial = false;
        try {
            java.lang.reflect.Method deviceHasKeyMethod = android.view.KeyCharacterMap.class.getMethod("deviceHasKey", int.class);
            boolean hasDialKey1 = (Boolean) deviceHasKeyMethod.invoke(null, 624);
            boolean hasDialKey2 = (Boolean) deviceHasKeyMethod.invoke(null, com.sony.scalar.sysutil.ScalarInput.ISV_KEY_MODE_DIAL);
            hasPhysicalPasmDial = hasDialKey1 || hasDialKey2;
        } catch (Exception e) {
            android.util.Log.e("JPEG.CAM", "Legacy API 10 Camera Detected.");
        }

        Filepaths.buildAppStructure();

        File thumbsDir = new File(Filepaths.getDcimDir(), ".thumbnails");
        if (!thumbsDir.exists()) thumbsDir.mkdirs();

        SharedPreferences prefs = getSharedPreferences("JPEG.CAM_Prefs", MODE_PRIVATE);
        prefShowFocusMeter   = prefs.getBoolean("focusMeter",    true);
        prefShowCinemaMattes = prefs.getBoolean("cinemaMattes",  false);
        prefShowGridLines    = prefs.getBoolean("gridLines",     false);

        cameraManager       = new SonyCameraManager(this);
        inputManager        = new InputManager(this);
        recipeManager       = new RecipeManager();
        recipeManager.extractPresetsIfNeeded(this);
        matrixManager       = new MatrixManager();
        matrixManager.scanMatrices();
        factoryBurnMatrices();
        connectivityManager = new ConnectivityManager(this, this);
        connectivityManager.getServer().setCallback(this);

        recipeManager.loadPreferences();

        try {
            digitalFont = Typeface.createFromAsset(getAssets(), "fonts/digital-7.ttf");
        } catch (Exception e) {
            Log.e("JPEG.CAM", "Could not load custom font.");
            digitalFont = null;
        }

        FrameLayout rootLayout = new FrameLayout(this);
        mSurfaceView = new SurfaceView(this);
        mSurfaceView.getHolder().addCallback(this);
        mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        rootLayout.addView(mSurfaceView, new FrameLayout.LayoutParams(-1, -1));

        buildUI(rootLayout);
        setContentView(rootLayout);
    }

    @Override
    public void onShutterHalfPressed() {
        if (menuController.isOpen()) { menuController.close(); return; }
        if (displayState == 0 && !menuController.isOpen()) setHUDVisibility(View.GONE);
        if (cameraManager != null && cameraManager.getCamera() != null && !cachedIsManualFocus) {
            if (afOverlay != null) afOverlay.startFocus(cameraManager.getCamera());
        }
    }

    @Override
    public void onShutterHalfReleased() {
        if (displayState == 0 && !menuController.isOpen()) setHUDVisibility(View.VISIBLE);
        if (afOverlay != null && cameraManager != null && cameraManager.getCamera() != null) {
            afOverlay.stopFocus(cameraManager.getCamera());
        }
    }

    @Override
    public void onDeletePressed() { finish(); }

    @Override
    public void onMenuPressed() {
        if (menuController.isOpen()) menuController.close();
        else menuController.open();
    }

    private Runnable hudUpdateRunnable = new Runnable() {
        @Override public void run() { updateMainHUD(); }
    };

    private void requestHudUpdate() {
        uiHandler.removeCallbacks(hudUpdateRunnable);
        uiHandler.postDelayed(hudUpdateRunnable, 100);
    }

    @Override
    public void onEnterPressed() {
        if (hudController.isActive()) {
            if (hudController.getMode() == 10) {
                if (menuController.isNamingMode()) {
                    menuController.setNamingMode(false);
                    String finalName = new String(menuController.getNameBuffer()).trim();
                    recipeManager.saveSlotToVault(finalName);
                    hudController.refreshVaultItems();
                    for (int i = 0; i < hudController.getVaultItems().size(); i++) {
                        if (hudController.getVaultItems().get(i).profileName.equalsIgnoreCase(finalName)) { hudController.setVaultIndex(i); break; }
                    }
                    hudController.update(); return;
                } else if (menuController.isConfirmingDelete()) {
                    if (hudController.getSelection() == 0) {
                        recipeManager.deleteVaultItem(hudController.getVaultIndex()); hudController.setVaultIndex(0);
                        hudController.refreshVaultItems();
                        if (!hudController.getVaultItems().isEmpty() && !hudController.getVaultItems().get(0).filename.equals("NONE")) recipeManager.previewVaultToSlot(hudController.getVaultItems().get(0).filename);
                        else recipeManager.resetCurrentSlot();
                        applyHardwareRecipe();
                        menuController.setConfirmingDelete(false); hudController.setSelection(1); hudController.update(); return;
                    } else if (hudController.getSelection() == 1) {
                        menuController.setConfirmingDelete(false); hudController.setSelection(0); hudController.update(); return;
                    }
                } else {
                    if (hudController.getSelection() == 0) {
                        menuController.setNamingMode(true);
                        RTLProfile activeProfile = recipeManager.getCurrentProfile();
                        String currentName = (activeProfile != null) ? activeProfile.profileName : "";
                        if (currentName != null && !currentName.isEmpty() && !currentName.startsWith("SLOT ")) menuController.fillNameBuffer(currentName);
                        else menuController.resetNameBuffer();
                        menuController.resetNameCursor(); hudController.update(); return;
                    } else if (hudController.getSelection() == 1) {
                        if (!hudController.getVaultItems().isEmpty() && !hudController.getVaultItems().get(hudController.getVaultIndex()).filename.equals("NONE")) {
                            recipeManager.previewVaultToSlot(hudController.getVaultItems().get(hudController.getVaultIndex()).filename);
                            applyHardwareRecipe();
                        }
                        recipeManager.savePreferences();
                        hudController.close();
                        return;
                    } else if (hudController.getSelection() == 2) {
                        recipeManager.resetCurrentSlot();
                        applyHardwareRecipe();
                        hudController.close();
                        return;
                    } else if (hudController.getSelection() == 3) {
                        if (!hudController.getVaultItems().isEmpty() && !hudController.getVaultItems().get(hudController.getVaultIndex()).filename.equals("NONE")) {
                            menuController.setConfirmingDelete(true); hudController.setSelection(1); hudController.update(); return;
                        }
                    }
                }
                if (!hudController.isActive()) {
                    hudController.hideOverlays();
                    menuController.getContainer().setVisibility(View.VISIBLE);
                    menuController.refreshDisplay();
                }
            return;
            }

            if (hudController.getMode() == 0 && hudController.getSelection() == -1) {
                RTLProfile p = recipeManager.getCurrentProfile();
                if (!menuController.isNamingMode()) {
                    for (int i = 0; i < matrixManager.getCount(); i++) {
                        int[] existing = matrixManager.getValues(i); boolean isMatch = true;
                        for (int j = 0; j < 9; j++) if (p.advMatrix[j] != existing[j]) { isMatch = false; break; }
                        if (isMatch) { tvTopStatus.setText("ALREADY SAVED: " + matrixManager.getNames().get(i)); tvTopStatus.setTextColor(Color.GREEN); return; }
                    }
                }
                menuController.setNamingMode(!menuController.isNamingMode());
                if (menuController.isNamingMode()) { menuController.resetNameBuffer(); menuController.resetNameCursor(); hudController.update();
                } else { String finalName = new String(menuController.getNameBuffer()).trim(); if (finalName.isEmpty()) finalName = "CUSTOM"; hudController.saveCustomMatrix(finalName); }
                return;
            }

            hudController.close();
            recipeManager.savePreferences();
            return;
        }

        if (menuController.dispatchHudLaunch()) return;

        if (!menuController.isOpen()) {
            displayState = (displayState == 0) ? 1 : 0;
            mainUIContainer.setVisibility(displayState == 0 ? View.VISIBLE : View.GONE);
            updateMainHUD();
        } else {
            menuController.handleEnter();
        }
    }

    @Override
    public void onUpPressed() {
        if (hudController.isActive() && (hudController.getMode() == 0 || hudController.getMode() == 10) && menuController.isNamingMode()) {
            char[] buf = menuController.getNameBuffer();
            int pos = menuController.getNameCursorPos();
            int idx = MenuController.CHARSET.indexOf(buf[pos]);
            if (idx == -1) idx = 0;
            idx = (idx + 1) % MenuController.CHARSET.length();
            buf[pos] = MenuController.CHARSET.charAt(idx);
            hudController.update();
            return;
        }
        if (hudController.isActive() && !menuController.isNamingMode()) { hudController.handleUp(); return; }
        if (menuController.isOpen()) { menuController.handleUp(); return; }
        navigateHomeSpatial(ScalarInput.ISV_KEY_UP);
    }

    @Override
    public void onDownPressed() {
        if (hudController.isActive() && (hudController.getMode() == 0 || hudController.getMode() == 10) && menuController.isNamingMode()) {
            char[] buf = menuController.getNameBuffer();
            int pos = menuController.getNameCursorPos();
            int idx = MenuController.CHARSET.indexOf(buf[pos]);
            if (idx == -1) idx = 0;
            idx = (idx - 1 + MenuController.CHARSET.length()) % MenuController.CHARSET.length();
            buf[pos] = MenuController.CHARSET.charAt(idx);
            hudController.update();
            return;
        }
        if (hudController.isActive() && !menuController.isNamingMode()) { hudController.handleDown(); return; }
        if (menuController.isOpen()) { menuController.handleDown(); return; }
        navigateHomeSpatial(ScalarInput.ISV_KEY_DOWN);
    }

    @Override
    public void onLeftPressed() {
        if (hudController.isActive() && (hudController.getMode() == 0 || hudController.getMode() == 10) && menuController.isNamingMode()) {
            menuController.advanceNameCursor(-1);
            hudController.update();
            return;
        }
        if (hudController.isActive() && !menuController.isNamingMode()) { hudController.handleLeft(); return; }
        if (menuController.isOpen()) { menuController.handleLeft(); return; }
        navigateHomeSpatial(ScalarInput.ISV_KEY_LEFT);
    }

    @Override
    public void onRightPressed() {
        if (hudController.isActive() && (hudController.getMode() == 0 || hudController.getMode() == 10) && menuController.isNamingMode()) {
            menuController.advanceNameCursor(1);
            hudController.update();
            return;
        }
        if (hudController.isActive() && !menuController.isNamingMode()) { hudController.handleRight(); return; }
        if (menuController.isOpen()) { menuController.handleRight(); return; }
        navigateHomeSpatial(ScalarInput.ISV_KEY_RIGHT);
    }

    @Override
    public void onCustomButtonPressed() {
        if (menuController.isOpen()) return;
        mDialMode = DIAL_MODE_ISO;
        updateMainHUD();
    }

    @Override
    public void onFrontDialRotated(int direction) {
        if (hudController.isActive() || menuController.isOpen()) { onControlWheelRotated(direction); return; }
        if (cameraManager != null && cameraManager.getCameraEx() != null) {
            if (direction > 0) cameraManager.getCameraEx().incrementShutterSpeed();
            else cameraManager.getCameraEx().decrementShutterSpeed();
            updateMainHUD();
        }
    }

    @Override
    public void onRearDialRotated(int direction) {
        if (hudController.isActive() || menuController.isOpen()) { onControlWheelRotated(direction); return; }
        if (cameraManager != null && cameraManager.getCameraEx() != null) {
            if (direction > 0) cameraManager.getCameraEx().incrementAperture();
            else cameraManager.getCameraEx().decrementAperture();
            updateMainHUD();
        }
    }

    @Override
    public void onControlWheelRotated(int direction) {
        if (hudController.isActive() && (hudController.getMode() == 0 || hudController.getMode() == 10) && menuController.isNamingMode()) {
            char[] buf = menuController.getNameBuffer();
            int pos = menuController.getNameCursorPos();
            int idx = MenuController.CHARSET.indexOf(buf[pos]);
            if (idx == -1) idx = 0;
            idx = (idx + direction + MenuController.CHARSET.length()) % MenuController.CHARSET.length();
            buf[pos] = MenuController.CHARSET.charAt(idx);
            hudController.update();
            return;
        }
        if (hudController.isActive() && !menuController.isNamingMode()) { hudController.handleDial(direction); return; }
        if (menuController.isOpen()) {
            menuController.handleDial(direction);
        } else {
            handleHardwareInput(direction);
        }
    }

    private void navigateHomeSpatial(int keyCode) {
        switch (mDialMode) {
            case DIAL_MODE_SHUTTER:
                if (keyCode == ScalarInput.ISV_KEY_RIGHT) mDialMode = DIAL_MODE_APERTURE;
                else if (keyCode == ScalarInput.ISV_KEY_UP || keyCode == ScalarInput.ISV_KEY_LEFT) mDialMode = DIAL_MODE_FOCUS;
                break;
            case DIAL_MODE_APERTURE:
                if (keyCode == ScalarInput.ISV_KEY_LEFT) mDialMode = DIAL_MODE_SHUTTER;
                else if (keyCode == ScalarInput.ISV_KEY_RIGHT) mDialMode = DIAL_MODE_ISO;
                else if (keyCode == ScalarInput.ISV_KEY_UP) mDialMode = DIAL_MODE_PASM;
                break;
            case DIAL_MODE_ISO:
                if (keyCode == ScalarInput.ISV_KEY_LEFT) mDialMode = DIAL_MODE_APERTURE;
                else if (keyCode == ScalarInput.ISV_KEY_RIGHT) mDialMode = DIAL_MODE_EXPOSURE;
                else if (keyCode == ScalarInput.ISV_KEY_UP) mDialMode = DIAL_MODE_REVIEW;
                break;
            case DIAL_MODE_EXPOSURE:
                if (keyCode == ScalarInput.ISV_KEY_LEFT) mDialMode = DIAL_MODE_ISO;
                else if (keyCode == ScalarInput.ISV_KEY_UP || keyCode == ScalarInput.ISV_KEY_RIGHT) mDialMode = DIAL_MODE_REVIEW;
                break;
            case DIAL_MODE_REVIEW:
                if (keyCode == ScalarInput.ISV_KEY_DOWN) mDialMode = DIAL_MODE_EXPOSURE;
                else if (keyCode == ScalarInput.ISV_KEY_LEFT) mDialMode = DIAL_MODE_RTL;
                else if (keyCode == ScalarInput.ISV_KEY_RIGHT || keyCode == ScalarInput.ISV_KEY_UP) mDialMode = DIAL_MODE_EXPOSURE;
                break;
            case DIAL_MODE_RTL:
                if (keyCode == ScalarInput.ISV_KEY_DOWN) mDialMode = DIAL_MODE_PASM;
                else if (keyCode == ScalarInput.ISV_KEY_RIGHT) mDialMode = DIAL_MODE_REVIEW;
                else if (keyCode == ScalarInput.ISV_KEY_LEFT) mDialMode = DIAL_MODE_PASM;
                break;
            case DIAL_MODE_PASM:
                if (keyCode == ScalarInput.ISV_KEY_DOWN) mDialMode = DIAL_MODE_FOCUS;
                else if (keyCode == ScalarInput.ISV_KEY_RIGHT || keyCode == ScalarInput.ISV_KEY_UP) mDialMode = DIAL_MODE_RTL;
                else if (keyCode == ScalarInput.ISV_KEY_LEFT) mDialMode = DIAL_MODE_FOCUS;
                break;
            case DIAL_MODE_FOCUS:
                if (keyCode == ScalarInput.ISV_KEY_UP || keyCode == ScalarInput.ISV_KEY_RIGHT) mDialMode = DIAL_MODE_PASM;
                else if (keyCode == ScalarInput.ISV_KEY_DOWN || keyCode == ScalarInput.ISV_KEY_LEFT) mDialMode = DIAL_MODE_SHUTTER;
                break;
        }
        updateMainHUD();
    }

    private void handleHardwareInput(int d) {
        if (cameraManager == null || cameraManager.getCamera() == null || cameraManager.getCameraEx() == null) return;

        Camera c = cameraManager.getCamera();
        CameraEx cx = cameraManager.getCameraEx();
        Camera.Parameters p = c.getParameters();
        CameraEx.ParametersModifier pm = cx.createParametersModifier(p);

        if (mDialMode == DIAL_MODE_RTL) {
            recipeManager.setCurrentSlot(recipeManager.getCurrentSlot() + d);
            applyHardwareRecipe();
        } else if (mDialMode == DIAL_MODE_SHUTTER) {
            if (d > 0) cx.incrementShutterSpeed(); else cx.decrementShutterSpeed();
        } else if (mDialMode == DIAL_MODE_APERTURE) {
            if (d > 0) cx.incrementAperture(); else cx.decrementAperture();
        } else if (mDialMode == DIAL_MODE_ISO) {
            List<Integer> isos = (List<Integer>) pm.getSupportedISOSensitivities();
            if (isos != null) {
                int idx = isos.indexOf(pm.getISOSensitivity());
                if (idx != -1) {
                    pm.setISOSensitivity(isos.get(Math.max(0, Math.min(isos.size()-1, idx + d))));
                    try { c.setParameters(p); } catch (Exception e) {}
                }
            }
        } else if (mDialMode == DIAL_MODE_EXPOSURE) {
            int ev = p.getExposureCompensation();
            p.setExposureCompensation(Math.max(p.getMinExposureCompensation(), Math.min(p.getMaxExposureCompensation(), ev + d)));
            try { c.setParameters(p); } catch (Exception e) {}
        } else if (mDialMode == DIAL_MODE_PASM) {
            if (hasPhysicalPasmDial) return;
            List<String> valid = new ArrayList<String>();
            String[] desired = {"program-auto", "aperture-priority", "shutter-priority", "shutter-speed", "manual-exposure", "auto"};
            List<String> supported = p.getSupportedSceneModes();
            if (supported != null) {
                for (String s : desired) if (supported.contains(s)) valid.add(s);
                if (!valid.isEmpty()) {
                    int idx = valid.indexOf(p.getSceneMode());
                    if (idx == -1) idx = 0;
                    p.setSceneMode(valid.get((idx + d + valid.size()) % valid.size()));
                    try { c.setParameters(p); } catch (Exception e) {}
                }
            }
        } else if (mDialMode == DIAL_MODE_FOCUS) {
            List<String> modes = p.getSupportedFocusModes();
            if (modes != null && !modes.isEmpty()) {
                int idx = modes.indexOf(p.getFocusMode());
                if (idx == -1) idx = 0;
                String next = modes.get((idx + d + modes.size()) % modes.size());
                if (android.os.Build.VERSION.SDK_INT >= 14) {
                    try { if (p.getMaxNumFocusAreas() > 0) p.setFocusAreas(null); } catch (Throwable t) {}
                }
                if (p.get("sony-focus-area") != null) p.set("sony-focus-area", "wide");
                p.setFocusMode(next);
                try { c.setParameters(p); } catch (Exception e) {
                    android.util.Log.e("JPEG.CAM", "Failed to set focus mode: " + e.getMessage());
                }
            }
        }
        updateMainHUD();
    }

    private void applyHardwareRecipe() {
        if (cameraManager == null || cameraManager.getCamera() == null) return;
        HardwareRecipeApplier.apply(cameraManager.getCamera(), recipeManager.getCurrentProfile());
    }

    private void setAutoPowerOffMode(boolean enable) {
        String mode = enable ? "APO/NORMAL" : "APO/NO";
        Intent intent = new Intent();
        intent.setAction("com.android.server.DAConnectionManagerService.apo");
        intent.putExtra("apo_info", mode);
        sendBroadcast(intent);
    }

    private void launchHudMode(int mode, int defaultSelection) {
        mainUIContainer.setVisibility(View.VISIBLE);
        setHUDVisibility(View.GONE);
        hudController.launch(mode, defaultSelection);
    }

    private void launchHudMode(int mode) { launchHudMode(mode, 0); }

    private void buildUI(FrameLayout rootLayout) {
        mainUIContainer = new FrameLayout(this);
        rootLayout.addView(mainUIContainer, new FrameLayout.LayoutParams(-1, -1));

        gridLines = new GridLinesView(this);
        mainUIContainer.addView(gridLines, new FrameLayout.LayoutParams(-1, -1));

        cinemaMattes = new CinemaMatteView(this);
        mainUIContainer.addView(cinemaMattes, new FrameLayout.LayoutParams(-1, -1));

        tvTopStatus = new TextView(this);
        tvTopStatus.setTextColor(Color.WHITE);
        tvTopStatus.setTextSize(20);
        if (digitalFont != null) tvTopStatus.setTypeface(digitalFont);
        else tvTopStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvTopStatus.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        topParams.setMargins(0, 15, 0, 0);
        mainUIContainer.addView(tvTopStatus, topParams);

        LinearLayout rightBar = new LinearLayout(this);
        rightBar.setOrientation(LinearLayout.VERTICAL);
        rightBar.setGravity(Gravity.RIGHT);

        LinearLayout batteryArea = new LinearLayout(this);
        batteryArea.setOrientation(LinearLayout.HORIZONTAL);
        batteryArea.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);

        tvBattery = new TextView(this);
        tvBattery.setTextColor(Color.rgb(227, 69, 20));
        tvBattery.setTextSize(14);
        tvBattery.setTypeface(Typeface.DEFAULT_BOLD);
        tvBattery.setPadding(0, 0, 5, 0);
        batteryArea.addView(tvBattery);

        batteryIcon = new BatteryView(this);
        batteryArea.addView(batteryIcon, new LinearLayout.LayoutParams(28, 12));
        rightBar.addView(batteryArea);

        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        rightParams.setMargins(0, 20, 30, 0);
        mainUIContainer.addView(rightBar, rightParams);

        LinearLayout leftBar = new LinearLayout(this);
        leftBar.setOrientation(LinearLayout.VERTICAL);
        tvMode = createSideTextIcon("M");
        leftBar.addView(tvMode);
        tvFocusMode = createSideTextIcon("AF-S");
        leftBar.addView(tvFocusMode);

        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        leftParams.setMargins(20, 20, 0, 0);
        mainUIContainer.addView(leftBar, leftParams);

        focusMeter = new AdvancedFocusMeterView(this);
        FrameLayout.LayoutParams fmParams = new FrameLayout.LayoutParams(-1, 140, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fmParams.setMargins(0, 0, 0, 70);
        mainUIContainer.addView(focusMeter, fmParams);

        llBottomBar = new LinearLayout(this);
        llBottomBar.setOrientation(LinearLayout.HORIZONTAL);
        llBottomBar.setGravity(Gravity.CENTER);

        tvValShutter  = createBottomText();
        tvValAperture = createBottomText();
        tvValIso      = createBottomText();
        tvValEv       = createBottomText();

        llBottomBar.addView(tvValShutter);
        llBottomBar.addView(tvValAperture);
        llBottomBar.addView(tvValIso);
        llBottomBar.addView(tvValEv);

        FrameLayout.LayoutParams botParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        botParams.setMargins(0, 0, 0, 25);
        mainUIContainer.addView(llBottomBar, botParams);

        afOverlay = new ProReticleView(this);
        mainUIContainer.addView(afOverlay, new FrameLayout.LayoutParams(-1, -1));

        menuController = new MenuController(this, rootLayout, this);
        hudController  = new HudController(this, mainUIContainer, this);
    }

    private TextView createBottomText() {
        TextView tv = new TextView(this);
        tv.setTextSize(26);
        if (digitalFont != null) tv.setTypeface(digitalFont);
        else tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setShadowLayer(4, 0, 0, Color.BLACK);
        tv.setPadding(20, 0, 20, 0);
        return tv;
    }

    private TextView createSideTextIcon(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(22);
        if (digitalFont != null) tv.setTypeface(digitalFont);
        else tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setPadding(25, 15, 25, 15);
        tv.setBackgroundColor(Color.argb(140, 40, 40, 40));
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, 0, 15);
        tv.setLayoutParams(lp);
        return tv;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action  = event.getAction();

        if (keyCode == 624 || keyCode == ScalarInput.ISV_KEY_MODE_DIAL ||
           (keyCode >= ScalarInput.ISV_KEY_MODE_INVALID && keyCode <= ScalarInput.ISV_KEY_MODE_CUSTOM3)) {
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                if (!hasPhysicalPasmDial) hasPhysicalPasmDial = true;
                if (cameraManager != null) onHardwareStateChanged();
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int k, android.view.KeyEvent e) {
        if (k == 624 || k == ScalarInput.ISV_KEY_MODE_DIAL ||
           (k >= ScalarInput.ISV_KEY_MODE_INVALID && k <= ScalarInput.ISV_KEY_MODE_CUSTOM3)) {
            if (!hasPhysicalPasmDial) hasPhysicalPasmDial = true;
            if (cameraManager != null) onHardwareStateChanged();
            return true;
        }
        if (inputManager != null) return inputManager.handleKeyDown(k, e) || super.onKeyDown(k, e);
        return super.onKeyDown(k, e);
    }

    @Override
    public boolean onKeyUp(int k, android.view.KeyEvent e) {
        if (k == 624 || k == ScalarInput.ISV_KEY_MODE_DIAL ||
           (k >= ScalarInput.ISV_KEY_MODE_INVALID && k <= ScalarInput.ISV_KEY_MODE_CUSTOM3)) {
            return true;
        }
        if (inputManager != null) return inputManager.handleKeyUp(k, e) || super.onKeyUp(k, e);
        return super.onKeyUp(k, e);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hardwareStateReceiver == null) {
            hardwareStateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (cameraManager != null) onHardwareStateChanged();
                }
            };
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.sony.scalar.hardware.action.MODE_DIAL_CHANGED");
        filter.addAction("com.android.server.DAConnectionManagerService.HardwareStateChanged");
        registerReceiver(hardwareStateReceiver, filter);

        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacksAndMessages(null);

        if (hardwareStateReceiver != null) {
            try { unregisterReceiver(hardwareStateReceiver); } catch (Exception e) {}
        }

        if (cameraManager != null && cameraManager.getCamera() != null) {
            try {
                Camera c = cameraManager.getCamera();
                Camera.Parameters p = c.getParameters();

                String currentPasm = p.getSceneMode();
                if (currentPasm != null) {
                    getSharedPreferences("JPEG.CAM_Prefs", MODE_PRIVATE)
                        .edit().putString("savedPasmMode", currentPasm).apply();
                }

                p.setFocusMode("auto");

                if (p.get("picture-effect")          != null) p.set("picture-effect",          "off");
                if (p.get("rgb-matrix-mode")         != null) p.set("rgb-matrix-mode",          "false");
                if (p.get("pro-color-mode")          != null) p.set("pro-color-mode",           "off");
                if (p.get("sharpness-gain-mode")     != null) p.set("sharpness-gain-mode",      "false");
                if (p.get("white-balance-shift-mode") != null) p.set("white-balance-shift-mode", "false");
                if (p.get("rgb-matrix")              != null) p.set("rgb-matrix",               "256,0,0,0,256,0,0,0,256");
                if (p.get("lens-correction-shading-color-red")  != null) p.set("lens-correction-shading-color-red",  "0");
                if (p.get("lens-correction-shading-color-blue") != null) p.set("lens-correction-shading-color-blue", "0");
                if (p.get("color-depth-red") != null) {
                    p.set("color-depth-red", "0"); p.set("color-depth-green", "0");
                    p.set("color-depth-blue", "0"); p.set("color-depth-cyan",  "0");
                    p.set("color-depth-magenta", "0"); p.set("color-depth-yellow", "0");
                }
                c.setParameters(p);
                Thread.sleep(200);
            } catch (Exception e) {}
        }

        if (cameraManager != null) cameraManager.close();
        try { unregisterReceiver(batteryReceiver); } catch (Exception e) {}

        if (connectivityManager != null) connectivityManager.stopNetworking();
        if (recipeManager != null) recipeManager.savePreferences();
        setAutoPowerOffMode(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null) connectivityManager.shutdown();
        System.exit(0);
    }

    private void setHUDVisibility(int v) {
        if (tvTopStatus  != null) tvTopStatus.setVisibility(v);
        if (llBottomBar  != null) llBottomBar.setVisibility(v);
        if (tvBattery    != null) tvBattery.setVisibility(v);
        if (batteryIcon  != null) batteryIcon.setVisibility(v);
        if (tvMode       != null) tvMode.setVisibility(v);
        if (tvFocusMode  != null) tvFocusMode.setVisibility(v);
        if (focusMeter   != null) focusMeter.setVisibility((v == View.VISIBLE && cachedIsManualFocus && prefShowFocusMeter) ? View.VISIBLE : View.GONE);
    }

    private void updateMainHUD() {
        if (cameraManager == null || cameraManager.getCamera() == null) return;

        if (hudController.isActive()) {
            setHUDVisibility(View.GONE);
            if (tvTopStatus != null) tvTopStatus.setVisibility(View.VISIBLE);
            if (focusMeter  != null) focusMeter.setVisibility(View.GONE);
            return;
        } else {
            setHUDVisibility(View.VISIBLE);
        }

        Camera c = cameraManager.getCamera();
        Camera.Parameters p = c.getParameters();
        CameraEx.ParametersModifier pm = cameraManager.getCameraEx().createParametersModifier(p);

        RTLProfile prof = recipeManager.getCurrentProfile();
        String customName = prof.profileName != null ? prof.profileName.trim() : ("RECIPE " + (recipeManager.getCurrentSlot() + 1));
        if (customName.isEmpty()) customName = "RECIPE " + (recipeManager.getCurrentSlot() + 1);

        if (tvTopStatus != null) {
            int slotNum = recipeManager.getCurrentSlot() + 1;
            tvTopStatus.setText("SLOT " + slotNum + ": " + customName);
            tvTopStatus.setTextColor(mDialMode == DIAL_MODE_RTL ? Color.WHITE : Color.rgb(0, 230, 118));
        }

        String sm = p.getSceneMode();
        if (tvMode != null) {
            if      ("manual-exposure".equals(sm))                                   tvMode.setText("M");
            else if ("aperture-priority".equals(sm))                                 tvMode.setText("A");
            else if ("shutter-priority".equals(sm) || "shutter-speed".equals(sm))   tvMode.setText("S");
            else if ("program-auto".equals(sm))                                      tvMode.setText("P");
            else if ("auto".equals(sm))                                              tvMode.setText("AUTO");
            else                                                                     tvMode.setText(sm != null ? sm.toUpperCase() : "SCN");
        }

        cachedAperture = pm.getAperture() / 100.0f;
        Pair<Integer, Integer> ss = pm.getShutterSpeed();

        if (tvValAperture != null) tvValAperture.setText(String.format("f%.1f", cachedAperture));
        if (tvValShutter  != null) tvValShutter.setText(ss.first == 1 && ss.second != 1 ? ss.first + "/" + ss.second : ss.first + "\"");
        if (tvValIso      != null) tvValIso.setText(pm.getISOSensitivity() == 0 ? "ISO AUTO" : "ISO " + pm.getISOSensitivity());
        if (tvValEv       != null) tvValEv.setText(String.format("%+.1f", p.getExposureCompensation() * p.getExposureCompensationStep()));

        if (tvValShutter  != null) tvValShutter.setTextColor(mDialMode == DIAL_MODE_SHUTTER  ? Color.WHITE : Color.rgb(227, 69, 20));
        if (tvValAperture != null) tvValAperture.setTextColor(mDialMode == DIAL_MODE_APERTURE ? Color.WHITE : Color.rgb(227, 69, 20));
        if (tvValIso      != null) tvValIso.setTextColor(mDialMode == DIAL_MODE_ISO      ? Color.WHITE : Color.rgb(227, 69, 20));
        if (tvValEv       != null) tvValEv.setTextColor(mDialMode == DIAL_MODE_EXPOSURE  ? Color.WHITE : Color.rgb(227, 69, 20));
        if (tvMode        != null) tvMode.setTextColor(mDialMode == DIAL_MODE_PASM       ? Color.WHITE : Color.rgb(227, 69, 20));

        String fm = p.getFocusMode();
        cachedIsManualFocus = "manual".equals(fm);
        if (tvFocusMode != null) {
            if      ("auto".equals(fm))                                                    tvFocusMode.setText("AF-S");
            else if (cachedIsManualFocus)                                                  tvFocusMode.setText("MF");
            else if ("continuous-video".equals(fm) || "continuous-picture".equals(fm))    tvFocusMode.setText("AF-C");
            else                                                                           tvFocusMode.setText(fm != null ? fm.toUpperCase() : "AF");
            tvFocusMode.setTextColor(mDialMode == DIAL_MODE_FOCUS ? Color.WHITE : Color.rgb(227, 69, 20));
        }

        if (focusMeter   != null) focusMeter.setVisibility(prefShowFocusMeter && cachedIsManualFocus ? View.VISIBLE : View.GONE);
        if (gridLines    != null) gridLines.setVisibility(prefShowGridLines    ? View.VISIBLE : View.GONE);
        if (cinemaMattes != null) cinemaMattes.setVisibility(prefShowCinemaMattes ? View.VISIBLE : View.GONE);
    }

    @Override public void surfaceCreated(SurfaceHolder h)  { hasSurface = true;  if (cameraManager != null) cameraManager.open(h); }
    @Override public void surfaceDestroyed(SurfaceHolder h) { hasSurface = false; if (cameraManager != null) cameraManager.close(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int h1) {}

    @Override
    public void onHardwareStateChanged() {
        runOnUiThread(new Runnable() { public void run() { requestHudUpdate(); } });
    }

    @Override
    public void onCameraReady() {
        if (cameraManager != null && cameraManager.getCamera() != null) {
            try {
                Camera c = cameraManager.getCamera();
                Camera.Parameters p = c.getParameters();
                if (android.os.Build.VERSION.SDK_INT >= 14) {
                    try { if (p.getMaxNumFocusAreas() > 0) p.setFocusAreas(null); } catch (Throwable t) {}
                }
                if (p.get("sony-focus-area") != null) p.set("sony-focus-area", "wide");
                c.setParameters(p);
                cachedIsManualFocus = "manual".equals(p.getFocusMode());

                String model = android.os.Build.MODEL;
                boolean hardwareIsFullFrame = model != null && (
                    model.contains("ILCE-7") || model.contains("ILCE-9") ||
                    model.contains("ILCE-1") || model.contains("DSC-RX1")
                );
                boolean isCropActive = "on".equals(p.get("sony-apsc-mode"));
                isFullFrame = hardwareIsFullFrame && !isCropActive;
            } catch (Exception e) {
                android.util.Log.e("JPEG.CAM", "Boot sync failed: " + e.getMessage());
            }
        }

        applyHardwareRecipe();
        updateMainHUD();

        uiHandler.postDelayed(new Runnable() {
            @Override public void run() { if (cameraManager != null) onHardwareStateChanged(); }
        }, 500);
    }

    @Override public void onShutterSpeedChanged() { runOnUiThread(new Runnable() { public void run() { requestHudUpdate(); } }); }
    @Override public void onApertureChanged()     { runOnUiThread(new Runnable() { public void run() { requestHudUpdate(); } }); }
    @Override public void onIsoChanged()          { runOnUiThread(new Runnable() { public void run() { requestHudUpdate(); } }); }

    @Override
    public void onFocusPositionChanged(final float ratio) {
        if (focusMeter != null && cachedIsManualFocus) {
            runOnUiThread(new Runnable() {
                public void run() {
                    cachedFocusRatio = ratio;
                    focusMeter.update(cachedFocusRatio, cachedAperture, 50.0f, isFullFrame ? 0.030f : 0.020f);
                }
            });
        }
    }

    @Override
    public void onFocalLengthChanged(final float focalLengthMm) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (focalLengthMm > 0.0f && cachedIsManualFocus) {
                    try {
                        Camera c = cameraManager.getCamera();
                        Camera.Parameters p = c.getParameters();
                        p.setFocusMode("auto");
                        c.setParameters(p);
                        cachedIsManualFocus = false;
                    } catch (Exception e) {}
                }
                updateMainHUD();
            }
        });
    }

    @Override
    public void onStatusUpdate(final String target, final String status) {
        runOnUiThread(new Runnable() {
            @Override public void run() { menuController.updateConnectionStatus(target, status); }
        });
    }

    // --- MenuController.HostCallback / HttpServer.Callback ---
    @Override public int                 getDisplayState()        { return displayState; }
    @Override public RecipeManager       getRecipeManager()       { return recipeManager; }
    @Override public ConnectivityManager getConnectivityManager() { return connectivityManager; }
    @Override public MatrixManager       getMatrixManager()       { return matrixManager; }
    @Override public Camera              getCamera()              { return cameraManager != null ? cameraManager.getCamera() : null; }
    @Override public String              getAppVersion()          { return getPackageManager() != null ? tryGetVersion() : "?"; }
    @Override public void                runOnMainThread(Runnable r) { uiHandler.post(r); }

    private String tryGetVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    @Override public boolean isPrefFocusMeter()   { return prefShowFocusMeter; }
    @Override public boolean isPrefCinemaMattes() { return prefShowCinemaMattes; }
    @Override public boolean isPrefGridLines()    { return prefShowGridLines; }
    @Override public int     getPrefJpegQuality() { return 95; }
    @Override public void    setPrefFocusMeter(boolean v)   { prefShowFocusMeter   = v; }
    @Override public void    setPrefCinemaMattes(boolean v) { prefShowCinemaMattes = v; }
    @Override public void    setPrefGridLines(boolean v)    { prefShowGridLines    = v; }
    @Override public void    setPrefJpegQuality(int v)      { /* no-op, quality setting removed */ }

    @Override public void closeHud() { hudController.reset(); }
    @Override public void onMenuOpened() { /* no-op */ }

    @Override public void onMenuClosed() {
        recipeManager.savePreferences();
        SharedPreferences.Editor ed = getSharedPreferences("JPEG.CAM_Prefs", MODE_PRIVATE).edit();
        ed.putBoolean("focusMeter",   prefShowFocusMeter);
        ed.putBoolean("cinemaMattes", prefShowCinemaMattes);
        ed.putBoolean("gridLines",    prefShowGridLines);
        ed.apply();
        applyHardwareRecipe();
        syncHardwareState();
        updateMainHUD();
    }

    @Override public void onLutPreloadNeeded() { /* no-op, LUT engine removed */ }
    @Override public void scheduleHardwareApply() {
        uiHandler.removeCallbacks(applySettingsRunnable);
        uiHandler.postDelayed(applySettingsRunnable, 150);
    }
    @Override public void onHudModeRequested(int mode) { launchHudMode(mode); }
    @Override public void onSetAutoPowerOffMode(boolean on) { setAutoPowerOffMode(on); }
    @Override public void restoreFocusMode(String savedMode) {
        if (savedMode != null && cameraManager != null && cameraManager.getCamera() != null) {
            try {
                Camera.Parameters p = cameraManager.getCamera().getParameters();
                p.setFocusMode(savedMode);
                cameraManager.getCamera().setParameters(p);
            } catch (Exception ignored) {}
        }
    }

    // --- HudController.HostCallback ---
    @Override public TextView       getTvTopStatus()      { return tvTopStatus; }
    @Override public Typeface        getDigitalFont()      { return digitalFont; }
    @Override public Handler         getUiHandler()        { return uiHandler; }
    @Override public MenuController  getMenuController()   { return menuController; }
    @Override public void            applyHardwareRecipeNow() { applyHardwareRecipe(); }
    @Override public void onHudClosed() {
        mainUIContainer.setVisibility(View.GONE);
        menuController.getContainer().setVisibility(View.VISIBLE);
        menuController.refreshDisplay();
    }

    private void syncHardwareState() {
        if (cameraManager == null || cameraManager.getCamera() == null) return;
        cachedIsManualFocus = "manual".equals(cameraManager.getCamera().getParameters().getFocusMode());
    }
}
