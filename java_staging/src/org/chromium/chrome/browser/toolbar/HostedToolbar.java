// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.google.android.apps.chrome.R;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.browser.ContextualMenuBar.ActionBarDelegate;
import org.chromium.chrome.browser.CustomSelectionActionModeCallback;
import org.chromium.chrome.browser.WindowDelegate;
import org.chromium.chrome.browser.appmenu.AppMenuButtonHelper;
import org.chromium.chrome.browser.document.BrandColorUtils;
import org.chromium.chrome.browser.dom_distiller.DomDistillerServiceFactory;
import org.chromium.chrome.browser.dom_distiller.DomDistillerTabUtils;
import org.chromium.chrome.browser.ntp.NativePageFactory;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.omnibox.LocationBar;
import org.chromium.chrome.browser.omnibox.LocationBarLayout;
import org.chromium.chrome.browser.omnibox.UrlBar;
import org.chromium.chrome.browser.omnibox.UrlContainer;
import org.chromium.chrome.browser.omnibox.UrlFocusChangeListener;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.ssl.ConnectionSecurityHelperSecurityLevel;
import org.chromium.chrome.browser.tab.ChromeTab;
import org.chromium.chrome.browser.widget.TintedImageButton;
import org.chromium.components.dom_distiller.core.DomDistillerService;
import org.chromium.components.dom_distiller.core.DomDistillerUrlUtils;
import org.chromium.ui.base.WindowAndroid;

/**
 * The Toolbar layout to be used for hosted mode. This is used for both phone and tablet UIs.
 */
public class HostedToolbar extends ToolbarLayout implements LocationBar {
    private UrlBar mUrlBar;
    private ImageView mSecurityButton;
    private ImageButton mCustomActionButton;
    private int mSecurityIconType;
    private boolean mUseDarkColors;
    private UrlContainer mUrlContainer;
    private TintedImageButton mBackButton;
    private Animator mSecurityButtonShowAnimator;
    private boolean mBackgroundColorSet;

    /**
     * Constructor for getting this class inflated from an xml layout file.
     */
    public HostedToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setBackground(new ColorDrawable(getResources().getColor(R.color.default_primary_color)));
        mUrlBar = (UrlBar) findViewById(R.id.url_bar);
        mUrlBar.setHint("");
        mUrlBar.setDelegate(this);
        mUrlBar.setEnabled(false);
        mUrlContainer = (UrlContainer) findViewById(R.id.url_container);
        mSecurityButton = (ImageButton) findViewById(R.id.security_button);
        mSecurityIconType = ConnectionSecurityHelperSecurityLevel.NONE;
        mCustomActionButton = (ImageButton) findViewById(R.id.action_button);
        mBackButton = (TintedImageButton) findViewById(R.id.back_button);
        mSecurityButtonShowAnimator = ObjectAnimator.ofFloat(mSecurityButton, ALPHA, 1);
        mSecurityButtonShowAnimator
                .setDuration(ToolbarPhone.URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
    }

    @Override
    public void initialize(ToolbarDataProvider toolbarDataProvider,
            ToolbarTabController tabController, AppMenuButtonHelper appMenuButtonHelper) {
        super.initialize(toolbarDataProvider, tabController, appMenuButtonHelper);
        updateVisualsForState();
    }

    @Override
    public void setHostedBackClickHandler(OnClickListener listener) {
        mBackButton.setOnClickListener(listener);
    }

    @Override
    public void addCustomActionButton(Bitmap buttonSource, OnClickListener listener) {
        mCustomActionButton.setImageDrawable(new BitmapDrawable(getResources(), buttonSource));
        mCustomActionButton.setOnClickListener(listener);
        mCustomActionButton.setVisibility(VISIBLE);
    }

    @Override
    public ChromeTab getCurrentTab() {
        return ChromeTab.fromTab(getToolbarDataProvider().getTab());
    }

    @Override
    public boolean showingOriginalUrlForPreview() {
        return false;
    }

    @Override
    public boolean shouldEmphasizeHttpsScheme() {
        return !mUseDarkColors;
    }

