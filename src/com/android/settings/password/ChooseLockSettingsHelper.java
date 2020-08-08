/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.password;

import static com.android.settings.Utils.SETTINGS_PACKAGE_NAME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.IntentSender;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.SetupWizardUtils;
import com.android.settings.Utils;
import com.android.settings.core.SubSettingLauncher;

import com.google.android.setupcompat.util.WizardManagerHelper;

public final class ChooseLockSettingsHelper {

    private static final String TAG = "ChooseLockSettingsHelper";

    public static final String EXTRA_KEY_TYPE = "type";
    public static final String EXTRA_KEY_PASSWORD = "password";
    public static final String EXTRA_KEY_RETURN_CREDENTIALS = "return_credentials";
    public static final String EXTRA_KEY_HAS_CHALLENGE = "has_challenge";
    public static final String EXTRA_KEY_CHALLENGE = "challenge";
    // Gatekeeper HardwareAuthToken
    public static final String EXTRA_KEY_CHALLENGE_TOKEN = "hw_auth_token";
    public static final String EXTRA_KEY_FOR_FINGERPRINT = "for_fingerprint";
    public static final String EXTRA_KEY_FOR_FACE = "for_face";
    public static final String EXTRA_KEY_FOR_CHANGE_CRED_REQUIRED_FOR_BOOT = "for_cred_req_boot";
    public static final String EXTRA_KEY_FOREGROUND_ONLY = "foreground_only";
    public static final String EXTRA_KEY_REQUEST_GK_PW = "request_gk_pw";
    // Gatekeeper password, which can subsequently be used to generate Gatekeeper
    // HardwareAuthToken(s) via LockSettingsService#verifyGatekeeperPassword
    public static final String EXTRA_KEY_GK_PW = "gk_pw";

    /**
     * When EXTRA_KEY_UNIFICATION_PROFILE_CREDENTIAL and EXTRA_KEY_UNIFICATION_PROFILE_ID are
     * provided to ChooseLockGeneric as fragment arguments {@link SubSettingLauncher#setArguments},
     * at the end of the password change flow, the supplied profile user
     * (EXTRA_KEY_UNIFICATION_PROFILE_ID) will be unified to its parent. The current profile
     * password is supplied by EXTRA_KEY_UNIFICATION_PROFILE_CREDENTIAL.
     */
    public static final String EXTRA_KEY_UNIFICATION_PROFILE_ID = "unification_profile_id";
    public static final String EXTRA_KEY_UNIFICATION_PROFILE_CREDENTIAL =
            "unification_profile_credential";

    /**
     * Intent extra for passing the requested min password complexity to later steps in the set new
     * screen lock flow.
     */
    public static final String EXTRA_KEY_REQUESTED_MIN_COMPLEXITY = "requested_min_complexity";

    /**
     * Intent extra for passing the label of the calling app to later steps in the set new screen
     * lock flow.
     */
    public static final String EXTRA_KEY_CALLER_APP_NAME = "caller_app_name";

    /**
     * Intent extra indicating that the calling app is an admin, such as a Device Adimn, Device
     * Owner, or Profile Owner.
     */
    public static final String EXTRA_KEY_IS_CALLING_APP_ADMIN = "is_calling_app_admin";

    /**
     * When invoked via {@link ConfirmLockPassword.InternalActivity}, this flag
     * controls if we relax the enforcement of
     * {@link Utils#enforceSameOwner(android.content.Context, int)}.
     */
    public static final String EXTRA_KEY_ALLOW_ANY_USER = "allow_any_user";

    @VisibleForTesting @NonNull LockPatternUtils mLockPatternUtils;
    @NonNull private final Activity mActivity;
    @Nullable private final Fragment mFragment;
    @NonNull private final Builder mBuilder;

    private ChooseLockSettingsHelper(@NonNull Builder builder, @NonNull Activity activity,
            @Nullable Fragment fragment) {
        mBuilder = builder;
        mActivity = activity;
        mFragment = fragment;
        mLockPatternUtils = new LockPatternUtils(activity);
    }

