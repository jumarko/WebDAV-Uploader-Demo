/*
 * Copyright (C) 2007-2011, GoodData(R) Corporation. All rights reserved.
 */
package com.gooddata.webdav;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import static java.lang.String.format;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.lang.Validate;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.MkColMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebDavUploader {

    private static final String WEBDAV_URI = "/uploads";
    private static final String PUT_TEMPLATE = WEBDAV_URI + "/%s/%s";

    private static final String DIRECTORY_SEPARATOR = "/";

    private static final int DEFAULT_PORT = 443;
    private static final String DEFAULT_PROTOCOL = "https";

    private final HttpClient webDavHttpClient;
    private final String superSecureToken;
    private final WebDavSstAuthenticator gdcAuthenticator;
    private volatile UserLogin userLogin;

    private final Logger logger = LoggerFactory.getLogger(getClass());



    /** @see #WebDavUploader(String, String, String, int, String)   */
    public WebDavUploader(final String webDavHost, final String username, final String password) {
        this(webDavHost, username, password, DEFAULT_PORT, DEFAULT_PROTOCOL);
    }

    /**
     * Creates new instance of {@link WebDavUploader} which will use given {@code username} and {@code password}
     * for authentication against WebDAV located at {@code webDavHost}.
     *
     * @param webDavHost hostname of WebDAV
     * @param username username for BASIC authentication
     * @param password password for BASIC authentication
     * @param webDavPort WebDAV port, typically 443
     * @param webDavProtocol protocol, typically https
     */
    public WebDavUploader(final String webDavHost, final String username, final String password,
            final int webDavPort, final String webDavProtocol) {
        final HttpClient httpClient = createHttpClient(webDavHost, webDavPort, webDavProtocol);
        httpClient.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        this.webDavHttpClient = httpClient;
        this.superSecureToken = null;
        this.gdcAuthenticator = null;
    }


    /**
     * Creates new instance of {@link WebDavUploader} which will use given {@code superSecureToken}
     * for authentication against WebDAV located at {@code webDavHost}.
     *
     * @param webDavHost WebDAV hostname
     * @param webDavPort WebDAV port, typically 443
     * @param webDavProtocol WebDAV protocol, typically https     * @param gdcHost
     * @param gdcHost hostname for GDC rest api - used by {@link WebDavSstAuthenticator} for authentication via given
     *                {@code superSecureToken}
     * @param gdcPort port for GDC rest api
     * @param gdcProtocol protocol for GDC rest api
     * @param superSecureToken Super secure token used for WebDAV authentication
     */
    public WebDavUploader(final String webDavHost, final int webDavPort, final String webDavProtocol,
            final String gdcHost, final int gdcPort, final String gdcProtocol, String superSecureToken) {
        Validate.notEmpty(superSecureToken, "Super secure token must not be empty to be able to authenticate against webdav!");

        this.webDavHttpClient = createHttpClient(webDavHost, webDavPort, webDavProtocol);
        this.superSecureToken= superSecureToken;
        this.gdcAuthenticator = new WebDavSstAuthenticator(gdcProtocol, gdcHost, gdcPort);
        logger.debug("New instance of WebDavUploader using SST token for authentication has been created.");
    }


    public void transferFile(final File fileToUpload, final String remoteDir, final String remoteFileName,
            final String contentType) {
        Validate.notNull(fileToUpload, "File for upload must be defined!");
        Validate.isTrue(fileToUpload.isFile(), format("File for upload=%s must exist!", fileToUpload.getAbsolutePath()));

        //upload the file
        transferRequestEntity(new FileRequestEntity(fileToUpload, contentType), remoteDir, remoteFileName, contentType);
    }



    public void transferInputStream(final InputStream inputStreamToUpload, final String remoteDir,
            final String remoteFileName, final String contentType) {
        Validate.notNull(inputStreamToUpload, "InputStream for upload must be defined!");
        transferRequestEntity(new InputStreamRequestEntity(inputStreamToUpload, contentType), remoteDir, remoteFileName, contentType);
    }



    public void transferRequestEntity(final RequestEntity requestEntityToUpload, final String remoteDir,
            final String remoteFileName, final String contentType) {
        Validate.notNull(requestEntityToUpload, "RequestEntity for uploading must be defined!");
        Validate.notEmpty(remoteDir, "remote dir must be defined!");
        Validate.notEmpty(remoteFileName, "remoteFileName must be defined");

        //create new remote directory with all subdirectories
        final String[] remoteDirectories = remoteDir.split(DIRECTORY_SEPARATOR);
        String parentDirectory = WEBDAV_URI;
        for (String remoteDirectory : remoteDirectories) {
            final String newDirectory = parentDirectory + "/" + remoteDirectory;
            final MkColMethod mkdir = new MkColMethod(newDirectory);
            //if the remote dir already exists, 301 is returned; the directory should not exist now
            this.executeAndReleaseMethod(mkdir, HttpStatus.SC_CREATED, HttpStatus.SC_MOVED_PERMANENTLY);

            // newDirectory is the parent directory for next subdirectory
            parentDirectory = newDirectory;
        }

        //upload the file
        final PutMethod put = new PutMethod(String.format(PUT_TEMPLATE, remoteDir, remoteFileName));

        put.setRequestEntity(requestEntityToUpload);
        //if the file already existed, 204 is returned instead of 201
        logger.info("action=webdav_upload status=start");
        try {
            this.executeAndReleaseMethod(put, HttpStatus.SC_CREATED);
        } catch (WebDavUploaderException e) {
            logger.info("action=webdav_upload status=error");
            throw e;
        }
        logger.info("action=webdav_upload status=finished");
    }





    //--------------------------------------------------- PRIVATE STUFF ------------------------------------------------

    /**
     * Creates and configures new instance of {@link HttpClient} suitable for both BASIC and token based authentication
     * schemes.
     */
    private HttpClient createHttpClient(String host, int port, String protocol) {
        Validate.notEmpty(host, "webdav host cannot be empty");
        Validate.isTrue(port > 0 && port < 65536, "valid webdav port must be specified");
        Validate.notEmpty(protocol, "webdav protocol cannot be empty");

        final MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();

        final HostConfiguration hostConfig = new HostConfiguration();
        hostConfig.setHost(host, port, protocol);

        final HttpClient httpClient = new HttpClient(connectionManager);
        httpClient.setHostConfiguration(hostConfig);
        return httpClient;
    }



    private String executeAndReleaseMethod(final HttpMethod method, final int... acceptedStatus) {
        try {
            try {
                executeMethod(method);
            } catch (final IOException e) {
                throw new WebDavUploaderException(
                        String.format("A problem occured while executing %s on %s!", method.getName(), method.getPath()), e);
            }

            final String resp;
            try {
                resp = method.getResponseBodyAsString();
            } catch (final IOException e) {
                throw new WebDavUploaderException("A problem occured while retrieving the response body!", e);
            }

            // returned status should be any of acceptedStatus array
            for (int status : acceptedStatus) {
                if (status == method.getStatusCode()) {
                    return resp;
                }
            }

            throw new WebDavUploaderException(
                    String.format("Something went wrong while executing %s on %s. %s expected, %d returned instead with response '%s'!",
                            method.getName(),
                            method.getPath(),
                            Arrays.toString(acceptedStatus),
                            method.getStatusCode(),
                            resp));
        } finally {
            method.releaseConnection();
        }
    }


    private List<String> executeAndReleasePropFindMethod(final PropFindMethod method) {

        try {
            final List<String> res = new ArrayList<String>();
            MultiStatus ms;

            try {
                executeMethod(method);
            } catch (final IOException e) {
                throw new WebDavUploaderException(
                        String.format("A problem occured while executing %s on %s!", method.getName(), method.getPath()), e);
            }

            try {
                ms = method.getResponseBodyAsMultiStatus();
            } catch (IOException e) {
                throw new WebDavUploaderException("A problem occured while retrieving the webDav multi status body", e);
            } catch (DavException e) {
                throw new WebDavUploaderException("A problem occured while retrieving the webDav multi status body", e);
            }

            if (method.getStatusCode() != HttpStatus.SC_MULTI_STATUS) {
                throw new WebDavUploaderException(String.format("Something went wrong while executing a PROPFIND method on %s."
                        + "%d expected, %d returned instead",
                        method.getPath(),
                        HttpStatus.SC_MULTI_STATUS,
                        method.getStatusCode()));
            }

            final MultiStatusResponse[] responses = ms.getResponses();

            for (final MultiStatusResponse resp : responses) {
                res.add(resp.getHref());
            }

            return res;
        } finally {
            method.releaseConnection();
        }
    }


    private void executeMethod(HttpMethod method) throws IOException {

        preAuthenticate();

        final int responseStatus = webDavHttpClient.executeMethod(method);

        if (responseStatus == HttpStatus.SC_UNAUTHORIZED && SstTokenAuthenticationUsed()) {
            // Temporary token is probably expired (default validity 600 secs,
            // see bear.git/resources/httpd/resources/global_variables.conf - key "TT_validity")
            logger.info("action=webdav_upload status=tt_token_expired reauthenticate using user's SST token");
            authenticate(method);
        }
    }

    private void authenticate(HttpMethod method) throws IOException {
        if (gdcAuthenticator == null) {
            throw new IllegalStateException("GdcAuthenticator must be set for authentication via sst token");
        }
        //#12842 release the connection before we try re-authenticate
        //because authentication itself allocates a new connection => deadlock
        method.releaseConnection();
        userLogin = gdcAuthenticator.authenticate(superSecureToken);
        if (userLogin != null) {
            preAuthenticate();
            //after successful authentication re-send the request
            logger.debug("action=webdav_upload status=RESENDING_REQUEST target_uri=" + method.getURI().toString());
            webDavHttpClient.executeMethod(method);
        }
    }

    private void preAuthenticate() {
        if (SstTokenAuthenticationUsed() && userLogin != null) {
            addCookie(WebDavSstAuthenticator.GDCAuthTT_COOKIE, userLogin.getGdcAuthTT());
        }
    }


    private boolean SstTokenAuthenticationUsed() {
        return superSecureToken != null;
    }

    private void addCookie(String cookieName, String cookieValue) {
        Validate.notNull(webDavHttpClient, "HttpClient must be set prior setting of cookies!");
        Validate.notNull(webDavHttpClient.getHostConfiguration(), "HttpClient.hostConfiguration must be set prior setting of cookies!");
        final Cookie cookie = new Cookie();
        cookie.setDomain(webDavHttpClient.getHostConfiguration().getHost());
        cookie.setName(cookieName);
        cookie.setValue(cookieValue);
        cookie.setPath("/");
        this.webDavHttpClient.getState().addCookie(cookie);
    }


}
