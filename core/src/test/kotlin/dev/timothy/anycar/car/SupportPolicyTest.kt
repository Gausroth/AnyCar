package dev.timothy.anycar.car

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportPolicyTest {
    @Test fun unknownYear() = assertEquals(SupportStatus.UNKNOWN, SupportPolicy.status(0, "US"))

    @Test fun pre1996IsNotSupportedAnywhere() {
        assertEquals(SupportStatus.NOT_SUPPORTED, SupportPolicy.status(1995, "US"))
        assertEquals(SupportStatus.NOT_SUPPORTED, SupportPolicy.status(1995, "DE"))
    }

    @Test fun usFrom1996IsSupported() {
        assertEquals(SupportStatus.SUPPORTED, SupportPolicy.status(1996, "US"))
        assertEquals(SupportStatus.SUPPORTED, SupportPolicy.status(2004, "us"))
    }

    @Test fun elsewhere1996To2003MayNotBeFullySupported() {
        assertEquals(SupportStatus.MAY_NOT_BE_FULLY_SUPPORTED, SupportPolicy.status(1996, "GB"))
        assertEquals(SupportStatus.MAY_NOT_BE_FULLY_SUPPORTED, SupportPolicy.status(2003, "GB"))
        assertEquals(SupportStatus.SUPPORTED, SupportPolicy.status(2004, "GB"))
    }
}
