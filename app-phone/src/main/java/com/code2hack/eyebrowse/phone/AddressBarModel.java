package com.code2hack.eyebrowse.phone;

/**
 * Draft/committed separation for the address control.
 *
 * <p>The field belongs to the user while they are editing: a late {@code onPageFinished} callback
 * must not replace a half-corrected address. The model is pure Java so the rules are unit-tested
 * without Android.
 */
final class AddressBarModel {

    private String draft = "";
    private boolean editing;

    String draft() {
        return draft;
    }

    boolean editing() {
        return editing;
    }

    /** The user typed or pasted into the field; the draft is now authoritative over page commits. */
    void onUserEdit(String text) {
        draft = text == null ? "" : text;
        editing = true;
    }

    /** Bring the draft in line with session state without starting an edit. */
    void syncTo(String url) {
        if (!editing) {
            draft = url == null ? "" : url;
        }
    }

    /** A submitted address was accepted: show the normalized URL and resume following commits. */
    String onSubmittedAccepted(String normalizedUrl) {
        editing = false;
        draft = normalizedUrl == null ? "" : normalizedUrl;
        return draft;
    }

    /** A submitted address was rejected: keep exactly what the user typed for correction. */
    String onSubmittedRejected() {
        editing = true;
        return draft;
    }
}
