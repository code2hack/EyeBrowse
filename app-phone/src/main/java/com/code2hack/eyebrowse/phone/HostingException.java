package com.code2hack.eyebrowse.phone;

/** A hosting resource-creation failure carrying the user-reportable reason. */
final class HostingException extends Exception {

    HostingException(String message) {
        super(message);
    }
}
