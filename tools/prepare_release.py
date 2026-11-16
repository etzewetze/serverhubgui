#!/usr/bin/env python3
"""Validate the universal artifact and prepare exactly the public release files."""
from pathlib import Path
import hashlib, json, shutil, zipfile
import xml.etree.ElementTree as E

ROOT=Path(__file__).resolve().parents[1]

def main():
    ns={"m":"http://maven.apache.org/POM/4.0.0"}
    version=E.parse(ROOT/"pom.xml").getroot().find("m:version",ns).text
    if (ROOT/"release-version.txt").read_text().strip()!=version:raise SystemExit("release-version.txt and pom.xml differ")
    jar=ROOT/f"universal/target/ServerHub-{version}.jar"
    pack=ROOT/"resource-pack-output/serverhub-pack.zip"
    with zipfile.ZipFile(jar)as z:
        names=z.namelist()
        for required in ("plugin.yml","velocity-plugin.json","config.yml","config.properties","font-widths.properties","serverhub-pack.zip","de/serverhub/paper/ServerHubPaper.class","de/serverhub/velocity/ServerHubVelocity.class","de/serverhub/common/Wire.class"):
            if required not in names:raise SystemExit(f"Missing universal resource: {required}")
        if any(n.startswith(("org/bukkit/","com/velocitypowered/api/"))for n in names):raise SystemExit("Server APIs must not be bundled")
        if z.read("serverhub-pack.zip")!=pack.read_bytes():raise SystemExit("Bundled ZIP differs")
        if json.loads(z.read("velocity-plugin.json"))["version"]!=version:raise SystemExit("Velocity descriptor version differs")
        if f"version: {version}"not in z.read("plugin.yml").decode():raise SystemExit("Paper descriptor version differs")
    if f"/v{version}/serverhub-pack.zip"not in (ROOT/"velocity/src/main/java/de/serverhub/velocity/PackUrls.java").read_text():raise SystemExit("Fixed release URL version differs")
    tests=[]
    reports=list(ROOT.glob("*/target/surefire-reports/TEST-*.xml"))+list(ROOT.glob("*/target/failsafe-reports/TEST-*.xml"))
    if not reports:raise SystemExit("No test reports; run mvn clean verify first")
    for path in sorted(reports):
        suite=E.parse(path).getroot()
        if int(suite.attrib.get("errors",0))or int(suite.attrib.get("failures",0)):raise SystemExit(f"Failed tests in {path}")
        tests.extend(c.attrib["classname"]+"."+c.attrib["name"]for c in suite.findall("testcase"))
    if not any("UniversalJarIT"in t for t in tests):raise SystemExit("Universal platform-isolation tests did not run")
    output=ROOT/"dist";output.mkdir(exist_ok=True)
    for path in (jar,pack,ROOT/"resource-pack-output/serverhub-pack.sha1",ROOT/"README.md",ROOT/"preview/Menue-Vorschau.png"):
        shutil.copyfile(path,output/path.name)
    (output/"PRUEFUNG.txt").write_text(f"ServerHub {version}\n{len(tests)} Tests erfolgreich. Java 25; Paper 26.3; Velocity 4.2.0.\n\n"+"\n".join("OK "+t for t in tests)+"\n\nUniversal-JAR und eingebettete ZIP geprüft.\nKein vollständiger Spieltest mit verbundenem Minecraft-Client.\n")
    sums=[]
    for name in (jar.name,"serverhub-pack.zip"):
        sums.append(hashlib.sha256((output/name).read_bytes()).hexdigest()+"  "+name)
    (output/"SHA256SUMS.txt").write_text("\n".join(sums)+"\n")
    archive=output/f"ServerHub-{version}-Komplettpaket.zip"
    with zipfile.ZipFile(archive,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9)as z:
        for name in (jar.name,"serverhub-pack.zip","serverhub-pack.sha1","README.md","Menue-Vorschau.png","PRUEFUNG.txt","SHA256SUMS.txt"):
            z.write(output/name,name)
    print(f"Release {version}: {len(tests)} tests; universal JAR {jar.stat().st_size} bytes; pack SHA-1 {hashlib.sha1(pack.read_bytes()).hexdigest()}")

if __name__=="__main__":main()
