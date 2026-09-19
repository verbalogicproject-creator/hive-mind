package com.example.ui.graph

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream

private const val APP_ORIGIN = "https://appassets.androidplatform.net"
private const val GRAPH_URL = "$APP_ORIGIN/assets/graph/index.html"
private const val MAX_INBOUND_MESSAGE_BYTES = 4_096
private const val MAX_PROJECTION_BYTES = 256 * 1_024

@Composable
fun GraphWebView(projection: GraphProjection, onNodeSelected: (GraphNode) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var failure by remember { mutableStateOf<String?>(null) }
  var reloadKey by remember { mutableStateOf(0) }
  val payload = remember(projection) { Json.encodeToString(projection) }
  if (payload.toByteArray().size > MAX_PROJECTION_BYTES) {
    GraphRecovery("The graph is too large to display safely.") { reloadKey++ }
    return
  }
  if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
    !WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE)) {
    GraphRecovery("This Android WebView cannot display the memory graph.") { reloadKey++ }
    return
  }
  if (failure != null) {
    GraphRecovery(failure!!) { failure = null; reloadKey++ }
    return
  }
  val webView = remember(reloadKey) {
    WebView(context).apply {
      configureSecureGraphWebView(
        projectionPayload = payload,
        onNodeSelected = { selectedId -> projection.nodes.firstOrNull { it.id == selectedId }?.let(onNodeSelected) },
        onFailure = { failure = it }
      )
    }
  }
  DisposableEffect(webView) { onDispose { webView.stopLoading(); webView.destroy() } }
  AndroidView(factory = { webView }, modifier = modifier.fillMaxSize())
}

@Composable
private fun GraphRecovery(message: String, retry: () -> Unit) {
  Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
    Text("Graph unavailable", style = MaterialTheme.typography.titleLarge)
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
    Button(onClick = retry) { Text("Try again") }
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureSecureGraphWebView(
  projectionPayload: String,
  onNodeSelected: (String) -> Unit,
  onFailure: (String) -> Unit
) {
  val assetLoader = WebViewAssetLoader.Builder()
    .setDomain("appassets.androidplatform.net")
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
    .build()
  settings.javaScriptEnabled = true
  settings.domStorageEnabled = false
  settings.allowFileAccess = false
  settings.allowContentAccess = false
  settings.allowFileAccessFromFileURLs = false
  settings.allowUniversalAccessFromFileURLs = false
  settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
  settings.javaScriptCanOpenWindowsAutomatically = false
  settings.setSupportMultipleWindows(false)
  setDownloadListener { _, _, _, _, _ -> onFailure("Downloads are disabled in the memory graph.") }
  webChromeClient = object : WebChromeClient() {
    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?) = false
  }
  WebViewCompat.addWebMessageListener(this, "ProjectMemory", setOf(APP_ORIGIN)) { _, message, sourceOrigin, isMainFrame, _ ->
    val body = message.data ?: return@addWebMessageListener
    if (!isMainFrame || sourceOrigin.toString().removeSuffix("/") != APP_ORIGIN || body.toByteArray().size > MAX_INBOUND_MESSAGE_BYTES) return@addWebMessageListener
    runCatching {
      val objectValue = Json.parseToJsonElement(body).jsonObject
      if (objectValue["type"]?.jsonPrimitive?.content != "node-selected") return@runCatching
      val id = objectValue["id"]?.jsonPrimitive?.content.orEmpty()
      require(id.length in 1..256 && id.matches(Regex("[A-Za-z0-9:_\\-.]+")))
      onNodeSelected(id)
    }
  }
  webViewClient = object : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
      val url = request?.url ?: return null
      return if (url.scheme == "https" && url.host == "appassets.androidplatform.net" && url.path.orEmpty().startsWith("/assets/graph/")) {
        assetLoader.shouldInterceptRequest(url)
      } else {
        WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
      }
    }
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
      val url = request?.url ?: return true
      return url.scheme != "https" || url.host != "appassets.androidplatform.net" || !url.path.orEmpty().startsWith("/assets/graph/")
    }
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
      if (url != GRAPH_URL) onFailure("The graph tried to open an unsupported location.")
    }
    override fun onPageFinished(view: WebView?, url: String?) {
      if (url == GRAPH_URL && view != null) WebViewCompat.postWebMessage(view, WebMessageCompat(projectionPayload), Uri.parse(APP_ORIGIN))
    }
    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
      if (request?.isForMainFrame == true) onFailure("The local graph could not be loaded. Try again.")
    }
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
      view?.destroy()
      onFailure("The graph renderer stopped. Reload the graph to continue.")
      return true
    }
  }
  loadUrl(GRAPH_URL)
}
