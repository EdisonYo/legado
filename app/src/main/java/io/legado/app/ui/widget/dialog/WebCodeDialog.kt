package io.legado.app.ui.widget.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogWebCodeViewBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import android.util.Base64

class WebCodeDialog() : BaseDialogFragment(R.layout.dialog_web_code_view) {

    constructor(code: String, requestId: String? = null) : this() {
        arguments = Bundle().apply {
            putString("code", code)
            putString("requestId", requestId)
        }
    }

    private val binding by viewBinding(DialogWebCodeViewBinding::bind)
    private var pendingCode: String? = null

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.inflateMenu(R.menu.code_edit)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_save -> {
                    binding.webView.evaluateJavascript("window.__save && window.__save();", null)
                    return@setOnMenuItemClickListener true
                }
            }
            true
        }
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            textZoom = 100
        }
        binding.webView.addJavascriptInterface(JsBridge(), "Android")
        pendingCode = arguments?.getString("code").orEmpty()
        binding.webView.loadDataWithBaseURL(
            null,
            buildHtml(pendingCode.orEmpty()),
            "text/html",
            "utf-8",
            null
        )
    }

    override fun onDestroyView() {
        binding.webView.apply {
            removeJavascriptInterface("Android")
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroyView()
    }

    private fun buildHtml(code: String): String {
        val encoded = Base64.encodeToString(code.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <style>
                html, body { margin:0; padding:0; height:100%; }
                body { font-family: monospace; background:#ffffff; }
                textarea {
                  box-sizing:border-box;
                  width:100%;
                  height:100%;
                  border:0;
                  padding:12px;
                  font-size:14px;
                  line-height:1.4;
                  outline:none;
                  resize:none;
                }
              </style>
            </head>
            <body>
              <textarea id="code"></textarea>
              <script>
                function b64ToUtf8(b64) {
                  try {
                    var bin = atob(b64);
                    var bytes = new Uint8Array(bin.length);
                    for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
                    if (window.TextDecoder) {
                      return new TextDecoder("utf-8").decode(bytes);
                    }
                    var esc = "";
                    for (var j = 0; j < bytes.length; j++) {
                      esc += "%" + ("00" + bytes[j].toString(16)).slice(-2);
                    }
                    return decodeURIComponent(esc);
                  } catch (e) {
                    return "";
                  }
                }
                var data = b64ToUtf8("$encoded");
                document.getElementById("code").value = data;
                window.__save = function() {
                  var value = document.getElementById("code").value || "";
                  if (window.Android && window.Android.save) {
                    window.Android.save(value);
                  }
                };
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun save(text: String) {
            if (text == pendingCode) {
                dismissAllowingStateLoss()
                return
            }
            pendingCode = text
            val requestId = arguments?.getString("requestId")
            (parentFragment as? Callback)?.onCodeSave(text, requestId)
                ?: (activity as? Callback)?.onCodeSave(text, requestId)
            dismissAllowingStateLoss()
        }
    }

    interface Callback {
        fun onCodeSave(code: String, requestId: String?)
    }
}
