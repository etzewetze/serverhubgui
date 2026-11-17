# ServerHub 2.5.0

- **Direkte Schlafmeldungen:** Paper meldet den gemessenen Pausenzustand über einen eigenen HTTP-Statuskanal an den Proxy. Funktioniert auch ohne Spieler auf dem Backend; eine frische Schlafmeldung überschreibt einen weiter grünen oder fehlgeschlagenen Ping.
- **Vollautomatische Einrichtung:** Status-Pings des Proxys übermitteln die Daten für den Berichtskanal. Kein manuelles Einstellen oder vorheriges Betreten jedes Backends. Zugangsdaten sind pro Ziel gebunden und werden für Neustarts gespeichert.
- **Schnellere Aktualisierung:** Standardprüfung alle 1 Sekunde, Pinglimit 1000 ms; Änderungen werden direkt an die Menü-Backends verteilt. Geöffnete Menüs erneuern sich bei tatsächlichen Änderungen. `status.refresh-open-menu: false` verhindert eine mögliche Rücksetzung der nativen Scrollposition.
- **Sichtbare Intervalle:** `plugins/serverhub-velocity/servers.yml`, Abschnitt `status` am Dateianfang. Die früheren Standardwerte werden mit Sicherung der alten Datei aktualisiert; eigene abweichende Intervalle bleiben erhalten.
- **Statusdiagnose:** `/serverhubproxy status` zeigt pro Ziel Backend-Meldung, deren Alter und das Ping-Ergebnis. Veraltete und verspätete Meldungen können neuere Zustände nicht überschreiben.
- **Feste Überschrift:** „Serverauswahl“ steht mit ihrer textabhängig breiten Rolle im festen Dialogkopf und scrollt nicht mit. Der Platz für Minecrafts Warnsymbol wird ausgeglichen.
- **Minecraft-Buttons:** standardmäßig native Buttons einschließlich Hover-/Fokusdarstellung. Schriften, Textfarben und Breiten bleiben einstellbar. Die optionale alte Holzoptik ist unter `menu.button-style: wood` verfügbar.

**Update:** dieselbe `ServerHub-2.5.0.jar` auf Proxy und allen Paper-Backends installieren und neu starten. ServerNavigator 1.2.0 bleibt kompatibel und muss für dieses Update nicht geändert werden. Vorherige ServerHub-Versionen nicht im selben Netzwerk mischen. Java 25, Paper 26.3; für ServerNavigator weiterhin Velocity 4.3.0.

**Statuskanal:** TCP **8125** muss intern vom Backend zum Proxy erreichbar sein. Bei Docker/NAT den Port am Proxy weiterleiten und gegebenenfalls `status.report-host` zentral setzen. Bei einem ausdrücklich gesetzten Host diesen nach einer Änderung einmal durch einen Backend-Beitritt über den Proxy bestätigen; bei `auto` ist das nicht nötig. Der Statusport ist unabhängig vom Pack-Port 8124 und muss nicht für Spieler öffentlich erreichbar sein. Bei fehlender Verbindung bleibt der Ping-Fallback aktiv. Native Paper-Pause ist messbar; ein externer Hoster, der den ganzen Java-Prozess stoppt, benötigt ein eigenes Schlafsignal bzw. eine passende Anbieterintegration.

**Gelbes Warnsymbol:** wird vom Minecraft-Client bei benutzerdefinierten Dialogen angezeigt. Es ist keine Plugin-Fehlermeldung und hat in der normalen Dialog-API keinen Ausschalter.

Automatisierte Build-, Transport-, Status- und Paketprüfungen; öffentliche Downloads mit Prüfsummen kontrolliert. Kein vollständiger Ingame-Test mit verbundenem Minecraft-Client.
