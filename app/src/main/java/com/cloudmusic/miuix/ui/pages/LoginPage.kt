package com.cloudmusic.miuix.ui.pages

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginPage(
    onBackClick: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    var loginSuccess by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
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
                                
                                android.util.Log.d("LoginPage", "Page finished: $url")
                                
                                // Check all possible cookie domains
                                val domains = listOf(
                                    "https://music.163.com",
                                    "https://y.music.163.com",
                                    "https://y.163.com",
                                    "https://163.com"
                                )
                                
                                val allCookies = StringBuilder()
                                for (domain in domains) {
                                    val domainCookies = cookieManager.getCookie(domain)
                                    android.util.Log.d("LoginPage", "$domain cookies: $domainCookies")
                                    if (!domainCookies.isNullOrEmpty()) {
                                        if (allCookies.isNotEmpty()) allCookies.append("; ")
                                        allCookies.append(domainCookies)
                                    }
                                }
                                
                                android.util.Log.d("LoginPage", "All combined cookies: $allCookies")
                                
                                if (!loginSuccess && allCookies.contains("MUSIC_U")) {
                                    loginSuccess = true
                                    // Also set cookies to music.163.com domain for OkHttp
                                    val music163Cookies = cookieManager.getCookie("https://music.163.com") ?: ""
                                    android.util.Log.d("LoginPage", "MUSIC_U found! music163 cookies: $music163Cookies")
                                    android.util.Log.d("LoginPage", "All cookies: $allCookies")
                                    onLoginSuccess(allCookies.toString())
                                }
                            }
                        }

                        loadUrl("https://y.music.163.com/m/login")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
