# SnapEnhance
A xposed mod to enhance the Snapchat experience
The project is currently in development, so expect bugs and crashes. Feel free to open an issue if you find any bug.

## build
   1. make sure you have the latest version of [LSPosed](https://github.com/LSPosed/LSPosed)
   2. clone this repo using ``git clone``
   3. run ``./gradlew assembleDebug``
   4. install the apk using adb ``adb install -r app/build/outputs/apk/debug/app-debug.apk``

## features
- media downloader (+ overlay merging)
- message auto save
- message in notifications
- message logger
- snapchat plus features
- anonymous story viewing
- stealth mode
- screenshot detection bypass
- conversation preview
- prevent status notifications
- UI tweaks (remove call button, record button, ...)
- ad blocker

## todo
- localization
- ui improvements
- snap splitting
