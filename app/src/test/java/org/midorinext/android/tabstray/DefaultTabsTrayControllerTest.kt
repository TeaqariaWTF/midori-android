/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tabstray

import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.profiler.Profiler
import mozilla.components.feature.tabs.TabsUseCases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.R
import org.midorinext.android.browser.browsingmode.BrowsingModeManager
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.home.HomeFragment

@RunWith(MidoriRobolectricTestRunner::class)
class DefaultTabsTrayControllerTest {
    @MockK(relaxed = true)
    private lateinit var trayStore: TabsTrayStore

    @MockK(relaxed = true)
    private lateinit var browserStore: BrowserStore

    @MockK(relaxed = true)
    private lateinit var browsingModeManager: BrowsingModeManager

    @MockK(relaxed = true)
    private lateinit var navController: NavController

    @MockK(relaxed = true)
    private lateinit var profiler: Profiler

    @MockK(relaxed = true)
    private lateinit var navigationInteractor: NavigationInteractor

    @MockK(relaxed = true)
    private lateinit var tabsUseCases: TabsUseCases

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `GIVEN private mode WHEN handleOpeningNewTab is called THEN a profile marker is added for the operations executed`() {
        profiler = spyk(profiler) {
            every { getProfilerTime() } returns Double.MAX_VALUE
        }

        createController().handleOpeningNewTab(true)

        verifyOrder {
            profiler.getProfilerTime()
            navController.navigate(
                TabsTrayFragmentDirections.actionGlobalHome(focusOnAddressBar = true)
            )
            navigationInteractor.onTabTrayDismissed()
            profiler.addMarker(
                "DefaultTabTrayController.onNewTabTapped",
                Double.MAX_VALUE
            )
        }
    }

    @Test
    fun `GIVEN normal mode WHEN handleOpeningNewTab is called THEN a profile marker is added for the operations executed`() {
        profiler = spyk(profiler) {
            every { getProfilerTime() } returns Double.MAX_VALUE
        }

        createController().handleOpeningNewTab(false)

        verifyOrder {
            profiler.getProfilerTime()
            navController.navigate(
                TabsTrayFragmentDirections.actionGlobalHome(focusOnAddressBar = true)
            )
            navigationInteractor.onTabTrayDismissed()
            profiler.addMarker(
                "DefaultTabTrayController.onNewTabTapped",
                Double.MAX_VALUE
            )
        }
    }

    @Test
    fun `GIVEN active private download WHEN handleTabDeletion is called for the last private tab THEN showCancelledDownloadWarning is called`() {
        var showCancelledDownloadWarningInvoked = false
        val controller = spyk(
            createController(
                showCancelledDownloadWarning = { _, _, _ ->
                    showCancelledDownloadWarningInvoked = true
                }
            )
        )
        val tab: TabSessionState = mockk { every { content.private } returns true }
        every { browserStore.state } returns mockk()
        every { browserStore.state.downloads } returns mapOf(
            "1" to DownloadState(
                "https://mozilla.org/download",
                private = true,
                destinationDirectory = "Download",
                status = DownloadState.Status.DOWNLOADING
            )
        )
        try {
            mockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
            every { browserStore.state.findTab("testTabId") } returns tab
            every { browserStore.state.getNormalOrPrivateTabs(any()) } returns listOf(tab)

            controller.handleTabDeletion("testTabId", "unknown")

            assertTrue(showCancelledDownloadWarningInvoked)
        } finally {
            unmockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
        }
    }

    @Test
    fun `WHEN handleTrayScrollingToPosition is called with smoothScroll=true THEN it scrolls to that position with smoothScroll`() {
        var selectTabPositionInvoked = false
        createController(
            selectTabPosition = { position, smoothScroll ->
                assertEquals(3, position)
                assertTrue(smoothScroll)
                selectTabPositionInvoked = true
            }
        ).handleTrayScrollingToPosition(3, true)

        assertTrue(selectTabPositionInvoked)
    }

    @Test
    fun `WHEN handleTrayScrollingToPosition is called with smoothScroll=true THEN it emits an action for the tray page of that tab position`() {
        createController().handleTrayScrollingToPosition(33, true)

        verify { trayStore.dispatch(TabsTrayAction.PageSelected(Page.positionToPage(33))) }
    }

    @Test
    fun `WHEN handleTrayScrollingToPosition is called with smoothScroll=false THEN it emits an action for the tray page of that tab position`() {
        createController().handleTrayScrollingToPosition(44, true)

        verify { trayStore.dispatch(TabsTrayAction.PageSelected(Page.positionToPage(44))) }
    }

