package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The address draft belongs to the user while they correct it; page state must not clobber it. */
public class AddressBarModelTest {

    @Test
    public void startsEmptyAndNotEditing() {
        AddressBarModel model = new AddressBarModel();
        assertEquals("", model.draft());
        assertFalse(model.editing());
    }

    @Test
    public void sessionStateFillsTheDraftWhenTheUserIsNotEditing() {
        AddressBarModel model = new AddressBarModel();
        model.syncTo("https://example.com/a");
        assertEquals("https://example.com/a", model.draft());
        model.syncTo(null);
        assertEquals("", model.draft());
    }

    @Test
    public void editingDraftSurvivesLateSessionUpdates() {
        AddressBarModel model = new AddressBarModel();
        model.syncTo("https://example.com/a");
        model.onUserEdit("https://exa");
        model.syncTo("https://example.com/b");
        assertEquals("https://exa", model.draft());
        assertTrue(model.editing());
    }

    @Test
    public void rejectedSubmissionKeepsExactlyTheTypedDraft() {
        AddressBarModel model = new AddressBarModel();
        model.syncTo("https://example.com/a");
        model.onUserEdit("exa mple.com");
        model.onSubmittedRejected();
        assertEquals("exa mple.com", model.draft());
        assertTrue(model.editing());
    }

    @Test
    public void acceptedSubmissionShowsTheNormalizedUrlAndResumesFollowingCommits() {
        AddressBarModel model = new AddressBarModel();
        model.onUserEdit("example.com");
        String shown = model.onSubmittedAccepted("https://example.com");
        assertEquals("https://example.com", shown);
        assertEquals("https://example.com", model.draft());
        assertFalse(model.editing());
        model.syncTo("https://example.com/next");
        assertEquals("https://example.com/next", model.draft());
    }

    @Test
    public void nullEditsAreTreatedAsEmptyText() {
        AddressBarModel model = new AddressBarModel();
        model.onUserEdit(null);
        assertEquals("", model.draft());
        assertTrue(model.editing());
    }
}
