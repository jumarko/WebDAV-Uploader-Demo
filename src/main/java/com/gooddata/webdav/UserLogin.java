/*
 * Copyright (C) 2007-2010, GoodData(R) Corporation. All rights reserved.
 */

package com.gooddata.webdav;

import static org.apache.commons.lang.Validate.notNull;

/**
 * This class represents structure returned after successful login.
 */
public final class UserLogin {

    private final String profileUri;
    private final String state;
    private final String gdcAuthSST;
    private final String gdcAuthTT;

    /**
     * Creates new user login structure.
     *
     * @param profileUri the user's profile uri (optional)
     * @param state user's state uri (optional)
     * @param gdcAuthSST the super secure token
     * @param gdcAuthTT the temporary token
     */
    public UserLogin(final String profileUri, final String state, final String gdcAuthSST, String gdcAuthTT) {
        notNull(gdcAuthSST, "the Super Secure Token must not be null");
        notNull(gdcAuthTT, "the Temporary Token mustnot be null");

        this.profileUri = profileUri;
        this.state = state;
        this.gdcAuthSST = gdcAuthSST;
        this.gdcAuthTT = gdcAuthTT;
    }

    /**
     * Returns the user's profile uri
     * @return the uri or null if it's not known
     */
    public String getProfileUri() {
        return profileUri;
    }

    public String getState() {
        return state;
    }

    /**
     * Returns the super secure token.
     * @return the token
     */
    public String getGdcAuthSST() {
        return gdcAuthSST;
    }

    /**
     * Returns the temporary token.
     * @return the token
     */
    public String getGdcAuthTT() {
        return gdcAuthTT;
    }

    @Override
    public String toString() {
        return "UserLogin [profileUri=" + profileUri + ", state=" + state
                + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((profileUri == null) ? 0 : profileUri.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        UserLogin other = (UserLogin) obj;
        if (profileUri == null) {
            if (other.profileUri != null)
                return false;
        } else if (!profileUri.equals(other.profileUri))
            return false;
        return true;
    }
}