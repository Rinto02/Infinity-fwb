/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.internal.graphics.ColorUtils;

import com.android.systemui.res.R;
import com.android.systemui.omni.header.StatusBarHeaderMachine;
import com.android.systemui.util.LargeScreenUtils;

import com.bosphere.fadingedgelayout.FadingEdgeLayout;

import java.lang.Math;

/**
 * View that contains the top-most bits of the QS panel (primarily the status bar with date, time,
 * battery, carrier info and privacy icons) and also contains the {@link QuickQSPanel}.
 */
public class QuickStatusBarHeader extends FrameLayout
            implements StatusBarHeaderMachine.IStatusBarHeaderMachineObserver {

    private boolean mExpanded;
    private boolean mQsDisabled;

    protected QuickQSPanel mHeaderQsPanel;

    private boolean mSceneContainerEnabled;

    // QS Header
    private ImageView mQsHeaderImageView;
    private FadingEdgeLayout mQsHeaderLayout;
    private boolean mHeaderImageEnabled;
    private StatusBarHeaderMachine mStatusBarHeaderMachine;
    private Drawable mCurrentBackground;
    private int mHeaderImageHeight;
    private final Handler mHandler = new Handler();
    private boolean mOpQsLayoutEnabled = false;
    private boolean mShouldShowOpQsLayout = false;
    private ViewGroup mOpQsLayout;

    private class OmniSettingsObserver extends ContentObserver {
        OmniSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_CUSTOM_HEADER), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT), false,
                    this, UserHandle.USER_ALL);
            }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
    private OmniSettingsObserver mOmniSettingsObserver;
    public TouchAnimator mQQSContainerAnimator;

    public QuickStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        mStatusBarHeaderMachine = new StatusBarHeaderMachine(context);
        mOmniSettingsObserver = new OmniSettingsObserver(mHandler);
        mOmniSettingsObserver.observe();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);
        mQsHeaderLayout = findViewById(R.id.layout_header);
        mQsHeaderImageView = findViewById(R.id.qs_header_image_view);
        mOpQsLayout = findViewById(R.id.qs_op_header_layout);

        mQsHeaderImageView.setClipToOutline(true);

        updateSettings();
    }

    void setSceneContainerEnabled(boolean enabled) {
        mSceneContainerEnabled = enabled;
        if (mSceneContainerEnabled) {
            updateResources();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mStatusBarHeaderMachine.addObserver(this);
        mStatusBarHeaderMachine.updateEnablement();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mStatusBarHeaderMachine.removeObserver(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Only react to touches inside QuickQSPanel
        if (event.getY() > mHeaderQsPanel.getTop()) {
            return super.onTouchEvent(event);
        } else {
            return false;
        }
    }

    void updateResources() {
        Resources resources = mContext.getResources();
        int orientation = getResources().getConfiguration().orientation;
        boolean largeScreenHeaderActive = LargeScreenUtils.shouldUseLargeScreenShadeHeader(resources);

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (mQsDisabled) {
            lp.height = 0;
        } else {
            lp.height = WRAP_CONTENT;
        }
        setLayoutParams(lp);

        MarginLayoutParams qqsLP = (MarginLayoutParams) mHeaderQsPanel.getLayoutParams();
        qqsLP.topMargin = mShouldShowOpQsLayout
            ? 0
            : resources.getDimensionPixelSize(
                largeScreenHeaderActive 
                ? R.dimen.qqs_layout_margin_top 
                : R.dimen.large_screen_shade_header_min_height);
        mHeaderQsPanel.setLayoutParams(qqsLP);

        Configuration config = resources.getConfiguration();
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            mQsHeaderLayout.setVisibility(mHeaderImageEnabled ? View.VISIBLE : View.GONE);
        } else {
            mQsHeaderLayout.setVisibility(View.GONE);
        }

        if (mShouldShowOpQsLayout && !mQsDisabled) {
            mOpQsLayout.setVisibility(View.VISIBLE);

            MarginLayoutParams opQqsLP = (MarginLayoutParams) mOpQsLayout.getLayoutParams();
            int qqsMarginTop = resources.getDimensionPixelSize(
                largeScreenHeaderActive
                ? R.dimen.qqs_layout_margin_top
                : R.dimen.large_screen_shade_header_min_height);
            opQqsLP.topMargin = qqsMarginTop;
            mOpQsLayout.setLayoutParams(opQqsLP);

            float qqsExpandY = resources.getDimensionPixelSize(R.dimen.qs_header_height)
                                + resources.getDimensionPixelSize(R.dimen.qs_op_header_layout_expanded_top_margin)
                                - qqsMarginTop;
            TouchAnimator.Builder builderP = new TouchAnimator.Builder()
                .addFloat(mOpQsLayout, "translationY", 0, qqsExpandY);
            mQQSContainerAnimator = builderP.build();
        } else {
            mOpQsLayout.setVisibility(View.GONE);
        }
    }

    public void setExpanded(boolean expanded, QuickQSPanelController quickQSPanelController) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        quickQSPanelController.setExpanded(expanded);
    }

    public void setExpansion(boolean forceExpanded, float expansionFraction, float panelTranslationY) {
        if (!mShouldShowOpQsLayout) {
            return;
        }

		if (mQQSContainerAnimator != null) {
            final float keyguardExpansionFraction = forceExpanded ? 1f : expansionFraction;
			mQQSContainerAnimator.setPosition(keyguardExpansionFraction);
		}

		setAlpha(forceExpanded ? expansionFraction : 1);
	}

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        updateResources();
    }

    private void updateSettings() {
        mHeaderImageEnabled = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER, 0,
                UserHandle.USER_CURRENT) == 1;
        mOpQsLayoutEnabled = QSUtils.isOosQsLayoutEnabled(getContext());
        mShouldShowOpQsLayout = QSUtils.shouldShowOosQsLayout(getContext());

        updateHeaderImage();
        updateResources();
    }

    private void setContentMargins(View view, int marginStart, int marginEnd) {
        MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        view.setLayoutParams(lp);
    }

    @Override
    public void updateHeader(final Drawable headerImage, final boolean force) {
        post(new Runnable() {
            public void run() {
                doUpdateStatusBarCustomHeader(headerImage, force);
            }
        });
    }

    @Override
    public void disableHeader() {
        post(new Runnable() {
            public void run() {
                mCurrentBackground = null;
                mQsHeaderImageView.setVisibility(View.GONE);
                mHeaderImageEnabled = false;
                updateResources();
            }
        });
    }

    @Override
    public void refreshHeader() {
        post(new Runnable() {
            public void run() {
                doUpdateStatusBarCustomHeader(mCurrentBackground, true);
            }
        });
    }

    private void doUpdateStatusBarCustomHeader(final Drawable next, final boolean force) {
        if (next != null) {
            mQsHeaderImageView.setVisibility(View.VISIBLE);
            mCurrentBackground = next;
            setNotificationPanelHeaderBackground(next, force);
            mHeaderImageEnabled = true;
            updateResources();
        } else {
            mCurrentBackground = null;
            mQsHeaderImageView.setVisibility(View.GONE);
            mHeaderImageEnabled = false;
            updateResources();
        }
    }

    private void setNotificationPanelHeaderBackground(final Drawable dw, final boolean force) {
        if (mQsHeaderImageView.getDrawable() != null && !force) {
            Drawable[] arrayDrawable = new Drawable[2];
            arrayDrawable[0] = mQsHeaderImageView.getDrawable();
            arrayDrawable[1] = dw;

            TransitionDrawable transitionDrawable = new TransitionDrawable(arrayDrawable);
            transitionDrawable.setCrossFadeEnabled(true);
            mQsHeaderImageView.setImageDrawable(transitionDrawable);
            transitionDrawable.startTransition(1000);
        } else {
            mQsHeaderImageView.setImageDrawable(dw);
        }
        applyHeaderBackgroundShadow();
    }

    private void applyHeaderBackgroundShadow() {
        final int headerShadow = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, 0,
                UserHandle.USER_CURRENT);
        if (mCurrentBackground != null && mQsHeaderImageView.getDrawable() != null) {
            mQsHeaderImageView.setImageAlpha(255 - headerShadow);
        }
    }

    private void updateHeaderImage() {
        mHeaderImageEnabled = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER, 0,
                UserHandle.USER_CURRENT) == 1;
        int headerHeight = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, 142,
                UserHandle.USER_CURRENT);
        int bottomFadeSize = (int) Math.round(headerHeight * 0.555);

        // Set the image header size
        mHeaderImageHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 
            headerHeight, getContext().getResources().getDisplayMetrics());
        ViewGroup.MarginLayoutParams qsHeaderParams = 
            (ViewGroup.MarginLayoutParams) mQsHeaderLayout.getLayoutParams();
        qsHeaderParams.height = mHeaderImageHeight;
        mQsHeaderLayout.setLayoutParams(qsHeaderParams);

        // Set the image fade size (it has to be a 55,5% related to the main size)
        mQsHeaderLayout.setFadeSizes(0,0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 
            bottomFadeSize, getContext().getResources().getDisplayMetrics()), 0);
    }
}