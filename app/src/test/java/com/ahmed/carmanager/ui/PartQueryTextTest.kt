package com.ahmed.carmanager.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PartQueryTextTest {
    @Test
    fun oemNumber_keepsAsciiHyphen() {
        assertEquals("26300-35505", PartQueryText.normalizeSeparators("26300-35505"))
    }

    @Test
    fun alphanumericPartCode_keepsHyphen() {
        assertEquals("G4FG-123", PartQueryText.normalizeSeparators("G4FG-123"))
    }

    @Test
    fun legacySplitNumericOem_isRepaired() {
        assertEquals("26300-35505", PartQueryText.normalizeSeparators("26300 35505"))
    }

    @Test
    fun legacySplitAlphanumericOem_isRepaired() {
        assertEquals("28113-F2000", PartQueryText.normalizeSeparators("28113 F2000"))
        assertEquals("G4FG-123", PartQueryText.normalizeSeparators("G4FG 123"))
    }

    @Test
    fun vehicleYearAndEngineCapacity_areNotJoined() {
        assertEquals("2021 1600", PartQueryText.normalizeSeparators("2021 1600"))
    }

    @Test
    fun textualHyphen_becomesSpace() {
        assertEquals("brake fluid", PartQueryText.normalizeSeparators("brake-fluid"))
    }

    @Test
    fun brakeFluidArabicAlias_usesCommonEgyptianStoreTerm() {
        assertEquals("زيت فرامل", PartQueryText.normalizeSeparators("سائل الفرامل"))
        assertEquals("زيت فرامل", PartQueryText.normalizeSeparators("سائل فرامل"))
        assertEquals("زيت فرامل", PartQueryText.normalizeSeparators("زيت الفرامل"))
    }

    @Test
    fun longDash_isAlwaysSeparator() {
        assertEquals("فلتر زيت", PartQueryText.normalizeSeparators("فلتر—زيت"))
    }
}
