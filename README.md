# Sync4 & Fiat Egea Car Companion

**Fiat Egea 2020 (1.4 Fire — Uconnect™ 7" HD Live)** ve **Ford Focus (SYNC 4 — 13.2" Geniş Ekran)** multimedya sistemleri için geliştirilmiş Android Auto yardımcısı, ekran yakalama (MediaProjection) kontrol paneli ve tam ekran HTML5 Web Tarayıcısı.

## Özellikler

- **Çoklu Araç Multimedya Desteği:**
  - **Fiat Egea 2020 (1.4 Fire):** Uconnect™ 7" HD Live ekranı için optimize edilmiş `800×480 (160 DPI)` ve `1024×600 (200 DPI)` akıcı 30 FPS profilleri.
  - **Ford Focus SYNC 4:** 13.2" geniş ekran için `1280×720` ve `1920×1080` profilleri.
- **Android Auto Araç Arayüzü (`androidx.car.app`):**
  - Araç ekranından doğrudan **Araç Modeli Seçimi**, hızlı yer imleri (YouTube Web, TRT tabii, Fiat Türkiye, Ford Türkiye, Google) ve anlık oturum durumu takibi.
  - Uconnect 7" (`800×480`) sürüş şablon sınırlarına (`PaneTemplate` & `ListTemplate`) tam uyumluluk.
- **Dahili HTML5 Web & Video Tarayıcı:**
  - YouTube ve canlı yayınlar için tam ekran (`Immersive Sticky`) video oynatma desteği, masaüstü/mobil görünüm geçişi ve SPA geri/ileri geçmiş yönetimi.
- **Güvenli Ön Plan Ekran Yansıtma Servisi:**
  - Android 14 / 15 (`targetSdk 35`) `FOREGROUND_SERVICE_MEDIA_PROJECTION` uyumlu servis mimarisi ve Direct Boot (kilitli ekranda USB bağlantısı) koruması.

## Kurulum ve Araç Bağlantısı

1. Projeyi Android Studio ile açıp derleyin veya `Releases` bölümünden / `app/build/outputs/apk/release/app-release.apk` konumundan APK dosyasını telefonunuza yükleyin.
2. Telefonunuzda **Ayarlar > Uygulamalar > Android Auto > Uygulama içi ek ayarlar** menüsüne gidin.
3. En alttaki **Sürüm** satırına 10 kez dokunarak Geliştirici Modunu açın.
4. Sağ üstteki üç nokta (⋮) menüsünden **Geliştirici ayarları**'na girip **Bilinmeyen kaynaklar (Unknown sources)** kutucuğunu işaretleyin ve **Uygulama modu**'nu **Geliştirici** yapın.
5. **Fiat Egea 2020 (1.4 Fire)** için telefonunuzu veri aktarım (data) destekli bir USB kabloyla vites önündeki USB portuna bağlayın.

## Teknolojiler

- **Dil & UI:** Kotlin 2.4, Jetpack Compose (BOM 2026.11), Material 3
- **Derleme & SDK:** AGP 9.5, Gradle 9.9, `compileSdk 37.2` / `targetSdk 37`
- **Araç Entegrasyonu:** Jetpack Car App Library (`1.7.0`)
- **Mimari:** Coroutines `StateFlow` reaktif durum köprüsü, `MediaProjection` ön plan servisi
