# ServerHub GUI 2.5.0

Zentrale Serverauswahl für Velocity mit festem Dialogkopf, Minecraft-Standardbuttons und direkten Schlafmeldungen der Backends. Statusprüfung standardmäßig jede Sekunde, auch ohne Spieler auf dem Zielserver. Eigene Schriften, ServerNavigator und der bearbeitbare Packordner bleiben erhalten.

[ServerHub-JAR](https://github.com/etzewetze/serverhubgui/releases/download/v2.5.0/ServerHub-2.5.0.jar) · [Release / Komplettpaket](https://github.com/etzewetze/serverhubgui/releases/tag/v2.5.0) · [ServerNavigator ab 1.2.0](https://github.com/etzewetze/ServerNavigator/releases/tag/v1.2.0)

**Minecraft Java / Paper 26.3, Java 25, Velocity 4.2.0 oder 4.3.0.** Dieselbe ServerHub-JAR auf Proxy und Backends. ServerNavigator 1.2.0 benötigt Velocity 4.3.0. Kein Client-Mod erforderlich.

![Drei verfügbare Schriftarten mit den echten Pack-Texturen; Designreferenz, kein Ingame-Screenshot](preview/Menue-Vorschau.png)

## Installation und Update

1. Proxy und Paper-Backends beenden. Alte ServerHub-JARs durch `ServerHub-2.5.0.jar` ersetzen; keine doppelten Versionen behalten.
2. Für ServerNavigator-Integration dessen neue `ServerNavigator-1.2.0.jar` auf dem Proxy installieren. Vorhandene Navigator-Ziele und Verzögerungen bleiben in dessen `config.toml`.
3. Netzwerk vollständig starten. Danach ServerHub-Einstellungen ausschließlich unter `plugins/serverhub-velocity/` am Proxy bearbeiten.
4. `/serverhubproxy reload` lädt Menü und Pack. `/serverhubproxy packcheck` prüft den Download. Nach geänderten Pack-Dateien neu verbinden.

Proxy und Backends gemeinsam aktualisieren: 2.5 ergänzt Statuskanal und Menüoptionen und ist nicht mit älteren ServerHub-Versionen mischbar. Die bisherige Standardkonfiguration `pack.mode=github` wird gesichert und auf `auto` umgestellt. Externe Downloadmodi bleiben erhalten. Die vorherige ZIP wird bei Änderungen als `serverhub-pack-backup-<hash>.zip` gesichert.

Paper erkennt Velocity-/Bungee-Forwarding in `config/paper-global.yml`, `spigot.yml` bzw. dem alten `paper.yml`. Im Netzwerk verwendet es die vom Proxy bestätigten Einstellungen und startet keinen lokalen Pack-Server. Bestehendes Modern Forwarding und der Schutz der Backends bleiben Voraussetzung. `network.mode` in der lokalen `config.yml` unterstützt `auto` (Standard), `proxy` und `standalone` als manuellen Override.

Ohne Proxy verwendet Paper **`plugins/ServerHub/servers.yml`**, **`plugins/ServerHub/pack/`** und **`plugins/ServerHub/config.properties`**. Dann lädt `/extramenu reload` lokal neu; es gibt keinen Proxy-Timeout. Ein eigenständiger Server kann mit dieser lokalen Ansicht sich selbst darstellen, aber keine fremden Velocity-Ziele ermitteln. Im erkannten Proxybetrieb werden lokale Menü- und Pack-Änderungen ignoriert.

## Statusintervall und Backend-Meldeweg

Die vollständige Konfiguration steht am Proxy in **`plugins/serverhub-velocity/servers.yml`**, direkt im Abschnitt `status` am Dateianfang. Es gibt dafür keine einzelnen Backend-Menükonfigurationen.

```yaml
status:
  enabled: true
  ping-interval-seconds: 1
  timeout-ms: 1000
  refresh-open-menu: true
  backend-reports: true
  report-bind: '0.0.0.0'
  report-port: 8125
  report-host: auto
  report-scheme: http
  heartbeat-interval-seconds: 1
  heartbeat-timeout-seconds: 5
```

Die bisherige Standardfrist von 10 Sekunden wird beim Update auf 1 Sekunde verkürzt, das alte Standard-Pinglimit von 2000 auf 1000 ms. Die vorherige Datei wird als `servers-v2.4-backup.yml` gesichert. Abweichende eigene Werte bleiben erhalten. `ping-interval-seconds` erlaubt 1–300 Sekunden.

Der Proxy übermittelt die Daten für den Berichtskanal automatisch im virtuellen Host seiner Minecraft-Statusanfrage. Das Backend muss dafür weder einen Spieler haben noch zuvor besucht werden. Es meldet danach periodisch und bei einer gemessenen Zustandsänderung über einen eigenen HTTP-Kanal. Die Berichte sind pro Ziel authentifiziert; nur registrierte Ziele werden angenommen. Der Proxy merkt sich Reihenfolge und Prozesskennung, damit alte oder verspätete Meldungen keinen neueren Zustand überschreiben. Abgelaufene Berichte fallen nach `heartbeat-timeout-seconds` auf die Pingprüfung zurück.

**Dafür muss TCP-Port 8125 vom Backend zum Proxy erreichbar sein.** Dieser interne Statusport gehört nicht zum Pack-Download und muss nicht für Spieler öffentlich freigegeben werden. Bei Docker/NAT die Portweiterleitung am Proxy einrichten. `report-host: auto` nutzt die tatsächliche IP des Proxys aus der Statusverbindung; bei abweichendem Routing zentral einen passenden Hostnamen oder eine IP setzen. Hostnamen sind auf 64 Zeichen begrenzt. Ein ausdrücklich gesetzter Host wird über den privaten Proxy-Kanal bestätigt; nach einer Änderung jedes betroffene Backend einmal über den Proxy betreten. Danach gilt der gespeicherte Meldeweg auch ohne Spieler und nach Neustarts. Der Standard `auto` benötigt diesen Schritt nicht. Bind/Port nach dem Start ändern: Proxy neu starten. `report-scheme: https` benötigt einen eigenen TLS-Reverse-Proxy vor dem eingebauten HTTP-Empfänger. Alle anderen Werte werden mit `/serverhubproxy reload` geladen. Ohne erreichbaren Berichtskanal bleibt der bisherige Ping-Fallback aktiv.

**`/serverhubproxy status`** zeigt je Ziel den Status, das Alter und den Wert der letzten Backend-Meldung sowie das Ping-Ergebnis. Bei „noch keine Backend-Meldung“ zuerst die neue JAR auf diesem Backend, `status.backend-reports`, Port 8125 und gegebenenfalls `report-host` prüfen. Die automatisch erstellten Dateien `status-secret.bin` am Proxy und `proxy-status.properties` am Backend enthalten Zugangsdaten zum Statuskanal; aufbewahren und nicht veröffentlichen. Sie müssen nicht manuell bearbeitet werden. Die gespeicherte Verbindung funktioniert nach einem Neustart weiter und wird durch neue Statusanfragen automatisch angepasst.

## Eine zentrale Serverdatei

**`plugins/serverhub-velocity/servers.yml`** enthält das Menü, die Darstellungen aller Ziele, die Schriftauswahl und das HUD. Beim Start und regelmäßig werden alle registrierten Velocity-Server ergänzt. Vorhandene Namen, Farben und Größen bleiben erhalten. Entfernte Ziele behalten ihre Einstellungen, erscheinen aber nicht in der Auswahl. Neue Einstellmöglichkeiten werden beim Update ergänzt.

```yaml
menu:
  title: Serverauswahl
  title-color: '#3D2817'
  title-font-size: 12
  title-padding: 12
  title-max-width: 320
  font-family: sans
  font-size: 12
  text-color: '#FFF4D6'
  button-width: 140
  layout: list
  navigation: scroll
  page-size: 6
  show-page-info: true
  show-server-count: false
  single-page-info: false
servers:
  lobby:
    name: Treffpunkt
    color: '#A8E091'
    font-size: 12
    font-family: ''
    permission: ''
    description: ['Unsere gemeinsame Lobby']
    hidden: false
    order: 0
    status-override: auto
    sleep-motd-contains: []
```

Die Schlüssel unter `servers` sind die echten Velocity-Namen, auch Namen mit Punkten. Bis zu 256 Ziele; Buchstaben, Zahlen, Punkt, Unterstrich und Bindestrich, maximal 64 Zeichen. `font-size`: **8, 10, 12, 14 oder 16**. Farben als `'#RRGGBB'`. Eine leere `font-family` übernimmt den Menüstandard. `order` sortiert aufsteigend, danach alphabetisch.

`hidden: true` blendet ein Ziel aus. `permission` wird am Proxy und auf Paper geprüft. Ein ausdrückliches `false` für `serverhub.connect.<target>` am Proxy sperrt das Ziel ebenfalls. Diese Prüfungen gelten auch beim Verbindungsversuch. Adressen, Ports oder Navigator-Zugangsdaten werden nicht in die Menüeinträge übertragen. Ein ungültiger Reload lässt die bisherige Laufzeitkonfiguration aktiv.

## Darstellung und Navigation

Der Titel steht zentriert im festen Dialogkopf und scrollt nicht mit der Serverliste. Das gelbe Warnsymbol neben dem Titel wird von Minecraft selbst für benutzerdefinierte Dialoge hinzugefügt; die Dialog-API kann es nicht abschalten. Es ist keine ServerHub-Fehlermeldung. `menu.title-warning-offset` reserviert links standardmäßig 24 Pixel als Gegenstück zum Symbol rechts, damit die Rolle mittig steht; bei anderer Clientdarstellung ist der Wert zwischen 0 und 40 anpassbar. Die Rolle wird aus drei PNG-Teilen in der gemessenen Textbreite zusammengesetzt: kurzer Text erhält eine kurze Rolle, längerer Text eine längere. `title-padding` bestimmt den seitlichen Rand; `title-max-width` begrenzt die Breite. Zu lange Namen werden passend gekürzt; volle Servernamen stehen im Tooltip.

Die Statuspunkte sitzen unabhängig von Name und Schriftgröße an derselben linken Position. Standardmäßig zeigt Minecraft seine eigenen Buttons einschließlich Hover-, Fokus- und deaktivierter Darstellung. ServerHub malt keine Textur darüber. Mit `menu.button-style: wood` lässt sich die frühere Holzoptik bei Bedarf einschalten. `menu.button-width` steuert die Breite der Serverbuttons. Für die drei Bedienbuttons gibt es eigene Werte:

```yaml
buttons:
  close:
    width: 170
    font-size: 14
    font-family: serif
    color: '#FFFFFF'
    background-color: '#CCDDEE'
  next:
    width: 140
    font-size: 10
    font-family: ''
    color: '#FFF4D6'
    background-color: '#FFFFFF'
  previous:
    width: 140
    font-size: 10
    font-family: ''
    color: '#FFF4D6'
    background-color: '#FFFFFF'
text:
  shadow: true
  shadow-color: '#24180E'
```

`color` ist die Schriftfarbe. `background-color` wirkt ausschließlich bei `menu.button-style: wood` und tönt dort die Holztextur. Im Standardmodus `minecraft` kommt der Hintergrund unverändert vom Client. Weiß erhält die Originalfarben der optionalen Holztextur. `menu.button-background-color` tönt alle Serverbuttons, `menu.title-background-color` die Rolle. Breiten: 80–300 GUI-Pixel. Die native Buttonhöhe bleibt 20 Pixel; die Schriftgröße ist separat wählbar. Die Texte stehen unter `menu.previous`, `menu.next` und `menu.close`.

| Einstellung | Wirkung |
|---|---|
| `menu.layout: list` | Server untereinander, Standard. |
| `menu.layout: grid` | Zwei Spalten. |
| `menu.navigation: scroll` | Alle freigegebenen Ziele; bei Platzmangel native Scrollliste. |
| `menu.navigation: pages` | Blättern mit `menu.page-size` Zielen je Seite. |
| `menu.show-page-info: false` | Seiteninfo vollständig ausblenden. |
| `menu.show-server-count: false` | Keine Angabe wie „6 Server“, Standard. |
| `menu.single-page-info: false` | Bei nur einer Seite keine Seiteninfo, Standard. |

Im Scrollmodus erscheint keine künstliche Anzeige „Seite 1/1“. Bei Seiten steht die optionale Info direkt über „Schließen“. In sehr kleinen Clientfenstern kann auch der Seitenmodus scrollen. Das Menü öffnet kein Truhen- oder Spielerinventar; Escape und Schließen führen ins Spiel zurück. Geänderte Statuswerte werden standardmäßig auch in bereits geöffneten Menüs angezeigt. Die native Dialog-API kann die Scrollposition dabei nicht übernehmen. `status.refresh-open-menu: false` verhindert diese Erneuerung; dann zeigt erneutes Öffnen den aktuellen Stand. Unveränderte Heartbeats bauen das Menü nicht neu auf.

## Drei Schriften und eigene Schriftdateien

`menu.font-family` bietet **`sans`**, **`serif`** und **`mono`**. Alle drei sind mit allen fünf Schriftgrößen im Standardpaket enthalten. Ihre Auswahl braucht keinen eigenen Pack-Host. Mit `custom` kann eine eigene Schrift verwendet werden:

```yaml
menu:
  font-family: custom
font:
  url: 'https://deine-domain.example/schriften/MeineSchrift.ttf'
  refresh: false
```

Der Beispiel-Link muss durch eine echte **direkte HTTPS-Adresse zu einer TTF- oder unterstützten OTF-Datei** ersetzt werden. Eine HTML-Seite, Google-Fonts-CSS oder ZIP funktioniert nicht. Maximal 8 MiB; Schriftlizenz beachten. Alternativ die Datei als **`pack/assets/serverhub/fonts/custom.ttf`** im Proxy-Pluginordner ablegen und `font.url: ''` lassen.

Beim Reload lädt ServerHub eine neue URL einmalig, prüft die Schrift und erzeugt Bitmap-Atlanten für alle fünf Größen. `font.refresh: true` erzwingt den erneuten Download bei jedem Reload; danach wieder auf `false` setzen. Text unterstützt ASCII und Latin-1 einschließlich deutscher Umlaute und ß. Weitere Zeichen werden in den Menülabels durch `?` ersetzt.

Unveränderte TTF-Dateien werden nicht bei jedem Reload neu gerastert. Dadurch bleiben manuelle Änderungen an den PNG-Schriftatlanten erhalten. Deren tatsächliche Glyphenbreiten werden neu gemessen und an die Backend-Sitzungen übertragen. Bestehende Spieler behalten die zu ihrem geladenen Pack gehörenden Breiten bis zum nächsten Beitritt.

## Schlafstatus und ServerNavigator

| Punkt | Bedeutung |
|---|---|
| Grün | Gültige Pingantwort, kein gemeldetes Schlafsignal. |
| Rot | Kein gültiger Ping innerhalb des Zeitlimits. |
| Gelb | Paper meldet eine Pause, ein konfiguriertes Schlafsignal liegt vor oder ServerNavigator startet/verbindet gerade. |
| Grau | Noch keine aktuelle Prüfung. |

**Paper-Pause wird direkt vom Backend gemeldet.** Das Backend liest `Server.isPaused()` im Paper-Scheduler, der auch während der nativen Pause weiterläuft, und sendet den Wert über den eigenen Statuskanal. Dafür braucht es keinen verbundenen Spieler. Eine frische Schlafmeldung hat Vorrang vor grünen und roten Status-Pings. Die Ping-Versionskennung `[serverhub:paused=1]` bzw. `0` bleibt als zusätzlicher Fallback bestehen; die Protokollnummer bleibt unverändert. Die neue ServerHub-JAR muss auch auf jedem Backend laufen.

Andere Schlaf-Hosts können vor dem eigentlichen Paper-Prozess antworten oder den gesamten Java-Prozess stoppen. Dann kann das Paper-Plugin selbst keinen weiteren Zustand messen. Deren Schlafzustand ist ohne eigenes Signal oder gezielte Integration in diesen Anbieter nicht zuverlässig ableitbar. Dafür `sleep-motd-contains` mit einer eindeutigen MOTD-/Versionskennung befüllen oder `status-override: sleeping` verwenden. Weitere Overrides: `auto`, `online`, `offline`. Null Spieler allein wird nicht als Schlaf interpretiert. Rot bedeutet nicht zwingend eine abgeschaltete Maschine; auch Netzwerkfehler können die Ursache sein.

Mit **ServerNavigator ab 1.2.0 auf dem Proxy** ist die Integration automatisch aktiv (`integration.servernavigator: true`). Verwaltete Ziele werden über dessen öffentliche Schnittstelle an seine vorhandene Warteschlange übergeben. ServerHub startet keinen konkurrierenden Direktwechsel und übernimmt keinen eigenen Start-Timer: Navigator behält Readiness-Prüfung, Wakeup, Verzögerung, Berechtigungen, Cooldowns, Meldungen und Abbruch. Dessen laufende Prüfung/Start-/Verbindungsphase wird gelb dargestellt; der eigene Shift-F-Actionbar-Hinweis ruht während der Wartephase.

Ohne Navigator oder für dort nicht verwaltete Ziele gilt der normale Velocity-Wechsel. Eine vorhandene veraltete oder fehlerhafte Navigator-Schnittstelle führt zu einer Meldung, damit ein fehlgeschlagener Aufruf nicht dessen Warteablauf umgeht. Die Integration lässt sich zentral ausschalten. Die Statusfarbe selbst sperrt keine Verbindungen: ein rotes oder gelbes Ziel darf weiterhin über Navigator geweckt werden.

`status.ping-interval-seconds` (Standard **1**) und `status.timeout-ms` (**1000**) steuern die Prüfung. `status.enabled: false` blendet Punkte aus und beendet Status-Pings. Die frühere Verzögerung bis zum nächsten Backend-Handshake entfällt: der Proxy verteilt Änderungen sofort und prüft außerdem regelmäßig den Stand.

## Pack im Pluginordner bearbeiten

Auf dem Proxy ist **`plugins/serverhub-velocity/pack/`** die bearbeitbare Quelle. Darin liegen `pack.mcmeta`, die Texturen unter `assets/serverhub/textures/`, die Fontdefinitionen und `assets/serverhub/fonts/`. PNG-Dateien direkt bearbeiten und **`/serverhubproxy reload`** ausführen. Die Ausgabe **`serverhub-pack.zip`**, SHA-1 und Paket-ID werden automatisch neu erzeugt. Nicht die erzeugte ZIP als Quelldatei bearbeiten.

Eigene Änderungen in `pack/` bleiben erhalten. Unveränderte Standarddateien werden beim Versionswechsel aktualisiert; fehlende Standarddateien werden beim Start/Reload ergänzt. Dadurch bleibt ein unveränderter Standardordner auch nach dem Update im GitHub-Downloadmodus. Bei manuellen Änderungen an den neun Roll-/Buttonteilen deren Abmessungen und Glyphenzuordnung beibehalten. Schriftatlanten müssen das vorhandene 16×12-Latin-1-Raster behalten. Die `_shadow`-Definitionen sollen dieselbe Schrifttextur mit um einen Pixel geändertem Ascent verwenden. Links außerhalb des Packordners und zu große Pakete werden abgelehnt. Sicherungen der vorherigen Ausgabe liegen neben der ZIP.

Downloadoptionen stehen in `config.properties`:

| `pack.mode` | Auslieferung |
|---|---|
| `auto` | Unveränderter Standard über den festen GitHub-Release; eigenes Pack automatisch über den eingebauten HTTP-Server. Standard. |
| `self-host` | Immer eingebauter Pack-Server; Standard TCP-Port 8124. |
| `external` | Direkte HTTP(S)-ZIP-Adresse unter `pack.external-url`; Download muss exakt der erzeugten lokalen ZIP entsprechen. |
| `github` | Nur das unveränderte Standardpaket. Eigene Dateien werden mit einer Fehlermeldung abgelehnt. |

**Eigene Pack-Dateien oder eine eigene Schrift benötigen einen von den Spielern erreichbaren Download.** Im Automatikmodus dafür den Pack-Port (Standard **8124/TCP**) am Proxy freigeben. `self-host.public-host=auto` verwendet den Verbindungsnamen des Spielers. Bei SRV, Port-Mapping oder Reverse-Proxy die öffentlichen Werte `self-host.public-host` und `self-host.advertised-port` setzen. Bind/Port nach Serverstart nur durch Neustart ändern. Der eingebaute Server spricht HTTP; `self-host.scheme=https` setzt einen separat eingerichteten TLS-Reverse-Proxy voraus.

Alternativ die erzeugte ZIP auf einem eigenen Webhost bereitstellen und `external` konfigurieren. Nach einem weiteren Pack-Reload dort ebenfalls die neuen exakt gleichen Bytes hochladen; `/serverhubproxy packcheck` kontrolliert die SHA-1. Eigene Änderungen können nicht automatisch den unveränderten öffentlichen GitHub-Release ersetzen.

Ein Pflichtpaketangebot pro Netzwerkbeitritt bleibt erhalten. Backend-Wechsel und Reload senden kein zweites Angebot. Nach Pack-Änderungen einmal neu verbinden. Alte, bereits angebotene ZIP-Versionen bleiben während ihrer aktiven Sitzungen am eingebauten Host erreichbar. Nur die erfolgreiche Anwendung genau dieses Pakets schaltet das Menü frei; Ablehnung, Fehler oder Timeout führen zum Kick. Andere Ressourcenpakete werden nicht entfernt.

## Bedienung und Befehle

Shift + F öffnet das Menü, ebenso `/extramenü`, `/extramenu`, `/extramen` oder `/serverhub`. Bei umbelegtem Handtausch gilt die neue Taste; die Grafik zeigt die Standardkombination.

Der Hinweis aus drei Holzbrettern zeigt Shift / + / F rechts neben der Hotbar und belegt keinen Inventarslot. Zentral unter `shift-f-hint`: `enabled`, `interval-seconds` (1–2), `x-offset` (94–512 GUI-Pixel rechts der Bildschirmmitte) und `y-offset` (−32 bis 32, positiv nach unten). Die Actionbar-Ausgabe kann von anderen HUD-Plugins überlagert werden; F1 blendet sie aus. Die Grafik ist ein Tastaturhinweis. Optional: `hotbar-icon.enabled: true` und `slot: 8` für einen zusätzlichen Rechtsklick-Gegenstand; belegte Slots werden nur in freie Inventarplätze umgelagert.

| Aktion | Befehl / Berechtigung |
|---|---|
| Menü öffnen | `/extramenu`, `serverhub.use` (Standard alle) |
| Proxy-Konfiguration und Pack laden | `/serverhubproxy reload`, `serverhub.proxy.reload` |
| Status und Download prüfen | `/serverhubproxy status` / `packcheck`, gleiches Proxy-Recht |
| Eigenständigen Paper-Server neu laden | `/extramenu reload`, `serverhub.reload` |
| Ziel sperren | `serverhub.connect.<target>` am Proxy ausdrücklich auf `false` |

## Build und Prüfung

Java 25, Maven 3.9+ und Python 3:

```sh
python3 tools/package_pack.py
mvn -B clean verify
python3 tools/prepare_release.py
```

Installierbare Universal-JAR: `universal/target/ServerHub-2.5.0.jar`. Modul-JARs sind interne Builddateien. Normale Builds verpacken die eingecheckten Grafiken unverändert. Zum Neugenerieren Pillow installieren und `tools/build_pack.py` / `tools/render_preview.py` ausführen. Das Laufzeit-Rendering eigener TTFs erfolgt mit Java AWT ohne Desktopfenster. SnakeYAML wird in der Universal-JAR in einen privaten Namespace gepackt.

Tests prüfen unter anderem echte HTTP-Berichte ohne Spielertransport, automatische Einrichtung durch Status-Pings, Zustandswechsel, abgelaufene Meldungen und Wiederholungsangriffe, Schlafsignale, Navigator-Routing ohne Direktwechsel, Schriftmetriken über den Plugin-Kanal, Titelzentrierung, ausgerichtete Statuspunkte, manuelle Pack-Änderungen, Schriftimport, Backups, deterministische ZIPs, Proxy-Erkennung und den einmaligen Paketlebenszyklus. Getrennte Klassenlader prüfen beide Plattform-Einstiegspunkte und den gepackten Ressourcen-Builder. GitHub Actions veröffentlicht nach erfolgreichem Build auf `main` und prüft die öffentlichen JAR-/Pack-Downloads samt Prüfsummen und eingebetteten Pack-Bytes.

**Kein vollständiger Ingame-Test.** Vorschau und Statuswerte sind eine Designreferenz. Darstellung, native Scrollliste, HUD-Position sowie Start-/Schlafverhalten im eigenen Netzwerk prüfen. Bedrock/Geyser und andere Minecraft-Versionen sind nicht geprüft. ServerHub wird gegen Velocity 4.2.0 gebaut; die Tests für ServerNavigator 1.2.0 verwenden Velocity 4.3.0. Die Kopplung nutzt ausschließlich deren gemeinsame öffentliche Player-/Plugin-Schnittstellen.

Holztextur aus der bereitgestellten Vorlage. Rollenteile, Informationszeile, Statuspunkt und Schriftatlanten werden aus den mitgelieferten Quellen generiert. Schriften: DejaVu Sans Bold, Serif und Sans Mono; Lizenz im Pack und unter `art-source/FONT-LICENSE.txt`.

- [Mojang: Dialogkopf und Warnsymbol](https://feedback.minecraft.net/hc/en-us/articles/36627959856141-Minecraft-Java-Edition-Snapshot-25w20a)
- [Paper Dialog-API](https://docs.papermc.io/paper/dev/dialogs/)
- [Paper Server.isPaused](https://jd.papermc.io/paper/26.3/org/bukkit/Server.html#isPaused())
- [Paper ServerListPingEvent](https://jd.papermc.io/paper/26.3/com/destroystokyo/paper/event/server/PaperServerListPingEvent.html)
- [Velocity RegisteredServer](https://jd.papermc.io/velocity/4.2.0/com/velocitypowered/api/proxy/server/RegisteredServer.html)