    @Override
    public void setUrlToPageUrl() {
        if (getCurrentTab() == null) {
            mUrlContainer.setUrlText(null, null, "");
            return;
        }

        String url = getCurrentTab().getUrl().trim();

        if (NativePageFactory.isNativePageUrl(url, getCurrentTab().isIncognito())) {
            // Don't show anything for Chrome URLs.
            mUrlContainer.setUrlText(null, null, "");
            return;
        }
        String displayText = getToolbarDataProvider().getText();
        Pair<String, String> urlText = LocationBarLayout.splitPathFromUrlDisplayText(displayText);
        displayText = urlText.first;
        String path = urlText.second;

        if (DomDistillerUrlUtils.isDistilledPage(url)) {
            if (isStoredArticle(url)) {
                Profile profile = getCurrentTab().getProfile();
                DomDistillerService domDistillerService =
                        DomDistillerServiceFactory.getForProfile(profile);
                String originalUrl = domDistillerService.getUrlForEntry(
                        DomDistillerUrlUtils.getValueForKeyInUrl(url, "entry_id"));
                displayText =
                        DomDistillerTabUtils.getFormattedUrlFromOriginalDistillerUrl(originalUrl);
            } else if (DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(url) != null) {
                String originalUrl = DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(url);
                displayText =
                        DomDistillerTabUtils.getFormattedUrlFromOriginalDistillerUrl(originalUrl);
            }
        }

        if (mUrlContainer.setUrlText(displayText, path, url)) {
            mUrlBar.deEmphasizeUrl();
            mUrlBar.emphasizeUrl();
        }
    }

    private boolean isStoredArticle(String url) {
        DomDistillerService domDistillerService =
                DomDistillerServiceFactory.getForProfile(Profile.getLastUsedProfile());
        String entryIdFromUrl = DomDistillerUrlUtils.getValueForKeyInUrl(url, "entry_id");
        if (TextUtils.isEmpty(entryIdFromUrl)) return false;
        return domDistillerService.hasEntry(entryIdFromUrl);
    }

    @Override
    public void updateLoadingState(boolean updateUrl) {
        updateSecurityIcon(getSecurityLevel());
    }

    @Override
    public void updateVisualsForState() {
        updateSecurityIcon(getSecurityLevel());
        ColorStateList colorStateList = getResources().getColorStateList(mUseDarkColors
                ? R.color.dark_mode_tint : R.color.light_mode_tint);
        mMenuButton.setTint(colorStateList);
        mBackButton.setTint(colorStateList);
        mUrlContainer.setUseDarkTextColors(mUseDarkColors);

        if (getProgressBar() != null) {
            int progressBarResource = !mUseDarkColors
                    ? R.drawable.progress_bar_white : R.drawable.progress_bar;
            getProgressBar().setProgressDrawable(
                    ApiCompatibilityUtils.getDrawable(getResources(), progressBarResource));
        }
    }

