package com.blockium.tvbrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private WebView webView;
    private FrameLayout rootContainer;
    private boolean isTV;
    private FrameLayout fullscreenContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int originalSystemUiVisibility;
    private int originalOrientation;

    private static final Set<String> AD_DOMAINS = new HashSet<>(Arrays.asList(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "pubads.g.doubleclick.net",
        "adservice.google.com",
        "adservice.google.co.in",
        "partnerad.l.doubleclick.net"
    ));

    private static final String[] AD_PATH_PATTERNS = {
        "/pagead/",
        "/ptracking"
    };

    private static final int IMMERSIVE_FLAGS =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        setContentView(R.layout.activity_main);

        rootContainer = findViewById(R.id.rootContainer);
        webView = findViewById(R.id.webView);

        isTV = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);

        if (!isTV) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (isTV) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            settings.setUserAgentString(
                "Mozilla/5.0 (PS4; Leanback Shell) Gecko/20100101 Firefox/65.0 LeanbackShell/01.00.01.75 Sony PS4/ (PS4, , no, CH)"
            );
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!isTV) {
                    // Mobile: Disable network-level adblocking to prevent the player from hanging on timeouts
                    return super.shouldInterceptRequest(view, request);
                }

                String url = request.getUrl().toString().toLowerCase();
                for (String domain : AD_DOMAINS) {
                    if (url.contains(domain)) {
                        return createEmptyResource();
                    }
                }
                for (String pattern : AD_PATH_PATTERNS) {
                    if (url.contains(pattern)) {
                        return createEmptyResource();
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject the ad skipper and cleanup styles when the page finishes loading
                injectAdSkipper(view);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenContainer != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                customViewCallback = callback;
                originalOrientation = getRequestedOrientation();
                originalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();

                webView.setVisibility(View.GONE);

                fullscreenContainer = new FrameLayout(MainActivity.this);
                fullscreenContainer.setBackgroundColor(Color.BLACK);
                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ));
                fullscreenContainer.setFocusable(true);
                fullscreenContainer.setFocusableInTouchMode(true);

                ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
                decorView.addView(fullscreenContainer, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ));

                if (!isTV) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                }

                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                decorView.setSystemUiVisibility(IMMERSIVE_FLAGS);
                fullscreenContainer.requestFocus();
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenView();
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                android.util.Log.d("WebViewConsole", consoleMessage.message() + " -- From line "
                                     + consoleMessage.lineNumber() + " of "
                                     + consoleMessage.sourceId());
                return true;
            }
        });

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();

        if (isTV) {
            webView.loadUrl("https://www.youtube.com/tv");
        } else {
            // Load standard youtube.com. The server-side redirect to m.youtube.com strips the X-Requested-With header,
            // which prevents YouTube from detecting the WebView and hiding the logo/search bar!
            webView.loadUrl("https://www.youtube.com");
        }
    }

    private void hideFullscreenView() {
        if (fullscreenContainer == null) {
            return;
        }

        ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
        decorView.removeView(fullscreenContainer);
        fullscreenContainer = null;

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        decorView.setSystemUiVisibility(originalSystemUiVisibility);

        webView.setVisibility(View.VISIBLE);

        if (!isTV) {
            setRequestedOrientation(originalOrientation);
        }

        webView.requestFocus();

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (fullscreenContainer != null) {
            fullscreenContainer.requestLayout();
            getWindow().getDecorView().setSystemUiVisibility(IMMERSIVE_FLAGS);
        }
    }

    private WebResourceResponse createEmptyResource() {
        return new WebResourceResponse(
            "text/plain",
            "utf-8",
            404,
            "Not Found",
            new HashMap<String, String>(),
            new ByteArrayInputStream("".getBytes())
        );
    }

    private void injectAdSkipper(WebView view) {
        String js = "javascript:(function() {\n" +
                "  if (window.blockiumActive) return;\n" +
                "  window.blockiumActive = true;\n" +
                "  \n" +
                "  // 1. Visual cleanup CSS (hides sponsor grids, overlays, ads, and overlay dialogs)\n" +
                "  const style = document.createElement('style');\n" +
                "  style.textContent = `\n" +
                "    ytd-promoted-sparkles-web-renderer, ytd-companion-card-renderer,\n" +
                "    .ytd-player-legacy-desktop-watch-ads-action-api-renderer, \n" +
                "    .ytp-ad-overlay-container, .ytp-ad-message-container,\n" +
                "    ytd-action-companion-ad-renderer, #player-ads, #masthead-ad,\n" +
                "    ytd-ad-slot-renderer, .ytp-ad-progress-list,\n" +
                "    ytm-promoted-sparkles-web-renderer, ytm-companion-card-renderer,\n" +
                "    ytm-ad-slot-renderer, ytm-promoted-item, .ytm-ad-progress-list,\n" +
                "    ytm-inline-ad-renderer, .ytm-inline-ad-renderer,\n" +
                "    ytm-playlist-bar, .ytm-playlist-bar, ytm-playlist-bar-renderer,\n" +
                "    ytm-mealbar-promo-renderer, .ytm-mealbar-promo-renderer,\n" +
                "    ytm-upsell-dialog-renderer, ytm-dialog, .ytm-brand-interstitial,\n" +
                "    ytm-consent-bump-v2-lightbox, ytm-bottom-sheet-renderer, ytm-promo-renderer,\n" +
                "    iron-overlay-backdrop, ytm-overlay-backdrop, .modal-backdrop,\n" +
                "    .ad-showing video, .ad-showing .html5-video-container,\n" +
                "    .ad-showing .ytp-ad-player-overlay, .ad-showing .video-ads,\n" +
                "    .ad-interrupting video, .ad-interrupting .html5-video-container {\n" +
                "      display: none !important; visibility: hidden !important; opacity: 0 !important; pointer-events: none !important;\n" +
                "    }\n" +
                "  `;\n" +
                "  document.head.appendChild(style);\n" +
                "  \n" +
                "  // 2. Fix missing controls in fullscreen: force YouTube to request fullscreen on the player container instead of the video tag\n" +
                "  var origReqFs = Element.prototype.requestFullscreen || Element.prototype.webkitRequestFullscreen;\n" +
                "  if (origReqFs) {\n" +
                "    var fsOverride = function() {\n" +
                "      var container = this.closest('.html5-video-player, #player-container-id, .player-container');\n" +
                "      if (container && container !== this) {\n" +
                "        return origReqFs.call(container);\n" +
                "      }\n" +
                "      return origReqFs.call(this);\n" +
                "    };\n" +
                "    HTMLVideoElement.prototype.requestFullscreen = fsOverride;\n" +
                "    HTMLVideoElement.prototype.webkitRequestFullscreen = fsOverride;\n" +
                "  }\n" +
                "  \n" +
                "  // 3. Auto-skip ads and dismiss popups\n" +
                "  const skipAd = function() {\n" +
                "    const adShowing = document.querySelector('.ad-showing, .ad-interrupting');\n" +
                "    if (adShowing) {\n" +
                "      const skipButtons = document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-slot, .ytm-ad-skip-button, .ytm-ad-skip-button-renderer, .ytm-ad-skip-button-container');\n" +
                "      if (skipButtons && skipButtons.length > 0) {\n" +
                "        skipButtons.forEach(b => b.click());\n" +
                "      }\n" +
                "      \n" +
                "      const video = document.querySelector('video.html5-main-video');\n" +
                "      if (video) {\n" +
                "        if (!window.isAdSpeededUp) {\n" +
                "          if (video.playbackRate !== 16) window.originalPlaybackRate = video.playbackRate;\n" +
                "          window.originalMuted = video.muted;\n" +
                "          window.isAdSpeededUp = true;\n" +
                "        }\n" +
                "        video.muted = true;\n" +
                "        video.playbackRate = 16;\n" +
                "        if (!isNaN(video.duration) && isFinite(video.duration)) {\n" +
                "          video.currentTime = video.duration;\n" +
                "        } else {\n" +
                "          video.currentTime = 9999;\n" +
                "        }\n" +
                "      }\n" +
                "    } else {\n" +
                "      const video = document.querySelector('video.html5-main-video');\n" +
                "      if (video && window.isAdSpeededUp) {\n" +
                "        video.playbackRate = window.originalPlaybackRate || 1;\n" +
                "        video.muted = window.originalMuted || false;\n" +
                "        window.isAdSpeededUp = false;\n" +
                "      }\n" +
                "    }\n" +
                "    \n" +
                "    // Dismiss overlay popups that pause the video\n" +
                "    document.querySelectorAll('ytm-upsell-dialog-renderer, .ytm-brand-interstitial, ytm-dialog, ytm-bottom-sheet-renderer').forEach(function(d) {\n" +
                "      try { d.remove(); } catch(e) {}\n" +
                "    });\n" +
                "  };\n" +
                "  \n" +
                "  setInterval(skipAd, 250);\n" +
                "  \n" +
                (isTV ? "" :
                "  // 4. Setup Swipe-Down gesture touch listener on Mobile player\n" +
                "  const setupSwipeDown = function() {\n" +
                "    const player = document.querySelector('#movie_player, .html5-video-player');\n" +
                "    if (player && !player.dataset.swipeHooked) {\n" +
                "      player.dataset.swipeHooked = 'true';\n" +
                "      let startX = 0;\n" +
                "      let startY = 0;\n" +
                "      player.addEventListener('touchstart', function(e) {\n" +
                "        startX = e.touches[0].clientX;\n" +
                "        startY = e.touches[0].clientY;\n" +
                "      }, { passive: true });\n" +
                "      player.addEventListener('touchend', function(e) {\n" +
                "        const endX = e.changedTouches[0].clientX;\n" +
                "        const endY = e.changedTouches[0].clientY;\n" +
                "        const diffX = endX - startX;\n" +
                "        const diffY = endY - startY;\n" +
                "        if (diffY > 80 && Math.abs(diffY) > Math.abs(diffX) * 1.5) {\n" +
                "          window.history.back();\n" +
                "        }\n" +
                "      }, { passive: true });\n" +
                "    }\n" +
                "  };\n" +
                "  setInterval(setupSwipeDown, 500);\n" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (fullscreenContainer != null) {
                hideFullscreenView();
                return true;
            }
            if (isTV && webView.dispatchKeyEvent(event)) {
                return true;
            }
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
