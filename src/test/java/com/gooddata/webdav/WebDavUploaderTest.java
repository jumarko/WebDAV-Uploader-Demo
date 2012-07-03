/*
 * Copyright (C) 2007-2011, GoodData(R) Corporation. All rights reserved.
 */
package com.gooddata.webdav;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test class for demonstration of WebDav authentication and upload mechanism.
 *
 * Check {@link #testSstTokenAuthentication()} for example of SST token based authentication
 * and {@link #testBasicAuthentication()} for normal HTTP BASIC authentication.
 */
public class WebDavUploaderTest {

    /**
     * Path to configuration file containing (only) SST token. Names of configuration properties are as follows.
     * Sample configuration file is located at src/test/resources/com/gooddata/webdav/webdav-sample.conf
     */
    private static final String CONFIG_FILE_PATH = "/opt/.webdav";
    private static final String SST_TOKEN_PROPERTY = "sst_token";
    private static final String LOGIN_PROPERTY = "login";
    private static final String PASSWORD_PROPERTY = "password";
    private static final String WEBDAV_HOST_PROPERTY = "host.webdav";
    private static final String GDC_HOST_PROPERTY = "host.gdc";
    private static Properties configurationProperties;

    @BeforeClass
    public static void readUploaderConfiguration() throws IOException {
        final Properties config = new Properties();
        config.load(new FileInputStream(CONFIG_FILE_PATH));
        configurationProperties = config;
    }

    @Test
    public void testSstTokenAuthentication() throws IOException {

        final WebDavUploader webDavUploader = new WebDavUploader(configurationProperties.getProperty(WEBDAV_HOST_PROPERTY),
                443, "https",
                configurationProperties.getProperty(GDC_HOST_PROPERTY), 443, "https", configurationProperties.getProperty(SST_TOKEN_PROPERTY));
        transferFile(webDavUploader);
    }


    @Test
    public void testBasicAuthentication() throws IOException {
        final WebDavUploader webDavUploader = new WebDavUploader(configurationProperties.getProperty(WEBDAV_HOST_PROPERTY),
                configurationProperties.getProperty(LOGIN_PROPERTY), configurationProperties.getProperty(PASSWORD_PROPERTY));
        transferFile(webDavUploader);
    }

    private void transferFile(WebDavUploader webDavUploader) throws IOException {
        Assert.assertNotNull(webDavUploader);
        // upload file
        File uploadFile = null;
        try {
            uploadFile = File.createTempFile("webdav", "tmp");
            FileUtils.writeStringToFile(uploadFile, "JUST A PLAIN TEXT!");
            webDavUploader.transferFile(uploadFile, "tmp", "webdav-tmp" + System.currentTimeMillis(), "application/octet-stream");

        } finally {
            FileUtils.deleteQuietly(uploadFile);
        }
    }


}
