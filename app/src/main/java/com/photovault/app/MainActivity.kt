package com.photovault.app

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null

    private val chooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cb = pendingCallback
            pendingCallback = null
            if (cb != null) {
                var uris: Array<Uri>? = null
                if (result.resultCode == RESULT_OK) {
                    val data = result.data
                    val clip = data?.clipData
                    val picked = data?.data
                    val cam = cameraUri
                    uris = when {
                        clip != null && clip.itemCount > 0 ->
                            Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                        picked != null -> arrayOf(picked)
                        cam != null -> arrayOf(cam)
                        else -> null
                    }
                }
                cb.onReceiveValue(uris)
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // Serves assets/index.html from a real https origin so IndexedDB (photo storage) works.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                pendingCallback?.onReceiveValue(null)
                pendingCallback = callback

                val accept = params.acceptTypes.joinToString(",")
                val wantsImage = accept.isEmpty() || accept.contains("image")

                val gallery = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if (wantsImage) "image/*" else "*/*"
                }

                val launchIntent: Intent
                if (wantsImage) {
                    val dir = File(cacheDir, "captures").apply { mkdirs() }
                    val photoFile = File.createTempFile("capture_", ".jpg", dir)
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity, "$packageName.fileprovider", photoFile
                    )
                    cameraUri = uri
                    val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                    launchIntent = if (params.isCaptureEnabled) {
                        camera
                    } else {
                        Intent.createChooser(gallery, "Choose photo")
                            .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
                    }
                } else {
                    cameraUri = null
                    launchIntent = gallery
                }

                return try {
                    chooserLauncher.launch(launchIntent)
                    true
                } catch (e: Exception) {
                    pendingCallback?.onReceiveValue(null)
                    pendingCallback = null
                    false
                }
            }
        }

        webView.addJavascriptInterface(Bridge(), "AndroidBridge")
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    /** Lets the page save and share backup files, which a plain WebView cannot do. */
    inner class Bridge {
        private fun safeName(name: String): String =
            name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "photo-vault-backup.json" }

        @JavascriptInterface
        fun saveFile(name: String, text: String) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName(name))
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    }
                    toast("Saved to Downloads")
                } else {
                    toast("Couldn't save the file")
                }
            } catch (e: Exception) {
                toast("Couldn't save the file")
            }
        }

        @JavascriptInterface
        fun shareFile(name: String, text: String) {
            try {
                val dir = File(cacheDir, "shared").apply { mkdirs() }
                val file = File(dir, safeName(name))
                file.writeText(text, Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(
                    this@MainActivity, "$packageName.fileprovider", file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    startActivity(Intent.createChooser(send, "Send backup"))
                }
            } catch (e: Exception) {
                toast("Couldn't share the file")
            }
        }

        private fun toast(msg: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show() }
        }
    }
}
