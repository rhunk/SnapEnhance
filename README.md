# SnapEnhance
A xposed mod to enhance the Snapchat experience. <br/>
The project is currently in development, so expect bugs and crashes. Feel free to open an issue if you find any bug.

[Download](https://github.com/rhunk/SnapEnhance/releases)

## build
   1. make sure you have the latest version of [LSPosed](https://github.com/LSPosed/LSPosed)
   2. clone this repo using ``git clone``
   3. run ``./gradlew assembleDebug``
   4. install the apk using adb ``adb install -r app/build/outputs/apk/debug/app-debug.apk``

## About the license
The GNU GPL v3 license is a free, open source software license that gives users the right to modify, share, or redistribute the software.<br/>
The user agrees to make the source code of the software freely available, in addition to any modifications, additions, or derivatives made. <br/>
If the software is redistributed, it must remain under the same GPLv3 license and any modifications must be clearly marked as such.<br/>

## Features
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
