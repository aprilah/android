package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.normalizeTONSites
import com.tonapps.tonkeeper.fragment.tonconnect.auth.TCAuthFragment
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.tabBarActiveIconColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.tonconnect.entities.DConnectEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppPayloadEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.drawable.HeaderDrawable
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.getCurrentFocus
import uikit.extensions.pinToBottomInsets
import uikit.extensions.setPaddingBottom
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.webview.bridge.BridgeWebView
import java.util.UUID
import kotlin.coroutines.resume

class DAppScreen: BaseFragment(R.layout.fragment_dapp) {

    private lateinit var headerDrawable: HeaderDrawable
    private lateinit var headerView: View
    private lateinit var backView: View
    private lateinit var titleView: AppCompatTextView
    private lateinit var hostView: AppCompatTextView
    private lateinit var menuView: View
    private lateinit var closeView: View
    private lateinit var refreshView: SwipeRefreshLayout
    private lateinit var webView: BridgeWebView

    private val args: DAppArgs by lazy { DAppArgs(requireArguments()) }
    private val rootViewModel: RootViewModel by activityViewModel()
    private val dAppViewModel: DAppViewModel by viewModel { parametersOf(args.url) }

    private val webViewCallback = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.normalizeTONSites()
            if (url.scheme != "https") {
                navigation?.openURL(url.toString(), true)
                return true
            }
            return rootViewModel.processDeepLink(url, false)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            refreshView.isRefreshing = true
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            refreshView.isRefreshing = false
            if (args.title.isNullOrBlank()) {
                titleView.text = view.title
            }
        }
    }

    private val _keyboardHeightFlow = MutableStateFlow(0)
    private val keyboardHeightFlow = _keyboardHeightFlow.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.trackEventClickDApp(args.url)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerDrawable = HeaderDrawable(requireContext())

        headerView = view.findViewById(R.id.header)
        headerView.background = headerDrawable

        backView = view.findViewById(R.id.back)
        backView.setOnClickListener { back() }

        titleView = view.findViewById(R.id.title)
        if (args.title.isNullOrBlank()) {
            titleView.text = getString(Localization.loading)
        } else {
            titleView.text = args.title
        }

        hostView = view.findViewById(R.id.host)
        hostView.text = args.host

        menuView = view.findViewById(R.id.menu)
        menuView.setOnClickListener { requestMenu(it) }

        closeView = view.findViewById(R.id.close)
        closeView.setOnClickListener { finish() }

        webView = view.findViewById(R.id.web_view)

        webView.clipToPadding = false
        webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            headerDrawable.setDivider(scrollY > 0)
        }

        webView.jsBridge = DAppBridge(
            send = { rootViewModel.tonconnectBridgeEvent(requireContext(), args.url, it) },
            connect = { _, request -> tonConnectAuth(request) },
            restoreConnection = { dAppViewModel.restoreConnection(args.url) },
            disconnect = { dAppViewModel.disconnect() }
        )
        webView.loadUrl(args.url)

        refreshView = view.findViewById(R.id.refresh)
        refreshView.setColorSchemeColors(requireContext().tabBarActiveIconColor)
        refreshView.setOnRefreshListener {
            webView.reload()
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            headerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusInsets.top
            }
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            _keyboardHeightFlow.value = imeInsets.bottom
            insets
        }

        combine(
            keyboardHeightFlow,
            webView.inputFocusFlow
        ) { keyboardHeight, inputFocusRect ->
            if (0 >= keyboardHeight) {
                0f
            } else {
                val webViewHeight = webView.height
                val inputBottom = inputFocusRect.bottom.dp
                val keyboardHeightFloat = keyboardHeight.toFloat()
                val visibleHeight = webViewHeight - keyboardHeightFloat
                val coveredHeight = webViewHeight - visibleHeight
                val neededTranslation = (inputBottom + coveredHeight) - webViewHeight
                if (neededTranslation > 0) {
                    val extraPadding = 20f
                    -minOf(neededTranslation + extraPadding, keyboardHeightFloat)
                } else {
                    0f
                }
            }
        }.onEach(webView::setTranslationY).launchIn(lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView.destroy()
    }

    private fun requestMenu(view: View) {
        collectFlow(dAppViewModel.getApp()) { openMenu(view, it) }
    }

    private fun openMenu(view: View, app: DConnectEntity?) {
        val actionSheet = ActionSheet(requireContext())
        actionSheet.addItem(REFRESH_ID, Localization.refresh, UIKitIcon.ic_refresh_16)
        if (app?.enablePush == true) {
            actionSheet.addItem(MUTE_ID, Localization.mute, UIKitIcon.ic_bell_disable_16)
        }
        actionSheet.addItem(SHARE_ID, Localization.share, UIKitIcon.ic_share_16)
        actionSheet.addItem(COPY_ID, Localization.copy, UIKitIcon.ic_copy_16)
        if (app != null) {
            actionSheet.addItem(DISCONNECT_ID, Localization.disconnect, UIKitIcon.ic_disconnect_16)
        }
        actionSheet.doOnItemClick = { actionClick(it.id) }
        actionSheet.show(view)
    }

    private fun actionClick(id: Long) {
        when (id) {
            REFRESH_ID -> webView.reload()
            MUTE_ID -> dAppViewModel.mute()
            SHARE_ID -> shareLink()
            COPY_ID -> requireContext().copyToClipboard(args.url)
            DISCONNECT_ID -> dAppViewModel.disconnect()
        }
    }

    private fun shareLink() {
        val sendIntent = Intent(Intent.ACTION_SEND)
        sendIntent.putExtra(Intent.EXTRA_TEXT, args.url)
        sendIntent.type = "text/plain"
        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private suspend fun tonConnectAuth(
        request: DAppPayloadEntity
    ): String? = suspendCancellableCoroutine { continuation ->
        val id = UUID.randomUUID().toString()
        navigation?.setFragmentResultListener(id) { bundle ->
            if (bundle.containsKey(TCAuthFragment.REPLY_ARG)) {
                continuation.resume(bundle.getString(TCAuthFragment.REPLY_ARG))
            } else {
                continuation.resume(null)
            }
        }
        openAuth(id, request)
    }

    private fun openAuth(id: String, request: DAppPayloadEntity) {
        val entity = DAppRequestEntity(
            id = id,
            r = request.toJSON().toString(),
        )
        navigation?.add(TCAuthFragment.newInstance(entity, id, true))
    }

    override fun onResume() {
        super.onResume()
        webView.addClientCallback(webViewCallback)
    }

    override fun onPause() {
        super.onPause()
        webView.removeClientCallback(webViewCallback)
    }

    private fun back() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onBackPressed(): Boolean {
        back()
        return false
    }

    companion object {

        private const val REFRESH_ID = 1L
        private const val MUTE_ID = 2L
        private const val SHARE_ID = 3L
        private const val COPY_ID = 4L
        private const val DISCONNECT_ID = 5L

        fun newInstance(
            title: String? = null,
            host: String? = null,
            url: String
        ): DAppScreen {
            val mustHost = if (host.isNullOrBlank()) {
                Uri.parse(url).host
            } else {
                host
            }
            return newInstance(DAppArgs(title, mustHost, Uri.parse(url)))
        }

        fun newInstance(
            args: DAppArgs,
        ): DAppScreen {
            val fragment = DAppScreen()
            fragment.setArgs(args)
            return fragment
        }
    }
}