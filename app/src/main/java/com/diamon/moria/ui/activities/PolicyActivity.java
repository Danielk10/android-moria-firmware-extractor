package com.diamon.moria.ui.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import com.diamon.moria.R;

public class PolicyActivity extends AppCompatActivity {

    public static final String PROVISIONAL_POLICY_URL = "https://todoandroid.42web.io/privacy-policy.html";
    private static final String LOCAL_POLICY_URL = "file:///android_asset/privacy-policy.html";

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_policy);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.menu_policy);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // Si falla la carga del enlace provisional por falta de internet o servidor caido, cargar fallback local
                if (request.isForMainFrame()) {
                    view.loadUrl(LOCAL_POLICY_URL);
                }
            }
        });

        // Cargar enlace provisional online
        webView.loadUrl(PROVISIONAL_POLICY_URL);
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
