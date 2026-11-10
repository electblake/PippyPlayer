package app.pippyplayer.tv

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun releaseTagMatchesInstalledVersion() {
        assertEquals(ReleaseVersion.parse("0.4.0"), ReleaseVersion.parse("v0.4.0"))
    }

    @Test fun comparesNumericComponentsRatherThanText() {
        assertTrue(ReleaseVersion.parse("v0.10.0") > ReleaseVersion.parse("0.9.9"))
        assertTrue(ReleaseVersion.parse("v1.0.0") > ReleaseVersion.parse("0.99.99"))
        assertTrue(ReleaseVersion.parse("v0.4.10") > ReleaseVersion.parse("0.4.9"))
    }

    @Test fun doesNotOfferEqualOrOlderVersions() {
        assertFalse(ReleaseVersion.parse("v0.4.0") > ReleaseVersion.parse("0.4.0"))
        assertFalse(ReleaseVersion.parse("v0.3.9") > ReleaseVersion.parse("0.4.0"))
    }
}
