package com.example.myapplication.domain.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseVersionComparerTest {

    @Test
    fun same_core_stable_vs_plain_no_banner() {
        assertFalse(AppReleaseVersionComparer.isRemoteSemanticallyNewer("3.2.6", "v3.2.6-Stable"))
    }

    @Test
    fun same_core_debug_vs_stable_no_banner() {
        assertFalse(AppReleaseVersionComparer.isRemoteSemanticallyNewer("3.2.6-debug", "v3.2.6-Stable"))
    }

    @Test
    fun newer_patch_returns_true() {
        assertTrue(AppReleaseVersionComparer.isRemoteSemanticallyNewer("v3.2.5", "3.2.6"))
        assertTrue(AppReleaseVersionComparer.isRemoteSemanticallyNewer("3.2.5", "v3.2.6-Stable"))
    }

    @Test
    fun equal_release_no_banner() {
        assertFalse(AppReleaseVersionComparer.isRemoteSemanticallyNewer("3.2.6", "v3.2.6"))
        assertFalse(AppReleaseVersionComparer.isRemoteSemanticallyNewer("v3.2.6", "v3.2.6"))
    }

    @Test
    fun local_newer_than_remote_no_banner() {
        assertFalse(AppReleaseVersionComparer.isRemoteSemanticallyNewer("v3.2.8", "3.2.7"))
    }

    @Test
    fun same_triple_dual_prerelease_no_banner() {
        assertFalse(AppReleaseVersionComparer.isRemoteSemanticallyNewer("1.0.0-beta.1", "v1.0.0-rc.1"))
    }
}
