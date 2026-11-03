package com.nahiyan.miramessenger

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var changeServerButton: Button
    private val prefs by lazy { getSharedPreferences("mira_app", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildLayout()
        configureWebView()

        val savedUrl = prefs.getString("app_url", null)
        if (savedUrl.isNullOrBlank()) {
            showServerDialog(null)
        } else {
            loadApp(savedUrl)
        }
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(249, 247, 241))
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(249, 247, 241))
        }

        val title = TextView(this).apply {
            text = "Mira Messenger"
            textSize = 19f
            setTextColor(Color.rgb(39, 73, 57))
            typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        changeServerButton = Button(this).apply {
            text = "Server"
            isAllCaps = false
            setOnClickListener { showServerDialog(prefs.getString("app_url", null)) }
        }
        toolbar.addView(title)
        toolbar.addView(changeServerButton)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(39, 73, 57))
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
            visibility = View.GONE
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(249, 247, 241))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        root.addView(toolbar)
        root.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
        ))
        root.addView(status)
        root.addView(webView)
        setContentView(root)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == currentHost()) return false
                if (uri.scheme == "https" && (uri.host == "accounts.google.com" || uri.host?.endsWith(".google.com") == true)) {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    return true
                }
                if (uri.scheme == "http" || uri.scheme == "https") {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    return true
                }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                status.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    status.text = "Couldn't load Mira. Check your connection or server URL, then tap Server to try again."
                    status.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun currentHost(): String? = prefs.getString("app_url", null)?.let {
        runCatching { Uri.parse(it).host }.getOrNull()
    }

    private fun loadApp(rawUrl: String) {
        val normalized = rawUrl.trim().trimEnd('/')
        if (!isValidHttpsUrl(normalized)) {
            showServerDialog(rawUrl)
            return
        }
        prefs.edit().putString("app_url", normalized).apply()
        status.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(normalized)
    }

    private fun isValidHttpsUrl(value: String): Boolean {
        return runCatching {
            val uri = Uri.parse(value)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)
    }

    private fun showServerDialog(existing: String?) {
        val field = EditText(this).apply {
            hint = "https://your-app.example.com"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            if (!existing.isNullOrBlank()) setText(existing)
            setSelection(text.length)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(TextView(this@MainActivity).apply {
                text = "Enter your deployed Mira Messenger HTTPS address. The website and its server-side chat API must already be online."
                textSize = 14f
                setTextColor(Color.rgb(65, 75, 67))
                setPadding(0, 0, 0, dp(12))
            })
            addView(field)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Connect to Mira")
            .setView(container)
            .setCancelable(existing != null)
            .setPositiveButton("Connect", null)
            .setNegativeButton(if (existing == null) "Close" else "Cancel") { _, _ ->
                if (existing == null) finish()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = field.text.toString().trim().trimEnd('/')
                if (!isValidHttpsUrl(url)) {
                    field.error = "Enter a valid HTTPS URL"
                    return@setOnClickListener
                }
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(field.windowToken, 0)
                dialog.dismiss()
                loadApp(url)
            }
        }
        dialog.show()
        field.requestFocus()
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
