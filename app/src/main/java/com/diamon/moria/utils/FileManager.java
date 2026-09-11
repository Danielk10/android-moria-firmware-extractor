package com.diamon.moria.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileManager {
    private static final String TAG = "FileManager";
    public static final String DEFAULT_DOWNLOADS_FOLDER = "Moria_Firmware";

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        result = cursor.getString(idx);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error consultando nombre del URI: " + e.getMessage());
            }
        }
        if (result == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    result = path.substring(cut + 1);
                } else {
                    result = path;
                }
            }
        }
        return result != null ? result : "imported_file.bin";
    }

    public static boolean copyUriToFile(Context context, Uri sourceUri, File destFile) {
        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) return false;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copiando URI a archivo destino", e);
            return false;
        }
    }

    public static boolean shouldIgnore(File f) {
        if (f == null) return true;
        String name = f.getName();
        if (name == null || name.trim().isEmpty() || name.startsWith(".")) return true;
        String lower = name.toLowerCase(Locale.US);
        if (lower.contains("profileinstall") || lower.contains("profile_install") || lower.contains("profile-install")) {
            return true;
        }
        return lower.equals("usr") || 
               lower.equals("bin") || 
               lower.equals("lib") || 
               lower.equals("cache") || 
               lower.equals("code_cache") || 
               lower.equals("app_webview") || 
               lower.equals("databases") || 
               lower.equals("shared_prefs") || 
               lower.equals("system") || 
               lower.equals("include") || 
               lower.equals("runtimes") || 
               lower.equals("dexopt") || 
               lower.equals("no_backup") || 
               lower.equals("tmp");
    }

    public static boolean exportFileToDownloads(Context context, File sourceFile, String subFolder) {
        if (sourceFile == null || !sourceFile.exists()) {
            return false;
        }
        
        if (sourceFile.isDirectory()) {
            File[] children = sourceFile.listFiles();
            boolean success = true;
            if (children != null) {
                for (File child : children) {
                    if (shouldIgnore(child)) continue;
                    String nextSubFolder = (subFolder == null || subFolder.isEmpty())
                            ? sourceFile.getName()
                            : subFolder + "/" + sourceFile.getName();
                    if (!exportFileToDownloads(context, child, nextSubFolder)) {
                        success = false;
                    }
                }
            }
            return success;
        }

        String fileName = sourceFile.getName();
        String targetRelativePath = Environment.DIRECTORY_DOWNLOADS + 
                (subFolder != null && !subFolder.trim().isEmpty() ? "/" + subFolder.trim() : "");

        String mimeType = "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".json")) {
            mimeType = "application/json";
        } else if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".md")) {
            mimeType = "text/plain";
        } else if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            mimeType = "text/html";
        } else if (lower.endsWith(".pdf")) {
            mimeType = "application/pdf";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
            values.put(MediaStore.Downloads.RELATIVE_PATH, targetRelativePath);

            Uri externalUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri fileUri = null;
            try {
                fileUri = context.getContentResolver().insert(externalUri, values);
            } catch (Exception e) {
                Log.w(TAG, "Fallo al insertar con subFolder: " + targetRelativePath + ", intentando en Downloads base", e);
            }

            if (fileUri == null) {
                try {
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    fileUri = context.getContentResolver().insert(externalUri, values);
                } catch (Exception e) {
                    Log.e(TAG, "Error insertando URI en Downloads base", e);
                }
            }

            if (fileUri != null) {
                try (OutputStream out = context.getContentResolver().openOutputStream(fileUri, "w");
                     InputStream in = new FileInputStream(sourceFile)) {
                    if (out == null) return false;
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                    return true;
                } catch (IOException e) {
                    Log.e(TAG, "Error exportando via MediaStore: " + fileName, e);
                }
            }
        } else {
            File destFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), subFolder != null ? subFolder : "");
            if (!destFolder.exists() && !destFolder.mkdirs()) {
                return false;
            }
            File destFile = new File(destFolder, fileName);
            try (InputStream in = new FileInputStream(sourceFile);
                 OutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Error exportando via File API: " + fileName, e);
            }
        }
        return false;
    }

    public static List<String> exportAllToDownloads(Context context, File sourceDir, String subFolder) {
        List<String> exportedFiles = new ArrayList<>();
        if (sourceDir == null || !sourceDir.exists()) {
            return exportedFiles;
        }

        if (sourceDir.isDirectory()) {
            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (shouldIgnore(file)) continue;
                    if (exportFileToDownloads(context, file, subFolder)) {
                        exportedFiles.add(file.getName());
                    }
                }
            }
        } else {
            if (!shouldIgnore(sourceDir) && exportFileToDownloads(context, sourceDir, subFolder)) {
                exportedFiles.add(sourceDir.getName());
            }
        }
        return exportedFiles;
    }
}
