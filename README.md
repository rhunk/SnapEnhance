<div align="center">
  <img src="https://raw.githubusercontent.com/rhunk/SnapEnhance/main/app/src/main/res/mipmap-xxxhdpi/launcher_icon_foreground.png" height="250" />

  [![Build](https://img.shields.io/github/actions/workflow/status/Lucky-069/SnapEnhance/android.yml?branch=main&logo=github&label=Build&event=push)](https://github.com/Lucky-069/SnapEnhance/actions/workflows/android.yml?query=branch%3Amain+event%3Apush+is%3Acompleted) [![Total](https://shields.io/github/downloads/Lucky-069/SnapEnhance/total?logo=Bookmeter&label=Downloads&logoColor=Green&color=Green)](https://github.com/Lucky-069/snapenhance/releases) [![Crowdin](https://badges.crowdin.net/snapenhance/localized.svg)](https://crowdin.com/project/snapenhance)

## This is merely a fork of the original project for personal use!<br>
## Use this at your own risk as the project is not supported by the original dev a.k.a rhunk nor should the issues for this repo be reported to the original dev. 

# SnapEnhance
SnapEnhance is an Xposed mod that enhances your Snapchat experience.<br/><br/>
Please note that this project is 

## Quick Start
Requirements:
- Rooted using Magisk or KernelSU
- LSPosed installed and fully functional

Although using this in an unrooted enviroment using something like LSPatch should be working fine, it is not recommended to do so, use at your own risk!

1. Install the module APK from either this [Github repo](https://github.com/Lucky-069/SnapEnhance/releases)
2. Turn on the module in LSPosed and make sure Snapchat is in scope
3. Force Stop Snapchat
4. Open the menu by clicking the [Settings Gear Icon](https://i.imgur.com/2grm8li.png)

## Download 
To Download the latest stable release, please visit the [Releases](https://github.com/Lucky-069/SnapEnhance/releases) page.<br/>
You can also download the latest debug build from the [Actions](https://github.com/Lucky-069/SnapEnhance/actions) section.<br/>

## Features
<details open>
  <summary>Spying & Privacy</summary>

  - Message logger
  - Prevent sending Read Receipts
  - Hide Bitmoji Presence
  - Better Notifications
  - Disable Metrics
  - Block Ads
  - Unlimited Snap View Time
  - Prevent Screenshot Notification
  - Prevent Status Notifications
  - Anonymous Story View
  - Prevent Typing Notification
</details>

<details open>
  <summary>Media Manager</summary>

  - Download any message in a chat (Snaps, External Media, Voice Notes, etc.)
  - Download any story (Private, Public, or Professional)
  - Anti Auto-download (Prevents automatic Downloading on specific conversations)
  - Gallery Media Send Override
  - Auto Save Messages
  - Force Source Quality
</details>

<details open>
  <summary>UI & Tweaks</summary>

  - Disable Camera
  - Immersive Camera Preview (Fix Snapchats camera bug)
  - Hide certain UI Elements
  - Show Streak Expiration Info
  - Disable Snap Splitting
  - Disable Video Length Restriction
  - Snapchat Plus
  - New Map UI
  - Location Spoofer
  - Disable Spotlight
  - Enable Official App Appearance Settings (Darkmode)
  - Pin Conversations
  - Multi Snap Limit bypass
  - Override Startup Page
</details>

<details open>
  <summary>Experimental</summary>

  - App Passcode
  - Infinite Story Boost
  - My Eyes Only Bypass
  - AMOLED Dark Mode
  - Chat Export (HTML, JSON and TXT)
</details>

## FAQ
<details>
  <summary>AI wallpapers and the Snapchat+ badge aren't working!</summary>
  
  - Yeah, they're server-sided and will probably never work.
</details>

<details>
  <summary>Can you add this feature, please?</summary>
  
  - Open an issue on our Github repo.
</details>

<details>
  <summary>When will this feature become available or finish?</summary>
  
  - At some point.
</details>

<details>
  <summary>Can I get banned with this?</summary>
  
  - Obviously, however, the risk is very low, and we have no reported cases of anyone ever getting banned while using the mod.
</details>

<details>
  <summary>Can I PM the developers?</summary>
  
  - No.
</details>

<details>
  <summary>This doesn't work!</summary>
  
  - Open an issue.
</details>

<details>
  <summary>My phone isn't rooted; how do I use this?</summary>
  
  - You can use LSPatch in combination with SnapEnhance to run this on an unrooted device, however this is unrecommended and not considered safe.
</details>

<details>
  <summary>Where can I download the latest stable build?</summary>
  
  - https://github.com/rhunk/snapenhance/releases
</details>

<details>
  <summary>Can I use HideMyApplist with this?</summary>
  
  - No, this will cause some severe issues, and the mod will not be able to inject.
</details>

## Privacy
We do not collect any user information. However, please be aware that third-party libraries may collect data as described in their respective privacy policies.
<details>
  <summary>Permissions</summary>
  
  - [android.permission.INTERNET](https://developer.android.com/reference/android/Manifest.permission#INTERNET)
  - [android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS](https://developer.android.com/reference/android/Manifest.permission.html#REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
  - [android.permission.POST_NOTIFICATIONS](https://developer.android.com/reference/android/Manifest.permission.html#POST_NOTIFICATIONS)
  - [android.permission.SYSTEM_ALERT_WINDOW](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW)
</details>

<details>
  <summary>Third-party libraries used</summary>
  
  - [libxposed](https://github.com/libxposed/api)
  - [ffmpeg-kit-full-gpl](https://github.com/arthenica/ffmpeg-kit)
  - [osmdroid](https://github.com/osmdroid/osmdroid)
  - [coil](https://github.com/coil-kt/coil)
  - [Dobby](https://github.com/jmpews/Dobby)
</details>

## Contributors
- [rathmerdominik](https://github.com/rathmerdominik)
- [Flole998](https://github.com/Flole998)
- [authorisation](https://github.com/authorisation/)
- [RevealedSoulEven](https://github.com/revealedsouleven)
- [iBasim](https://github.com/ibasim)
- [xerta555](https://github.com/xerta555)
- [TheVisual](https://github.com/TheVisual)  

## Credits
## All credits go to the original dev

- [LSPosed](https://github.com/LSPosed/LSPosed)
- [dexlib2](https://android.googlesource.com/platform/external/smali/+/refs/heads/main/dexlib2/)
- [ffmpeg-kit-full-gpl](https://github.com/arthenica/ffmpeg-kit)
- [osmdroid](https://github.com/osmdroid/osmdroid)
- [coil](https://github.com/coil-kt/coil)
- [Dobby](https://github.com/jmpews/Dobby)
- [rhunk](https://github.com/rhunk) 

## License
The [GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.en.html#license-text) license is a free, open-source software license that grants users the right to modify, share, and redistribute the software.
By using this software, you agree to make the source code freely available, along with any modifications, additions, or derivatives.
When redistributing the software, it must remain under the same [GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html#license-text) license, and any modifications should be clearly indicated as such.
