package com.github.ma1co.pmcademo.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.StatFs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
    public static final int PORT = 8080;
    private Context context;
    private Callback callback;

    public interface Callback {
        RecipeManager getRecipeManager();
        android.hardware.Camera getCamera();
        void runOnMainThread(Runnable r);
    }

    public HttpServer(Context context) {
        super(PORT);
        this.context = context;
        try {
            java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
            InputStream is = context.getAssets().open("keystore.p12");
            ks.load(is, "jpegcam".toCharArray());
            is.close();
            javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, "jpegcam".toCharArray());
            makeSecure(NanoHTTPD.makeSSLSocketFactory(ks, kmf.getKeyManagers()), null);
        } catch (Exception e) {}
    }

    public void setCallback(Callback cb) { this.callback = cb; }

    // Dispatches r on the main thread and blocks until it completes (max 3s).
    private void runOnMainThreadAndWait(final Runnable r) {
        if (callback == null) return;
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        callback.runOnMainThread(new Runnable() {
            public void run() {
                try { r.run(); } finally { latch.countDown(); }
            }
        });
        try { latch.await(3, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) {}
    }

    private String profileToJson(RTLProfile p) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"profileName\":\"").append(p.profileName.replace("\"", "\\\"")).append("\"");
        sb.append(",\"whiteBalance\":\"").append(p.whiteBalance).append("\"");
        sb.append(",\"wbShift\":").append(p.wbShift);
        sb.append(",\"wbShiftGM\":").append(p.wbShiftGM);
        sb.append(",\"dro\":\"").append(p.dro).append("\"");
        sb.append(",\"contrast\":").append(p.contrast);
        sb.append(",\"saturation\":").append(p.saturation);
        sb.append(",\"sharpness\":").append(p.sharpness);
        sb.append(",\"sharpnessGain\":").append(p.sharpnessGain);
        sb.append(",\"colorMode\":\"").append(p.colorMode).append("\"");
        sb.append(",\"colorDepthRed\":").append(p.colorDepthRed);
        sb.append(",\"colorDepthGreen\":").append(p.colorDepthGreen);
        sb.append(",\"colorDepthBlue\":").append(p.colorDepthBlue);
        sb.append(",\"colorDepthCyan\":").append(p.colorDepthCyan);
        sb.append(",\"colorDepthMagenta\":").append(p.colorDepthMagenta);
        sb.append(",\"colorDepthYellow\":").append(p.colorDepthYellow);
        sb.append(",\"advMatrix\":[");
        for (int i = 0; i < 9; i++) sb.append(p.advMatrix[i]).append(i < 8 ? "," : "");
        sb.append("]");
        sb.append(",\"proColorMode\":\"").append(p.proColorMode).append("\"");
        sb.append(",\"pictureEffect\":\"").append(p.pictureEffect).append("\"");
        sb.append(",\"peToyCameraTone\":\"").append(p.peToyCameraTone).append("\"");
        sb.append(",\"softFocusLevel\":").append(p.softFocusLevel);
        sb.append(",\"vignetteHardware\":").append(p.vignetteHardware);
        sb.append(",\"shadingRed\":").append(p.shadingRed);
        sb.append(",\"shadingBlue\":").append(p.shadingBlue);
        sb.append("}");
        return sb.toString();
    }

    private void applyJsonToProfile(RTLProfile p, JSONObject json) {
        if (json.has("profileName")) p.profileName = json.optString("profileName", p.profileName);
        if (json.has("whiteBalance")) p.whiteBalance = json.optString("whiteBalance", p.whiteBalance);
        if (json.has("wbShift")) p.wbShift = json.optInt("wbShift", p.wbShift);
        if (json.has("wbShiftGM")) p.wbShiftGM = json.optInt("wbShiftGM", p.wbShiftGM);
        if (json.has("dro")) p.dro = json.optString("dro", p.dro);
        if (json.has("contrast")) p.contrast = json.optInt("contrast", p.contrast);
        if (json.has("saturation")) p.saturation = json.optInt("saturation", p.saturation);
        if (json.has("sharpness")) p.sharpness = json.optInt("sharpness", p.sharpness);
        if (json.has("sharpnessGain")) p.sharpnessGain = json.optInt("sharpnessGain", p.sharpnessGain);
        if (json.has("colorMode")) p.colorMode = json.optString("colorMode", p.colorMode);
        if (json.has("colorDepthRed")) p.colorDepthRed = json.optInt("colorDepthRed", p.colorDepthRed);
        if (json.has("colorDepthGreen")) p.colorDepthGreen = json.optInt("colorDepthGreen", p.colorDepthGreen);
        if (json.has("colorDepthBlue")) p.colorDepthBlue = json.optInt("colorDepthBlue", p.colorDepthBlue);
        if (json.has("colorDepthCyan")) p.colorDepthCyan = json.optInt("colorDepthCyan", p.colorDepthCyan);
        if (json.has("colorDepthMagenta")) p.colorDepthMagenta = json.optInt("colorDepthMagenta", p.colorDepthMagenta);
        if (json.has("colorDepthYellow")) p.colorDepthYellow = json.optInt("colorDepthYellow", p.colorDepthYellow);
        if (json.has("advMatrix")) {
            JSONArray arr = json.optJSONArray("advMatrix");
            if (arr != null && arr.length() == 9) {
                for (int i = 0; i < 9; i++) p.advMatrix[i] = arr.optInt(i, p.advMatrix[i]);
            }
        }
        if (json.has("proColorMode")) p.proColorMode = json.optString("proColorMode", p.proColorMode);
        if (json.has("pictureEffect")) p.pictureEffect = json.optString("pictureEffect", p.pictureEffect);
        if (json.has("peToyCameraTone")) p.peToyCameraTone = json.optString("peToyCameraTone", p.peToyCameraTone);
        if (json.has("softFocusLevel")) p.softFocusLevel = json.optInt("softFocusLevel", p.softFocusLevel);
        if (json.has("vignetteHardware")) p.vignetteHardware = json.optInt("vignetteHardware", p.vignetteHardware);
        if (json.has("shadingRed")) p.shadingRed = json.optInt("shadingRed", p.shadingRed);
        if (json.has("shadingBlue")) p.shadingBlue = json.optInt("shadingBlue", p.shadingBlue);
    }

    private String readBody(IHTTPSession session) {
        try {
            String lenStr = session.getHeaders().get("content-length");
            int len = lenStr != null ? Integer.parseInt(lenStr) : 0;
            if (len <= 0) return "";
            byte[] buf = new byte[len];
            int read = 0;
            InputStream is = session.getInputStream();
            while (read < len) {
                int n = is.read(buf, read, len - read);
                if (n < 0) break;
                read += n;
            }
            return new String(buf, 0, read, "UTF-8");
        } catch (Exception e) { return ""; }
    }

    private Response json(String body) {
        Response r = newFixedLengthResponse(Response.Status.OK, "application/json", body);
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    private Response jsonError(Response.Status status, String msg) {
        Response r = newFixedLengthResponse(status, "application/json", "{\"error\":\"" + msg + "\"}");
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        // --- NATIVE CORS HANDSHAKE ---
        // This allows your website to send LUTs directly to the camera 
        // without the user needing to install any browser extensions.
        if (Method.OPTIONS.equals(method)) {
            Response res = newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "");
            res.addHeader("Access-Control-Allow-Origin", "*");
            res.addHeader("Access-Control-Allow-Methods", "POST, GET, PUT, OPTIONS");
            res.addHeader("Access-Control-Allow-Headers", "x-file-name, content-length, content-type");
            return res;
        }

        try {
            // ---------------------------------------------------------------
            // Hardware parameter API
            // ---------------------------------------------------------------
            if (uri.equals("/api/hardware")) {
                if (callback == null) return jsonError(Response.Status.SERVICE_UNAVAILABLE, "not ready");
                if (Method.GET.equals(method)) {
                    return json(profileToJson(callback.getRecipeManager().getCurrentProfile()));
                }
                if (Method.PUT.equals(method)) {
                    final String body = readBody(session);
                    try {
                        final JSONObject json = new JSONObject(body);
                        final RecipeManager rm = callback.getRecipeManager();
                        runOnMainThreadAndWait(new Runnable() {
                            public void run() {
                                applyJsonToProfile(rm.getCurrentProfile(), json);
                                android.hardware.Camera cam = callback.getCamera();
                                if (cam != null) HardwareRecipeApplier.apply(cam, rm.getCurrentProfile());
                                rm.savePreferences();
                            }
                        });
                        return json(profileToJson(rm.getCurrentProfile()));
                    } catch (Exception e) {
                        return jsonError(Response.Status.BAD_REQUEST, "invalid json");
                    }
                }
            }

            // ---------------------------------------------------------------
            // Recipe slot API
            // ---------------------------------------------------------------
            if (uri.equals("/api/recipes/active") && Method.GET.equals(method)) {
                if (callback == null) return jsonError(Response.Status.SERVICE_UNAVAILABLE, "not ready");
                return json("{\"active\":" + callback.getRecipeManager().getCurrentSlot() + "}");
            }

            if (uri.equals("/api/recipes") && Method.GET.equals(method)) {
                if (callback == null) return jsonError(Response.Status.SERVICE_UNAVAILABLE, "not ready");
                RecipeManager rm = callback.getRecipeManager();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < 10; i++) {
                    RTLProfile p = rm.getProfile(i);
                    String name = p != null ? p.profileName : ("SLOT " + (i + 1));
                    if (i > 0) sb.append(",");
                    sb.append("{\"index\":").append(i).append(",\"name\":\"").append(name.replace("\"", "\\\"")).append("\"}");
                }
                sb.append("]");
                return json(sb.toString());
            }

            // PUT /api/recipes/{n}/load  and  PUT /api/recipes/{n}/save
            if (uri.startsWith("/api/recipes/") && Method.PUT.equals(method)) {
                if (callback == null) return jsonError(Response.Status.SERVICE_UNAVAILABLE, "not ready");
                String[] parts = uri.split("/");
                // parts: ["", "api", "recipes", "{n}", "load|save"]
                if (parts.length == 5) {
                    int slot;
                    try { slot = Integer.parseInt(parts[3]); } catch (NumberFormatException e) {
                        return jsonError(Response.Status.BAD_REQUEST, "invalid slot");
                    }
                    if (slot < 0 || slot > 9) return jsonError(Response.Status.BAD_REQUEST, "slot 0-9");
                    final int finalSlot = slot;
                    final RecipeManager rm = callback.getRecipeManager();

                    if ("load".equals(parts[4])) {
                        runOnMainThreadAndWait(new Runnable() {
                            public void run() {
                                rm.setCurrentSlot(finalSlot);
                                android.hardware.Camera cam = callback.getCamera();
                                if (cam != null) HardwareRecipeApplier.apply(cam, rm.getCurrentProfile());
                            }
                        });
                        return json("{\"active\":" + rm.getCurrentSlot() + "}");
                    }
                    if ("save".equals(parts[4])) {
                        final String body = readBody(session);
                        runOnMainThreadAndWait(new Runnable() {
                            public void run() {
                                if (!body.isEmpty()) {
                                    try {
                                        JSONObject json = new JSONObject(body);
                                        String name = json.optString("name", null);
                                        if (name != null && !name.isEmpty()) rm.getProfile(finalSlot).profileName = name;
                                    } catch (Exception ignored) {}
                                }
                                rm.setCurrentSlot(finalSlot);
                                rm.savePreferences();
                            }
                        });
                        return json(profileToJson(rm.getProfile(finalSlot)));
                    }
                }
            }

            // ---------------------------------------------------------------
            // Vault API
            // ---------------------------------------------------------------
            if (uri.equals("/api/vault") && Method.GET.equals(method)) {
                if (callback == null) return jsonError(Response.Status.SERVICE_UNAVAILABLE, "not ready");
                java.util.List<RecipeManager.VaultItem> items = callback.getRecipeManager().getVaultItems();
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (RecipeManager.VaultItem item : items) {
                    if (item.filename.equals("NONE")) continue;
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{\"filename\":\"").append(item.filename.replace("\"", "\\\""))
                      .append("\",\"profileName\":\"").append(item.profileName.replace("\"", "\\\"")).append("\"}");
                }
                sb.append("]");
                return json(sb.toString());
            }

            if (uri.equals("/api/vault/load") && Method.PUT.equals(method)) {
                if (callback == null) return jsonError(Response.Status.SERVICE_UNAVAILABLE, "not ready");
                String body = readBody(session);
                String filename;
                try { filename = new JSONObject(body).getString("filename"); }
                catch (Exception e) { return jsonError(Response.Status.BAD_REQUEST, "missing filename"); }
                final String fn = filename;
                final RecipeManager rm = callback.getRecipeManager();
                runOnMainThreadAndWait(new Runnable() {
                    public void run() {
                        rm.previewVaultToSlot(fn);
                        android.hardware.Camera cam = callback.getCamera();
                        if (cam != null) HardwareRecipeApplier.apply(cam, rm.getCurrentProfile());
                    }
                });
                return json(profileToJson(rm.getCurrentProfile()));
            }

            // Upload endpoint for LUTs and Lenses
            if (Method.POST.equals(method) && (uri.equals("/api/upload_lut") || uri.equals("/api/upload"))) {
                FileOutputStream out = null;
                File tempFile = null;
                File destFile = null;
                
                try {
                    Map<String, String> headers = session.getHeaders();
                    String fileName = headers.get("x-file-name");
                    
                    if (fileName == null) {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"Missing filename\"}");
                    }

                    // Smart Router Logic
                    String lowerName = fileName.toLowerCase();
                    File targetDir = null;
                    
                    if (lowerName.endsWith(".cube") || lowerName.endsWith(".cub")) {
                        targetDir = Filepaths.getLutDir();
                    } else if (lowerName.endsWith(".txt") || lowerName.endsWith(".lens")) {
                        targetDir = new File(Filepaths.getAppDir(), "LENSES");
                    } else {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"Unsupported file type\"}");
                    }

                    if (!targetDir.exists()) targetDir.mkdirs();

                    String contentLengthStr = headers.get("content-length");
                    int contentLength = contentLengthStr != null ? Integer.parseInt(contentLengthStr) : 0;
                    
                    // Save stream to a temporary file first
                    tempFile = new File(targetDir, "upload_" + System.currentTimeMillis() + ".tmp");
                    destFile = new File(targetDir, fileName);

                    InputStream in = session.getInputStream();
                    out = new FileOutputStream(tempFile);
                    
                    byte[] buffer = new byte[8192];
                    int read;
                    int totalRead = 0;
                    
                    while (totalRead < contentLength) {
                        int bytesToRead = Math.min(buffer.length, contentLength - totalRead);
                        read = in.read(buffer, 0, bytesToRead);
                        if (read == -1) break;
                        out.write(buffer, 0, read);
                        totalRead += read;
                    }
                    
                    out.flush();
                    out.close(); 
                    out = null;

                    // Finalize the file rename
                    if (tempFile.exists()) {
                        if (destFile.exists()) destFile.delete(); 
                        tempFile.renameTo(destFile);
                    }

                    Response success = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"success\"}");
                    success.addHeader("Access-Control-Allow-Origin", "*");
                    return success;
                } catch (Exception e) {
                    if (tempFile != null && tempFile.exists()) tempFile.delete(); 
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\":\"Upload failed\"}");
                } finally {
                    try { if (out != null) out.close(); } catch (Exception e) {}
                }
            }

            // Dashboard Home
            if (uri.equals("/cert")) {
                InputStream is = context.getAssets().open("keystore.crt");
                Response r = newChunkedResponse(Response.Status.OK, "application/x-x509-ca-cert", is);
                r.addHeader("Content-Disposition", "attachment; filename=\"jpegcam.crt\"");
                return r;
            }

            if (uri.equals("/")) {
                InputStream is = context.getAssets().open("index.html");
                return newChunkedResponse(Response.Status.OK, "text/html", is);
            }

            // PWA static assets
            if (uri.equals("/manifest.json")) {
                InputStream is = context.getAssets().open("manifest.json");
                return newChunkedResponse(Response.Status.OK, "application/manifest+json", is);
            }
            if (uri.equals("/sw.js")) {
                InputStream is = context.getAssets().open("sw.js");
                return newChunkedResponse(Response.Status.OK, "application/javascript", is);
            }
            if (uri.equals("/icon.png")) {
                InputStream is = context.getAssets().open("icon.png");
                byte[] data = new byte[is.available()];
                is.read(data);
                is.close();
                return newFixedLengthResponse(Response.Status.OK, "image/png", new java.io.ByteArrayInputStream(data), data.length);
            }

            // System Status API
            if (uri.equals("/api/system")) {
                StatFs stat = new StatFs(Filepaths.getStorageRoot().getPath());
                long bytesAvailable = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();
                double gbAvailable = bytesAvailable / (1024.0 * 1024.0 * 1024.0);
                
                File gradedDir = Filepaths.getGradedDir();
                boolean hasGraded = gradedDir.exists() && gradedDir.listFiles() != null && gradedDir.listFiles().length > 0;
                
                String json = String.format("{\"storage_gb\": \"%.1f\", \"has_graded\": %b}", gbAvailable, hasGraded);
                return newFixedLengthResponse(Response.Status.OK, "application/json", json);
            }

            // File Listing API
            if (uri.startsWith("/api/files")) {
                Map<String, String> params = session.getParms();
                String folderParam = params.get("folder"); 
                
                List<File> allFiles = getMediaFiles(folderParam);
                StringBuilder json = new StringBuilder();
                json.append("{\"folder\": \"").append(folderParam).append("\", \"files\": [");
                for (int i = 0; i < allFiles.size(); i++) {
                    File f = allFiles.get(i);
                    json.append("{\"name\":\"").append(f.getName())
                        .append("\", \"date\":").append(f.lastModified())
                        .append(", \"size\":").append(f.length()).append("}");
                    if (i < allFiles.size() - 1) json.append(",");
                }
                json.append("]}");
                return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString());
            }

            // Image Delivery (Thumbs and Full Size)
            if (uri.startsWith("/thumb/") || uri.startsWith("/full/")) {
                Map<String, String> params = session.getParms();
                String folder = params.get("folder");
                String name = params.get("name");
                
                File file = findRequestedFile(folder, name);

                if (file != null && file.exists()) {
                    if (uri.startsWith("/full/")) {
                        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", new FileInputStream(file), file.length());
                    } else {
                        // Extract embedded thumbnail if available
                        if (folder != null && !folder.equals("GRADED")) {
                            try {
                                ExifInterface exif = new ExifInterface(file.getAbsolutePath());
                                byte[] thumb = exif.getThumbnail();
                                if (thumb != null) return newFixedLengthResponse(Response.Status.OK, "image/jpeg", new ByteArrayInputStream(thumb), thumb.length);
                            } catch (Exception e) {}
                        }
                        
                        // Generate thumbnail from full image
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = 8;
                        opts.inPurgeable = true; 
                        Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                        if (bm != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            bm.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                            byte[] data = baos.toByteArray();
                            bm.recycle(); 
                            return newFixedLengthResponse(Response.Status.OK, "image/jpeg", new ByteArrayInputStream(data), data.length);
                        }
                    }
                }
            }

        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Server Error");
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404");
    }

    private List<File> getMediaFiles(String folderType) {
        List<File> result = new ArrayList<File>();
        if (folderType != null && folderType.equals("GRADED")) {
            File gradedDir = Filepaths.getGradedDir();
            if (gradedDir.exists()) {
                File[] files = gradedDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (!f.isDirectory() && f.getName().toLowerCase().endsWith(".jpg")) result.add(f);
                    }
                }
            }
        } else {
            File dcimDir = Filepaths.getDcimDir();
            if (dcimDir.exists()) {
                File[] subDirs = dcimDir.listFiles();
                if (subDirs != null) {
                    for (File subDir : subDirs) {
                        if (subDir.isDirectory() && subDir.getName().toUpperCase().endsWith("MSDCF")) {
                            File[] files = subDir.listFiles();
                            if (files != null) {
                                for (File f : files) {
                                    if (!f.isDirectory() && f.getName().toLowerCase().endsWith(".jpg")) result.add(f);
                                }
                            }
                        }
                    }
                }
            }
        }
        Collections.sort(result, new Comparator<File>() {
            public int compare(File f1, File f2) { return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified()); }
        });
        return result;
    }

    private File findRequestedFile(String folder, String name) {
        if (folder != null && folder.equals("GRADED")) {
            return new File(Filepaths.getGradedDir(), name);
        } else {
            File dcimDir = Filepaths.getDcimDir();
            if (dcimDir.exists()) {
                File[] subDirs = dcimDir.listFiles();
                if (subDirs != null) {
                    for (File subDir : subDirs) {
                        if (subDir.isDirectory() && subDir.getName().toUpperCase().endsWith("MSDCF")) {
                            File testFile = new File(subDir, name);
                            if (testFile.exists()) return testFile; 
                        }
                    }
                }
            }
        }
        return null;
    }
}