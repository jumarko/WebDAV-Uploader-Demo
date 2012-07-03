/*
 * Copyright (C) 2007-2011, GoodData(R) Corporation. All rights reserved.
 */
package com.gooddata.webdav;

import java.io.IOException;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.cookie.CookieSpecBase;
import org.apache.commons.httpclient.cookie.MalformedCookieException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authenticator providing a simple way how to authenticate user against GoodData REST api with given SST token.
 * @see #authenticate(String)
 */
public class WebDavSstAuthenticator {

    private static final String URI_TEMPLATE = "${protocol}://${host}:${port}${path}";

    private static final String LOGIN_URI = "/gdc/account/login";
    private static final String TOKEN_URI = "/gdc/account/token";

    private static final String GDC_AUTH_SST_COOKIE = "GDCAuthSST";
    public static final String GDCAuthTT_COOKIE = "GDCAuthTT";


    private final HttpClient httpClient;
    private final String host;
    private final int port;
    private final String protocol;

    private static final Logger logger = LoggerFactory.getLogger(WebDavSstAuthenticator.class);

    static {
        logger.info("Registering AcceptGDCAuthSSTCookiePolicy");
        CookiePolicy.registerCookieSpec("AcceptGDCAuthSSTCookiePolicy", AcceptGDCAuthSSTCookiePolicy.class);
    }



    public WebDavSstAuthenticator(String protocol, String host, int port) {
        Validate.notEmpty(protocol);
        Validate.notEmpty(host);
        this.httpClient = new HttpClient();
        this.host = host;
        this.port = port;
        this.protocol = protocol;
    }

    /**
     * Authenticates user with given {@code superSecureToken}
     * @param superSecureToken SST token used for autentication
     * @return {@link UserLogin} object with correctly set SST and TT tokens
     */
    public UserLogin authenticate(String superSecureToken) {
        String gdcAuthTT = null;
        //Requesting GDCAuthTT
        String ttURI = getFullURI(protocol, host, port, TOKEN_URI);
        HttpMethod temporaryTokenGet = new GetMethod(ttURI);
        try {
            HttpState httpState = new HttpState();
            //set SST token
            Cookie ssTcookie = new Cookie();
            ssTcookie.setDomain(host);
            ssTcookie.setName(GDC_AUTH_SST_COOKIE);
            ssTcookie.setValue(superSecureToken);
            ssTcookie.setPath("/gdc/account");
            httpState.addCookie(ssTcookie);

            //do the request
            doHttpCall(protocol, httpState, temporaryTokenGet);

            Cookie[] cookies = httpState.getCookies();
            for(int i = 0; i < cookies.length; i++ ){
                Cookie cookie = cookies[i];
                String name = cookie.getName();
                String value = cookie.getValue();
                logger.trace("Response header name={} value={}", name, value);
                if(GDCAuthTT_COOKIE.equals(name)) {
                    gdcAuthTT = value;
                    break;
                }
            }
            if(gdcAuthTT == null) {
                throw new RuntimeException("Missing GDCAuthTT cookie in the response");
            }
            logger.debug("Returned GDCAuthTT={}", gdcAuthTT);
        } finally {
            temporaryTokenGet.releaseConnection();
        }

        return new UserLogin(null, null, superSecureToken, gdcAuthTT);
    }

    private String getFullURI(String protocol, String host, int port, String path) {
        return URI_TEMPLATE.replace("${protocol}", protocol)
                .replace("${host}", host)
                .replace("${port}", String.valueOf(port))
                .replace("${path}", path);
    }

    private void doHttpCall(String protocol, HttpState httpState, HttpMethod method){
        URI uri = null;
        try {
            uri = method.getURI();
            //GDC REST API supports only JSON so we can hardcode headers for content type negotiation setup
            method.setRequestHeader("Content-Type", "application/json; charset= utf-8");
            method.setRequestHeader("Accept", "application/json");

            //register special cookie policy that is more friendly to cookie's origin path
            method.getParams().setCookiePolicy("AcceptGDCAuthSSTCookiePolicy");

            int statusCode = httpClient.executeMethod(new HostConfiguration(), method, httpState);

            if (isSuccess(statusCode)) {
                return;
            } else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                //incorrect username or password
                throw new RuntimeException("Wrong credentials!");
            } else {
                throw new RuntimeException("Unexpected status code=" + statusCode + " returned by call to uri=" + uri);
            }
        } catch (IOException e) {
            throw new RuntimeException("Exception occured while calling uri=" + uri, e);
        }
    }

    private boolean isSuccess(int statusCode) {
        return statusCode < 400;
    }


    /**
     * The server sets SST cookie to the path /gdc/account/token, but the requests originates on /gdc/account/login.
     * It causes that HTTP client refuses the cookie because of the different path. Therefore this class bypasses validation of
     * the cookie's path.
     */
    public static final class AcceptGDCAuthSSTCookiePolicy extends CookieSpecBase {
        @Override
        public void validate(String host, int port, String path,
                boolean secure, Cookie cookie) throws MalformedCookieException {
            boolean ignoreValidation = TOKEN_URI.equals(cookie.getPath());
            if(!ignoreValidation) {
                super.validate(host, port, path, secure, cookie);
            }
        }
    }

}
