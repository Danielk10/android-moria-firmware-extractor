package com.diamon.moria.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AssetHelper {
    private static final String TAG = "AssetHelper";
    private static final String PREFS_NAME = "AssetHelperPrefs";
    private static final String KEY_EXTRACTED = "assets_extracted_v1";
    private static final int BUFFER_SIZE = 8192;
    private static volatile String cachedAssetRuntimeRoot;
    private static final String COMPILED_RUNTIME_ROOT = "data/data/com.diamon.moria/files/usr";

    /**
     * Prepara runtime de assets y enlaces simbolicos nativos una sola vez.
     */
    public static synchronized boolean ensureRuntimeReady(Context context) {
        long start = System.currentTimeMillis();
        String runtimeRoot = resolveAssetRuntimeRoot(context);
        if (runtimeRoot == null) {
            Log.e(TAG, "No se encontro ruta runtime en assets (data/data/*/files/usr).");
            return false;
        }

        File usrDir = new File(context.getFilesDir(), "usr");
        boolean alreadyExtracted = areAssetsExtracted(context);

        if (!alreadyExtracted) {
            if (!extractAssets(context, runtimeRoot, usrDir)) {
                return false;
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_EXTRACTED, true).apply();

            boolean linked = ensureNativeToolLinks(context);
            long duration = System.currentTimeMillis() - start;
            Log.i(TAG, "ensureRuntimeReady (extraccion) completado en " + duration + "ms. Resultado: " + linked);
            return linked;
        }

        boolean ok = ensureNativeToolLinks(context);
        long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "ensureRuntimeReady (enlaces) completado en " + duration + "ms. Resultado: " + ok);
        return ok;
    }

    public static String getResolvedRuntimeRoot(Context context) {
        return resolveAssetRuntimeRoot(context);
    }

    private static String resolveAssetRuntimeRoot(Context context) {
        if (cachedAssetRuntimeRoot != null) {
            return cachedAssetRuntimeRoot;
        }

        AssetManager assetManager = context.getAssets();
        try {
            String[] exactChildren = assetManager.list(COMPILED_RUNTIME_ROOT);
            if (exactChildren != null && exactChildren.length > 0) {
                cachedAssetRuntimeRoot = COMPILED_RUNTIME_ROOT;
                Log.i(TAG, "Runtime root de assets detectado (exacto): " + cachedAssetRuntimeRoot);
                return cachedAssetRuntimeRoot;
            }

            String[] pkgCandidates = assetManager.list("data/data");
            if (pkgCandidates == null || pkgCandidates.length == 0) {
                return null;
            }

            String expectedPkg = context.getPackageName();
            List<String> ordered = new ArrayList<>();
            ordered.add(expectedPkg);
            for (String candidate : pkgCandidates) {
                if (candidate != null && !candidate.equals(expectedPkg)) {
                    ordered.add(candidate);
                }
            }

            for (String pkg : ordered) {
                if (pkg == null || pkg.isEmpty()) {
                    continue;
                }
                String candidateRoot = "data/data/" + pkg + "/files/usr";
                String[] children = assetManager.list(candidateRoot);
                if (children != null && children.length > 0) {
                    cachedAssetRuntimeRoot = candidateRoot;
                    Log.i(TAG, "Runtime root de assets detectado: " + candidateRoot);
                    return cachedAssetRuntimeRoot;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "No se pudo resolver runtime root de assets: " + e.getMessage());
        }
        return null;
    }

    public static boolean extractAssets(Context context, String assetPath, File destDir) {
        AssetManager assetManager = context.getAssets();

        try {
            String[] files = assetManager.list(assetPath);

            if (files == null || files.length == 0) {
                return copyAssetFile(assetManager, assetPath, destDir);
            } else {
                if (!destDir.exists() && !destDir.mkdirs()) {
                    Log.e(TAG, "No se pudo crear directorio: " + destDir.getAbsolutePath());
                    return false;
                }

                for (String fileName : files) {
                    if (fileName == null || fileName.isEmpty())
                        continue;

                    String childAssetPath = assetPath + "/" + fileName;
                    File childDestDir = new File(destDir, fileName);

                    String[] subFiles = assetManager.list(childAssetPath);
                    if (subFiles != null && subFiles.length > 0) {
                        if (!extractAssets(context, childAssetPath, childDestDir)) {
                            return false;
                        }
                    } else {
                        if (!copyAssetFile(assetManager, childAssetPath, destDir)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error extrayendo assets: " + e.getMessage());
            return false;
        }
    }

    private static boolean copyAssetFile(AssetManager assetManager, String assetPath, File destDir) {
        String fileName = assetPath.substring(assetPath.lastIndexOf('/') + 1);
        File destFile = new File(destDir, fileName);

        if (destFile.exists()) {
            return true;
        }

        if (!destDir.exists()) {
            if (!destDir.mkdirs() && !destDir.exists()) {
                Log.e(TAG, "No se pudo crear directorio: " + destDir.getAbsolutePath());
                return false;
            }
        } else if (!destDir.isDirectory()) {
            Log.e(TAG, "Existe un archivo con el mismo nombre que el directorio: " + destDir.getAbsolutePath());
            return false;
        }

        try (InputStream in = assetManager.open(assetPath);
                OutputStream out = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            if (assetPath.contains("/bin/") || assetPath.contains("/sbin/")) {
                destFile.setExecutable(true, true);
            }
            Log.d(TAG, "Copiado: " + destFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error copiando " + assetPath + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean areAssetsExtracted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean flagged = prefs.getBoolean(KEY_EXTRACTED, false);

        File shareDir = new File(context.getFilesDir(), "usr/share");
        boolean shareExists = shareDir.exists() && shareDir.isDirectory();

        if (flagged && shareExists) {
            String[] list = shareDir.list();
            if (list != null && list.length > 0) {
                return true;
            }
        }

        return false;
    }

    public static boolean ensureNativeToolLinks(Context context) {
        File filesDir = context.getFilesDir();
        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File usrBin = new File(filesDir, "usr/bin");
        File usrLib = new File(filesDir, "usr/lib");

        if (!usrBin.exists()) usrBin.mkdirs();
        if (!usrLib.exists()) usrLib.mkdirs();

        boolean ok = true;
        // Binarios ejecutables de Moria
        ok &= linkTool(new File(usrBin, "moria"), new File(nativeLibDir, "libmoria_bin.so"));
        ok &= linkTool(new File(usrBin, "bsdunzip"), new File(nativeLibDir, "libbsdunzip.so"));

        // Sonames de librerias compartidas requeridas
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libarchive.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libarchive.so.13");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libarchive.so.13.9.0");

        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libc++_shared.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "liblz4.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "liblzma.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "liblzma.so.5");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libz.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libz.so.1");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libzstd.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libzstd.so.1");

        // Librerias complementarias para descompresion y bsdunzip
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libcrypto.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libcrypto.so.3");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libiconv.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libcharset.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libxml2.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libxml2.so.16");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libacl.so");

        return ok;
    }

    private static boolean linkRuntimeSoname(File usrLib, File nativeLibDir, String runtimeSoname) {
        File linkPath = new File(usrLib, runtimeSoname);
        File source = resolveNativeLibrary(nativeLibDir, runtimeSoname);
        if (source == null) {
            Log.w(TAG, "No se encontro libreria para soname runtime: " + runtimeSoname);
            return false;
        }
        boolean result = linkTool(linkPath, source);
        if (!result) {
            Log.e(TAG, "Fallo al enlazar soname runtime: " + runtimeSoname);
        }
        return result;
    }

    private static File resolveNativeLibrary(File nativeLibDir, String runtimeSoname) {
        for (String candidate : getNativeCandidates(runtimeSoname)) {
            File file = new File(nativeLibDir, candidate);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    private static List<String> getNativeCandidates(String runtimeSoname) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(runtimeSoname);

        int soMarker = runtimeSoname.indexOf(".so.");
        if (soMarker > 0) {
            String base = runtimeSoname.substring(0, soMarker);
            String suffix = runtimeSoname.substring(soMarker + 4).replace('.', '_');
            candidates.add(base + "_" + suffix + ".so");
            candidates.add(base + ".so");
        }

        return new ArrayList<>(candidates);
    }

    private static boolean linkTool(File linkPath, File target) {
        if (!target.exists()) {
            Log.e(TAG, "Libreria nativa faltante. Imposible enlazar: " + target.getAbsolutePath());
            return false;
        }

        try {
            if (linkPath.exists() && linkPath.getCanonicalPath().equals(target.getCanonicalPath())) {
                return true;
            }
            linkPath.delete();
            Os.symlink(target.getAbsolutePath(), linkPath.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Symlink fallo para " + linkPath.getName() + " -> " + target.getName()
                    + ": " + e.getMessage() + ". Intentando fallback por copia...");
            return copyFile(target, linkPath);
        }
    }

    private static boolean copyFile(File source, File dest) {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        try (InputStream in = new java.io.FileInputStream(source);
                OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            File binParent = dest.getParentFile();
            String parentName = binParent != null ? binParent.getName() : "";
            if ("bin".equals(parentName) || "sbin".equals(parentName)) {
                dest.setExecutable(true, true);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copiando archivo de fallback: " + e.getMessage());
            return false;
        }
    }
}