    public static class Builder {
        @NonNull private final Activity mActivity;
        @Nullable private Fragment mFragment;

        private int mRequestCode;
        @Nullable private CharSequence mTitle;
        @Nullable private CharSequence mHeader;
        @Nullable private CharSequence mDescription;
        @Nullable private CharSequence mAlternateButton;
        private boolean mReturnCredentials;
        private boolean mExternal;
        private boolean mForegroundOnly;
        // ChooseLockSettingsHelper will determine the caller's userId if none provided.
        private int mUserId;
        private boolean mAllowAnyUserId;
        // The challenge can be 0, which is different than "no challenge"
        @Nullable Long mChallenge;
        boolean mRequestGatekeeperPassword;

        public Builder(@NonNull Activity activity) {
            mActivity = activity;
            mUserId = Utils.getCredentialOwnerUserId(mActivity);
        }

        public Builder(@NonNull Activity activity, @NonNull Fragment fragment) {
            this(activity);
            mFragment = fragment;
        }

        /**
         * @param requestCode for onActivityResult
         */
        @NonNull public Builder setRequestCode(int requestCode) {
            mRequestCode = requestCode;
            return this;
        }

        /**
         * @param title of the confirmation screen; shown in the action bar
         */
        @NonNull public Builder setTitle(@Nullable CharSequence title) {
            mTitle = title;
            return this;
        }

        /**
         * @param header of the confirmation screen; shown as large text
         */
        @NonNull public Builder setHeader(@Nullable CharSequence header) {
            mHeader = header;
            return this;
        }

        /**
         * @param description of the confirmation screen
         */
        @NonNull public Builder setDescription(@Nullable CharSequence description) {
            mDescription = description;
            return this;
        }

        /**
         * @param alternateButton text for an alternate button
         */
        @NonNull public Builder setAlternateButton(@Nullable CharSequence alternateButton) {
            mAlternateButton = alternateButton;
            return this;
        }

        /**
         * @param returnCredentials if true, puts the following credentials into intent for
         *                          onActivityResult with the following keys:
         *                          {@link #EXTRA_KEY_TYPE}, {@link #EXTRA_KEY_PASSWORD},
         *                          {@link #EXTRA_KEY_CHALLENGE_TOKEN}, {@link #EXTRA_KEY_GK_PW}
         *                          Note that if this is true, this can only be called internally.
         */
        @NonNull public Builder setReturnCredentials(boolean returnCredentials) {
            mReturnCredentials = returnCredentials;
            return this;
        }

        /**
         * @param userId for whom the credential should be confirmed.
         */
        @NonNull public Builder setUserId(int userId) {
            mUserId = userId;
            return this;
        }

        /**
         * @param allowAnyUserId Allows the caller to prompt for credentials of any user, including
         *                       those which aren't associated with the current user. As an example,
         *                       this is useful when unlocking the storage for secondary users.
         */
        @NonNull public Builder setAllowAnyUserId(boolean allowAnyUserId) {
            mAllowAnyUserId = allowAnyUserId;
            return this;
        }

        /**
         * @param external specifies whether this activity is launched externally, meaning that it
         *                 will get a dark theme, allow biometric authentication, and it will
         *                 forward the activity result.
         */
        @NonNull public Builder setExternal(boolean external) {
            mExternal = external;
            return this;
        }

        /**
         * @param foregroundOnly if true, the confirmation activity will be finished if it loses
         *                       foreground.
         */
        @NonNull public Builder setForegroundOnly(boolean foregroundOnly) {
            mForegroundOnly = foregroundOnly;
            return this;
        }

        /**
         * @param challenge an opaque payload that will be wrapped in the Gatekeeper's payload
         *                  if authentication is successful. Common use case is for the caller's
         *                  secure layer (e.g. Trusted Execution Environment) to 1) verify that
         *                  the Gatekeeper HAT's HMAC is valid, and 2) if so, perform an operation
         *                  based on the challenge.
         */
        @NonNull public Builder setChallenge(long challenge) {
            mChallenge = challenge;
            return this;
        }

