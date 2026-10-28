# PTT Direct Android

Dit is een nieuwe Android-portofoonapp met de vier schermen uit het ontwerp:
**Aanmelden, PTT, Kanalen, Gebruikers** plus **Instellingen**.

De app verbindt uitsluitend met `ptt.mijnts3.nl` via de bestaande TeamSpeak-client.
De gebruiker geeft enkel een nickname op. De TeamSpeak-server moet gasttoegang
zonder wachtwoord toestaan. Er is geen serverconfiguratie in de UI.

## APK via GitHub Actions

Maak een afzonderlijke GitHub-repository `PTT-Direct-Android` en upload de bestanden
van dit project, **inclusief de map `.github/workflows/`**.
Open **Actions -> Bouw PTT Direct Android -> Run workflow**. Na een geslaagde
build staat `PTT-Direct-Android-APK` onder Artifacts.

## Techniek

- Jetpack Compose met eigen donker/blauwe interface.
- Echte kanalen, online gebruikers en actuele sprekers uit de TS6-Droid-NL backend.
- Vasthouden/loslaten PTT en optionele hardware-PTT.
- 3-tonige rogerpiep aan/uit, mediavolume, automatische reconnect via de TS service.
- APK met eigen Android applicationId `nl.pttdirect.android` (naast bestaande apps).
- Vereiste upstream-licenties en auteursvermeldingen blijven in de broncode
  van de oorspronkelijke repositories aanwezig. Bekijk ook hun GPLv3-voorwaarden.

**Let op:** een vast adres in een mobiele app is niet geheim voor iemand die de
APK of netwerkverbinding onderzoekt. Dit ontwerp verbergt het adres alleen
voor normale eindgebruikers en biedt geen aparte gebruikersauthenticatie.

Dit pakket bevat bouwbestanden en is nog niet op een Android-toestel getest;
controleer de resultaten van de CI-build en test vervolgens op een toestel.
