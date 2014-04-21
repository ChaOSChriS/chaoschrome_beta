// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextmenu;

import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.content.browser.ContentViewCore;

/**
 * A helper class that handles generating context menus for {@link ContentViewCore}s.
 */
public class ContextMenuHelper implements OnCreateContextMenuListener, OnMenuItemClickListener {
    private long mNativeContextMenuHelper;

    private ContextMenuPopulator mPopulator;
    private ContextMenuParams mCurrentContextMenuParams;

    private ContextMenuHelper(long nativeContextMenuHelper) {
        mNativeContextMenuHelper = nativeContextMenuHelper;
    }

    @CalledByNative
    private static ContextMenuHelper create(long nativeContextMenuHelper) {
        return new ContextMenuHelper(nativeContextMenuHelper);
    }

    @CalledByNative
    private void destroy() {
        mNativeContextMenuHelper = 0;
    }

    /**
     * @param populator A {@link ContextMenuPopulator} that is responsible for managing and showing
     *                  context menus.
     */
    @CalledByNative
    private void setPopulator(ContextMenuPopulator populator) {
        mPopulator = populator;
    }

    /**
     * Starts showing a context menu for {@code view} based on {@code params}.
     * @param contentViewCore The {@link ContentViewCore} to show the menu to.
     * @param params          The {@link ContextMenuParams} that indicate what menu items to show.
     */
    @CalledByNative
    private void showContextMenu(ContentViewCore contentViewCore, ContextMenuParams params) {
        final View view = contentViewCore.getContainerView();

        if (!shouldShowMenu(params)
                || view == null
                || view.getVisibility() != View.VISIBLE
                || view.getParent() == null) {
            return;
        }

        mCurrentContextMenuParams = params;

        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        contentViewCore.setIgnoreRemainingTouchEvents();
        view.setOnCreateContextMenuListener(this);
        view.showContextMenu();
    }

    /**
     * Starts a download based on the current {@link ContextMenuParams}.
     * @param isLink Whether or not the download target is a link.
     */
    public void startContextMenuDownload(boolean isLink) {
        if (mNativeContextMenuHelper != 0) nativeOnStartDownload(mNativeContextMenuHelper, isLink);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (!shouldShowMenu(mCurrentContextMenuParams)) return;

        if (mCurrentContextMenuParams.isCustomMenu()) {
            for (int i = 0; i < mCurrentContextMenuParams.getCustomMenuSize(); i++) {
                menu.add(Menu.NONE, i, Menu.NONE, mCurrentContextMenuParams.getCustomLabelAt(i));
            }
        } else {
            assert mPopulator != null;
            mPopulator.buildContextMenu(menu, v.getContext(), mCurrentContextMenuParams);
        }

        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setOnMenuItemClickListener(this);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (mCurrentContextMenuParams.isCustomMenu()) {
            if (mNativeContextMenuHelper != 0) {
                final int action = mCurrentContextMenuParams.getCustomActionAt(item.getItemId());
                nativeOnCustomItemSelected(mNativeContextMenuHelper, action);
            }
            return true;
        } else {
            return mPopulator.onItemSelected(this, mCurrentContextMenuParams, item.getItemId());
        }
    }

    /**
     * @return The {@link ContextMenuPopulator} responsible for populating the context menu.
     */
    @VisibleForTesting
    public ContextMenuPopulator getPopulator() {
        return mPopulator;
    }

    private boolean shouldShowMenu(ContextMenuParams params) {
        // Custom menus are handled by this class and do not require a ContextMenuPopulator.
        return params.isCustomMenu() ||
                (mPopulator != null && mPopulator.shouldShowContextMenu(params));
    }

    private native void nativeOnStartDownload(long nativeContextMenuHelper, boolean isLink);
    private native void nativeOnCustomItemSelected(long nativeContextMenuHelper, int action);
}