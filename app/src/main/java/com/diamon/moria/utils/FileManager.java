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

import androidx.documentfile.provider.DocumentFile;

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

    public interface FolderImportListener {
        void onProgress(int filesCopied, String currentFileName);
    }

    public static class FolderImportResult {
        public final boolean success;
        public final File folder;
        public final int fileCount;
        public final long totalBytes;

        public FolderImportResult(boolean success, File folder, int fileCount, long totalBytes) {
            this.success = success;
            this.folder = folder;
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
        }
    }

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
                    if (child == null || child.getName().startsWith(".")) continue;
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

    public static long getFolderSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        long total = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory()) {
                    total += getFolderSize(f);
                } else {
                    total += f.length();
                }
            }
        }
        return total;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static FolderImportResult importDocumentTree(Context context, Uri treeUri, File workspaceDir, FolderImportListener listener) {
        if (context == null || treeUri == null || workspaceDir == null) {
            return new FolderImportResult(false, null, 0, 0);
        }

        DocumentFile rootDoc = DocumentFile.fromTreeUri(context, treeUri);
        if (rootDoc == null || !rootDoc.exists()) {
            return new FolderImportResult(false, null, 0, 0);
        }

        String folderName = rootDoc.getName();
        if (folderName == null || folderName.trim().isEmpty()) {
            folderName = "imported_folder";
        }

        File targetDir = new File(workspaceDir, folderName);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return new FolderImportResult(false, null, 0, 0);
        }

        int[] counter = new int[]{0};
        boolean ok = copyDocumentTree(context, rootDoc, targetDir, listener, counter);
        long size = ok ? getFolderSize(targetDir) : 0;
        return new FolderImportResult(ok, targetDir, counter[0], size);
    }

    public static boolean copyDocumentTree(Context context, DocumentFile sourceDoc, File destDir, FolderImportListener listener, int[] counter) {
        if (sourceDoc == null || !sourceDoc.exists()) return false;
        if (!destDir.exists() && !destDir.mkdirs()) return false;

        DocumentFile[] children = sourceDoc.listFiles();
        if (children == null) return true;

        for (DocumentFile child : children) {
            if (child == null || !child.exists()) continue;
            String childName = child.getName();
            if (childName == null || childName.trim().isEmpty() || childName.equals(".") || childName.equals("..")) {
                continue;
            }

            if (child.isDirectory()) {
                File subDir = new File(destDir, childName);
                if (!subDir.exists() && !subDir.mkdirs()) {
                    Log.e(TAG, "No se pudo crear subdirectorio local: " + subDir.getAbsolutePath());
                    return false;
                }
                if (!copyDocumentTree(context, child, subDir, listener, counter)) {
                    return false;
                }
            } else if (child.isFile()) {
                File targetFile = new File(destDir, childName);
                if (!copyDocumentToFile(context, child, targetFile)) {
                    Log.e(TAG, "Fallo al copiar archivo: " + childName);
                    return false;
                }
                counter[0]++;
                if (listener != null) {
                    listener.onProgress(counter[0], childName);
                }
            }
        }
        return true;
    }

    public static boolean copyDocumentToFile(Context context, DocumentFile sourceDoc, File destFile) {
        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        try (InputStream in = context.getContentResolver().openInputStream(sourceDoc.getUri());
             OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) return false;
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copiando DocumentFile a destino " + destFile.getAbsolutePath(), e);
            return false;
        }
    }
}
