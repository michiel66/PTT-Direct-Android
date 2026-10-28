#!/usr/bin/env python3
"""Maak PTT Direct van de gepinde, NL-gepatchte TS6-Droid Android-broncode.

Draai DIT script als laatste, na de zeven bestaande TS6-Droid-NL patches.
Het vervangt alleen de gebruikersinterface/router en de app-identiteit:
de bewezen audio-, service- en TeamSpeak-clients blijven behouden.
"""
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = Path(__file__).resolve().parent
APP = ROOT / 'app'
KOTLIN = APP / 'src/main/kotlin/dev/tsdroid/ui/screen'
RES = APP / 'src/main/res'

for path in [ROOT / 'app/build.gradle.kts', KOTLIN / 'AppNavigation.kt',
             ROOT / 'app/src/main/kotlin/dev/tsdroid/viewmodel/ConnectionViewModel.kt',
             SCRIPT / 'PttDirectApp.kt', SCRIPT / 'ptt_direct_bg.png', SCRIPT / 'ptt_direct_icon.png']:
    if not path.exists():
        raise SystemExit(f'Bestand ontbreekt: {path}')

(KOTLIN / 'PttDirectApp.kt').write_text((SCRIPT / 'PttDirectApp.kt').read_text(encoding='utf-8'), encoding='utf-8')
(RES / 'drawable').mkdir(parents=True, exist_ok=True)
for image in ['ptt_direct_bg.png', 'ptt_direct_icon.png']:
    shutil.copy2(SCRIPT / image, RES / 'drawable' / image)

# Behoud de complete TS6-service (geen eigen gesimuleerde gebruikers of audio).
nav = KOTLIN / 'AppNavigation.kt'
nav.write_text('''package dev.tsdroid.ui.screen

import androidx.compose.runtime.Composable

@Composable
fun AppNavigation(initialAnimeBackground: Boolean) {
    PttDirectApp()
}
''', encoding='utf-8')

# Vaste server is een constante in de nieuwe UI. Er bestaat geen route meer
# naar het originele connect-scherm met serveradres/favorieten.
connection = ROOT / 'app/src/main/kotlin/dev/tsdroid/viewmodel/ConnectionViewModel.kt'
src = connection.read_text(encoding='utf-8')
start_marker = '        // Check for overlay permission before starting the service\n'
end_marker = '        try {\n            val intent = Intent(context, TsConnectionService::class.java)'
if src.count(start_marker) != 1 or src.count(end_marker) != 1:
    raise SystemExit('Verbindingsscherm is gewijzigd; controleer de overlay-permissionpatch')
a = src.index(start_marker)
b = src.index(end_marker, a)
src = (src[:a] +
       '        // PTT Direct gebruikt geen overlay: toestemming hiervoor is niet vereist.\n\n' +
       src[b:])
connection.write_text(src, encoding='utf-8')

# De Android-voormelding toont alleen onze naam/status, niet de
# achterliggende TeamSpeak-servernaam.
service = ROOT / 'app/src/main/kotlin/dev/tsdroid/service/TsConnectionService.kt'
s = service.read_text(encoding='utf-8')
line = 'val serverName = tsClient.serverInfo.value?.name ?: getString(R.string.connecting)'
if s.count(line) != 1:
    raise SystemExit('Servicemelding is gewijzigd; controleer privacy van servernaam')
service.write_text(s.replace(line, 'val serverName = "Verbonden"'), encoding='utf-8')

settings = ROOT / 'app/src/main/kotlin/dev/tsdroid/data/SettingsStore.kt'
s = settings.read_text(encoding='utf-8')
needle = 'it[KEY_ENABLE_FLOATING_WINDOW] ?: true'
if s.count(needle) != 1:
    raise SystemExit('Instellingenbron wijkt af: standaard zwevend venster')
settings.write_text(s.replace(needle, 'it[KEY_ENABLE_FLOATING_WINDOW] ?: false'), encoding='utf-8')

gradle = APP / 'build.gradle.kts'
s = gradle.read_text(encoding='utf-8')
s, n = re.subn(r'applicationId\s*=\s*"[^"]+"', 'applicationId = "nl.pttdirect.android"', s, count=1)
if n != 1:
    raise SystemExit('applicationId niet gevonden')
s, n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.0.0-PTT-Direct"', s, count=1)
if n != 1:
    raise SystemExit('versionName niet gevonden')
gradle.write_text(s, encoding='utf-8')

manifest = APP / 'src/main/AndroidManifest.xml'
s = manifest.read_text(encoding='utf-8')
needle = 'android:icon="@mipmap/ic_launcher"'
if s.count(needle) != 1:
    raise SystemExit('Android launcher-icon niet gevonden')
manifest.write_text(s.replace(needle, 'android:icon="@drawable/ptt_direct_icon"'), encoding='utf-8')

# Zet de openbare merknaam in alle beschikbare vertalingen; TeamSpeak
# blijft alleen de backend. De servernaam wordt NIET als UI-tekst opgenomen.
for string_file in RES.glob('values*/strings.xml'):
    s = string_file.read_text(encoding='utf-8')
    s, _ = re.subn(r'(<string name="app_name">).*?(</string>)',
                   r'\g<1>PTT Direct\g<2>', s, count=1, flags=re.DOTALL)
    string_file.write_text(s, encoding='utf-8')

print('PTT Direct: vaste server, vier schermen, PTT, echte kanalen en gebruikers')
print('Applicatie-ID: nl.pttdirect.android (aparte installatie)')
