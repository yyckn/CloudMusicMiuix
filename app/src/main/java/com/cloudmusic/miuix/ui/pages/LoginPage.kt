package com.cloudmusic.miuix.ui.pages

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginPage(
    onBackClick: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    var loginSuccess by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    
    BackHandler {
        onBackClick()
    }
    
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "登录",
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onBackClick() }
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { context ->
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()

                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Pixel 3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                cookieManager.flush()

                                val music163Cookies = cookieManager.getCookie("https://music.163.com") ?: ""
                                val yMusicCookies = cookieManager.getCookie("https://y.music.163.com") ?: ""

                                val allCookies = buildString {
                                    if (music163Cookies.isNotEmpty()) append(music163Cookies)
                                    if (yMusicCookies.isNotEmpty()) {
                                        if (isNotEmpty()) append("; ")
                                        append(yMusicCookies)
                                    }
                                }

                                if (allCookies.isNotEmpty() && allCookies.contains("MUSIC_U") && !loginSuccess) {
                                    loginSuccess = true
                                    onLoginSuccess(allCookies)
                                }
                            }
                        }

                        loadUrl("https://music.163.com/#/login")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
