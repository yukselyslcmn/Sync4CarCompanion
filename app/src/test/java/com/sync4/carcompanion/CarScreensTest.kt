package com.sync4.carcompanion

import android.content.Intent
import androidx.car.app.OnDoneCallback
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.serialization.Bundleable
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.TestScreenManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.sync4.carcompanion.car.CompanionBookmarksScreen
import com.sync4.carcompanion.car.CompanionMainScreen
import com.sync4.carcompanion.car.CompanionVehicleScreen
import com.sync4.carcompanion.state.BrowserState
import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.MirrorProfile
import com.sync4.carcompanion.state.MirrorStatus
import com.sync4.carcompanion.state.VehicleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Android Auto (Fiat Uconnect 7" / Ford SYNC 4) şablonlarının içerik ve davranış testleri. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarScreensTest {

    private lateinit var carContext: TestCarContext
    private val repo = CompanionStateRepository

    @Before
    fun setUp() {
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        setApiLevel(7)
        repo.setMirrorStatus(MirrorStatus.Idle)
        repo.selectVehicle(VehicleModel.FIAT_EGEA_2020)
        repo.consumePendingUrl()
        repo.updateBrowserState { BrowserState() }
    }

    /** TestCarContext araç API seviyesini kendisi belirlemez; özel alanı yansıma ile ayarlıyoruz. */
    private fun setApiLevel(level: Int) {
        val f = androidx.car.app.CarContext::class.java.getDeclaredField("mCarAppApiLevel")
        f.isAccessible = true
        f.setInt(carContext, level)
    }

    private fun invokeClick(delegate: Any) {
        val callback = object : OnDoneCallback {
            override fun onSuccess(response: Bundleable?) {}
            override fun onFailure(response: Bundleable) {}
        }
        val m = delegate.javaClass.methods.first { it.name == "sendClick" }
        m.isAccessible = true
        m.invoke(delegate, callback)
    }

    private fun click(row: Row) {
        invokeClick(row.onClickDelegate!!)
    }

    private fun click(action: Action) {
        invokeClick(action.onClickDelegate!!)
    }

    private lateinit var current: androidx.car.app.Screen

    private fun newCtrl(screen: androidx.car.app.Screen): ScreenController {
        current = screen
        val sm = carContext.getCarService(ScreenManager::class.java)
        if (!(sm as TestScreenManager).hasScreens()) {
            sm.push(screen)
        }
        return ScreenController(screen)
    }

    private fun mainTemplate(): PaneTemplate =
        current.onGetTemplate() as PaneTemplate

    // ---------- Ana ekran ----------

    @Test
    fun mainScreen_idle_showsThreeRowsAndTwoActionsWithinUconnectLimits() {
        val c = newCtrl(CompanionMainScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        val pane = mainTemplate().pane

        assertEquals(3, pane.rows.size)
        assertTrue("Pane en fazla 2 aksiyon", pane.actions.size <= 2)
        assertEquals("Araç: Fiat Egea 2020 (1.4 Fire)", pane.rows[0].title.toString())
        assertEquals("Ekran Yansıtma Durumu", pane.rows[1].title.toString())
        assertTrue(pane.rows[1].texts.first().toString().startsWith("Beklemede"))
        assertEquals("Telefonda Aç", pane.actions[0].title.toString())
        assertTrue("Telefonda Aç park halinde zorunlu olmalı", pane.actions[0].onClickDelegate!!.isParkedOnly)
        assertEquals("Web Kısayolları", pane.actions[1].title.toString())
        assertFalse(pane.actions[1].onClickDelegate!!.isParkedOnly)
    }

    @Test
    fun mainScreen_showsVehicleResolutionAndBrowserInfo() {
        repo.updateBrowserState { it.copy(pageTitle = "Egea Forum", currentUrl = "https://forum.example") }
        val c = newCtrl(CompanionMainScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        val rows = mainTemplate().pane.rows

        assertTrue(rows[0].texts.first().toString().contains("800×480"))
        assertEquals("Web: Egea Forum", rows[2].title.toString())
        assertEquals("https://forum.example", rows[2].texts.first().toString())
    }

    @Test
    fun mainScreen_whenMirrorActive_showsStopActionAndResetsOnStopClick() {
        repo.setMirrorStatus(MirrorStatus.Active(MirrorProfile.EGEA_UCONNECT_800, 1L, 42L))
        val c = newCtrl(CompanionMainScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        val pane = mainTemplate().pane

        assertEquals("Yansıtmayı Durdur", pane.actions[0].title.toString())
        assertFalse("Durdurma sürüş sırasında da çalışmalı", pane.actions[0].onClickDelegate!!.isParkedOnly)
        val status = pane.rows[1].texts.first().toString()
        assertTrue(status.contains("Aktif") && status.contains("42 kare"))

        click(pane.actions[0])
        assertEquals(MirrorStatus.Idle, repo.mirrorStatus.value)
    }

    @Test
    fun mainScreen_whenStarting_alsoShowsStopAction() {
        repo.setMirrorStatus(MirrorStatus.Starting)
        val c = newCtrl(CompanionMainScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        assertEquals("Yansıtmayı Durdur", mainTemplate().pane.actions[0].title.toString())
    }

    @Test
    fun mainScreen_whenError_showsErrorMessageAndOpenAction() {
        repo.setMirrorStatus(MirrorStatus.Error("İzin reddedildi"))
        val c = newCtrl(CompanionMainScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        val pane = mainTemplate().pane
        assertEquals("Hata: İzin reddedildi", pane.rows[1].texts.first().toString())
        assertEquals("Telefonda Aç", pane.actions[0].title.toString())
    }

    @Test
    fun mainScreen_refreshesTemplateWhenVehicleChanges() {
        val c = newCtrl(CompanionMainScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)

        repo.selectVehicle(VehicleModel.FORD_FOCUS_SYNC4)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val rows = mainTemplate().pane.rows
        assertEquals("Araç: Ford Focus Titanium", rows[0].title.toString())
        assertTrue(rows[0].texts.first().toString().contains("1280×720"))
    }

    @Test
    fun mainScreen_openOnPhoneAction_startsMainActivity() {
        val c = newCtrl(CompanionMainScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        click(mainTemplate().pane.actions[0])

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val started: Intent? = shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals(MainActivity::class.java.name, started!!.component!!.className)
    }

    @Test
    fun mainScreen_bookmarksAction_pushesBookmarksScreen() {
        val c = newCtrl(CompanionMainScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        click(mainTemplate().pane.actions[1])

        val top = carContext.getCarService(ScreenManager::class.java).top
        assertTrue(top is CompanionBookmarksScreen)
    }

    @Test
    fun mainScreen_headerSwitchVehicleAction_pushesVehicleScreen() {
        val c = newCtrl(CompanionMainScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        val switchAction = mainTemplate().header!!.endHeaderActions[0]
        click(switchAction)

        val top = carContext.getCarService(ScreenManager::class.java).top
        assertTrue(top is CompanionVehicleScreen)
    }

    // ---------- Araç seçim ekranı ----------

    @Test
    fun vehicleScreen_listsAllVehiclesAndMarksSelected() {
        val c = newCtrl(CompanionVehicleScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        val items = (current.onGetTemplate() as ListTemplate).singleList!!.items.map { it as Row }

        assertEquals(VehicleModel.entries.size, items.size)
        assertEquals("✓ Fiat Egea 2020 (1.4 Fire)", items[0].title.toString())
        assertEquals("Ford Focus Titanium", items[1].title.toString())
        assertTrue(items[0].texts.first().toString().contains("800×480"))
        assertTrue(items[1].texts.first().toString().contains("1280×720"))
    }

    @Test
    fun vehicleScreen_clickingFord_selectsVehicleAndPopsScreen() {
        val sm = carContext.getCarService(ScreenManager::class.java) as TestScreenManager
        sm.push(CompanionMainScreen(carContext))
        val vehicleScreen = CompanionVehicleScreen(carContext)
        sm.push(vehicleScreen)
        current = vehicleScreen
        val c = ScreenController(vehicleScreen)
        c.moveToState(Lifecycle.State.RESUMED)

        val items = (current.onGetTemplate() as ListTemplate).singleList!!.items.map { it as Row }
        click(items[1])

        assertEquals(VehicleModel.FORD_FOCUS_SYNC4, repo.selectedVehicle.value)
        assertEquals(MirrorProfile.SYNC4_720P, repo.selectedProfile.value)
        assertTrue(sm.top is CompanionMainScreen)
    }

    @Test
    fun vehicleScreen_afterSwitchingToFord_marksFordWithCheck() {
        repo.selectVehicle(VehicleModel.FORD_FOCUS_SYNC4)
        val c = newCtrl(CompanionVehicleScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        val items = (current.onGetTemplate() as ListTemplate).singleList!!.items.map { it as Row }
        assertEquals("Fiat Egea 2020 (1.4 Fire)", items[0].title.toString())
        assertEquals("✓ Ford Focus Titanium", items[1].title.toString())
    }

    // ---------- Kısayol ekranı ----------

    @Test
    fun bookmarksScreen_matchesDefaultBookmarksAndUconnectLimit() {
        val c = newCtrl(CompanionBookmarksScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        val items = (current.onGetTemplate() as ListTemplate).singleList!!.items.map { it as Row }

        assertEquals(repo.defaultBookmarks.size, items.size)
        assertTrue("Uconnect 7\" için en fazla 6 satır", items.size <= 6)
        repo.defaultBookmarks.forEachIndexed { i, b ->
            assertEquals(b.title, items[i].title.toString())
            assertEquals(b.subtitle, items[i].texts.first().toString())
            assertTrue("Yer imi tıklaması park halinde olmalı", items[i].onClickDelegate!!.isParkedOnly)
        }
    }

    @Test
    fun bookmarksScreen_click_requestsUrlAndLaunchesPhoneAppAndOpensVideoMirrorScreen() {
        val sm = carContext.getCarService(ScreenManager::class.java) as TestScreenManager
        sm.push(CompanionMainScreen(carContext))
        val bookmarksScreen = CompanionBookmarksScreen(carContext)
        sm.push(bookmarksScreen)
        current = bookmarksScreen
        val c = ScreenController(bookmarksScreen)
        c.moveToState(Lifecycle.State.RESUMED)

        val items = (current.onGetTemplate() as ListTemplate).singleList!!.items.map { it as Row }
        click(items[0]) // YouTube Web

        assertEquals("https://m.youtube.com", repo.consumePendingUrl())
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val started = shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals(MainActivity::class.java.name, started.component!!.className)
        assertTrue(sm.top is com.sync4.carcompanion.car.CompanionVideoMirrorScreen)
    }

    @Test
    fun videoMirrorScreen_providesNavigationTemplateWithPlayPauseBookmarksAndPanelActions() {
        val videoScreen = com.sync4.carcompanion.car.CompanionVideoMirrorScreen(carContext)
        val c = newCtrl(videoScreen)
        c.moveToState(Lifecycle.State.RESUMED)

        val template = videoScreen.onGetTemplate() as androidx.car.app.navigation.model.NavigationTemplate
        val actions = template.actionStrip!!.actions
        assertEquals(3, actions.size)
        assertEquals("Oynat / Duraklat", actions[0].title.toString())
        assertEquals("Web Kısayolları", actions[1].title.toString())
        assertEquals("Kontrol Paneli", actions[2].title.toString())
    }

    // ---------- Eski API seviyesi (< 7) başlık yolu ----------

    @Test
    @Suppress("DEPRECATION")
    fun legacyApiLevel_mainScreen_usesTitleAndActionStrip() {
        setApiLevel(6)
        val c = newCtrl(CompanionMainScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        val t = mainTemplate()
        assertEquals("Fiat Egea 1.4 Fire Yardımcı", t.title.toString())
        assertNotNull(t.actionStrip)
        assertEquals("Araç Seç", t.actionStrip!!.actions[0].title.toString())
    }

    @Test
    @Suppress("DEPRECATION")
    fun legacyApiLevel_listScreens_useTitleAndBackAction() {
        setApiLevel(6)
        val vScreen = CompanionVehicleScreen(carContext)
        val v = newCtrl(vScreen)
        v.moveToState(Lifecycle.State.RESUMED)
        val vt = vScreen.onGetTemplate() as ListTemplate
        assertEquals("Araç Profili Seç", vt.title.toString())
        assertEquals(Action.BACK, vt.headerAction)

        val bScreen = CompanionBookmarksScreen(carContext)
        val b = newCtrl(bScreen)
        b.moveToState(Lifecycle.State.RESUMED)
        val bt = bScreen.onGetTemplate() as ListTemplate
        assertEquals("Hızlı Web Kaynakları", bt.title.toString())
        assertEquals(Action.BACK, bt.headerAction)
    }

    @Test
    fun modernApiLevel_mainScreen_usesHeaderWithSwitchVehicleEndAction() {
        val c = newCtrl(CompanionMainScreen(carContext))
        c.moveToState(Lifecycle.State.RESUMED)
        val h = mainTemplate().header!!
        assertEquals("Fiat Egea 1.4 Fire Yardımcı", h.title.toString())
        assertEquals("Araç Seç", h.endHeaderActions[0].title.toString())
    }

    @Test
    fun mainScreen_throttlesInvalidationOnProgressAndSubStepFrames() {
        val trackedScreen = CompanionMainScreen(carContext)
        val c = newCtrl(trackedScreen)
        c.moveToState(Lifecycle.State.RESUMED)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val appManager = carContext.getCarService(androidx.car.app.AppManager::class.java)
            as androidx.car.app.testing.TestAppManager
        appManager.reset()

        // Yalnızca WebView yükleme yüzdesi değiştiğinde araç şablonu yenilenmemeli (kota koruması)
        for (p in listOf(10, 35, 70, 95)) {
            repo.updateBrowserState { it.copy(progress = p, isLoading = true) }
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        assertEquals(0, appManager.templatesReturned.size)

        // Sayfa başlığı değiştiğinde yenilenmeli
        repo.updateBrowserState { it.copy(pageTitle = "Yeni Başlık", isLoading = false, progress = 100) }
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, appManager.templatesReturned.size)

        // Yansıtma aktif olduğunda 1 yenileme, 15..135 kare arası 0 yenileme, 150. karede +1 yenileme
        appManager.reset()
        repo.setMirrorStatus(MirrorStatus.Active(MirrorProfile.EGEA_UCONNECT_800, 100L, 0L))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, appManager.templatesReturned.size)

        for (frames in listOf(15L, 45L, 90L, 135L)) {
            repo.updateCapturedFrames(frames)
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        assertEquals(1, appManager.templatesReturned.size)

        repo.updateCapturedFrames(150L)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(2, appManager.templatesReturned.size)
    }
}
