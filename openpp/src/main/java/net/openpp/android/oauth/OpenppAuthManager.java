package net.openpp.android.oauth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;

import net.openpp.android.push.OpenppPushManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.scribe.builder.ServiceBuilder;
import org.scribe.exceptions.OAuthConnectionException;
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
class Param {
    Activity activity;
    String code;
    Token accessToken;
}

public class OpenppAuthManager {
    public static final String SCHEME = "http"; // TODO: It must be "https" !!
    public static final String API_USER_INFO_PATH = "/api/user/me";
    public static final String PREFERENCE_NAME = "openpp_auth";
    public static final String PROPERTY_ACCESS_TOKEN = "accessToken";
    public static final String PARAMETER_GRANT_TYPE = "grant_type";
    public static final String PARAMETER_REFRESH_TOKEN = "refresh_token";
    private static final int MAX_ATTEMPTS = 5;
    private static final int BACKOFF_MILLI_SECONDS = 2000;
    private static final Random random = new Random();

    // Tag used on log messages.
    static final String TAG = "OpenppAuthManager";

    static OpenppAuthManager instance = new OpenppAuthManager();
    OAuthService service;
    HashMap<String,String> userInfo = new HashMap<>();

    private String authServerName;
    private String resourceServerName;
    private String apiKey;
    private String apiSecret;

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
        return instance;
    }

    /**
     * Sets the API Key.
     * @param apiKey
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Sets the API Secret.
     * @param apiSecret
     */
    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    /**
     * Sets the authorization server name.
     * @param authServerName
     */
    public void setAuthServerName(String authServerName) {
        this.authServerName = authServerName;
    }

    /**
     * Gets the authorization server name.
     * @return
     */
    public String getAuthServerName() {
        return authServerName;
    }

    /**
     * Sets the resource server name.
     * @param resourceServerName
     */
    public void setResourceServerName(String resourceServerName) {
        this.resourceServerName = resourceServerName;
    }

    /**
     * Gets the resource server name.
     * @return
     */
    public String getResourceServerName() {
        return resourceServerName;
    }

    /**
     * Gets the resource owner's information.
     * @param key key of resource owner's information
     * @return value
     */
    public String getUserInfo(String key) {
        return this.userInfo.get(key);
    }

    /**
     * Processes the authorization and retrieves the resource owner's information.
     * @param activity
     */
    public void auth(Activity activity) {
        createOAuthService(activity);

        Token accessToken = getStoredAccessToken(activity);
        if (accessToken == null) {
            startAuth(activity);
        } else {
            getUserInfoTask(activity, accessToken);
        }
    }

    /**
     * Sends the request using the OAuth.
     * Note: This method must not be called directly from the UI thread.
     * @param context
     * @param verb
     * @param url
     * @param bodyParam
     * @return
     * @throws IOException
     */
    public Response sendOAuthRequest(Context context, Verb verb, String url, HashMap<String, String> bodyParam) throws IOException {
        Token accessToken = getStoredAccessToken(context);
        if (accessToken == null) {
            throw new IOException("No access token found.");
        }
        OAuthRequest request = new OAuthRequest(verb, url);
        for (Map.Entry<String, String> stringStringEntry : bodyParam.entrySet()) {
            Map.Entry entry = (Map.Entry) stringStringEntry;
            request.addBodyParameter((String) entry.getKey(), (String) entry.getValue());
        }
        service.signRequest(accessToken, request);

        // Send the request.
        // As the server might be down, we will retry it a couple
        // times.
        long backoff = BACKOFF_MILLI_SECONDS + random.nextInt(1000);
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            try {
                Response response =  request.send();
                if (isTokenExpired(response)) {
                    refreshAccessToken(context, extractRefreshToken(accessToken));
                    return sendOAuthRequest(context, verb, url, bodyParam);
                }
                return response;
            } catch (Exception e) {
                Log.e(TAG, "Failed to register on attempt " + i + ":" + e);
                if (i == MAX_ATTEMPTS) {
                    break;
                }
                try {
                    Log.d(TAG, "Sleeping for " + backoff + " ms before retry");
                    Thread.sleep(backoff);
                } catch (InterruptedException e1) {
                    Log.d(TAG, "Thread interrupted: abort remaining retries!");
                    Thread.currentThread().interrupt();
                    break;
                }
                // increase backoff exponentially
                backoff *= 2;
            }
        }
        return null;
    }

    /**
     * Starts the browser activity for the authorization.
     * @param activity
     */
    private void startAuth(Activity activity) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(service.getAuthorizationUrl(null)));
        activity.startActivity(i);
    }

    /**
     * Creates the OAuthService.
     * @param context
     * @return
     */
    private OAuthService createOAuthService(Context context) {
        if (service == null) {
            service = new ServiceBuilder()
                    .provider(OpenppOAuthApi.class)
                    .apiKey(apiKey)
                    .apiSecret(apiSecret)
                    .callback(getCallbackUri(context))
                    .build();
        }
        return service;
    }

    /**
     * Gets the redirect uri for OAuth.
     * @param context
     * @return
     */
    private String getCallbackUri(Context context) {
        return "intent://callback/#Intent;scheme=" + context.getPackageName() +  ";package=" + context.getPackageName() + ";end";
    }

    /**
     * Extracts the authorization code and starts to retrieve the access token.
     * @param intent
     */
    public void parseIntent(Activity activity, Intent intent) {
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
        getAccessTokenTask(activity, code);
    }

    /**
     * Gets the access token from the authorization server.
     * @param activity
     * @param code
     */
    private void getAccessTokenTask(Activity activity, String code) {
        Param param = new Param();
        param.activity = activity;
        param.code = code;

        new AsyncTask<Param, Void, Param>() {

            @Override
            protected Param doInBackground(Param... params) {
                Param param = params[0];

                long backoff = BACKOFF_MILLI_SECONDS + random.nextInt(1000);
                for (int i = 1; i <= MAX_ATTEMPTS; i++) {
                    try {
                        Token token = getAccessToken(param.code);
                        storeAccessToken(param.activity, token);
                        param.accessToken = token;
                        return param;
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
                return null;
            }

            @Override
            protected void onPostExecute(Param param) {
                if (param != null) {
                    getUserInfoTask(param.activity, param.accessToken);
                }
            }
        }.execute(param, null, null);
    }

    /**
     * Gets the access token from the authorization server.
     * @param code
     * @return access token
     */
    private Token getAccessToken(String code) {
        Verifier verifier = new Verifier(code);
        return service.getAccessToken(null, verifier);
    }

    /**
     * Gets the resource owner's information from the resource server.
     * @param activity
     * @param accessToken
     */
    private void getUserInfoTask(Activity activity, Token accessToken) {
        Param param = new Param();
        param.activity = activity;
        param.accessToken = accessToken;
        new AsyncTask<Param, Void, Param>() {

            @Override
            protected Param doInBackground(Param... params) {
                Param param = params[0];

                long backoff = BACKOFF_MILLI_SECONDS + random.nextInt(1000);
                for (int i = 1; i <= MAX_ATTEMPTS; i++) {
                    try {
                        getUserInfo(param.activity, param.accessToken);
                        return param;
                    } catch (OAuthConnectionException e1) {
                        Log.e(TAG, "Failed to get the user info on attempt " + i + ":" + e1);
                        if (i == MAX_ATTEMPTS) {
                            break;
                        }
                        try {
                            Log.d(TAG, "Sleeping for " + backoff + " ms before retry");
                            Thread.sleep(backoff);
                        } catch (InterruptedException e2) {
                            Log.d(TAG, "Thread interrupted: abort to get the user info!");
                            Thread.currentThread().interrupt();
                            break;
                        }
                        // increase backoff exponentially
                        backoff *= 2;
                    } catch (JSONException e2) {
                        break;
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Param param) {
                if (param != null) {
                    OpenppPushManager.getInstance().registerInBackground(param.activity);
                }
            }
        }.execute(param, null, null);
    }

    /**
     * Gets the resource owner's information from the resource server.
     * @param accessToken
     * @throws OAuthConnectionException, JSONException
     */
    private void getUserInfo(Context context, Token accessToken) throws JSONException {
        OAuthRequest request = new OAuthRequest(Verb.GET, buildGetUserInfoUri());
        service.signRequest(accessToken, request);

        try {
            Response response = request.send();
            if (isTokenExpired(response)) {
                Token newToken = refreshAccessToken(context, extractRefreshToken(accessToken));
                getUserInfo(context, newToken);
                return;
            }
            JSONObject obj = new JSONObject(response.getBody());
            userInfo.put("uid", obj.getString("uid"));
        } catch (OAuthConnectionException e1) {
            Log.e(TAG, "Failed to get the user info: " + e1.getMessage());
            throw e1;
        } catch (JSONException e2) {
            Log.e(TAG, "Failed to parse the user info: " + e2.getMessage());
            throw e2;
        }
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
     * Build the uri to gets the resource owner's information.
     * @return
     */
    private String buildGetUserInfoUri() {
        return SCHEME + "://" + resourceServerName + API_USER_INFO_PATH;
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
     * @param context
     * @param refreshToken
     */
    private Token refreshAccessToken(Context context, String refreshToken) {
        OpenppOAuthApi api = new OpenppOAuthApi();
        OAuthRequest request = new OAuthRequest(Verb.POST, api.getAccessTokenEndpointWithoutGrantType());
        request.addBodyParameter(OAuthConstants.CLIENT_ID, apiKey);
        request.addBodyParameter(OAuthConstants.CLIENT_SECRET, apiSecret);
        request.addBodyParameter(PARAMETER_GRANT_TYPE, PARAMETER_REFRESH_TOKEN);
        request.addBodyParameter(PARAMETER_REFRESH_TOKEN, refreshToken);

        Response response = request.send();
        Token  token =  api.getAccessTokenExtractor().extract(response.getBody());
        storeAccessToken(context, token);
        return token;
    }

    /**
     * Gets the access token from the application's SharedPreferences.
     * @param context
     * @return
     */
    private Token getStoredAccessToken(Context context) {
        final SharedPreferences prefs = getSharedPreferences(context);
        return new Gson().fromJson(prefs.getString(PROPERTY_ACCESS_TOKEN, null), Token.class);
    }

    /**
     * Stores the access token in application's SharedPreferences.
     * {@code SharedPreferences}.
     *
     * @param context Application's context.
     * @param accessToken Access Token
     */
    private void storeAccessToken(Context context, Token accessToken) {
        final SharedPreferences prefs = getSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_ACCESS_TOKEN, new Gson().toJson(accessToken));
        editor.apply();
    }

    /**
     * Gets the application's SharedPreferences.
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
    }
}