        /**
         * Requests that LockSettingsService return the Gatekeeper Password (instead of the
         * Gatekeeper HAT). This allows us to use a single entry of the user's credential
         * to create multiple Gatekeeper HATs containing distinct challenges via
         * {@link LockPatternUtils#verifyGatekeeperPassword(byte[], long, int)}.
         *
         * Upon confirmation of the user's password, the Gatekeeper Password will be returned via
         * onActivityResult with the key being {@link #EXTRA_KEY_GK_PW}.
         * @param requestGatekeeperPassword
         *
         * Note that invoking {@link #setChallenge(long)} will be treated as a no-op if Gatekeeper
         * Password has been requested.
         */
        @NonNull public Builder setRequestGatekeeperPassword(boolean requestGatekeeperPassword) {
            mRequestGatekeeperPassword = requestGatekeeperPassword;
            return this;
        }

        @NonNull public ChooseLockSettingsHelper build() {
            if (!mAllowAnyUserId && mUserId != LockPatternUtils.USER_FRP) {
                Utils.enforceSameOwner(mActivity, mUserId);
            }

            if (mExternal && mReturnCredentials) {
                throw new IllegalArgumentException("External and ReturnCredentials specified. "
                        + " External callers should never be allowed to receive credentials in"
                        + " onActivityResult");
            }

            if (mChallenge != null && !mReturnCredentials) {
                // HAT containing the signed challenge will not be available to the caller.
                Log.w(TAG, "Challenge set but not requesting ReturnCredentials. Are you sure this"
                        + " is what you want?");
            }

            return new ChooseLockSettingsHelper(this, mActivity, mFragment);
        }

        public boolean show() {
            return build().launch();
        }
    }

    /**
     * If a PIN, Pattern, or Password exists, prompt the user to confirm it.
     * @return true if the confirmation activity is shown (e.g. user has a credential set up)
     */
    public boolean launch() {
        final long challenge = mBuilder.mChallenge != null ? mBuilder.mChallenge : 0L;
        return launchConfirmationActivity(mBuilder.mRequestCode, mBuilder.mTitle, mBuilder.mHeader,
                mBuilder.mDescription, mBuilder.mReturnCredentials, mBuilder.mExternal,
                mBuilder.mChallenge != null, challenge, mBuilder.mUserId,
                mBuilder.mAlternateButton, mBuilder.mAllowAnyUserId, mBuilder.mForegroundOnly,
                mBuilder.mRequestGatekeeperPassword);
    }

    private boolean launchConfirmationActivity(int request, @Nullable CharSequence title,
            @Nullable CharSequence header, @Nullable CharSequence description,
            boolean returnCredentials, boolean external, boolean hasChallenge,
            long challenge, int userId, @Nullable CharSequence alternateButton,
            boolean allowAnyUser, boolean foregroundOnly, boolean requestGatekeeperPassword) {
        final int effectiveUserId = UserManager.get(mActivity).getCredentialOwnerProfile(userId);
        boolean launched = false;

        switch (mLockPatternUtils.getKeyguardStoredPasswordQuality(effectiveUserId)) {
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                launched = launchConfirmationActivity(request, title, header, description,
                        returnCredentials || hasChallenge
                                ? ConfirmLockPattern.InternalActivity.class
                                : ConfirmLockPattern.class, returnCredentials, external,
                                hasChallenge, challenge, userId, alternateButton, allowAnyUser,
                                foregroundOnly, requestGatekeeperPassword);
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
            case DevicePolicyManager.PASSWORD_QUALITY_MANAGED:
                launched = launchConfirmationActivity(request, title, header, description,
                        returnCredentials || hasChallenge
                                ? ConfirmLockPassword.InternalActivity.class
                                : ConfirmLockPassword.class, returnCredentials, external,
                                hasChallenge, challenge, userId, alternateButton, allowAnyUser,
                                foregroundOnly, requestGatekeeperPassword);
                break;
        }
        return launched;
    }

