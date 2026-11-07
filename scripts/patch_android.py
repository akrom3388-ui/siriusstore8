#!/usr/bin/env python3
"""cap add android dan keyin ishlaydi: manifest, native kod, gradle, ikonka va splash ni tuzatadi."""
import glob, os, re, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AND = os.path.join(ROOT, "android")
MAIN = os.path.join(AND, "app/src/main")
MANIFEST = os.path.join(MAIN, "AndroidManifest.xml")
JAVA_DIR = os.path.join(MAIN, "java/uz/siriusstore/app")
RES = os.path.join(MAIN, "res")
GRADLE = os.path.join(AND, "app/build.gradle")

# 1) AndroidManifest ---------------------------------------------------------
m = open(MANIFEST, encoding="utf-8").read()
perm_names = [
    "INTERNET", "SEND_SMS", "CALL_PHONE", "RECEIVE_BOOT_COMPLETED", "WAKE_LOCK", "SCHEDULE_EXACT_ALARM",
    "POST_NOTIFICATIONS", "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "USE_BIOMETRIC", "USE_FINGERPRINT",
    "RECORD_AUDIO", "MODIFY_AUDIO_SETTINGS", "CAMERA",
]
perms = ""
for p in perm_names:
    tag = '<uses-permission android:name="android.permission.%s" />' % p
    if ("android.permission.%s\"" % p) not in m:
        perms += "    " + tag + "\n"
if 'android.hardware.telephony' not in m:
    perms += '    <uses-feature android:name="android.hardware.telephony" android:required="false" />\n'
for feat in ("camera", "microphone"):
    if ('android.hardware.%s"' % feat) not in m:
        perms += '    <uses-feature android:name="android.hardware.%s" android:required="false" />\n' % feat
if perms:
    m = m.replace("<application", perms + "    <application", 1)

blocks = ""
if "DailyReceiver" not in m:
    blocks += """
        <receiver android:name=".DailyReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
"""
if "SyncReceiver" not in m:
    blocks += """
        <receiver android:name=".SyncReceiver" android:exported="false" />
"""
if "SiriusFileProvider" not in m:
    blocks += """
        <provider
            android:name=".SiriusFileProvider"
            android:authorities="${applicationId}.siriusfp"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/sirius_paths" />
        </provider>
"""
if blocks:
    m = m.replace("</application>", blocks + "    </application>", 1)
# ekran qulflash/rotatsiya paytida ilova qayta yuklanmasin, klaviatura sozlamasi
if "windowSoftInputMode" not in m:
    m = m.replace("<activity", '<activity android:windowSoftInputMode="adjustResize"', 1)
open(MANIFEST, "w", encoding="utf-8").write(m)

# 2) Java fayllar (MainActivity ham almashtiriladi) ----------------------------
os.makedirs(JAVA_DIR, exist_ok=True)
for f in glob.glob(os.path.join(ROOT, "native", "*.java")):
    shutil.copy(f, JAVA_DIR)
print("Java fayllar nusxalandi ->", JAVA_DIR)

# 3) Gradle qaramliklari ---------------------------------------------------------
g = open(GRADLE, encoding="utf-8").read()
deps = ['    implementation "androidx.work:work-runtime:2.9.0"\n',
        '    implementation "androidx.biometric:biometric:1.1.0"\n']
add = "".join(d for d in deps if d.split('"')[1].split(":")[1] not in g)
if add:
    g, n = re.subn(r"^dependencies \{\n", "dependencies {\n" + add, g, count=1, flags=re.M)
    if n != 1:
        sys.exit("app/build.gradle da 'dependencies {' topilmadi")
open(GRADLE, "w", encoding="utf-8").write(g)

# 4) Ikonka, splash, resurslar ----------------------------------------------------
src = os.path.join(ROOT, "assets", "res")
for dirpath, _, files in os.walk(src):
    for fn in files:
        s = os.path.join(dirpath, fn)
        d = os.path.join(RES, os.path.relpath(s, src))
        os.makedirs(os.path.dirname(d), exist_ok=True)
        shutil.copy(s, d)
# Capacitor ning standart splash rasmlari o'rniga bizning splash.xml (qora fon + logotip)
for f in glob.glob(os.path.join(RES, "drawable*", "splash.png")):
    os.remove(f)
# 5) Ilova nomi launcherda ikki qatorda: SIRIUS / STORE
strings = os.path.join(RES, "values", "strings.xml")
if os.path.exists(strings):
    t = open(strings, encoding="utf-8").read()
    t2 = re.sub(r'(<string name="app_name">)[^<]*(</string>)', r'\1SIRIUS\\nSTORE\2', t)
    open(strings, "w", encoding="utf-8").write(t2)
    print("app_name -> SIRIUS\\nSTORE")
# launcher yorlig'i: adaptive ikonka fonsiz (shaffof), nomi @string/app_name
print("Patch tayyor")