    @Override
    public void setMenuButtonHelper(final AppMenuButtonHelper helper) {
        mMenuButton.setOnTouchListener(new OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return helper.onTouch(v, event);
            }
        });
        mMenuButton.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                    return helper.onEnterKeyPress(view);
                }
                return false;
            }
        });
    }

    @Override
    public View getMenuAnchor() {
        return mMenuButton;
    }

    @Override
    public ColorDrawable getBackground() {
        return (ColorDrawable) super.getBackground();
    }

    @Override
    public void initializeControls(WindowDelegate windowDelegate, ActionBarDelegate delegate,
            WindowAndroid windowAndroid) {
    }

    private int getSecurityLevel() {
        if (getCurrentTab() == null) return ConnectionSecurityHelperSecurityLevel.NONE;
        return getCurrentTab().getSecurityLevel();
    }

    @Override
    public void updateSecurityIcon(int securityLevel) {
        // ImageView#setImageResource is no-op if given resource is the current one.
        mSecurityButton.setImageResource(LocationBarLayout.getSecurityIconResource(
                securityLevel, !shouldEmphasizeHttpsScheme()));

        if (mSecurityIconType == securityLevel) return;
        mSecurityIconType = securityLevel;

        if (securityLevel == ConnectionSecurityHelperSecurityLevel.NONE) {
            // TODO(yusufo): Add an animator for hiding as well.
            mSecurityButton.setVisibility(GONE);
        } else {
            if (mSecurityButtonShowAnimator.isRunning()) mSecurityButtonShowAnimator.cancel();
            mSecurityButton.setVisibility(VISIBLE);
            mSecurityButtonShowAnimator.start();
            mUrlBar.deEmphasizeUrl();
        }
        mUrlBar.emphasizeUrl();
        mUrlBar.invalidate();
    }

    /**
     * For extending classes to override and carry out the changes related with the primary color
     * for the current tab changing.
     */
    @Override
    protected void onPrimaryColorChanged() {
        if (mBackgroundColorSet) return;
        int primaryColor = getToolbarDataProvider().getPrimaryColor();
        getBackground().setColor(primaryColor);
        mUseDarkColors = !BrandColorUtils.shouldUseLightDrawablesForToolbar(primaryColor);
        updateVisualsForState();
        mBackgroundColorSet = true;
    }

    @Override
    protected void onNavigatedToDifferentPage() {
        super.onNavigatedToDifferentPage();
        mUrlContainer.setTrailingTextVisible(true);
    }

    @Override
    public void setLoadProgress(int progress) {
        super.setLoadProgress(progress);
        if (progress == 100) mUrlContainer.setTrailingTextVisible(false);
    }

    @Override
    public View getContainerView() {
        return this;
    }

    @Override
    public void setDefaultTextEditActionModeCallback(CustomSelectionActionModeCallback callback) {
        mUrlBar.setCustomSelectionActionModeCallback(callback);
    }

    private void updateLayoutParams() {
        int startMargin = 0;
        int urlContainerChildIndex = -1;
        for (int i = 0; i < getChildCount(); i++) {
            View childView = getChildAt(i);
            if (childView.getVisibility() != GONE) {
                LayoutParams childLayoutParams = (LayoutParams) childView.getLayoutParams();
                if (ApiCompatibilityUtils.getMarginStart(childLayoutParams) != startMargin) {
                    ApiCompatibilityUtils.setMarginStart(childLayoutParams, startMargin);
                    childView.setLayoutParams(childLayoutParams);
                }
                if (childView == mUrlContainer) {
                    urlContainerChildIndex = i;
                    break;
                }
                int widthMeasureSpec;
                int heightMeasureSpec;
                if (childLayoutParams.width == LayoutParams.WRAP_CONTENT) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                            getMeasuredWidth(), MeasureSpec.AT_MOST);
                } else if (childLayoutParams.width == LayoutParams.MATCH_PARENT) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                            getMeasuredWidth(), MeasureSpec.EXACTLY);
                } else {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                            childLayoutParams.width, MeasureSpec.EXACTLY);
                }
                if (childLayoutParams.height == LayoutParams.WRAP_CONTENT) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                            getMeasuredHeight(), MeasureSpec.AT_MOST);
                } else if (childLayoutParams.height == LayoutParams.MATCH_PARENT) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                            getMeasuredHeight(), MeasureSpec.EXACTLY);
                } else {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                            childLayoutParams.height, MeasureSpec.EXACTLY);
                }
                childView.measure(widthMeasureSpec, heightMeasureSpec);
                startMargin += childView.getMeasuredWidth();
            }
        }

        assert urlContainerChildIndex != -1;
        int urlContainerMarginEnd = 0;
        for (int i = urlContainerChildIndex + 1; i < getChildCount(); i++) {
            View childView = getChildAt(i);
            if (childView.getVisibility() != GONE) {
                urlContainerMarginEnd += childView.getMeasuredWidth();
            }
        }
        LayoutParams urlLayoutParams = (LayoutParams) mUrlContainer.getLayoutParams();

        if (ApiCompatibilityUtils.getMarginEnd(urlLayoutParams) != urlContainerMarginEnd) {
            ApiCompatibilityUtils.setMarginEnd(urlLayoutParams, urlContainerMarginEnd);
            mUrlContainer.setLayoutParams(urlLayoutParams);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateLayoutParams();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public LocationBar getLocationBar() {
        return this;
    }

    // Toolbar and LocationBar calls that are not relevant here.

    @Override
    public void setToolbarDataProvider(ToolbarDataProvider model) {
        assert model.equals(getToolbarDataProvider());
    }

    @Override
    public void onUrlPreFocusChanged(boolean gainFocus) {
    }

    @Override
    public void setUrlFocusChangeListener(UrlFocusChangeListener listener) { }

    @Override
    public void setUrlBarFocus(boolean shouldBeFocused) { }

    @Override
    public long getFirstUrlBarFocusTime() {
        return 0;
    }

    @Override
    public void setIgnoreURLBarModification(boolean ignore) {
    }

    @Override
    public void hideSuggestions() {
    }

    @Override
    public void updateMicButtonState() {
    }

    @Override
    public void onTabLoadingNTP(NewTabPage ntp) {
    }

    @Override
    public void setAutocompleteProfile(Profile profile) {
    }

    @Override
    public void backKeyPressed() { }
}
