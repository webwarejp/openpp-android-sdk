/*
 * Copyright (C) 2015 webware,Inc
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
package net.openpp.android.push;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import net.openpp.android.R;
import net.openpp.android.auth.OpenppAuthListener;
import net.openpp.android.auth.OpenppAuthManager;
import net.openpp.android.location.OpenppLocationManager;

import org.scribe.model.Verb;

import java.io.IOException;
import java.util.HashMap;

/**
 * @author shiroko@webware.co.jp
 */
public class OpenppPushManager implements OpenppAuthListener {
    public static final String SCHEME = "http"; // TODO: It must be "https" !!
    private static final String API_REGISTRATION_PATH = "/api/push/device/android/register";
    static final String PREFERENCE_NAME = "openpp_push";
    private static final String PROPERTY_REG_ID = "registrationId";
    private static final String PROPERTY_ADV_ID = "advertisingId";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    public static final String PARAM_APPLICATION_NAME = "application_name";
    public static final String PARAM_DEVICE_ID = "device_identifier";
    public static final String PARAM_REG_ID = "registration_id";
    private static final String PARAM_UID = "uid";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    // Tag used on log messages.
    private static final String TAG = "OpenppPushManager";

    private static final OpenppPushManager mInstance = new OpenppPushManager();

    private String mSenderId;
    private String mRegistrationServerName;
    private java.lang.Class mWakeupActivity;
    private Integer mIconResourceId = R.drawable.ic_stat_gcm;
    private boolean mRegisteringLocation = true;
    private GoogleCloudMessaging mGcm;
    private Context mContext;

    /**
     * Constructor
     */
    private OpenppPushManager() {
    }

    /**
     * Gets the OpenppPushManager instance.
     * @return OpenppPushManager instance
     */
    public static OpenppPushManager getInstance() {
        return mInstance;
    }

    /**
     * Sets the sender ID that is the project number you got from the API Console, as described in "Getting Started."
     * @param senderId sender ID
     */
    public void setSenderId(final String senderId) {
        mSenderId = senderId;
    }

    /**
     * Sets the server name to register for the push notification.
     * @param registrationServerName registration server name
     */
    public void setRegistrationServerName(String registrationServerName) {
        mRegistrationServerName = registrationServerName;
    }

    /**
     * Gets the server name to register for the push notification.
     * @return
     */
    public String getRegistrationServerName() {
        return mRegistrationServerName;
    }

    /**
     * Sets the activity class that will be woke up when user touches the notification.
     * @param activity activity class
     */
    public void setWakeupActivity(java.lang.Class<?> activity) {
        mWakeupActivity = activity;
    }

    /**
     * Gets the activity class that will be woke up when user touches the notification.
     * @return activity class
     */
    public java.lang.Class<?> getWakeupActivity() {
        return mWakeupActivity;
    }

    /**
     * Sets the icon resource ID used as the notification's small icon.
     * @param iconResourceId icon resource ID
     */
    public void setIconResourceId(final Integer iconResourceId) {
        mIconResourceId = iconResourceId;
    }

    /**
     * Gets the icon resource ID used as the notification's small icon.
     * @return icon resource ID
     */
    public Integer getIconResourceId() {
        return mIconResourceId;
    }

    /**
     * Sets the the api key for your application.
     * The api key might be supplied by the authorization server on registering your app.
     * @param apiKey
     */
    public void setApiKey(String apiKey) {
        OpenppAuthManager.getInstance().setApiKey(apiKey);
    }

    /**
     * Sets the the api secret for your application.
     * The api secret might be supplied by the authorization server on registering your app.
     * @param apiSecret
     */
    public void setApiSecret(String apiSecret) {
        OpenppAuthManager.getInstance().setApiSecret(apiSecret);
    }

    /**
     * Sets the authorization server name.
     * @param authServerName
     */
    public void setAuthServerName(String authServerName) {
        OpenppAuthManager.getInstance().setAuthServerName(authServerName);
    }

    /**
     * Sets the resource server name.
     * @param resourceServerName
     */
    public void setResourceServerName(String resourceServerName) {
        OpenppAuthManager.getInstance().setResourceServerName(resourceServerName);
    }

    /**
     * Sets whether your app registers the device's location to the backend server.
     * @param registeringLocation
     */
    public void setRegisteringLocation(boolean registeringLocation) {
        mRegisteringLocation = registeringLocation;
    }

    /**
     * Registers this device to the backend server to receive the push notification.
     * @param activity
     */
    public void register(Activity activity) {
        if (null == mContext) {
            // Save the application context.
            mContext = activity.getApplicationContext();

            if (checkPlayServices(activity)) {
                // Retrieves the GCM registration ID.
                retrieveRegistrationIdInBackground();
                // Start the authorization process to access to the backend server's API.
                OpenppAuthManager.getInstance().auth(activity, this);
            } else {
                Log.i(TAG, "No valid Google Play Services APK found.");
            }
        }
    }

