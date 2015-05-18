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
package net.openpp.android.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;
import org.scribe.builder.ServiceBuilder;
import org.scribe.model.OAuthConstants;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * @author shiroko@webware.co.jp
 */
public class OpenppAuthManager {
    static final String SCHEME = "http"; // TODO: It must be "https" !!
    static final String API_USER_INFO_PATH = "/api/user/me";
    static final String PREFERENCE_NAME = "openpp_auth";
    static final String PROPERTY_ACCESS_TOKEN = "accessToken";
    static final String PARAMETER_GRANT_TYPE = "grant_type";
    static final String PARAMETER_REFRESH_TOKEN = "refresh_token";
    private static final int MAX_ATTEMPTS = 5;
    private static final int BACKOFF_MILLI_SECONDS = 2000;
    private static final Random mRandom = new Random();

    // Tag used on log messages.
    private static final String TAG = "OpenppAuthManager";

    private static final OpenppAuthManager mInstance = new OpenppAuthManager();
    private OAuthService mService;
    private Context mContext;
    private HashMap<String,String> mUserInfo = new HashMap<>();

    private String mAuthServerName;
    private String mResourceServerName;
    private String mApiKey;
    private String mApiSecret;
    private OpenppAuthListener mListener;

    /**
     * Constructor
     */
    private OpenppAuthManager() {
    }

    /**
     * Gets the OpenppAuthManager instance.
     * @return OpenppAuthManager instance
     */
    public static OpenppAuthManager getInstance() {
        return mInstance;
    }

    /**
     * Sets the API Key.
     * @param apiKey
     */
    public void setApiKey(String apiKey) {
        mApiKey = apiKey;
    }

    /**
     * Sets the API Secret.
     * @param apiSecret
     */
    public void setApiSecret(String apiSecret) {
        mApiSecret = apiSecret;
    }

    /**
     * Sets the authorization server name.
     * @param authServerName
     */
    public void setAuthServerName(String authServerName) {
        mAuthServerName = authServerName;
    }

    /**
     * Gets the authorization server name.
     * @return
     */
    public String getAuthServerName() {
        return mAuthServerName;
    }

    /**
     * Sets the resource server name.
     * @param resourceServerName
     */
    public void setResourceServerName(String resourceServerName) {
        mResourceServerName = resourceServerName;
    }

    /**
     * Gets the resource server name.
     * @return
     */
    public String getResourceServerName() {
        return mResourceServerName;
    }

    /**
     * Gets the resource owner's information.
     * @param key key of resource owner's information
     * @return value
     */
    public String getUserInfo(String key) {
        return mUserInfo.get(key);
    }

    /**
     * Processes the authorization and retrieves the resource owner's information.
     * @param activity activity
     */
    public void auth(Activity activity, OpenppAuthListener listener) {
        if (null == mContext) {
            mContext = activity.getApplicationContext();
            // save the listener for callback.
            mListener = listener;

            createOAuthService();

            Token accessToken = getStoredAccessToken();
            if (accessToken == null) {
                startAuthWithBrowser(activity);
            } else {
                getUserInfoInBackground();
            }
        }
    }

