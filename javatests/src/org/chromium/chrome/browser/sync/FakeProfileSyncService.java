// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync;

import android.content.Context;

/**
 * Fake some ProfileSyncService methods for testing.
 *
 * Only what has been needed for tests so far has been faked.
 */
public class FakeProfileSyncService extends ProfileSyncService {

    private boolean mBackendInitialized;

    public FakeProfileSyncService(Context context) {
        super(context);
    }

    @Override
    public boolean isSyncInitialized() {
        return mBackendInitialized;
    }

    public void setSyncInitialized(boolean syncInitialized) {
        mBackendInitialized = syncInitialized;
    }

    @Override
    public boolean isUsingSecondaryPassphrase() {
        return true;
    }
}