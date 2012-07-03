/*
 * Copyright (C) 2007-2011, GoodData(R) Corporation. All rights reserved.
 */
package com.gooddata.webdav;

/**
 * Specific exception thrown when some error at {@link WebDavUploader}'s upload process occurs.
 */
public class WebDavUploaderException extends RuntimeException {
    public WebDavUploaderException(String message) {
        super(message);
    }

    public WebDavUploaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
