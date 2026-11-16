# ServerHub 2.4.0

- **Paper-Schlafstatus:** erkennt die tatsächliche Paper-Pause über `Server.isPaused()` und überträgt ein eindeutiges Ping-Signal. Ein pausierter, weiter pingbarer Server erscheint gelb. Null Spieler allein gilt weiterhin nicht als Schlaf.
- **ServerNavigator ab 1.2.0:** GUI-Klicks nutzen dessen Warteschlange, Startverzögerung, Readiness und Wakeup. Kein konkurrierender Direktwechsel für verwaltete Ziele. Startphasen sind gelb; der ServerHub-Actionbar-Hinweis ruht beim Warten.
- **Zentrierter Titel:** Rolle aus skalierbaren PNG-Teilen, Breite passend zur Schrift und Textlänge; einstellbarer Rand und maximale Breite.
- **Darstellung:** feste linke Statuspunktposition, zuschaltbarer Textschatten, Holzbuttons ohne helle Eckverzierungen. Schließen, Weiter und Zurück haben eigene Breite, Schriftgröße, Schriftart, Textfarbe und Hintergrundtönung.
- **Schriftwahl:** Sans, Serif und Mono in 8/10/12/14/16 Pixeln. Eigene TTF/OTF per direktem HTTPS-Link oder lokaler Datei; der Proxy erzeugt Schriftgrafiken und verteilt passende Pixelbreiten an Paper.
- **Bearbeitbares Pack:** `plugins/serverhub-velocity/pack/` enthält die Quelldateien. Reload erstellt ZIP, Hash und Paket-ID samt Sicherung der vorherigen Ausgabe. Unveränderte Standardbytes bleiben exakt zum GitHub-Download identisch.
- **Proxy-Vorrang:** im Netzwerk nur am Proxy bearbeiten; eigenständiges Paper verwendet den lokalen Packordner und `/extramenu reload`.

**Update:** ServerHub auf Proxy und allen Paper-Backends gemeinsam ersetzen und neu starten. Für die Navigator-Anbindung zusätzlich [ServerNavigator 1.2.0](https://github.com/etzewetze/ServerNavigator/releases/tag/v1.2.0) auf dem Proxy installieren; dieses benötigt Velocity 4.3.0. Die Menüübertragung ist nicht mit ServerHub 2.3 mischbar. Vorhandene Einträge bleiben erhalten, neue Optionen werden in `servers.yml` ergänzt.

**Eigene Texturen/Schriften:** Der neue Standard `pack.mode=auto` verwendet für eigene Pack-Inhalte den eingebauten HTTP-Server. Dafür muss der Pack-Port (Standard 8124/TCP) für Spieler erreichbar sein, oder eine passende externe ZIP-Adresse eingerichtet werden. Nach Pack-Änderungen neu verbinden; Backend-Wechsel und Reload bieten das Paket weiterhin nicht erneut an. Änderungen ausschließlich im entpackten `pack/` vornehmen; die ZIP ist generierte Ausgabe.

**Prüfung:** automatisierte Tests, Universal-JAR-Plattformprüfung und öffentliche Release-Downloads samt Prüfsummen. Kein vollständiger Ingame-Test. Andere Schlaf-Proxys benötigen weiterhin ein eindeutiges eigenes Schlafsignal oder eine Statusvorgabe. Details und Konfigurationsbeispiele stehen in der README.
