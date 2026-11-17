ServerHub 2.5.1 erweitert die zentrale Serverauswahl für Velocity und Paper.

- **AMP-Schlafstatus:** optionale direkte CubeCoders-AMP-API-Abfrage über `amp.yml`, mit Zuordnung pro Server, einstellbarem Intervall und Ablauf alter Werte. Ein zwischengespeicherter grüner Minecraft-Ping überstimmt keinen AMP-Schlafstatus. Ohne aktuelle, eindeutige AMP-Daten erscheint Grau statt einer behaupteten Online-Anzeige.
- **Mehrere eigene Schriften:** beliebig benannte TTF/OTF in `fonts/` oder mehrere HTTPS-Links unter `fonts` in `servers.yml`. Fehlerhafte Schriftdateien legen das Pack nicht mehr still; Diagnose in `font-problems.txt`, verfügbare IDs in `fonts-available.txt` und `/serverhubproxy fonts`.
- **Mehr Platz:** automatische Buttonbreite mit gemeinsamer Ausrichtung, begrenzbar und pro Server überschreibbar. Neue transparente Pergamentrolle mit breitenvariablen Mittelteilen und größerem Innenabstand; Titel weiterhin fest über der Scrollliste.
- **Eigene Buttongrafiken:** PNGs unter `backgrounds/`, pro Server auswählbar, getönt und optional mit abgerundeter Grafik. Standard bleibt der native Minecraft-Button. Native Klickfläche und darunterliegende Ecken bleiben rechteckig.
- **Server-News:** `/sh news start <Nachricht>` und `/sh news stop`. Aus der Lobby für ein anderes Ziel: `/sh news start --server challenges <Nachricht>`. News-Symbol am Button; Klick öffnet die Nachricht mit „Server betreten“ und „Zurück“. Zentrale Speicherung am Proxy in `news.yml`. Berechtigung `serverhub.news` auf Paper und Proxy.
- **Optionale Infos:** Hover standardmäßig aus, global und pro Server anpassbar. Präfixe mit Spielerzahl und optionalem PlaceholderAPI auf dem aktuellen Paper-Server.

**Update:** Dieselbe `ServerHub-2.5.1.jar` auf Proxy und allen Paper-Backends installieren und neu starten. Menüprotokoll 3 ist nicht mit älteren ServerHub-Versionen mischbar. Eigene Konfigurationen und Packdateien bleiben erhalten; unveränderte alte Standardwerte für Breite und Titelabstand werden migriert.

**Für AMP einmalig `amp.yml` am Proxy aktivieren und Paneladresse sowie Benutzer/Service-Login konfigurieren.** Falls Namen abweichen, `amp-instance` pro Ziel setzen. Keine AMP-Zugangsdaten werden an Backends oder Spieler übertragen. ServerNavigator ab 1.2.0 behält seine Start- und Verbindungsverzögerungen.

**Eigene Schriften/Grafiken brauchen einen für Spieler erreichbaren Packdownload.** Bei `pack.mode=auto` wechselt ein angepasstes Pack auf den eingebauten HTTP-Server, standardmäßig 8124/TCP. Diesen Port und gegebenenfalls `self-host.public-host`/`advertised-port` konfigurieren oder die erzeugte ZIP extern hosten. `/serverhubproxy packcheck` prüft URL und Prüfsumme vom Proxy aus. Danach neu verbinden.

Java 25, Minecraft Java/Paper 26.3, Velocity 4.2/4.3 (ServerNavigator 1.2.0: 4.3). Automatisierte Tests und öffentliche Release-Prüfungen stehen in `PRUEFUNG.txt`. Kein vollständiger Spieltest oder Zugriff auf die konkrete AMP-Installation. Das gelbe Warnsymbol für eigene Dialoge wird weiterhin vom Minecraft-Client angezeigt und ist per Dialog-API nicht abschaltbar.