    /**
     * Parses the intent which passed by the browser.
     *
     * @param intent
     */
    public void parseIntent(Intent intent) {
        OpenppAuthManager.getInstance().parseIntent(intent);
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     *
     * @param activity
     * @return
     */
    private boolean checkPlayServices(Activity activity) {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, activity,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
            }
            return false;
        }
        return true;
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private int getAppVersion() {
        try {
            PackageInfo packageInfo = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and the app versionCode in the application's
     * shared preferences.
     */
    private void retrieveRegistrationIdInBackground() {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                // Retrieve the GCM registration ID.
                String registrationId = getStoredRegistrationId();
                if (registrationId.isEmpty()) {
                    try {
                        if (mGcm == null) {
                            mGcm = GoogleCloudMessaging.getInstance(mContext);
                        }
                        registrationId = mGcm.register(mSenderId);

                        // Persist the regID - no need to register again.
                        storeRegistrationId(registrationId);
                    } catch (IOException e) {
                        // If there is an error, don't just keep trying to register.
                        // Require the user to click a button again, or perform
                        // exponential back-off.
                        Log.e(TAG, "Error :" + e.getMessage());
                    }
                }

                // Retrieve the Advertising ID for the device's identifier.
                String advertisingId = getAdvertisingId();
               if  (null != advertisingId && !advertisingId.isEmpty()) {
                   storeAdvertisingId(advertisingId);
               }

                return null;
            }
        }.execute(null, null, null);
    }

    /**
     *
     */
    public void registerInBackground() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                // Sends the registration ID to the backend server over HTTP, so it
                // can use GCM/HTTP or CCS to send messages to your app.
                try {
                    sendRegistrationRequest(new HashMap<String, String>());
                } catch (IOException e) {
                    // The retry had done.
                    Log.e(TAG, "Error :" + e.getMessage());
                }

                return null;
            }
        }.execute(null, null, null);
    }

    /**
     * Gets the current registration ID for application on GCM service, if there is one.
     * <p>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    private String getStoredRegistrationId() {
        final SharedPreferences prefs = getGcmPreferences();
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (null == registrationId || registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion();
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    /**
     * Stores the registration ID and the app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param registrationId registration ID
     */
    private void storeRegistrationId(String registrationId) {
        final SharedPreferences prefs = getGcmPreferences();
        int appVersion = getAppVersion();
        Log.i(TAG, "Saving registrationId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, registrationId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.apply();
    }

    /**
     *
     * @return
     */
    private String getAdvertisingId() {
        try {
            AdvertisingIdClient.Info info = AdvertisingIdClient.getAdvertisingIdInfo(mContext);
            return info.getId();
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect to Google Play Services.");
        } catch (GooglePlayServicesNotAvailableException e) {
            Log.e(TAG, "Google Play Services is not available.");
        } catch (GooglePlayServicesRepairableException e) {
            Log.e(TAG, "Google Play Services is not installed, up-to-date, or enabled.");
        }
        return "";
    }

    /**
     *
     * @return
     */
    private String getStoredAdvertisingId() {
        final SharedPreferences prefs = getGcmPreferences();
        return prefs.getString(PROPERTY_ADV_ID, "");
    }

    /**
     *
     * @param advertisingId
     */
    private void storeAdvertisingId(String advertisingId) {
        final SharedPreferences prefs = getGcmPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_ADV_ID, advertisingId);
        editor.apply();
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGcmPreferences() {
        return mContext.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
    }

    /**
     * Sends the registration request to the backend server.
     * @param bodyParam
     */
    public void sendRegistrationRequest(HashMap<String, String> bodyParam) throws IOException {
        String uid = OpenppAuthManager.getInstance().getUserInfo(PARAM_UID);
        if (null == uid) {
            // if the authorization has not yet done, do nothing.
            return;
        }
        String advertisingId = getStoredAdvertisingId();
        if (null == advertisingId || advertisingId.isEmpty()) {
            // if the advertising id has not yet retrieve, do nothing.
            return;
        }

        bodyParam.put(PARAM_APPLICATION_NAME, mContext.getPackageName());
        bodyParam.put(PARAM_DEVICE_ID, getStoredAdvertisingId());
        bodyParam.put(PARAM_REG_ID, getStoredRegistrationId());
        bodyParam.put(PARAM_UID, uid);

        String url = SCHEME + "://" + mRegistrationServerName + API_REGISTRATION_PATH;

        OpenppAuthManager.getInstance().sendOAuthRequest(Verb.POST, url, bodyParam);
    }

    @Override
    public void onAuthorized() {
        if (mRegisteringLocation) {
            OpenppLocationManager.getInstance().startLocationService(mContext);
        } else {
            registerInBackground();
        }
    }
}
