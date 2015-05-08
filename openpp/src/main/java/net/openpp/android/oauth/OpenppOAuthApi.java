package net.openpp.android.oauth;

import org.scribe.builder.api.DefaultApi20;
import org.scribe.extractors.AccessTokenExtractor;
import org.scribe.extractors.JsonTokenExtractor;
import org.scribe.model.OAuthConfig;
import org.scribe.utils.OAuthEncoder;

/**
 * @author shiroko@webware.co.jp
 */
public class OpenppOAuthApi extends DefaultApi20 {

    private static final String AUTHORIZE_PATH = "/oauth/v2/auth?client_id=%s&redirect_uri=%s&response_type=code";
    private static final String SCOPED_AUTHORIZE_PATH = AUTHORIZE_PATH + "&scope=%s";
    private static final String ACCESS_TOKEN_ENDPOINT_PATH = "/oauth/v2/token";
    private static final String GRANT_TYPE_PARAM = "grant_type=authorization_code";

    @Override
    public String getAccessTokenEndpoint() {
        String server = OpenppAuthManager.getInstance().getAuthServerName();
        return buildUrl(ACCESS_TOKEN_ENDPOINT_PATH) + "?" + GRANT_TYPE_PARAM;
    }

    public String getAccessTokenEndpointWithoutGrantType() {
        return buildUrl(ACCESS_TOKEN_ENDPOINT_PATH);
    }

    @Override
    public String getAuthorizationUrl(OAuthConfig config) {
        // Append scope if present
        if(config.hasScope())
        {
            return String.format(buildUrl(SCOPED_AUTHORIZE_PATH), config.getApiKey(), OAuthEncoder.encode(config.getCallback()), OAuthEncoder.encode(config.getScope()));
        }
        else
        {
            return String.format(buildUrl(AUTHORIZE_PATH), config.getApiKey(), OAuthEncoder.encode(config.getCallback()));
        }
    }

    private String buildUrl(String path) {
        String server = OpenppAuthManager.getInstance().getAuthServerName();
        return OpenppAuthManager.SCHEME + "://" + server + path;
    }

    @Override
    public AccessTokenExtractor getAccessTokenExtractor() {
        return new JsonTokenExtractor();
    }
}