    private boolean launchConfirmationActivity(int request, CharSequence title, CharSequence header,
            CharSequence message, Class<?> activityClass, boolean returnCredentials,
            boolean external, boolean hasChallenge, long challenge,
            int userId, @Nullable CharSequence alternateButton, boolean allowAnyUser,
            boolean foregroundOnly, boolean requestGatekeeperPassword) {
        final Intent intent = new Intent();
        intent.putExtra(ConfirmDeviceCredentialBaseFragment.TITLE_TEXT, title);
        intent.putExtra(ConfirmDeviceCredentialBaseFragment.HEADER_TEXT, header);
        intent.putExtra(ConfirmDeviceCredentialBaseFragment.DETAILS_TEXT, message);
        // TODO: Remove dark theme and show_cancel_button options since they are no longer used
        intent.putExtra(ConfirmDeviceCredentialBaseFragment.DARK_THEME, false);
        intent.putExtra(ConfirmDeviceCredentialBaseFragment.SHOW_CANCEL_BUTTON, false);
        intent.putExtra(ConfirmDeviceCredentialBaseFragment.SHOW_WHEN_LOCKED, external);
        intent.putExtra(ConfirmDeviceCredentialBaseFragment.USE_FADE_ANIMATION, external);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_RETURN_CREDENTIALS, returnCredentials);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, hasChallenge);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, challenge);
        intent.putExtra(Intent.EXTRA_USER_ID, userId);
        intent.putExtra(KeyguardManager.EXTRA_ALTERNATE_BUTTON_LABEL, alternateButton);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_FOREGROUND_ONLY, foregroundOnly);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_ALLOW_ANY_USER, allowAnyUser);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_REQUEST_GK_PW,
                requestGatekeeperPassword);

        intent.setClassName(SETTINGS_PACKAGE_NAME, activityClass.getName());
        if (external) {
            intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            if (mFragment != null) {
                copyOptionalExtras(mFragment.getActivity().getIntent(), intent);
                mFragment.startActivity(intent);
            } else {
                copyOptionalExtras(mActivity.getIntent(), intent);
                mActivity.startActivity(intent);
            }
        } else {
            if (mFragment != null) {
                copyInternalExtras(mFragment.getActivity().getIntent(), intent);
                mFragment.startActivityForResult(intent, request);
            } else {
                copyInternalExtras(mActivity.getIntent(), intent);
                mActivity.startActivityForResult(intent, request);
            }
        }
        return true;
    }

    private void copyOptionalExtras(Intent inIntent, Intent outIntent) {
        IntentSender intentSender = inIntent.getParcelableExtra(Intent.EXTRA_INTENT);
        if (intentSender != null) {
            outIntent.putExtra(Intent.EXTRA_INTENT, intentSender);
        }
        int taskId = inIntent.getIntExtra(Intent.EXTRA_TASK_ID, -1);
        if (taskId != -1) {
            outIntent.putExtra(Intent.EXTRA_TASK_ID, taskId);
        }
        // If we will launch another activity once credentials are confirmed, exclude from recents.
        // This is a workaround to a framework bug where affinity is incorrect for activities
        // that are started from a no display activity, as is ConfirmDeviceCredentialActivity.
        // TODO: Remove once that bug is fixed.
        if (intentSender != null || taskId != -1) {
            outIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            outIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        }
    }

    private void copyInternalExtras(Intent inIntent, Intent outIntent) {
        SetupWizardUtils.copySetupExtras(inIntent, outIntent);
        String theme = inIntent.getStringExtra(WizardManagerHelper.EXTRA_THEME);
        if (theme != null) {
            outIntent.putExtra(WizardManagerHelper.EXTRA_THEME, theme);
        }
    }
}