    @Test
    fun `GIVEN already on browserFragment WHEN handleNavigateToBrowser is called THEN the tray is dismissed`() {
        every { navController.currentDestination?.id } returns R.id.browserFragment

        var dismissTrayInvoked = false
        createController(dismissTray = { dismissTrayInvoked = true }).handleNavigateToBrowser()

        assertTrue(dismissTrayInvoked)
        verify(exactly = 0) { navController.popBackStack() }
        verify(exactly = 0) { navController.popBackStack(any<Int>(), any()) }
        verify(exactly = 0) { navController.navigate(any<Int>()) }
        verify(exactly = 0) { navController.navigate(any<NavDirections>()) }
        verify(exactly = 0) { navController.navigate(any<NavDirections>(), any<NavOptions>()) }
    }

    @Test
    fun `GIVEN not already on browserFragment WHEN handleNavigateToBrowser is called THEN the tray is dismissed and popBackStack is executed`() {
        every { navController.currentDestination?.id } returns R.id.browserFragment + 1
        every { navController.popBackStack(R.id.browserFragment, false) } returns true

        var dismissTrayInvoked = false
        createController(dismissTray = { dismissTrayInvoked = true }).handleNavigateToBrowser()

        assertTrue(dismissTrayInvoked)
        verify { navController.popBackStack(R.id.browserFragment, false) }
        verify(exactly = 0) { navController.navigate(any<Int>()) }
        verify(exactly = 0) { navController.navigate(any<NavDirections>()) }
        verify(exactly = 0) { navController.navigate(any<NavDirections>(), any<NavOptions>()) }
    }

    @Test
    fun `GIVEN not already on browserFragment WHEN handleNavigateToBrowser is called and popBackStack fails THEN it navigates to browserFragment`() {
        every { navController.currentDestination?.id } returns R.id.browserFragment + 1
        every { navController.popBackStack(R.id.browserFragment, false) } returns false

        var dismissTrayInvoked = false
        createController(dismissTray = { dismissTrayInvoked = true }).handleNavigateToBrowser()

        assertTrue(dismissTrayInvoked)
        verify { navController.popBackStack(R.id.browserFragment, false) }
        verify { navController.navigate(R.id.browserFragment) }
    }

    @Test
    fun `GIVEN not already on browserFragment WHEN handleNavigateToBrowser is called and popBackStack succeeds THEN the method finishes`() {
        every { navController.popBackStack(R.id.browserFragment, false) } returns true

        var dismissTrayInvoked = false
        createController(dismissTray = { dismissTrayInvoked = true }).handleNavigateToBrowser()

        assertTrue(dismissTrayInvoked)
        verify(exactly = 1) { navController.popBackStack(R.id.browserFragment, false) }
        verify(exactly = 0) { navController.navigate(R.id.browserFragment) }
    }

    @Test
    fun `GIVEN more tabs opened WHEN handleTabDeletion is called THEN that tab is removed and an undo snackbar is shown`() {
        val tab: TabSessionState = mockk {
            every { content } returns mockk()
            every { content.private } returns true
        }
        every { browserStore.state } returns mockk()
        try {
            mockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
            every { browserStore.state.findTab("22") } returns tab
            every { browserStore.state.getNormalOrPrivateTabs(any()) } returns listOf(tab, mockk())

            var showUndoSnackbarForTabInvoked = false
            createController(
                showUndoSnackbarForTab = {
                    assertTrue(it)
                    showUndoSnackbarForTabInvoked = true
                }
            ).handleTabDeletion("22")

            verify { tabsUseCases.removeTab("22") }
            assertTrue(showUndoSnackbarForTabInvoked)
        } finally {
            unmockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
        }
    }

    @Test
    fun `GIVEN only one tab opened WHEN handleTabDeletion is called THEN that it navigates to home where the tab will be removed`() {
        var showUndoSnackbarForTabInvoked = false
        val controller = spyk(createController(showUndoSnackbarForTab = { showUndoSnackbarForTabInvoked = true }))
        val tab: TabSessionState = mockk {
            every { content } returns mockk()
            every { content.private } returns true
        }
        every { browserStore.state } returns mockk()
        try {
            mockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
            every { browserStore.state.findTab(testTabId) } returns tab
            every { browserStore.state.getNormalOrPrivateTabs(any()) } returns listOf(tab)

            controller.handleTabDeletion("33")

            verify { controller.dismissTabsTrayAndNavigateHome("33") }
            verify(exactly = 0) { tabsUseCases.removeTab(any()) }
            assertFalse(showUndoSnackbarForTabInvoked)
        } finally {
            unmockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
        }
    }

    @Test
    fun `WHEN handleMultipleTabsDeletion is called to close all private tabs THEN that it navigates to home where that tabs will be removed and shows undo snackbar`() {
        var showUndoSnackbarForTabInvoked = false
        val controller = spyk(
            createController(
                showUndoSnackbarForTab = {
                    assertTrue(it)
                    showUndoSnackbarForTabInvoked = true
                }
            )
        )

        val privateTab = createTab(url = "url", private = true)

        every { browserStore.state } returns mockk()
        try {
            mockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
            every { browserStore.state.getNormalOrPrivateTabs(any()) } returns listOf(mockk(), mockk())

            controller.handleMultipleTabsDeletion(listOf(privateTab, mockk()))

            verify { controller.dismissTabsTrayAndNavigateHome(HomeFragment.ALL_PRIVATE_TABS) }
            assertTrue(showUndoSnackbarForTabInvoked)
            verify(exactly = 0) { tabsUseCases.removeTabs(any()) }
        } finally {
            unmockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
        }
    }

