package com.sync4.carcompanion.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.sync4.carcompanion.state.CompanionStateRepository

/**
 * Fiat Egea 2020 (Uconnect 7" HD Live), Ford SYNC 4 ve Desktop Head Unit (DHU) için
 * Android for Cars App Library giriş servisi.
 */
class CompanionCarAppService : CarAppService() {

    override fun onCreate() {
        super.onCreate()
        CompanionStateRepository.init(applicationContext)
    }

    override fun createHostValidator(): HostValidator {
        // Telefona doğrudan APK (sideload) olarak kurulduğunda Fiat Uconnect, Ford SYNC 4,
        // kablosuz Android Auto adaptörleri ve DHU ile kesintisiz çalışması için tüm ana bilgisayarlara izin verilir.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return CompanionCarSession()
    }
}
