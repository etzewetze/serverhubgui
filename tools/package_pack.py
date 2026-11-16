#!/usr/bin/env python3
"""Package committed Minecraft assets without resizing or regenerating artwork."""
from pathlib import Path
import hashlib, json, zipfile

ROOT=Path(__file__).resolve().parents[1]

def main():
    source=ROOT/"resource-pack"
    for path in source.rglob("*.json"):
        data=json.loads(path.read_text(encoding="utf-8"))
        for provider in data.get("providers",[]):
            if provider.get("type")=="bitmap":
                height=provider.get("height",8);ascent=provider.get("ascent")
                if not isinstance(height,int) or height<1 or not isinstance(ascent,int) or ascent>height:
                    raise SystemExit(f"Invalid bitmap height/ascent in {path}")
                chars=provider.get("chars",[])
                if not chars or not chars[0] or len({len(row) for row in chars})!=1:
                    raise SystemExit(f"Invalid bitmap character grid in {path}")
                ns,name=provider["file"].split(":",1)
                texture=source/"assets"/ns/"textures"/name
                if not texture.is_file():raise SystemExit(f"Missing bitmap: {texture}")
    output=ROOT/"resource-pack-output";output.mkdir(exist_ok=True)
    archive=output/"serverhub-pack.zip"
    with zipfile.ZipFile(archive,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9)as z:
        for path in sorted(source.rglob("*")):
            if path.is_file():
                entry=zipfile.ZipInfo(path.relative_to(source).as_posix(),date_time=(2026,11,16,0,0,0))
                entry.compress_type=zipfile.ZIP_DEFLATED
                z.writestr(entry,path.read_bytes())
    digest=hashlib.sha1(archive.read_bytes()).hexdigest()
    (output/"serverhub-pack.sha1").write_text(digest+"\n")
    print(f"serverhub-pack.zip: SHA-1 {digest}")

if __name__=="__main__":main()
