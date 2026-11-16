# ServerHub 2.3.0

- **Zentrale Konfiguration:** `plugins/serverhub-velocity/servers.yml` steuert Servernamen, Farben, Schriftgrößen, Rechte, Menü und HUD für das ganze Netzwerk. Alle erkannten Velocity-Ziele werden in dieser Datei ergänzt; bestehende Anpassungen bleiben erhalten. `/serverhubproxy reload` verteilt Änderungen ohne manuelle Backend-Einstellungen.
- **Automatische Netzwerkerkennung:** Paper erkennt die vorhandene Forwarding-Konfiguration. Im Netzwerk übernimmt es bestätigte Proxy-Einstellungen; auf eigenständigen Servern gibt es keinen Proxy-Timeout-Kick und keine funktionslose Netzwerk-Auswahl.
- **Listen oder Raster:** `menu.layout: list|grid`. `menu.navigation: scroll|pages` erlaubt eine native scrollbare Liste oder Seiten. Standard: Server untereinander, Scrollen bei Platzmangel.
- **Dezente Info:** im Scrollmodus keine Seiteninfo; auf einer einzelnen Seite standardmäßig ebenfalls keine. Optional ausschaltbare Info steht im Seitenmodus direkt vor „Schließen“. Die Serveranzahl ist standardmäßig ausgeblendet.
- **Statuspunkte:** grün bei gültigem Ping, rot bei fehlender Erreichbarkeit, grau während einer noch offenen Prüfung. Gelb nur bei einer ausdrücklich konfigurierten Schlaf-Kennung in MOTD/Versionsbeschreibung oder `status-override: sleeping`. Ein leerer Server wird nicht als schlafend ausgegeben.
- **Bestehende Funktionen:** native Dialoge ohne Truhen-/Spielerinventar, Pergamentüberschrift, Holzbuttons und Shift/+/F-Hinweis; weiterhin eine Universal-JAR und ein Pflichtpaketangebot pro Netzwerkverbindung.

**Update:** dieselbe neue JAR auf Velocity und allen Paper-Backends installieren und das Netzwerk vollständig neu starten. Danach nur noch `servers.yml` am Proxy bearbeiten und dort neu laden. Frühere lokale Paper-Menüanpassungen werden im Netzwerkmodus nicht verwendet; gewünschte Werte einmal zentral übernehmen. Unveränderte Standardpakete aus 2.1/2.2 werden gesichert und aktualisiert. Eigene bzw. extern gehostete Pakete selbst aktualisieren. **Paper 26.3, Velocity 4.2.0, Java 25.**

**Prüfung:** automatisierte Tests und öffentliche Release-Downloads samt Prüfsummen. Kein vollständiger Ingame-Test; Scroll-/HUD-Darstellung im eigenen Client prüfen. Ein normaler Ping kann einen allgemeinen Schlafzustand nicht zuverlässig melden; die gelbe Anzeige setzt ein explizites Signal oder eine Vorgabe voraus.