    /**
     * Sends the request using the OAuth.<p>
     * <strong>Note:</strong> This method must not be called directly from the UI thread.
     * @param verb
     * @param url
     * @param bodyParam
     * @return
     * @throws IOException
     */
    public Response sendOAuthRequest(Verb verb, String url, HashMap<String, String> bodyParam) throws IOException {
        Token accessToken = getStoredAccessToken();
        if (null == accessToken) {
            throw new IOException("No access token found.");
        }

        OAuthRequest request = new OAuthRequest(verb, url);
        if (null != bodyParam) {
            for (Map.Entry<String, String> stringStringEntry : bodyParam.entrySet()) {
                Map.Entry entry = (Map.Entry) stringStringEntry;
                request.addBodyParameter((String) entry.getKey(), (String) entry.getValue());
            }
        }
        mService.signRequest(accessToken, request);

        // Send the request.
        // As the server might be down, we will retry it a couple
        // times.
        long backoff = BACKOFF_MILLI_SECONDS + mRandom.nextInt(1000);
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            try {
                Response response =  request.send();
                if (isTokenExpired(response)) {
                    refreshAccessToken(extractRefreshToken(accessToken));
                    return sendOAuthRequest(verb, url, bodyParam);
                }
                return response;
            } catch (IOException e) {
                Log.e(TAG, "Failed to access on attempt " + i + ":" + e);
                if (i == MAX_ATTEMPTS) {
                    throw e;
                }
                try {
                    Log.d(TAG, "Sleeping for " + backoff + " ms before retry");
                    Thread.sleep(backoff);
                } catch (InterruptedException e1) {
                    Log.d(TAG, "Thread interrupted: abort remaining retries!");
                    Thread.currentThread().interrupt();
                    throw new IOException("Thread interrupted.");
                }
                // increase backoff exponentially
                backoff *= 2;
            }
        }
        return null;
    }

    /**
     * Starts the browser activity for the authorization.
     */
    private void startAuthWithBrowser(Activity activity) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(mService.getAuthorizationUrl(null)));
        activity.startActivity(i);
    }

    /**
     * Creates the OAuthService.
     * @return
     */
    private OAuthService createOAuthService() {
        if (mService == null) {
            mService = new ServiceBuilder()
                    .provider(OpenppOAuthApi.class)
                    .apiKey(mApiKey)
                    .apiSecret(mApiSecret)
                    .callback(getCallbackUri())
                    .build();
        }
        return mService;
    }

    /**
     * Gets the redirect uri for OAuth.
     * @return
     */
    private String getCallbackUri() {
        return "intent://callback/#Intent;scheme=" + mContext.getPackageName() +  ";package=" + mContext.getPackageName() + ";end";
    }

    /**
     * Extracts the authorization code and starts to retrieve the access token.
     * @param intent
     */
    public void parseIntent(Intent intent) {
        String action = intent.getAction();
        if (action == null || !action.equals(Intent.ACTION_VIEW)){
            Log.e(TAG, "Invalid action.");
            return;
        }

        Uri uri = intent.getData();
        if (uri == null) {
            Log.e(TAG, "No valid URI found.");
            return;
        }

        String code = uri.getQueryParameter("code");
        if (code == null) {
            Log.e(TAG, "No valid code found.");
            return;
        }
        getAccessTokenInBackground(code);
    }

    /**
     * Gets the access token from the authorization server.
     * @param code
     */
    private void getAccessTokenInBackground(String code) {
        new AsyncTask<String, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(String... params) {
                String code = params[0];

                long backoff = BACKOFF_MILLI_SECONDS + mRandom.nextInt(1000);
                for (int i = 1; i <= MAX_ATTEMPTS; i++) {
                    try {
                        Token token = getAccessToken(code);
                        storeAccessToken(token);
                        return true;
                    } catch (Exception e1) {
                        Log.e(TAG, "Failed to get the access token on attempt " + i + ":" + e1);
                        if (i == MAX_ATTEMPTS) {
                            break;
                        }
                        try {
                            Log.d(TAG, "Sleeping for " + backoff + " ms before retry");
                            Thread.sleep(backoff);
                        } catch (InterruptedException e2) {
                            Log.d(TAG, "Thread interrupted: abort to get the access token!");
                            Thread.currentThread().interrupt();
                            break;
                        }
                        // increase backoff exponentially
                        backoff *= 2;
                    }
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    getUserInfoInBackground();
                }
            }
        }.execute(code, null, null);
    }

    /**
     * Gets the access token from the authorization server.
     * @param code
     * @return access token
     */
    private Token getAccessToken(String code) {
        Verifier verifier = new Verifier(code);
        return mService.getAccessToken(null, verifier);
    }

    /**
     * Gets the resource owner's information from the resource server.
     */
    private void getUserInfoInBackground() {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    Response response = sendOAuthRequest(Verb.GET, buildGetUserInfoUri(), null);
                    JSONObject obj = new JSONObject(response.getBody());
                    mUserInfo.put("uid", obj.getString("uid"));
                    return true;
                } catch (IOException e1) {
                    Log.e(TAG, "Failed to get the user info on attempt: " + e1.getMessage());
                } catch (JSONException e2) {
                    Log.e(TAG, "Failed to parse the user info: " + e2.getMessage());
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    // Callback after the authorization has completed.
                    mListener.onAuthorized();
                }
            }
        }.execute(null, null, null);
    }

    /**
     * Build the uri to gets the resource owner's information.
     * @return
     */
    private String buildGetUserInfoUri() {
        return SCHEME + "://" + mResourceServerName + API_USER_INFO_PATH;
    }

    /**
     * Returns whether the response shows that the access token is expired.
     * @param response
     * @return
     */
    private boolean isTokenExpired(Response response) {
        return 401 == response.getCode();
    }

    /**
     * Extracts the refresh token from the Token class.
     * @param token
     * @return refresh token
     */
    private String extractRefreshToken(Token token) {
        try {
            JSONObject obj = new JSONObject(token.getRawResponse());
            return obj.getString(PARAMETER_REFRESH_TOKEN);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to extract the refresh token: " + e.getMessage());
            return null;
        }
    }

    /**
     * Refreshes the access token by the refresh token.
     * @param refreshToken
     */
    private Token refreshAccessToken(String refreshToken) {
        OpenppOAuthApi api = new OpenppOAuthApi();
        OAuthRequest request = new OAuthRequest(Verb.POST, api.getAccessTokenEndpointWithoutGrantType());
        request.addBodyParameter(OAuthConstants.CLIENT_ID, mApiKey);
        request.addBodyParameter(OAuthConstants.CLIENT_SECRET, mApiSecret);
        request.addBodyParameter(PARAMETER_GRANT_TYPE, PARAMETER_REFRESH_TOKEN);
        request.addBodyParameter(PARAMETER_REFRESH_TOKEN, refreshToken);

        Response response = request.send();
        Token  token = api.getAccessTokenExtractor().extract(response.getBody());
        storeAccessToken(token);
        return token;
    }

    /**
     * Gets the access token from the application's SharedPreferences.
     * @return
     */
    private Token getStoredAccessToken() {
        final SharedPreferences prefs = getSharedPreferences();
        return new Gson().fromJson(prefs.getString(PROPERTY_ACCESS_TOKEN, null), Token.class);
    }

    /**
     * Stores the access token in application's SharedPreferences.
     * {@code SharedPreferences}.
     *
     * @param accessToken Access Token
     */
    private void storeAccessToken(Token accessToken) {
        final SharedPreferences prefs = getSharedPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_ACCESS_TOKEN, new Gson().toJson(accessToken));
        editor.apply();
    }

    /**
     * Gets the application's SharedPreferences.
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
    }
}
