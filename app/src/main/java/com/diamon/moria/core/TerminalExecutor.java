package com.diamon.moria.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.diamon.moria.R;

public class TerminalExecutor {
    private static final String TAG = "TerminalExecutor";

    public interface Callback {
        void onOutput(String text);
        void onCommandStarted(String command);
        void onCommandFinished(int exitCode);
        void onClearRequested();
    }

    private final Context context;
    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private File currentWorkDir;
    private volatile Process currentProcess;

    public TerminalExecutor(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        this.currentWorkDir = context.getFilesDir();
    }

    public File getCurrentWorkDir() {
        return currentWorkDir;
    }

    public void setCurrentWorkDir(File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            this.currentWorkDir = dir;
        }
    }

    public synchronized boolean isRunning() {
        Process p = currentProcess;
        return p != null && p.isAlive();
    }

    public synchronized void abort() {
        Process p = currentProcess;
        if (p != null) {
            try {
                p.destroyForcibly();
                postOutput(context.getString(R.string.log_process_aborted));
            } catch (Exception e) {
                Log.e(TAG, "Error abortando proceso: " + e.getMessage());
            }
            currentProcess = null;
        }
    }

    public void execute(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) return;
        final String cmd = commandLine.trim();

        executor.execute(() -> runCommand(cmd));
    }

    private void runCommand(String commandLine) {
        postStarted(commandLine);

        if (commandLine.contains("|") || commandLine.contains(">") || commandLine.contains("<") || commandLine.contains(";") || commandLine.contains("&&")) {
            executeInShell(commandLine);
            return;
        }

        String[] tokens = splitCommandLine(commandLine);
        if (tokens.length == 0) {
            postFinished(0);
            return;
        }

        String primary = tokens[0];
        if ("./run_moria.sh".equals(primary) || "run_moria.sh".equals(primary) || "run_moria".equals(primary)) {
            tokens[0] = "moria";
            primary = "moria";
        }

        // 1. Manejo de comandos internos tipo shell
        switch (primary.toLowerCase(Locale.US)) {
            case "clear":
            case "cls":
                mainHandler.post(callback::onClearRequested);
                postFinished(0);
                return;

            case "help":
            case "?":
                printSandboxHelp();
                postFinished(0);
                return;

            case "pwd":
                postOutput(currentWorkDir.getAbsolutePath() + "\n");
                postFinished(0);
                return;

            case "cd":
                executeCd(tokens);
                return;

            case "ls":
                executeLs(tokens);
                return;

            case "cat":
                executeCat(tokens);
                return;

            case "rm":
                executeRm(tokens);
                return;

            case "mkdir":
                executeMkdir(tokens);
                return;

            case "cp":
                executeCp(tokens);
                return;

            case "touch":
                executeTouch(tokens);
                return;

            case "echo":
                executeEcho(commandLine);
                return;
        }

        // 2. Ejecucion de binarios nativos (moria, bsdunzip o rutas de binarios)
        executeNativeBinary(tokens);
    }

    private void executeCd(String[] tokens) {
        if (tokens.length < 2 || tokens[1].equals("~")) {
            currentWorkDir = context.getFilesDir();
            postOutput("Directorio de trabajo: " + currentWorkDir.getAbsolutePath() + "\n");
            postFinished(0);
            return;
        }

        String target = tokens[1];
        File newDir;
        if (target.startsWith("/")) {
            newDir = new File(target);
        } else if (target.equals("..")) {
            newDir = currentWorkDir.getParentFile();
            if (newDir == null) newDir = currentWorkDir;
        } else {
            newDir = new File(currentWorkDir, target);
        }

        if (newDir.exists() && newDir.isDirectory()) {
            currentWorkDir = newDir;
            postOutput(currentWorkDir.getAbsolutePath() + "\n");
            postFinished(0);
        } else {
            postOutput("cd: no existe el directorio: " + target + "\n");
            postFinished(1);
        }
    }

    private void executeLs(String[] tokens) {
        File dir = currentWorkDir;
        if (tokens.length > 1) {
            String p = tokens[1];
            dir = p.startsWith("/") ? new File(p) : new File(currentWorkDir, p);
        }

        if (!dir.exists() || !dir.isDirectory()) {
            postOutput("ls: no se puede acceder a '" + dir.getName() + "': No existe el directorio\n");
            postFinished(1);
            return;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            postOutput("(directorio vacio)\n");
            postFinished(0);
            return;
        }

        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder sb = new StringBuilder();

        for (File f : files) {
            if (f.getName().startsWith(".") && !tokensHasFlag(tokens, "-a")) continue;
            String type = f.isDirectory() ? "d" : "-";
            String r = f.canRead() ? "r" : "-";
            String w = f.canWrite() ? "w" : "-";
            String x = f.canExecute() ? "x" : "-";
            String size = f.isDirectory() ? "<DIR>" : formatFileSize(f.length());
            String date = sdf.format(new Date(f.lastModified()));
            sb.append(String.format(Locale.US, "%s%s%s%s %8s  %s  %s\n", type, r, w, x, size, date, f.getName()));
        }
        postOutput(sb.toString());
        postFinished(0);
    }

    private boolean tokensHasFlag(String[] tokens, String flag) {
        for (String t : tokens) {
            if (flag.equals(t)) return true;
        }
        return false;
    }

    private void executeCat(String[] tokens) {
        if (tokens.length < 2) {
            postOutput("Uso: cat <archivo>\n");
            postFinished(1);
            return;
        }
        File f = resolveFile(tokens[1]);
        if (!f.exists() || !f.isFile()) {
            postOutput("cat: " + tokens[1] + ": Archivo no encontrado\n");
            postFinished(1);
            return;
        }
        if (f.length() > 2 * 1024 * 1024) {
            postOutput("cat: El archivo es demasiado grande para mostrar (> 2 MB)\n");
            postFinished(1);
            return;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
            String line;
            while ((line = br.readLine()) != null) {
                postOutput(line + "\n");
            }
            postFinished(0);
        } catch (IOException e) {
            postOutput("cat: Error leyendo archivo: " + e.getMessage() + "\n");
            postFinished(1);
        }
    }

    private void executeTouch(String[] tokens) {
        if (tokens.length < 2) {
            postOutput("Uso: touch <archivo>\n");
            postFinished(1);
            return;
        }
        File f = resolveFile(tokens[1]);
        try {
            if (!f.exists()) {
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                if (f.createNewFile()) {
                    postOutput("Creado: " + f.getName() + "\n");
                    postFinished(0);
                    return;
                }
            } else {
                f.setLastModified(System.currentTimeMillis());
                postFinished(0);
                return;
            }
        } catch (IOException e) {
            postOutput("touch: error: " + e.getMessage() + "\n");
            postFinished(1);
            return;
        }
    }

    private void executeRm(String[] tokens) {
        if (tokens.length < 2) {
            postOutput("Uso: rm [-r|-rf] <archivo o directorio>\n");
            postFinished(1);
            return;
        }

        boolean recursive = false;
        String targetName = null;
        for (int i = 1; i < tokens.length; i++) {
            if ("-r".equalsIgnoreCase(tokens[i]) || "-rf".equalsIgnoreCase(tokens[i]) || "-fr".equalsIgnoreCase(tokens[i])) {
                recursive = true;
            } else if (targetName == null) {
                targetName = tokens[i];
            }
        }

        if (targetName == null) {
            postOutput("Uso: rm [-r|-rf] <archivo o directorio>\n");
            postFinished(1);
            return;
        }

        File f = resolveFile(targetName);
        if (!f.exists()) {
            postOutput("rm: no se puede borrar '" + targetName + "': No existe el archivo o carpeta\n");
            postFinished(1);
            return;
        }

        if (f.isDirectory() && !recursive) {
            postOutput("rm: '" + targetName + "' es un directorio (use 'rm -r' o 'rm -rf')\n");
            postFinished(1);
            return;
        }

        boolean ok = recursive ? deleteRecursively(f) : f.delete();
        if (ok) {
            postOutput("Eliminado: " + f.getName() + "\n");
            postFinished(0);
        } else {
            postOutput("rm: no se pudo eliminar " + f.getName() + "\n");
            postFinished(1);
        }
    }

    private boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }

    private void executeMkdir(String[] tokens) {
        if (tokens.length < 2) {
            postOutput("Uso: mkdir [-p] <directorio>\n");
            postFinished(1);
            return;
        }

        boolean makeParents = false;
        String dirName = null;
        for (int i = 1; i < tokens.length; i++) {
            if ("-p".equals(tokens[i])) {
                makeParents = true;
            } else if (dirName == null) {
                dirName = tokens[i];
            }
        }

        if (dirName == null) {
            postOutput("Uso: mkdir [-p] <directorio>\n");
            postFinished(1);
            return;
        }

        File d = resolveFile(dirName);
        if (d.exists()) {
            postOutput("mkdir: no se puede crear el directorio '" + dirName + "': Ya existe\n");
            postFinished(1);
            return;
        }

        boolean ok = makeParents ? d.mkdirs() : d.mkdir();
        if (ok) {
            postOutput("Directorio creado: " + d.getName() + "\n");
            postFinished(0);
        } else {
            postOutput("mkdir: no se pudo crear " + d.getName() + "\n");
            postFinished(1);
        }
    }

    private void executeCp(String[] tokens) {
        boolean recursive = false;
        String srcPath = null;
        String dstPath = null;

        for (int i = 1; i < tokens.length; i++) {
            if ("-r".equalsIgnoreCase(tokens[i]) || "-R".equalsIgnoreCase(tokens[i])) {
                recursive = true;
            } else if (srcPath == null) {
                srcPath = tokens[i];
            } else if (dstPath == null) {
                dstPath = tokens[i];
            }
        }

        if (srcPath == null || dstPath == null) {
            postOutput("Uso: cp [-r] <origen> <destino>\n");
            postFinished(1);
            return;
        }

        File src = resolveFile(srcPath);
        File dst = resolveFile(dstPath);
        if (!src.exists()) {
            postOutput("cp: origen no existe: " + srcPath + "\n");
            postFinished(1);
            return;
        }

        if (src.isDirectory() && !recursive) {
            postOutput("cp: omitiendo directorio '" + srcPath + "' (use 'cp -r')\n");
            postFinished(1);
            return;
        }

        if (dst.isDirectory()) {
            dst = new File(dst, src.getName());
        }

        boolean ok = recursive ? copyRecursively(src, dst) : copySingleFile(src, dst);
        if (ok) {
            postOutput("Copiado: " + src.getName() + " -> " + dst.getName() + "\n");
            postFinished(0);
        } else {
            postOutput("cp: error al copiar " + src.getName() + "\n");
            postFinished(1);
        }
    }

    private boolean copyRecursively(File src, File dest) {
        if (src.isDirectory()) {
            if (!dest.exists() && !dest.mkdirs()) return false;
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!copyRecursively(child, new File(dest, child.getName()))) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return copySingleFile(src, dest);
        }
    }

    private boolean copySingleFile(File src, File dst) {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error en copySingleFile: " + e.getMessage());
            return false;
        }
    }

    private void executeEcho(String commandLine) {
        int firstSpace = commandLine.indexOf(' ');
        if (firstSpace == -1) {
            postOutput("\n");
            postFinished(0);
            return;
        }
        String content = commandLine.substring(firstSpace + 1).trim();
        postOutput(content + "\n");
        postFinished(0);
    }

    private void executeNativeBinary(String[] tokens) {
        String binaryName = tokens[0];
        File filesDir = context.getFilesDir();
        File usrBin = new File(filesDir, "usr/bin");
        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);

        File executableFile = null;
        if (binaryName.startsWith("/") || binaryName.startsWith("./")) {
            executableFile = resolveFile(binaryName);
        } else {
            File candidateInUsr = new File(usrBin, binaryName);
            if (candidateInUsr.exists()) {
                executableFile = candidateInUsr;
            } else {
                File candidateInJni = new File(nativeLibDir, "lib" + binaryName + "_bin.so");
                if (candidateInJni.exists()) {
                    executableFile = candidateInJni;
                } else {
                    candidateInJni = new File(nativeLibDir, "lib" + binaryName + ".so");
                    if (candidateInJni.exists()) {
                        executableFile = candidateInJni;
                    }
                }
            }
        }

        if (executableFile == null || !executableFile.exists()) {
            postOutput("Comando no reconocido: " + binaryName + "\nEscriba 'help' o '?' para ver los comandos disponibles.\n");
            postFinished(127);
            return;
        }

        try {
            executableFile.setExecutable(true, true);

            List<String> fullCmd = new ArrayList<>();
            fullCmd.add(executableFile.getAbsolutePath());
            for (int i = 1; i < tokens.length; i++) {
                fullCmd.add(tokens[i]);
            }

            ProcessBuilder pb = new ProcessBuilder(fullCmd);
            pb.directory(currentWorkDir);

            Map<String, String> env = pb.environment();
            String ldPath = nativeLibDir.getAbsolutePath() + ":" + new File(filesDir, "usr/lib").getAbsolutePath();
            env.put("LD_LIBRARY_PATH", ldPath);
            env.put("PATH", usrBin.getAbsolutePath() + ":" + System.getenv("PATH"));
            env.put("HOME", filesDir.getAbsolutePath());

            pb.redirectErrorStream(true);

            currentProcess = pb.start();

            try (InputStream is = currentProcess.getInputStream()) {
                byte[] buffer = new byte[2048];
                int n;
                while ((n = is.read(buffer)) != -1) {
                    postOutput(new String(buffer, 0, n, StandardCharsets.UTF_8));
                }
            }

            int exitCode = currentProcess.waitFor();
            currentProcess = null;
            postFinished(exitCode);

        } catch (Exception e) {
            postOutput(context.getString(R.string.log_execution_error, e.getMessage()));
            postFinished(1);
        }
    }

    private void executeInShell(String commandLine) {
        File filesDir = context.getFilesDir();
        File usrBin = new File(filesDir, "usr/bin");
        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);

        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", commandLine);
            pb.directory(currentWorkDir);

            Map<String, String> env = pb.environment();
            String ldPath = nativeLibDir.getAbsolutePath() + ":" + new File(filesDir, "usr/lib").getAbsolutePath();
            env.put("LD_LIBRARY_PATH", ldPath);
            env.put("PATH", usrBin.getAbsolutePath() + ":" + nativeLibDir.getAbsolutePath() + ":" + System.getenv("PATH"));
            env.put("HOME", filesDir.getAbsolutePath());

            pb.redirectErrorStream(true);

            currentProcess = pb.start();

            try (InputStream is = currentProcess.getInputStream()) {
                byte[] buffer = new byte[2048];
                int n;
                while ((n = is.read(buffer)) != -1) {
                    postOutput(new String(buffer, 0, n, StandardCharsets.UTF_8));
                }
            }

            int exitCode = currentProcess.waitFor();
            currentProcess = null;
            postFinished(exitCode);

        } catch (Exception e) {
            postOutput(context.getString(R.string.log_execution_error, e.getMessage()));
            postFinished(1);
        }
    }

    private void printSandboxHelp() {
        String help = "======================================================\n"
                + "  Terminal Sandbox - Comandos del Entorno\n"
                + "======================================================\n\n"
                + "COMANDOS INTEGRADOS DEL SANDBOX:\n"
                + "  ls [-a] [dir]           Listar archivos y directorios\n"
                + "  pwd                     Mostrar ruta del directorio actual\n"
                + "  cd <dir>                Cambiar directorio ('cd ~' para inicio)\n"
                + "  cat <archivo>           Ver contenido de un archivo de texto\n"
                + "  touch <archivo>         Crear archivo vacío o actualizar fecha\n"
                + "  mkdir [-p] <dir>        Crear un nuevo directorio\n"
                + "  rm [-r|-rf] <objetivo>  Eliminar archivo o directorio\n"
                + "  cp [-r] <origen> <dst>  Copiar archivo o directorio\n"
                + "  echo <texto>            Imprimir texto en la terminal\n"
                + "  clear / cls             Limpiar el historial de la pantalla\n"
                + "  help / ?                Mostrar esta ayuda del sandbox\n\n"
                + "======================================================\n"
                + "  EJECUCIÓN DEL BINARIO NATIVO REAL (MORIA)\n"
                + "======================================================\n"
                + "Desde la caja de comandos inferior puede ejecutar directamente\n"
                + "el binario nativo real con cualquier comando u opción:\n\n"
                + "  moria [opciones] <archivo|directorio>\n\n"
                + "EJEMPLOS:\n"
                + "  moria firmware.bin          Identificar firmware\n"
                + "  moria -e firmware.bin       Extraer sistemas de archivos\n"
                + "  moria -c firmware.bin       Cortar (carve) flujos de bytes\n"
                + "  moria -A firmware.bin       Inspección profunda completa\n"
                + "  moria -E firmware.bin       Análisis de entropía\n"
                + "  moria --list firmware.bin   Listar contenidos sin extraer\n"
                + "  moria -e file.bin | grep sq Tuberías y filtros shell\n\n"
                + "Para ver la ayuda nativa completa del binario Moria, ejecute:\n"
                + "  moria --help\n"
                + "======================================================\n";
        postOutput(help);
    }

    private File resolveFile(String path) {
        if (path.startsWith("/")) {
            return new File(path);
        }
        return new File(currentWorkDir, path);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String[] splitCommandLine(String commandLine) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (c == '\"' || c == '\'') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (sb.length() > 0) {
                    list.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            list.add(sb.toString());
        }
        return list.toArray(new String[0]);
    }

    private void postOutput(String text) {
        mainHandler.post(() -> callback.onOutput(text));
    }

    private void postStarted(String command) {
        mainHandler.post(() -> callback.onCommandStarted(command));
    }

    private void postFinished(int exitCode) {
        mainHandler.post(() -> callback.onCommandFinished(exitCode));
    }
}
