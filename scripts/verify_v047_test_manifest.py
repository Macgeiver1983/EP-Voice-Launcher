"""Check the final signed QA APK manifest decoded by Android SDK apkanalyzer."""

import sys
from pathlib import Path
from xml.etree import ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"


def android(element, attribute):
    return element.get(ANDROID + attribute)


def require(condition, message):
    if not condition:
        raise SystemExit(message)


manifest = ET.parse(Path(sys.argv[1])).getroot()
accessibility = ET.parse(Path(sys.argv[2])).getroot()
require(manifest.get("package") == "de.europlast.epvoice.v047test", "Unexpected package")
require(android(manifest, "versionCode") == "12", "Unexpected version code")
require(android(manifest, "versionName") == "0.4.7-signed", "Unexpected version name")

permissions = {android(item, "name") for item in manifest.findall("uses-permission")}
required = {
    "android.permission.RECORD_AUDIO",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MICROPHONE",
    "android.permission.POST_NOTIFICATIONS",
}
allowed = required | {"de.europlast.epvoice.v047test.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
require(required <= permissions <= allowed, f"Unexpected permissions: {sorted(permissions)}")
require(not any(item.tag.startswith("uses-permission-sdk") for item in manifest),
        "Unexpected SDK-specific permissions")

app = manifest.find("application")
require(app is not None and android(app, "debuggable") in (None, "false"), "Debuggable APK")
require(android(app, "allowBackup") == "false", "App backup is enabled")
require(android(app, "usesCleartextTraffic") == "false", "Cleartext traffic is enabled")

services = {android(service, "name"): service for service in app.findall("service")}
voice = services["de.europlast.epvoice.ChatGptVoiceAccessibilityService"]
require(android(voice, "exported") == "true", "Accessibility service must be available to Android")
require(android(voice, "permission") == "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "Accessibility service is missing the system binding permission")
require(any(android(action, "name") == "android.accessibilityservice.AccessibilityService"
            for action in voice.findall("intent-filter/action")),
        "Accessibility service has no system intent filter")
wake = services["de.europlast.epvoice.WakeService"]
require(android(wake, "exported") == "false" and android(wake, "foregroundServiceType") in
        ("microphone", "0x00000080", "0x80", "128"), "Unexpected wake service configuration")

require(android(accessibility, "packageNames") == "com.openai.chatgpt",
        "Accessibility event scope is broader than ChatGPT")
require(android(accessibility, "isAccessibilityTool") == "false", "Misclassified accessibility tool")
require(android(accessibility, "canRetrieveWindowContent") == "true", "Required window access missing")
require(android(accessibility, "canPerformGestures") in (None, "false"), "Gesture capability enabled")
print("PASS: Signed QA APK permissions, components and accessibility configuration")
