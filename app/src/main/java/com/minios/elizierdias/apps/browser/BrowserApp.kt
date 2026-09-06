/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.apps.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

private data class BrowserTab(
    val id: Int,
    val url: String,
    val title: String,
)

private val ChromeBg = Color(0xFF202124)
private val TabActive = Color(0xFF303134)
private val TabInactive = Color(0xFF202124)
private val AddressBg = Color(0xFF303134)
private val TextPrimary = Color(0xFFE8EAED)
private val TextMuted = Color(0xFF9AA0A6)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserApp() {
    val context = LocalContext.current

    val tabs = remember {
        mutableStateListOf(
            BrowserTab(0, "https://www.google.com", "Nova aba"),
        )
    }

    var selectedTabId by remember { mutableIntStateOf(0) }
    var addressText by remember { mutableStateOf("https://www.google.com") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
    val selectedTab = tabs.getOrNull(selectedIndex) ?: tabs.first()

    fun applyDesktopViewport(view: WebView) {
        // Pedir layout desktop e zoom razoável (evita sites “gigantes”)
        view.evaluateJavascript(
            """
            (function() {
              var meta = document.querySelector('meta[name=viewport]');
              if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                document.head.appendChild(meta);
              }
              meta.setAttribute('content',
                'width=1280, initial-scale=0.85, maximum-scale=3.0, user-scalable=yes');
              try { document.documentElement.style.zoom = '0.9'; } catch(e) {}
            })();
            """.trimIndent(),
            null,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChromeBg),
    ) {
        // —— Abas (estilo Chrome)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(ChromeBg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items = tabs, key = { it.id }) { tab ->
                    val active = tab.id == selectedTabId
                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .width(160.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(if (active) TabActive else TabInactive)
                            .clickable {
                                selectedTabId = tab.id
                                addressText = tab.url
                            }
                            .padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tab.title.ifBlank { "Nova aba" },
                            modifier = Modifier.weight(1f),
                            color = if (active) TextPrimary else TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = {
                                if (tabs.size == 1) {
                                    tabs[0] = BrowserTab(0, "https://www.google.com", "Nova aba")
                                    selectedTabId = 0
                                    addressText = "https://www.google.com"
                                    webView?.loadUrl("https://www.google.com")
                                } else {
                                    val index = tabs.indexOfFirst { it.id == tab.id }
                                    tabs.removeAll { it.id == tab.id }
                                    if (selectedTabId == tab.id) {
                                        val newIndex = (index - 1).coerceIn(0, tabs.lastIndex)
                                        selectedTabId = tabs[newIndex].id
                                        addressText = tabs[newIndex].url
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                "Fechar",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    val newId = (tabs.maxOfOrNull { it.id } ?: 0) + 1
                    tabs.add(BrowserTab(newId, "https://www.google.com", "Nova aba"))
                    selectedTabId = newId
                    addressText = "https://www.google.com"
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Filled.Add, "Nova aba", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
        }

        // —— Barra de endereço compacta
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(TabActive)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { webView?.takeIf { it.canGoBack() }?.goBack() },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Filled.ArrowBack, "Voltar", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { webView?.takeIf { it.canGoForward() }?.goForward() },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Filled.ArrowForward, "Avançar", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { webView?.reload() },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Filled.Refresh, "Recarregar", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(AddressBg)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    decorationBox = { inner ->
                        if (addressText.isEmpty()) {
                            Text("Pesquisar ou escrever um URL", color = TextMuted, fontSize = 13.sp)
                        }
                        inner()
                    },
                )
            }

            IconButton(
                onClick = {
                    val target = normalizeUrl(addressText)
                    webView?.loadUrl(target)
                    if (selectedIndex in tabs.indices) {
                        tabs[selectedIndex] = tabs[selectedIndex].copy(url = target)
                    }
                },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Filled.Search, "Ir", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false

                            // Escala tipo PC — não “gigante”
                            textZoom = 85
                            defaultFontSize = 14
                            minimumFontSize = 10

                            // User-Agent desktop Chrome (sites servem layout PC)
                            userAgentString =
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                    "Chrome/122.0.0.0 Safari/537.36"

                            allowFileAccess = false
                            allowContentAccess = true
                            mediaPlaybackRequiresUserGesture = false
                            loadsImagesAutomatically = true
                            blockNetworkImage = false
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = false

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (!url.isNullOrBlank()) {
                                    addressText = url
                                    if (selectedIndex in tabs.indices) {
                                        tabs[selectedIndex] = tabs[selectedIndex].copy(url = url)
                                    }
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                applyDesktopViewport(view)
                                if (selectedIndex !in tabs.indices) return
                                val title = view.title?.takeIf { it.isNotBlank() } ?: "Página"
                                tabs[selectedIndex] = tabs[selectedIndex].copy(
                                    url = url ?: tabs[selectedIndex].url,
                                    title = title,
                                )
                            }
                        }

                        webChromeClient = WebChromeClient()
                        loadUrl(selectedTab.url)
                        webView = this
                    }
                },
                update = { view ->
                    if (view.url != selectedTab.url && selectedTab.url.isNotBlank()) {
                        view.loadUrl(selectedTab.url)
                    }
                    webView = view
                },
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }
}

private fun normalizeUrl(input: String): String {
    val value = input.trim()
    if (value.isEmpty()) return "https://www.google.com"
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    if (value.contains(".") && !value.contains(" ")) return "https://$value"
    return "https://www.google.com/search?q=${URLEncoder.encode(value, "UTF-8")}"
}
