package com.diamon.moria.ui.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.diamon.moria.R;
import com.diamon.moria.core.TerminalExecutor;
import com.diamon.moria.ui.views.LogScrollView;
import com.diamon.moria.utils.AssetHelper;
import com.diamon.moria.utils.FileManager;

import java.io.File;
import java.util.ArrayList;
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
    private ImageButton btnClear;
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
                        appendLog("[SISTEMA] Espacio de trabajo configurado: " + treeUri + "\n");
                    }
                }
            });

    // Selector de archivo firmware objetivo / importación
    private final ActivityResultLauncher<Intent> fileImportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importFileToWorkspace(uri);
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
        btnClear = findViewById(R.id.btnClear);
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
                    appendLog("[INICIALIZACIÓN] " + getString(R.string.str_log_runtime_ready) + "\n");
                } else {
                    appendLog("[ADVERTENCIA] " + getString(R.string.str_log_runtime_failed) + "\n");
                }
            });
        }).start();
    }

    private void updateTargetDisplay() {
        if (currentTargetFile != null && currentTargetFile.exists()) {
            String sizeStr = currentTargetFile.length() < 1024 ? currentTargetFile.length() + " B" : (currentTargetFile.length() / 1024) + " KB";
            tvTarget.setText(getString(R.string.target_selected, currentTargetFile.getName(), sizeStr));
        } else {
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
        btnDelete.setOnClickListener(v -> performDelete());
        btnPaste.setOnClickListener(v -> pasteFromClipboard());
        btnCopy.setOnClickListener(v -> copyLogsToClipboard());
        btnClear.setOnClickListener(v -> onClearRequested());
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

    private void performDelete() {
        if (etCommand == null) return;
        int sel = etCommand.getSelectionStart();
        if (sel > 0) {
            etCommand.getText().delete(sel - 1, sel);
        }
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
            for (File f : files) {
                if (f.getName().startsWith(".")) continue;
                String label = f.isDirectory() ? "[DIR] " + f.getName() : f.getName();
                displayNames.add(label);
                targetFiles.add(f);
            }
        }

        displayNames.add(getString(R.string.dialog_import_new_file));

        String[] items = displayNames.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_select_target_title)
                .setItems(items, (dialog, which) -> {
                    if (which == targetFiles.size()) {
                        openFilePicker();
                    } else {
                        currentTargetFile = targetFiles.get(which);
                        updateTargetDisplay();
                        appendLog("[OBJETIVO] Seleccionado: " + currentTargetFile.getName() + "\n");
                    }
                })
                .setNegativeButton(R.string.str_close, null)
                .show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        fileImportLauncher.launch(intent);
    }

    private void runMoriaAction(String actionFlag) {
        if (currentTargetFile == null || !currentTargetFile.exists()) {
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
            appendLog("[IMPORTADO] " + msg + "\n");
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } else {
            String err = getString(R.string.str_file_import_error, name);
            appendLog("[ERROR] " + err + "\n");
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
        }
    }

    private void exportAllFilesToDownloads() {
        File workDir = terminalExecutor.getCurrentWorkDir();
        List<String> exported = FileManager.exportAllToDownloads(this, workDir, FileManager.DEFAULT_DOWNLOADS_FOLDER);

        if (!exported.isEmpty()) {
            String msg = getString(R.string.str_export_success, exported.size(), FileManager.DEFAULT_DOWNLOADS_FOLDER);
            appendLog("\n[EXPORTACIÓN EXITOSA]\n" + msg + ":\n");
            for (String file : exported) {
                appendLog("  • " + file + "\n");
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } else {
            String msg = getString(R.string.str_export_none);
            appendLog("\n[EXPORTACIÓN] " + msg + "\n");
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
        ClipData clip = ClipData.newPlainText("Moria Logs", text);
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
        TextView dialogText = new TextView(this);
        int padding = (int) (18 * getResources().getDisplayMetrics().density);
        dialogText.setPadding(padding, padding, padding, padding / 2);
        dialogText.setMovementMethod(LinkMovementMethod.getInstance());
        String html = getString(R.string.str_about_licenses_html);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dialogText.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT));
        } else {
            @SuppressWarnings("deprecation")
            CharSequence text = Html.fromHtml(html);
            dialogText.setText(text);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.about_licenses_dialog_title)
                .setView(dialogText)
                .setPositiveButton(R.string.str_close, null)
                .show();
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
}
