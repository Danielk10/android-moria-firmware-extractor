package com.diamon.moria.ui.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.diamon.moria.R;
import com.diamon.moria.core.TerminalExecutor;
import com.diamon.moria.ui.views.LogScrollView;
import com.diamon.moria.utils.AssetHelper;
import com.diamon.moria.utils.FileManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TerminalExecutor.Callback {

    private static final String PREFS_NAME = "MoriaPrefs";
    private static final String KEY_WORK_DIR = "custom_work_dir";

    // Vistas de estado de carga inicial
    private LinearLayout layoutLoading;
    private LinearLayout layoutMainUI;
    private TextView tvLoadingText;

    // Vistas de la UI Principal
    private TextView tvTarget;
    private TextView tvStatus;
    private TextView tvLog;
    private EditText etCommand;

    private Button btnSelectTarget;

    private Button btnIdentify;
    private Button btnExtract;
    private Button btnCarve;
    private Button btnEntropy;
    private Button btnList;
    private Button btnScanAll;

    private CheckBox cbJson;
    private CheckBox cbBroad;

    // Controles de Terminal y Navegación
    private Button btnRun;
    private ImageButton btnHistoryPrev;
    private ImageButton btnHistoryNext;
    private ImageButton btnArrowLeft;
    private ImageButton btnArrowRight;
    private ImageButton btnTab;
    private ImageButton btnDelete;
    private ImageButton btnPaste;
    private ImageButton btnCopy;
    private ImageButton btnAbort;
    private LogScrollView scrollLog;

    // Historial de comandos terminal
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

    private TerminalExecutor terminalExecutor;
    private SharedPreferences prefs;

    private File currentTargetFile = null;

    // Selector de carpeta de trabajo
    private final ActivityResultLauncher<Intent> directoryPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(treeUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        } catch (Exception ignored) {}

                        prefs.edit().putString(KEY_WORK_DIR, treeUri.toString()).apply();
                        appendLog(getString(R.string.log_system_work_dir, treeUri.toString()));
                    }
                }
            });

    // Selector de archivo(s) firmware objetivo / importación
    private final ActivityResultLauncher<Intent> fileImportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        List<Uri> uris = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            Uri uri = data.getClipData().getItemAt(i).getUri();
                            if (uri != null) uris.add(uri);
                        }
                        importMultipleFilesToWorkspace(uris);
                    } else if (data.getData() != null) {
                        importFileToWorkspace(data.getData());
                    }
                }
            });

    // Selector de carpeta completa para importar al espacio de trabajo
    private final ActivityResultLauncher<Intent> folderImportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(treeUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignored) {}
                        importFolderToWorkspace(treeUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        setupTerminal();
        setupListeners();
        initNativeRuntime();
    }

    private void initViews() {
        layoutLoading = findViewById(R.id.layoutLoading);
        layoutMainUI = findViewById(R.id.layoutMainUI);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        tvTarget = findViewById(R.id.tvTarget);
        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        etCommand = findViewById(R.id.etCommand);

        btnSelectTarget = findViewById(R.id.btnSelectTarget);

        btnIdentify = findViewById(R.id.btnIdentify);
        btnExtract = findViewById(R.id.btnExtract);
        btnCarve = findViewById(R.id.btnCarve);
        btnEntropy = findViewById(R.id.btnEntropy);
        btnList = findViewById(R.id.btnList);
        btnScanAll = findViewById(R.id.btnScanAll);

        cbJson = findViewById(R.id.cbJson);
        cbBroad = findViewById(R.id.cbBroad);

        btnRun = findViewById(R.id.btnRun);
        btnHistoryPrev = findViewById(R.id.btnHistoryPrev);
        btnHistoryNext = findViewById(R.id.btnHistoryNext);
        btnArrowLeft = findViewById(R.id.btnArrowLeft);
        btnArrowRight = findViewById(R.id.btnArrowRight);
        btnTab = findViewById(R.id.btnTab);
        btnDelete = findViewById(R.id.btnDelete);
        btnPaste = findViewById(R.id.btnPaste);
        btnCopy = findViewById(R.id.btnCopy);
        btnAbort = findViewById(R.id.btnAbort);
        scrollLog = findViewById(R.id.scrollLog);
    }

    private void setupTerminal() {
        terminalExecutor = new TerminalExecutor(this, this);
        updateTargetDisplay();
    }

    private void initNativeRuntime() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutMainUI.setVisibility(View.GONE);

        new Thread(() -> {
            boolean wasExtracted = AssetHelper.areAssetsExtracted(getApplicationContext());
            if (!wasExtracted) {
                runOnUiThread(() -> tvLoadingText.setText(R.string.str_extracting_libs));
            } else {
                runOnUiThread(() -> tvLoadingText.setText(R.string.str_verifying_dependencies));
            }

            boolean ready = AssetHelper.ensureRuntimeReady(getApplicationContext());

            runOnUiThread(() -> {
                layoutLoading.setVisibility(View.GONE);
                layoutMainUI.setVisibility(View.VISIBLE);
                if (ready) {
                    appendLog(getString(R.string.log_init_prefix, getString(R.string.str_log_runtime_ready)));
                } else {
                    appendLog(getString(R.string.log_warning_prefix, getString(R.string.str_log_runtime_failed)));
                }
            });
        }).start();
    }

    private void updateTargetDisplay() {
        if (currentTargetFile != null && currentTargetFile.exists() && !FileManager.shouldIgnore(currentTargetFile)) {
            String sizeStr;
            if (currentTargetFile.isDirectory()) {
                File[] sub = currentTargetFile.listFiles();
                int count = sub != null ? sub.length : 0;
                sizeStr = count + " items";
            } else {
                sizeStr = currentTargetFile.length() < 1024 ? currentTargetFile.length() + " B" : (currentTargetFile.length() / 1024) + " KB";
            }
            tvTarget.setText(getString(R.string.target_selected, currentTargetFile.getName(), sizeStr));
        } else {
            currentTargetFile = null;
            tvTarget.setText(R.string.target_none);
        }
    }

    private void setupListeners() {
        // Selección de archivo firmware
        btnSelectTarget.setOnClickListener(v -> showTargetPickerDialog());
        tvTarget.setOnClickListener(v -> showTargetPickerDialog());

        // Acciones nativas de Moria
        btnIdentify.setOnClickListener(v -> runMoriaAction(""));
        btnExtract.setOnClickListener(v -> runMoriaAction("-e"));
        btnCarve.setOnClickListener(v -> runMoriaAction("-c"));
        btnEntropy.setOnClickListener(v -> runMoriaAction("-E"));
        btnList.setOnClickListener(v -> runMoriaAction("--list"));
        btnScanAll.setOnClickListener(v -> runMoriaAction("-A"));

        // Terminal y Comandos
        btnRun.setOnClickListener(v -> submitCommand());
        etCommand.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                submitCommand();
                return true;
            }
            return false;
        });

        // Navegación con teclado hardware (DPAD Up/Down para historial)
        etCommand.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    navigateHistory(-1);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    navigateHistory(1);
                    return true;
                }
            }
            return false;
        });

        // Controles de barra de herramientas de Terminal (Calculo_Estructural)
        btnHistoryPrev.setOnClickListener(v -> navigateHistory(-1));
        btnHistoryNext.setOnClickListener(v -> navigateHistory(1));
        btnArrowLeft.setOnClickListener(v -> moveCursor(-1));
        btnArrowRight.setOnClickListener(v -> moveCursor(1));
        btnTab.setOnClickListener(v -> performTab());
        btnDelete.setOnClickListener(v -> onClearRequested());
        btnPaste.setOnClickListener(v -> pasteFromClipboard());
        btnCopy.setOnClickListener(v -> copyLogsToClipboard());
        btnAbort.setOnClickListener(v -> terminalExecutor.abort());
    }

    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty() || etCommand == null) return;

        if (historyIndex == -1) {
            historyIndex = commandHistory.size();
        }

        historyIndex += direction;

        if (historyIndex < 0) {
            historyIndex = 0;
        } else if (historyIndex >= commandHistory.size()) {
            historyIndex = commandHistory.size();
            etCommand.setText("");
            return;
        }

        String command = commandHistory.get(historyIndex);
        etCommand.setText(command);
        etCommand.setSelection(command.length());
    }

    private void moveCursor(int delta) {
        if (etCommand == null) return;
        int sel = etCommand.getSelectionStart();
        int newSel = Math.max(0, Math.min(sel + delta, etCommand.length()));
        etCommand.setSelection(newSel);
    }

    private void performTab() {
        if (etCommand == null) return;
        int sel = Math.max(0, etCommand.getSelectionStart());
        etCommand.getText().insert(sel, "    ");
    }

    private void pasteFromClipboard() {
        if (etCommand == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence textToPaste = clipboard.getPrimaryClip().getItemAt(0).getText();
            if (textToPaste != null) {
                int sel = Math.max(0, etCommand.getSelectionStart());
                etCommand.getText().insert(sel, textToPaste);
            }
        }
    }

    private void showTargetPickerDialog() {
        File workDir = terminalExecutor != null ? terminalExecutor.getCurrentWorkDir() : null;
        File[] files = (workDir != null && workDir.exists()) ? workDir.listFiles() : null;

        List<String> displayNames = new ArrayList<>();
        List<File> targetFiles = new ArrayList<>();

        if (files != null) {
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : files) {
                if (FileManager.shouldIgnore(f)) continue;
                displayNames.add(f.isDirectory() ? "[DIR] " + f.getName() : f.getName());
                targetFiles.add(f);
            }
        }

        displayNames.add(getString(R.string.dialog_import_new_file));
        displayNames.add(getString(R.string.dialog_import_new_folder));

        String[] items = displayNames.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_select_target_title)
                .setItems(items, (dialog, which) -> {
                    if (which == targetFiles.size()) {
                        openFilePicker();
                    } else if (which == targetFiles.size() + 1) {
                        openFolderPicker();
                    } else {
                        currentTargetFile = targetFiles.get(which);
                        updateTargetDisplay();
                        appendLog(getString(R.string.log_target_selected, currentTargetFile.getName()));
                    }
                })
                .setNegativeButton(R.string.str_close, null)
                .show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("*/*");
        fileImportLauncher.launch(intent);
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        folderImportLauncher.launch(intent);
    }

    private void runMoriaAction(String actionFlag) {
        if (currentTargetFile == null || !currentTargetFile.exists() || FileManager.shouldIgnore(currentTargetFile)) {
            currentTargetFile = null;
            updateTargetDisplay();
            Toast.makeText(this, R.string.select_target_prompt, Toast.LENGTH_SHORT).show();
            showTargetPickerDialog();
            return;
        }

        List<String> cmdParts = new ArrayList<>();
        cmdParts.add("moria");
        if (actionFlag != null && !actionFlag.isEmpty()) {
            cmdParts.add(actionFlag);
        }
        if (cbJson.isChecked()) {
            cmdParts.add("-j");
        }
        if (cbBroad.isChecked()) {
            cmdParts.add("--broad");
        }
        cmdParts.add("\"" + currentTargetFile.getName() + "\"");

        String fullCommand = String.join(" ", cmdParts);
        submitCustomCommand(fullCommand);
    }

    private void submitCommand() {
        String cmd = etCommand.getText() != null ? etCommand.getText().toString().trim() : "";
        if (cmd.isEmpty()) return;
        etCommand.setText("");
        submitCustomCommand(cmd);
    }

    private void submitCustomCommand(String cmd) {
        if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(cmd)) {
            commandHistory.add(cmd);
        }
        historyIndex = commandHistory.size();
        terminalExecutor.execute(cmd);
    }

    private void importFileToWorkspace(Uri uri) {
        String name = FileManager.getFileName(this, uri);
        File destFile = new File(terminalExecutor.getCurrentWorkDir(), name);

        boolean ok = FileManager.copyUriToFile(this, uri, destFile);
        if (ok) {
            currentTargetFile = destFile;
            updateTargetDisplay();
            String sizeStr = destFile.length() < 1024 ? destFile.length() + " B" : (destFile.length() / 1024) + " KB";
            String msg = getString(R.string.str_file_imported, name, sizeStr);
            appendLog(getString(R.string.log_imported_prefix, msg));
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } else {
            String err = getString(R.string.str_file_import_error, name);
            appendLog(getString(R.string.log_error_prefix, err));
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
        }
    }

    private void importMultipleFilesToWorkspace(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.str_folder_importing, "...", 0));

        new Thread(() -> {
            int successCount = 0;
            File lastDest = null;
            File workDir = terminalExecutor.getCurrentWorkDir();

            for (Uri uri : uris) {
                String name = FileManager.getFileName(this, uri);
                File destFile = new File(workDir, name);
                if (FileManager.copyUriToFile(this, uri, destFile)) {
                    successCount++;
                    lastDest = destFile;
                }
            }

            final int count = successCount;
            final File selected = lastDest;
            runOnUiThread(() -> {
                tvStatus.setVisibility(View.GONE);
                if (count > 0 && selected != null) {
                    currentTargetFile = selected;
                    updateTargetDisplay();
                    String msg = getString(R.string.str_files_imported, count);
                    appendLog(getString(R.string.log_imported_prefix, msg));
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                } else {
                    String err = getString(R.string.str_file_import_error, "multi-files");
                    appendLog(getString(R.string.log_error_prefix, err));
                    Toast.makeText(MainActivity.this, err, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void importFolderToWorkspace(Uri treeUri) {
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.str_folder_importing, "...", 0));
        appendLog(getString(R.string.log_init_prefix, getString(R.string.str_folder_importing, "directorio", 0)));

        new Thread(() -> {
            File workDir = terminalExecutor.getCurrentWorkDir();
            final long[] lastUpdateTime = new long[]{System.currentTimeMillis()};

            FileManager.FolderImportResult result = FileManager.importDocumentTree(
                    this, treeUri, workDir, (filesCopied, currentFile) -> {
                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime[0] > 400 || filesCopied % 20 == 0) {
                            lastUpdateTime[0] = now;
                            runOnUiThread(() -> {
                                tvStatus.setText(getString(R.string.str_folder_importing, currentFile, filesCopied));
                            });
                        }
                    });

            runOnUiThread(() -> {
                tvStatus.setVisibility(View.GONE);
                if (result.success && result.folder != null) {
                    currentTargetFile = result.folder;
                    updateTargetDisplay();
                    String sizeStr = FileManager.formatSize(result.totalBytes);
                    String msg = getString(R.string.str_folder_imported, result.folder.getName(), result.fileCount, sizeStr);
                    appendLog(getString(R.string.log_imported_prefix, msg));
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                } else {
                    String folderName = "directorio";
                    try {
                        androidx.documentfile.provider.DocumentFile doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(MainActivity.this, treeUri);
                        if (doc != null && doc.getName() != null) folderName = doc.getName();
                    } catch (Exception ignored) {}
                    String err = getString(R.string.str_folder_import_error, folderName);
                    appendLog(getString(R.string.log_error_prefix, err));
                    Toast.makeText(MainActivity.this, err, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void exportAllFilesToDownloads() {
        File workDir = terminalExecutor.getCurrentWorkDir();
        List<String> exported = FileManager.exportAllToDownloads(this, workDir, FileManager.DEFAULT_DOWNLOADS_FOLDER);

        if (!exported.isEmpty()) {
            String msg = getString(R.string.str_export_success, exported.size(), FileManager.DEFAULT_DOWNLOADS_FOLDER);
            appendLog(getString(R.string.log_export_success_header, msg));
            for (String file : exported) {
                appendLog("  • " + file + "\n");
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } else {
            String msg = getString(R.string.str_export_none);
            appendLog(getString(R.string.log_export_none_header, msg));
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyLogsToClipboard() {
        String text = tvLog.getText() != null ? tvLog.getText().toString() : "";
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.str_no_logs_to_copy, Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.app_name), text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.str_logs_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void appendLog(String text) {
        tvLog.append(text);
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_set_working_dir) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            directoryPickerLauncher.launch(intent);
            return true;
        } else if (id == R.id.action_import_file) {
            openFilePicker();
            return true;
        } else if (id == R.id.action_import_folder) {
            openFolderPicker();
            return true;
        } else if (id == R.id.action_export_downloads) {
            exportAllFilesToDownloads();
            return true;
        } else if (id == R.id.action_policy) {
            startActivity(new Intent(this, PolicyActivity.class));
            return true;
        } else if (id == R.id.action_about_licenses) {
            showAboutAndLicensesDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showAboutAndLicensesDialog() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_dark));

        TextView dialogText = new TextView(this);
        int padding = (int) (18 * getResources().getDisplayMetrics().density);
        dialogText.setPadding(padding, padding, padding, padding);
        dialogText.setMovementMethod(LinkMovementMethod.getInstance());
        dialogText.setTextColor(Color.WHITE);
        dialogText.setLinkTextColor(ContextCompat.getColor(this, R.color.secondary));
        dialogText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        String html = getString(R.string.str_about_licenses_html);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dialogText.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT));
        } else {
            @SuppressWarnings("deprecation")
            CharSequence text = Html.fromHtml(html);
            dialogText.setText(text);
        }
        scrollView.addView(dialogText);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomDarkDialogTheme)
                .setTitle(R.string.about_licenses_dialog_title)
                .setView(scrollView)
                .setPositiveButton(R.string.str_close, null)
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.surface_dark)));
            }
            Button posBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (posBtn != null) {
                posBtn.setTextColor(ContextCompat.getColor(this, R.color.secondary));
            }
        });

        dialog.show();
    }

    // Callbacks de TerminalExecutor
    @Override
    public void onOutput(String text) {
        appendLog(text);
    }

    @Override
    public void onCommandStarted(String command) {
        btnAbort.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.str_status_running, command));
        appendLog("$ " + command + "\n");
    }

    @Override
    public void onCommandFinished(int exitCode) {
        btnAbort.setVisibility(View.GONE);
        tvStatus.setVisibility(View.GONE);
        updateTargetDisplay();
    }

    @Override
    public void onClearRequested() {
        tvLog.setText("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (terminalExecutor != null) {
            terminalExecutor.destroy();
        }
    }
}