    @Test
    fun `WHEN handleMultipleTabsDeletion is called to close all normal tabs THEN that it navigates to home where that tabs will be removed and shows undo snackbar`() {
        var showUndoSnackbarForTabInvoked = false
        val controller = spyk(
            createController(
                showUndoSnackbarForTab = {
                    assertFalse(it)
                    showUndoSnackbarForTabInvoked = true
                }
            )
        )

        val normalTab = createTab(url = "url", private = false)

        every { browserStore.state } returns mockk()
        try {
            mockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
            every { browserStore.state.getNormalOrPrivateTabs(any()) } returns listOf(mockk(), mockk())

            controller.handleMultipleTabsDeletion(listOf(normalTab, normalTab))

            verify { controller.dismissTabsTrayAndNavigateHome(HomeFragment.ALL_NORMAL_TABS) }
            verify(exactly = 0) { tabsUseCases.removeTabs(any()) }
            assertTrue(showUndoSnackbarForTabInvoked)
        } finally {
            unmockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
        }
    }

    @Test
    fun `WHEN handleMultipleTabsDeletion is called to close some private tabs THEN that it uses tabsUseCases#removeTabs and shows an undo snackbar`() {
        var showUndoSnackbarForTabInvoked = false
        val controller = spyk(createController(showUndoSnackbarForTab = { showUndoSnackbarForTabInvoked = true }))
        val privateTab = createTab(id = "42", url = "url", private = true)

        every { browserStore.state } returns mockk()
        try {
            mockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
            every { browserStore.state.getNormalOrPrivateTabs(any()) } returns listOf(mockk(), mockk())

            controller.handleMultipleTabsDeletion(listOf(privateTab))

            verify { tabsUseCases.removeTabs(listOf("42")) }
            verify(exactly = 0) { controller.dismissTabsTrayAndNavigateHome(any()) }
            assertTrue(showUndoSnackbarForTabInvoked)
        } finally {
            unmockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
        }
    }

    @Test
    fun `WHEN handleMultipleTabsDeletion is called to close some normal tabs THEN that it uses tabsUseCases#removeTabs and shows an undo snackbar`() {
        var showUndoSnackbarForTabInvoked = false
        val controller = spyk(createController(showUndoSnackbarForTab = { showUndoSnackbarForTabInvoked = true }))
        val privateTab = createTab(id = "24", url = "url", private = false)

        every { browserStore.state } returns mockk()
        try {
            mockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
            every { browserStore.state.getNormalOrPrivateTabs(any()) } returns listOf(mockk(), mockk())

            controller.handleMultipleTabsDeletion(listOf(privateTab))

            verify { tabsUseCases.removeTabs(listOf("24")) }
            verify(exactly = 0) { controller.dismissTabsTrayAndNavigateHome(any()) }
            assertTrue(showUndoSnackbarForTabInvoked)
        } finally {
            unmockkStatic("mozilla.components.browser.state.selector.SelectorsKt")
        }
    }

    @Test
    fun `WHEN dismissTabsTrayAndNavigateHome is called with a specific tab id THEN tray is dismissed and navigates home is opened to delete that tab`() {
        var dismissTrayInvoked = false
        var navigateToHomeAndDeleteSessionInvoked = false
        createController(
            dismissTray = {
                dismissTrayInvoked = true
            },
            navigateToHomeAndDeleteSession = {
                assertEquals("randomId", it)
                navigateToHomeAndDeleteSessionInvoked = true
            }
        ).dismissTabsTrayAndNavigateHome("randomId")

        assertTrue(dismissTrayInvoked)
        assertTrue(navigateToHomeAndDeleteSessionInvoked)
    }

    private fun createController(
        navigateToHomeAndDeleteSession: (String) -> Unit = { },
        selectTabPosition: (Int, Boolean) -> Unit = { _, _ -> },
        dismissTray: () -> Unit = { },
        showUndoSnackbarForTab: (Boolean) -> Unit = { _ -> },
        showCancelledDownloadWarning: (Int, String?, String?) -> Unit = { _, _, _ -> }
    ): DefaultTabsTrayController {
        return DefaultTabsTrayController(
            trayStore,
            browserStore,
            browsingModeManager,
            navController,
            navigateToHomeAndDeleteSession,
            profiler,
            navigationInteractor,
            tabsUseCases,
            selectTabPosition,
            dismissTray,
            showUndoSnackbarForTab,
            showCancelledDownloadWarning = showCancelledDownloadWarning
        )
    }
}
