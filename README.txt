PHOTO VAULT - Android Studio project
====================================

What you need: a computer with Android Studio (free, from developer.android.com/studio)
and an internet connection the first time, so it can download its build tools.

Build the APK
1. Unzip this folder.
2. In Android Studio choose File > Open and select the "PhotoVault" folder.
3. Wait for "Gradle sync" to finish (a few minutes the first time). Accept any
   prompt to install missing SDK components. If it offers to update Gradle or the
   Android Gradle Plugin, accepting is fine.
4. Menu: Build > Build Bundle(s) / APK(s) > Build APK(s).
5. When it finishes, click "locate" in the popup. The file is
   app/build/outputs/apk/debug/app-debug.apk

Install on your phone
1. Copy app-debug.apk to the phone (USB, Drive, WhatsApp to yourself, etc.).
2. Tap the file. Android will ask you to allow installing from this source. Allow it.
3. Open "Photo Vault".

Notes
- Needs Android 10 or newer.
- Photos are stored only on the phone where the app is installed.
- Share > Send backup file opens the phone's share menu. On the other phone,
  open the app and use Import backup.
- Export backup saves a .json file in the Downloads folder.
- Uninstalling the app deletes its photos. Export a backup first.
- Use Android Studio to build (not the command line): this folder has no gradle wrapper jar.
