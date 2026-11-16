# ServerHub 2.2.0

- **Automatische Serverliste:** alle aktuell in Velocity registrierten Ziele werden direkt vom Proxy übernommen und regelmäßig neu abgeglichen. Eigene Namen, Farben, Beschreibungen und Rechte sind als optionale Anpassungen weiter möglich.
- **Native Dialoge:** das Servermenü öffnet kein Truhen- oder Spielerinventar. Echte Dialogaktionen ersetzen unsichtbare Items und Inventarslots. Zwei Spalten, Seiten, Tooltips und Escape bleiben nutzbar.
- **Lesbare Gestaltung:** größere, breite Holzbuttons; neue kräftige DejaVu-Sans-Bold-Schrift; „Serverauswahl“ mittig auf einer Pergamentrolle mit dunkler Schrift.
- **HUD-Hinweis:** drei Holzbretter mit **Shift / + / F** rechts neben der Hotbar; kein belegter Inventarslot. Position über `shift-f-hint.x-offset` und `y-offset` anpassbar. Ausgabe über Actionbar-Font, daher mögliche Überschneidungen mit anderen HUD-/Actionbar-Plugins.
- **Update von 2.1:** Paper-Konfiguration wird vor der Umstellung gesichert, eigene Ziele/Rechte bleiben erhalten. Im GitHub-Modus wird ausschließlich die unveränderte alte Standard-ZIP gesichert und aktualisiert. Eigene oder extern gehostete Pakete selbst aktualisieren.
- **Paketpflicht:** weiterhin eine Universal-JAR auf Proxy und Backends, HTTPS-Standarddownload und genau ein Paketangebot pro Netzwerkverbindung. Backend-Wechsel und Reload lösen keinen erneuten Download aus.

**Installation:** Velocity und Paper vollständig beenden, alte ServerHub-JARs entfernen, dieselbe neue JAR auf Proxy und allen Backends installieren und vollständig neu starten. Anschließend `/serverhubproxy packcheck` ausführen und neu verbinden. **Paper 26.3, Velocity 4.2.0, Java 25. Kein Client-Mod nötig.**

**Prüfung:** automatisierte Tests für dynamische Ziele, Berechtigungen, Protokollgrenzen, Migration und den bestehenden Paketlebenszyklus; zusätzlich Plattform-Isolation der Universal-JAR und öffentliche Release-Downloads samt Prüfsummen. Kein vollständiger Ingame-Test. Die Menüvorschau ist eine Designreferenz; HUD-Ausrichtung und Dialogdarstellung im eigenen Client prüfen.
