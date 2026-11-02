package com.astraedus.nudge.ui.overlay

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.astraedus.nudge.domain.webview.PauseWebviewResolvedItem
import com.astraedus.nudge.domain.webview.PauseWebviewSite
import com.astraedus.nudge.domain.webview.PauseWebviewTag
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebviewContent(
    delaySeconds: Int,
    initialItem: PauseWebviewResolvedItem?,
    allSites: List<PauseWebviewSite>,
    autoProceedOnZero: Boolean,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onSiteSelected: (String) -> Unit = {},
    onTagSelected: (siteId: String, tagId: String) -> Unit = { _, _ -> },
    appLabel: String? = null
) {
    var currentSiteId by remember(initialItem) {
        mutableStateOf(initialItem?.siteId ?: allSites.firstOrNull()?.id ?: "")
    }

    val currentSite = allSites.firstOrNull { it.id == currentSiteId } ?: allSites.firstOrNull()

    var currentTagId by remember(currentSite) {
        mutableStateOf(
            currentSite?.selectedTagId
                ?: initialItem?.tagId
                ?: currentSite?.tags?.firstOrNull()?.id
                ?: ""
        )
    }

    val currentTag: PauseWebviewTag? = currentSite?.tags?.firstOrNull { it.id == currentTagId }
        ?: currentSite?.tags?.firstOrNull()

    var siteDropdownExpanded by remember { mutableStateOf(false) }

    var remainingSeconds by remember { mutableIntStateOf(delaySeconds) }
    val completed = remember { AtomicBoolean(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds--
            }
            if (autoProceedOnZero) {
                if (completed.compareAndSet(false, true)) onComplete()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            // --- Top Bar: Level 1 (Web Site) & Level 2 (Custom Tags) + Actions ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left area: Level 1 (Website Dropdown / Pill) & Level 2 (Custom Tags)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Level 1: Site Selector (e.g. Wikipedia vs Daily Word)
                        if (allSites.isNotEmpty()) {
                            Box {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = currentSite?.name ?: "Web",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        if (allSites.size > 1) {
                                            IconButton(
                                                onClick = { siteDropdownExpanded = true },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = "Switch website",
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (allSites.size > 1) {
                                    DropdownMenu(
                                        expanded = siteDropdownExpanded,
                                        onDismissRequest = { siteDropdownExpanded = false }
                                    ) {
                                        allSites.forEach { site ->
                                            DropdownMenuItem(
                                                text = { Text(site.name) },
                                                trailingIcon = {
                                                    if (site.id == currentSiteId) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                },
                                                onClick = {
                                                    siteDropdownExpanded = false
                                                    if (site.id != currentSiteId) {
                                                        currentSiteId = site.id
                                                        val targetTag = site.tags.firstOrNull { it.id == site.selectedTagId }
                                                            ?: site.tags.firstOrNull()
                                                        currentTagId = targetTag?.id ?: ""
                                                        onSiteSelected(site.id)
                                                        targetTag?.url?.let {
                                                            hasError = false
                                                            webViewInstance?.loadUrl(it)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Level 2: Custom Tag Chips for current site (RANDOM, TODAY, etc.)
                        val tags = currentSite?.tags ?: emptyList()
                        if (tags.isNotEmpty()) {
                            tags.forEach { tag ->
                                val selected = tag.id == currentTag?.id
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            currentTagId = tag.id
                                            currentSite?.let { site ->
                                                onTagSelected(site.id, tag.id)
                                            }
                                            hasError = false
                                            webViewInstance?.loadUrl(tag.url)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = tag.tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // Top-right actions: Reload & Countdown / Ready indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                hasError = false
                                currentTag?.url?.let { webViewInstance?.loadUrl(it) }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload webview",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (remainingSeconds > 0) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${remainingSeconds}s",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Ready",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Ready",
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- WebView Body: Occupies available height, strictly clipped ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (currentTag != null) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                clipToOutline = true
                                overScrollMode = View.OVER_SCROLL_NEVER
                                isVerticalScrollBarEnabled = true
                                isHorizontalScrollBarEnabled = false
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                        hasError = false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            isLoading = false
                                            hasError = true
                                        }
                                    }
                                }
                                loadUrl(currentTag.url)
                                webViewInstance = this
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
                        },
                        modifier = Modifier
                            .matchParentSize()
                            .clipToBounds()
                            .graphicsLayer { clip = true }
                    )

                    // Thin linear progress bar at the top edge of WebView
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.TopCenter)
                                .zIndex(3f),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Error fallback
                    if (hasError) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Unable to load page",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Check your connection or verify the URL configuration.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                IconButton(
                                    onClick = {
                                        hasError = false
                                        webViewInstance?.loadUrl(currentTag.url)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry"
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No webview configured.\nAdd educational URLs in Settings.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- Bottom Bar: Exactly 2 buttons (Continue & Go Back) ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val proceedText = if (remainingSeconds > 0) {
                        "Wait ${remainingSeconds}s"
                    } else if (appLabel != null) {
                        "Continue to $appLabel"
                    } else {
                        "Continue"
                    }

                    // 1. Continue button: unlocked after timeout
                    ContinueButton(
                        onClick = {
                            if (completed.compareAndSet(false, true)) {
                                onComplete()
                            }
                        },
                        enabled = remainingSeconds <= 0,
                        text = proceedText
                    )

                    // 2. Go back button: always accessible
                    LeaveButton(
                        onClick = onCancel,
                        text = "Go Back"
                    )
                }
            }
        }
    }
}
