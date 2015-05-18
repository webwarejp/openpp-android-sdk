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
        return buildUrl(ACCESS_TOKEN_ENDPOINT_PATH) + "?" + GRANT_TYPE_PARAM;
    }

    public String getAccessTokenEndpointWithoutGrantType() {
        return buildUrl(ACCESS_TOKEN_ENDPOINT_PATH);
    }

    @Override
    public String getAuthorizationUrl(OAuthConfig config) {
        // Append scope if present
        if(config.hasScope()) {
            return String.format(buildUrl(SCOPED_AUTHORIZE_PATH), config.getApiKey(), OAuthEncoder.encode(config.getCallback()), OAuthEncoder.encode(config.getScope()));
        } else {
            return String.format(buildUrl(AUTHORIZE_PATH), config.getApiKey(), OAuthEncoder.encode(config.getCallback()));
        }
    }

    /**
     *
     * @param path
     * @return
     */
    private String buildUrl(String path) {
        return OpenppAuthManager.SCHEME + "://" + OpenppAuthManager.getInstance().getAuthServerName() + path;
    }

    @Override
    public AccessTokenExtractor getAccessTokenExtractor() {
        return new JsonTokenExtractor();
    }
}
