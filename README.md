# XGPU Engine - Fake FPS Monitor

Aplikasi Android mirip Project Infinity X dengan fake FPS overlay (165-185 FPS, mayoritas 180-185).

## Struktur Project
```
XGPUEngine/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/xgpu/engine/
│       │   ├── model/DeviceInfo.kt         ← Data model
│       │   ├── ui/
│       │   │   ├── MainActivity.kt         ← Main screen
│       │   │   └── MainViewModel.kt        ← State management
│       │   └── overlay/
│       │       └── FpsOverlayService.kt    ← Fake FPS service
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml       ← Layout utama
│           │   └── overlay_fps.xml         ← Layout overlay FPS
│           ├── drawable/                   ← Semua drawable
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── color/                     ← Switch selectors
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Cara Build

### Persiapan
1. Install **Android Studio** (versi Hedgehog / 2023.1+)
2. Install **JDK 17**

### Langkah Build
1. Buka Android Studio → **File → Open** → pilih folder `XGPUEngine`
2. Tunggu Gradle sync selesai (butuh internet pertama kali)
3. Jika ada error "Gradle version": update ke versi terbaru di **File → Project Structure**
4. Klik **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK ada di: `app/build/outputs/apk/debug/app-debug.apk`

### Atau via Terminal
```bash
cd XGPUEngine
./gradlew assembleDebug
```

---

## Fitur Aplikasi

### Tampilan Utama (mirip Project Infinity X)
- Nama perangkat + codename
- Versi ROM + badge Official ✓
- Spesifikasi lengkap (Prosesor, Display, Baterai, Kamera, RAM, ROM)
- Badge Android version
- Info Maintainer, Security Patch, Kernel

### Edit Info (Tombol paling bawah)
- Tap **"✎ Edit Info"** untuk membuka panel edit
- Semua field bisa diubah
- Tap **📷 Ganti Foto HP** untuk pilih foto dari galeri
- Tap **💾 Simpan** untuk menyimpan perubahan
- Panel otomatis tersembunyi setelah simpan → tampilan tetap clean

### XGPU ENGINE - Fake FPS Overlay
- Toggle ON/OFF di bagian bawah
- Pertama kali aktif → minta izin "Display over other apps" → izinkan
- Overlay muncul di semua aplikasi, bisa di-drag ke mana saja
- FPS range: **165-185** FPS
  - 80% waktu: **180-185** FPS (stabil/hijau)
  - 15% waktu: **175-179** FPS (kuning-hijau)
  - 5% waktu: **165-174** FPS (jarang/amber)
- Perubahan FPS smooth, tidak loncat tiba-tiba
- Tulisan **"XGPU ENGINE"** di bawah angka FPS

---

## Troubleshooting

**Error: "uses-permission android:foregroundServiceType"**
→ Hapus `android:foregroundServiceType="specialUse"` dari AndroidManifest.xml jika target SDK < 34

**Error: "SwitchMaterial not found"**
→ Pastikan dependency `com.google.android.material:material:1.11.0` ada di build.gradle

**Overlay tidak muncul**
→ Pergi ke Settings → Apps → XGPU Engine → Display over other apps → Izinkan

**Foto HP tidak tersimpan setelah restart**
→ Tambahkan SharedPreferences untuk menyimpan URI foto (opsional improvement)
