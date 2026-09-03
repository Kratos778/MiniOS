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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserApp() {

    val context = LocalContext.current

    val tabs = remember {
        mutableStateListOf(
            BrowserTab(
                id = 0,
                url = "https://www.google.com",
                title = "Nova aba",
            )
        )
    }

    var selectedTabId by remember {
        mutableIntStateOf(0)
    }

    var addressText by remember {
        mutableStateOf("https://www.google.com")
    }

    var webView by remember {
        mutableStateOf<WebView?>(null)
    }

    val selectedIndex = tabs
        .indexOfFirst { it.id == selectedTabId }
        .coerceAtLeast(0)

    val selectedTab = tabs
        .getOrNull(selectedIndex)
        ?: tabs.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
    ) {

        // ─────────────────────────────────────────────
        // TAB BAR
        // ─────────────────────────────────────────────

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(Color(0xFF161B22)),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {

                items(
                    items = tabs,
                    key = { it.id },
                ) { tab ->

                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .width(150.dp)
                            .background(
                                if (tab.id == selectedTabId) {
                                    Color(0xFF21262D)
                                } else {
                                    Color(0xFF161B22)
                                },
                                RoundedCornerShape(
                                    topStart = 7.dp,
                                    topEnd = 7.dp,
                                ),
                            )
                            .clickable {

                                selectedTabId = tab.id
                                addressText = tab.url
                            }
                            .padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {

                        Text(
                            text = tab.title
                                .ifBlank { "Nova aba" },
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFD0D7DE),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        IconButton(
                            onClick = {

                                if (tabs.size == 1) {

                                    tabs[0] = BrowserTab(
                                        id = 0,
                                        url = "https://www.google.com",
                                        title = "Nova aba",
                                    )

                                    selectedTabId = 0
                                    addressText =
                                        "https://www.google.com"

                                } else {

                                    val index =
                                        tabs.indexOfFirst {
                                            it.id == tab.id
                                        }

                                    tabs.removeAll {
                                        it.id == tab.id
                                    }

                                    if (selectedTabId == tab.id) {

                                        val newIndex =
                                            (index - 1)
                                                .coerceIn(
                                                    0,
                                                    tabs.lastIndex,
                                                )

                                        selectedTabId =
                                            tabs[newIndex].id

                                        addressText =
                                            tabs[newIndex].url
                                    }
                                }
                            },
                            modifier = Modifier
                                .width(30.dp)
                                .height(30.dp),
                        ) {

                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Fechar",
                                tint = Color(0xFF8B949E),
                            )
                        }
                    }
                }
            }

            // Nova aba
            IconButton(
                onClick = {

                    val newId =
                        (tabs.maxOfOrNull { it.id } ?: 0) + 1

                    tabs.add(
                        BrowserTab(
                            id = newId,
                            url = "https://www.google.com",
                            title = "Nova aba",
                        )
                    )

                    selectedTabId = newId
                    addressText =
                        "https://www.google.com"
                },
                modifier = Modifier
                    .width(38.dp)
                    .height(38.dp),
            ) {

                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Nova aba",
                    tint = Color(0xFFC9D1D9),
                )
            }
        }

        // ─────────────────────────────────────────────
        // TOOLBAR
        // ─────────────────────────────────────────────

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(Color(0xFF0D1117))
                .padding(
                    horizontal = 6.dp,
                    vertical = 5.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            IconButton(
                onClick = {
                    webView
                        ?.takeIf { it.canGoBack() }
                        ?.goBack()
                },
                modifier = Modifier
                    .width(36.dp)
                    .height(36.dp),
            ) {

                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color(0xFFC9D1D9),
                )
            }

            IconButton(
                onClick = {
                    webView
                        ?.takeIf { it.canGoForward() }
                        ?.goForward()
                },
                modifier = Modifier
                    .width(36.dp)
                    .height(36.dp),
            ) {

                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = "Avançar",
                    tint = Color(0xFFC9D1D9),
                )
            }

            IconButton(
                onClick = {
                    webView?.reload()
                },
                modifier = Modifier
                    .width(36.dp)
                    .height(36.dp),
            ) {

                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Recarregar",
                    tint = Color(0xFFC9D1D9),
                )
            }

            Spacer(
                modifier = Modifier.width(5.dp)
            )

            // Barra de endereço compacta
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(
                        Color(0xFF161B22),
                        RoundedCornerShape(17.dp),
                    )
                    .padding(
                        horizontal = 14.dp,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {

                androidx.compose.foundation.text.BasicTextField(
                    value = addressText,
                    onValueChange = {
                        addressText = it
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFFC9D1D9),
                        fontSize = 12.sp,
                    ),
                    decorationBox = { innerTextField ->

                        if (addressText.isEmpty()) {

                            Text(
                                text = "Pesquisar ou introduzir endereço",
                                color = Color(0xFF6E7681),
                                fontSize = 12.sp,
                            )
                        }

                        innerTextField()
                    },
                )
            }

            Spacer(
                modifier = Modifier.width(4.dp)
            )

            IconButton(
                onClick = {

                    val target =
                        normalizeUrl(addressText)

                    webView?.loadUrl(target)

                    if (selectedIndex in tabs.indices) {

                        tabs[selectedIndex] =
                            tabs[selectedIndex].copy(
                                url = target,
                            )
                    }
                },
                modifier = Modifier
                    .width(38.dp)
                    .height(36.dp),
            ) {

                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Abrir",
                    tint = Color(0xFFC9D1D9),
                )
            }
        }

        // ─────────────────────────────────────────────
        // WEB CONTENT
        // ─────────────────────────────────────────────

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),

                factory = { ctx ->

                    WebView(ctx).apply {

                        layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )

                        settings.apply {

                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true

                            cacheMode =
                                WebSettings.LOAD_DEFAULT

                            builtInZoomControls = false
                            displayZoomControls = false

                            useWideViewPort = true
                            loadWithOverviewMode = true

                            allowFileAccess = false
                            allowContentAccess = true

                            mediaPlaybackRequiresUserGesture =
                                true

                            setSupportZoom(true)
                        }

                        CookieManager
                            .getInstance()
                            .setAcceptCookie(true)

                        CookieManager
                            .getInstance()
                            .setAcceptThirdPartyCookies(
                                this,
                                true,
                            )

                        webViewClient =
                            object : WebViewClient() {

                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    return false
                                }

                                override fun onPageStarted(
                                    view: WebView,
                                    url: String?,
                                    favicon: Bitmap?,
                                ) {

                                    super.onPageStarted(
                                        view,
                                        url,
                                        favicon,
                                    )

                                    if (!url.isNullOrBlank()) {

                                        addressText = url

                                        if (
                                            selectedIndex in tabs.indices
                                        ) {

                                            tabs[selectedIndex] =
                                                tabs[selectedIndex]
                                                    .copy(
                                                        url = url,
                                                    )
                                        }
                                    }
                                }

                                override fun onPageFinished(
                                    view: WebView,
                                    url: String?,
                                ) {

                                    super.onPageFinished(
                                        view,
                                        url,
                                    )

                                    if (
                                        selectedIndex !in tabs.indices
                                    ) {
                                        return
                                    }

                                    val title =
                                        view.title
                                            ?.takeIf {
                                                it.isNotBlank()
                                            }
                                            ?: "Página"

                                    tabs[selectedIndex] =
                                        tabs[selectedIndex]
                                            .copy(
                                                url = url
                                                    ?: tabs[
                                                        selectedIndex
                                                    ].url,
                                                title = title,
                                            )
                                }
                            }

                        webChromeClient =
                            WebChromeClient()

                        loadUrl(selectedTab.url)

                        webView = this
                    }
                },

                update = { view ->

                    if (
                        view.url != selectedTab.url &&
                        selectedTab.url.isNotBlank()
                    ) {
                        view.loadUrl(selectedTab.url)
                    }

                    webView = view
                },
            )
        }
    }

    // ─────────────────────────────────────────────
    // CLEANUP
    // ─────────────────────────────────────────────

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

private fun normalizeUrl(
    input: String,
): String {

    val value = input.trim()

    if (value.isEmpty()) {
        return "https://www.google.com"
    }

    if (
        value.startsWith("http://") ||
        value.startsWith("https://")
    ) {
        return value
    }

    if (
        value.contains(".") &&
        !value.contains(" ")
    ) {
        return "https://$value"
    }

    return "https://www.google.com/search?q=${
        URLEncoder.encode(
            value,
            "UTF-8",
        )
    }"
}
