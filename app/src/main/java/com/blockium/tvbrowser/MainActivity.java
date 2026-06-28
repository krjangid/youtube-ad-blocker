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
            // Normal status bar — YouTube needs this to show logo/search header correctly
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
            // Hardcode the verified working mobile Chrome User Agent to bypass YouTube's WebView detection
            settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // Do NOT block network requests for ads! Blocking them causes YouTube's player to hang 
                // and wait for timeouts, which causes the "video taking a lot of time" bug.
                // We rely exclusively on the JavaScript ad skipper to skip ads instantly.
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                scheduleAdBlockInjection(view);
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

                // Hide the WebView so that the customView (which now contains the full player DOM) can handle touches
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
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 10) {
                    injectAdSkipper(view);
                }
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
            // Remove X-Requested-With to help prevent WebView detection
            Map<String, String> extraHeaders = new HashMap<>();
            extraHeaders.put("X-Requested-With", "");
            webView.loadUrl("https://m.youtube.com", extraHeaders);
        }
    }

    /** No longer needed but kept for compatibility */
    private static String stripWebViewMarker(String userAgent) {
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
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
        // Re-fit fullscreen overlay after rotation to landscape
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

    private void scheduleAdBlockInjection(WebView view) {
        injectAdSkipper(view);
        view.postDelayed(() -> injectAdSkipper(view), 1000);
        view.postDelayed(() -> injectAdSkipper(view), 3000);
        view.postDelayed(() -> injectAdSkipper(view), 6000);
    }

    private void injectAdSkipper(WebView view) {
        String js =
            "(function() {\n" +
            "  // 1. Inject Chrome globals to bypass YouTube WebView detection and restore the header/logo/search bar\n" +
            "  try {\n" +
            "    if (!window.blockiumGlobalsInjected) {\n" +
            "      window.blockiumGlobalsInjected = true;\n" +
            "      if (!window.chrome) {\n" +
            "        window.chrome = {\n" +
            "          app: {},\n" +
            "          runtime: {},\n" +
            "          loadTimes: function() {},\n" +
            "          csi: function() {}\n" +
            "        };\n" +
            "      }\n" +
            "    }\n" +
            "  } catch(e) {}\n" +
            "  \n" +
            "  // 2. Fix missing controls in fullscreen: force YouTube to request fullscreen on the player container instead of the raw video tag\n" +
            "  try {\n" +
            "    if (!window.blockiumFsFixed) {\n" +
            "      window.blockiumFsFixed = true;\n" +
            "      var origReqFs = Element.prototype.requestFullscreen || Element.prototype.webkitRequestFullscreen;\n" +
            "      if (origReqFs) {\n" +
            "        var fsOverride = function() {\n" +
            "          var container = this.closest('.html5-video-player, #player-container-id, .player-container');\n" +
            "          if (container && container !== this) {\n" +
            "            return origReqFs.call(container);\n" +
            "          }\n" +
            "          return origReqFs.call(this);\n" +
            "        };\n" +
            "        HTMLVideoElement.prototype.requestFullscreen = fsOverride;\n" +
            "        HTMLVideoElement.prototype.webkitRequestFullscreen = fsOverride;\n" +
            "      }\n" +
            "    }\n" +
            "  } catch(e) {}\n" +
            "  \n" +
            "  // 3. Inject CSS to hide ads, Premium popups, and backdrops that hijack touch events\n" +
            "  try {\n" +
            "    if (!window.blockiumStyleInjected) {\n" +
            "      var target = document.head || document.documentElement;\n" +
            "      if (target) {\n" +
            "        window.blockiumStyleInjected = true;\n" +
            "        var style = document.createElement('style');\n" +
            "        style.textContent = 'ytd-promoted-sparkles-web-renderer,ytd-companion-card-renderer," +
            "          .ytd-player-legacy-desktop-watch-ads-action-api-renderer,.ytp-ad-overlay-container," +
            "          .ytp-ad-message-container,ytd-action-companion-ad-renderer,#player-ads,#masthead-ad," +
            "          ytd-ad-slot-renderer,.ytp-ad-progress-list,ytm-promoted-sparkles-web-renderer," +
            "          ytm-companion-card-renderer,ytm-ad-slot-renderer,ytm-promoted-item,.ytm-ad-progress-list," +
            "          .ytm-mealbar-promo-renderer,.ytp-ad-player-overlay,.ytp-ad-text,.ytp-ad-image," +
            "          .ytp-ad-preview-container,.ytp-ad-action-interstitial," +
            "          ytm-upsell-dialog-renderer, ytm-dialog, .ytm-brand-interstitial," +
            "          ytm-consent-bump-v2-lightbox, ytm-bottom-sheet-renderer, ytm-promo-renderer," +
            "          iron-overlay-backdrop, ytm-overlay-backdrop, .modal-backdrop" +
            "          {display:none!important;visibility:hidden!important;pointer-events:none!important;}\\n" +
            "          ytm-app-header-renderer { display: block !important; visibility: visible !important; }\\n" +
            "          ytm-header-bar { display: flex !important; visibility: visible !important; }';\n" +
            "        target.appendChild(style);\n" +
            "      }\n" +
            "    }\n" +
            "  } catch(e) {}\n" +
            "  \n" +
            "  // 4. Ad-skipping and dialog-dismissal background loop\n" +
            "  try {\n" +
            "    if (!window.blockiumSkipInterval) {\n" +
            "      window.blockiumSkipInterval = true;\n" +
            "      var skipAd = function() {\n" +
            "        try {\n" +
            "          var adShowing = document.querySelector('.ad-showing,.ad-interrupting');\n" +
            "          document.querySelectorAll('.ytp-ad-skip-button,.ytp-ad-skip-button-modern," +
            "            .ytp-skip-ad-button,.ytp-ad-skip-button-slot,.ytp-ad-skip-button-container," +
            "            .ytm-ad-skip-button,.ytm-ad-skip-button-renderer,.ytm-ad-skip-button-container').forEach(function(b) {\n" +
            "            try { b.click(); } catch(e) {}\n" +
            "          });\n" +
            "          document.querySelectorAll('ytm-button-renderer').forEach(function(btn) {\n" +
            "            var t = btn.innerText ? btn.innerText.toLowerCase() : '';\n" +
            "            if (t.includes('skip') || t.includes('no thanks') || t.includes('dismiss') || t.includes('not now') || t.includes('छोड़ें') || t.includes('नहीं')) {\n" +
            "              try { btn.querySelector('button').click(); } catch(e) {}\n" +
            "            }\n" +
            "          });\n" +
            "          document.querySelectorAll('ytm-upsell-dialog-renderer, .ytm-brand-interstitial, ytm-dialog, ytm-bottom-sheet-renderer, iron-overlay-backdrop, ytm-overlay-backdrop, .modal-backdrop').forEach(function(d) {\n" +
            "            try { d.remove(); } catch(e) {}\n" +
            "          });\n" +
            "          var video = document.querySelector('video.html5-main-video,#movie_player video,.html5-video-player video');\n" +
            "          if (!video) return;\n" +
            "          if (adShowing) {\n" +
            "            if (!window.isAdSpeededUp) {\n" +
            "              window.originalPlaybackRate = video.playbackRate || 1;\n" +
            "              window.originalMuted = video.muted;\n" +
            "              window.isAdSpeededUp = true;\n" +
            "            }\n" +
            "            video.muted = true;\n" +
            "            video.playbackRate = 16;\n" +
            "            if (!isNaN(video.duration) && isFinite(video.duration) && video.duration > 0) {\n" +
            "              video.currentTime = video.duration - 0.05;\n" +
            "            }\n" +
            "          } else if (window.isAdSpeededUp) {\n" +
            "            video.playbackRate = window.originalPlaybackRate || 1;\n" +
            "            video.muted = window.originalMuted || false;\n" +
            "            window.isAdSpeededUp = false;\n" +
            "            if (video.paused) { try { video.play(); } catch(e) {} }\n" +
            "          }\n" +
            "        } catch(err) {}\n" +
            "      };\n" +
            "      setInterval(skipAd, 200);\n" +
            "    }\n" +
            "  } catch(e) {}\n" +
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

        if (fullscreenContainer != null && fullscreenContainer.dispatchKeyEvent(event)) {
            return true;
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
