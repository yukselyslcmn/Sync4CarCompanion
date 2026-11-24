# Araç Companion (Fiat Egea Uconnect & Ford SYNC 4) ProGuard / R8 Kuralları
# CarAppService giriş noktalarını ve Car App Library yansıma (reflection) sınıflarını koru
-keep class com.sync4.carcompanion.car.** { *; }
-keep class androidx.car.app.** { *; }
-keep enum com.sync4.carcompanion.state.** { *; }
-dontwarn androidx.car.app.**
