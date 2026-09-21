package com.code2hack.eyebrowse.phone
import org.junit.Assert.*
import org.junit.Test

class HostingPresentationProfileTest {
    @Test fun fallbackIsStableAndNotPhoneDensity() {
        assertEquals(HostingPresentationProfile(480, 640, 160), HostingPresentationProfile.RG_DESIGN_FALLBACK)
    }
    @Test fun reportedContentProfileNeedNotEqualFallback() {
        assertNotEquals(HostingPresentationProfile.RG_DESIGN_FALLBACK, HostingPresentationProfile(480, 480, 240))
    }
    @Test fun phonePaddingIsNotCaptureGeometry() {
        assertEquals(1034 to 1770, PhoneContentViewport.size(1038, 1776, 2, 3, 2, 3))
        assertEquals(480, HostingPresentationProfile.RG_DESIGN_FALLBACK.width)
    }
    @Test fun phoneRotationDoesNotChangePrivateProfile() {
        val epochs = PresentationEpochs()
        val epoch = epochs.begin(4, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        PhoneContentViewport.size(1034,1772,0,0,0,0)
        PhoneContentViewport.size(2239,499,0,0,0,0)
        assertSame(epoch, epochs.current)
        assertEquals(HostingPresentationProfile(480,640,160), epoch.profile)
    }
    @Test fun staleCallbackCannotOwnSuccessor() {
        val epochs = PresentationEpochs()
        val a = epochs.begin(1, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        epochs.retire()
        val b = epochs.begin(2, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        assertFalse(epochs.owns(a)); assertTrue(epochs.owns(b))
    }
    @Test fun sameGenerationNumberStillDoesNotAuthorizeOldToken() {
        val epochs = PresentationEpochs()
        val a = epochs.begin(1, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        val b = epochs.begin(1, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        assertFalse(epochs.owns(a)); assertTrue(epochs.owns(b))
    }
    @Test fun retiredGenerationHasNoAuthority() {
        val epochs = PresentationEpochs()
        val a = epochs.begin(1, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        epochs.retire()
        assertFalse(epochs.owns(a)); assertNull(epochs.current)
    }
    @Test fun invalidProfileIsRejectedNotClamped() {
        assertThrows(IllegalArgumentException::class.java) { HostingPresentationProfile(0,640,160) }
        assertThrows(IllegalArgumentException::class.java) { HostingPresentationProfile(4096,4096,160) }
        assertThrows(IllegalArgumentException::class.java) { HostingPresentationProfile(480,640,0) }
    }
    @Test fun transientUnlaidOutPhoneIsNotPrivateFallback() {
        assertEquals(0 to 0, PhoneContentViewport.size(0,0,3,3,3,3))
    }
}
