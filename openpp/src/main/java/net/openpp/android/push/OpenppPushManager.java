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
import net.openpp.android.oauth.OpenppAuthManager;

import org.scribe.model.Verb;

import java.io.IOException;
import java.util.HashMap;

/**
 * @author shiroko@webware.co.jp
 */
public class OpenppPushManager {
    public static final String PREFERENCE_NAME = "openpp_push";
    public static final String PROPERTY_REG_ID = "registrationId";
    public static final String PROPERTY_ADV_ID = "advertisingId";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final String PARAM_APPLICATION_NAME = "application_name";
    private static final String PARAM_DEVICE_ID = "device_identifier";
    private static final String PARAM_REG_ID = "registration_id";
    private static final String PARAM_UID = "uid";
    private static final String SCHEME = "http";
    private static final String API_REGISTRATION_PATH = "/api/push/device/android/register";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    // Tag used on log messages.
    static final String TAG = "OpenppPushManager";

    private static OpenppPushManager instance = new OpenppPushManager();

    private String senderId;
    private String registrationServerName;
    private java.lang.Class wakeupActivity;
    private Integer iconResourceId = R.drawable.ic_stat_gcm;
    private GoogleCloudMessaging gcm;

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
        return instance;
    }

    /**
     * Sets the sender ID that is the project number you got from the API Console, as described in "Getting Started."
     * @param senderId sender ID
     */
    public void setSenderId(final String senderId) {
        this.senderId = senderId;
    }

    /**
     * Sets the server name to register for the push notification.
     * @param registrationServerName registration server name
     */
    public void setRegistrationServerName(String registrationServerName) {
        this.registrationServerName = registrationServerName;
    }

    /**
     * Sets the activity class that will be woke up when user touches the notification.
     * @param activity activity class
     */
    public void setWakeupActivity(java.lang.Class<?> activity) {
        this.wakeupActivity = activity;
    }

    /**
     * Gets the activity class that will be woke up when user touches the notification.
     * @return activity class
     */
    public java.lang.Class<?> getWakeupActivity() {
        return this.wakeupActivity;
    }

    /**
     * Sets the icon resource ID used as the notification's small icon.
     * @param iconResourceId icon resource ID
     */
    public void setIconResourceId(final Integer iconResourceId) {
        this.iconResourceId = iconResourceId;
    }

    /**
     * Gets the icon resource ID used as the notification's small icon.
     * @return icon resource ID
     */
    public Integer getIconResourceId() {
        return this.iconResourceId;
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
     * Registers this device to the backend server to receive the push notification.
     * @param activity
     */
    public void register(Activity activity) {
        if (checkPlayServices(activity)) {
            OpenppAuthManager.getInstance().auth(activity);
        }  else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }
    }

    /**
     * Parse the intent which passed by the browser.
     * @param intent
     */
    public void parseIntent(Activity activity, Intent intent) {
        OpenppAuthManager.getInstance().parseIntent(activity, intent);
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
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
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
    public void registerInBackground(Context context) {
        new AsyncTask<Context, Void, Void>() {
            @Override
            protected Void doInBackground(Context... params) {
                Context context = params[0];
                String registrationId = getRegistrationId(context);
                String advertisingId = getAdvertisingId(context);

                if (!registrationId.isEmpty()
                        && (advertisingId.isEmpty()
                        || advertisingId.equals(getStoredAdvertisingId(context)))) {
                    return null;
                }

                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    registrationId = gcm.register(senderId);

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.
                    sendRegistrationIdToBackend(context, registrationId, advertisingId);

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Persist the regID - no need to register again.
                    storeRegistrationId(context, registrationId);
                    storeAdvertisingId(context, advertisingId);
                } catch (IOException ex) {
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                    Log.e(TAG, "Error :" + ex.getMessage());
                }
                return null;
            }
        }.execute(context, null, null);
    }

    /**
     * Gets the current registration ID for application on GCM service, if there is one.
     * <p>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
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
     * @param context application's context.
     * @param registrationId registration ID
     */
    private void storeRegistrationId(Context context, String registrationId) {
        final SharedPreferences prefs = getGcmPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving registrationId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, registrationId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.apply();
    }

    /**
     *
     * @param context
     * @return
     */
    private String getAdvertisingId(Context context) {
        try {
            AdvertisingIdClient.Info info = AdvertisingIdClient.getAdvertisingIdInfo(context);
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
     * @param context
     * @return
     */
    private String getStoredAdvertisingId(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        return prefs.getString(PROPERTY_ADV_ID, "");
    }

    /**
     *
     * @param context
     * @param advertisingId
     */
    private void storeAdvertisingId(Context context, String advertisingId) {
        final SharedPreferences prefs = getGcmPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, advertisingId);
        editor.apply();
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGcmPreferences(Context context) {
        return context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
    }

    /**
     * Sends the registration ID to the backend server, so it can use GCM/HTTP or CCS to send
     * messages to your app.
     * @param context
     * @param registrationId
     * @param advertisingId
     */
    private void sendRegistrationIdToBackend(final Context context, final String registrationId, final String advertisingId) {
        HashMap<String, String> bodyParam = new HashMap<>();
        bodyParam.put(PARAM_APPLICATION_NAME, context.getPackageName());
        bodyParam.put(PARAM_DEVICE_ID, advertisingId);
        bodyParam.put(PARAM_REG_ID, registrationId);
        bodyParam.put(PARAM_UID, OpenppAuthManager.getInstance().getUserInfo("uid"));

        String url = SCHEME + "://" + registrationServerName + API_REGISTRATION_PATH;
        try {
            OpenppAuthManager.getInstance().sendOAuthRequest(context, Verb.POST, url, bodyParam);
        } catch (IOException e) {
            Log.e(TAG, "Registration is failed: " + e.getMessage());
        }
    }
}
