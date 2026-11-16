#!/usr/bin/env python3
"""Prepare the supplied/generated art as exact Minecraft sprites and font atlases.

Requires Pillow, a source background, the supplied button, and the bundled font.
No network access. Output is deterministic (including ZIP timestamps).
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json, zipfile, hashlib, shutil

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "resource-pack"
ASSETS = OUT / "assets/serverhub"

def save_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")

def main():
    if OUT.exists():shutil.rmtree(OUT)
    (ASSETS/"textures/gui").mkdir(parents=True, exist_ok=True)
    src=ROOT/"art-source"
    # Alpha threshold ignores nearly transparent generator padding.
    def trim(path):
        im=Image.open(path).convert("RGBA")
        box=im.getchannel("A").point(lambda a:255 if a>200 else 0).getbbox()
        return im.crop(box)
    panel=trim(src/"background-source.png").resize((176,222),Image.Resampling.NEAREST)
    button=trim(src/"buttons-source.png").resize((64,18),Image.Resampling.NEAREST)
    panel.save(ASSETS/"textures/gui/background.png")
    button.save(ASSETS/"textures/gui/buttons.png")
    providers=[]
    for count in range(7):
        canvas=panel.copy();draw=ImageDraw.Draw(canvas)
        for i in range(count):canvas.alpha_composite(button,(12+(i%2)*88,21+(i//2)*34))
        for x in (12,66,120):canvas.alpha_composite(button.resize((44,15),Image.Resampling.NEAREST),(x,108))
        draw.rectangle((8,125,167,137),fill="#B9AC96")
        draw.line((8,125,167,125),fill="#937C5B")
        draw.line((8,137,167,137),fill="#6B5C48")
        # Player inventory is intentionally still visible and usable after closing.
        for row in range(4):
            y=139+row*18 if row<3 else 197
            for col in range(9):
                x=7+18*col
                draw.rectangle((x,y,x+17,y+17),fill="#2F2D2A")
                draw.line((x,y+17,x+17,y+17,x+17,y),fill="#9C8665")
                draw.rectangle((x+1,y+1,x+16,y+16),fill="#55534F")
        canvas.save(ASSETS/f"textures/gui/menu_{count}.png")
        providers.append({"type":"bitmap","file":f"serverhub:gui/menu_{count}.png","height":222,"ascent":13,"chars":[chr(0xE000+count)]})
    save_json(ASSETS/"font/gui.json",{"providers":providers})
    advances={}
    for bit in range(11):advances[chr(0xF100+bit)]=-(1<<bit);advances[chr(0xF200+bit)]=1<<bit
    save_json(ASSETS/"font/space.json",{"providers":[{"type":"space","advances":advances}]})
    widths=[]
    # Plain Latin + Latin-1, including äöüÄÖÜß. Rendered once to a crisp bitmap.
    codes=list(range(32,128))+list(range(160,256))
    chars=["".join(chr(c) for c in codes[i:i+16]) for i in range(0,len(codes),16)]
    for size in (8,10,12):
        atlas=Image.new("RGBA",(256,16*len(chars)),(255,255,255,0))
        font=ImageFont.truetype(str(src/"DejaVuSansCondensed.ttf"),size)
        for index,code in enumerate(codes):
            if code in (32,160):widths.append(f"{size}.{code}={max(3,size//2)}");continue
            glyph=Image.new("L",(16,16),0)
            ImageDraw.Draw(glyph).text((0,12),chr(code),font=font,fill=255,anchor="ls",stroke_width=0)
            glyph=glyph.point(lambda v:255 if v>=100 else 0)
            bbox=glyph.getbbox()
            width=(bbox[2] if bbox else 0)+1
            widths.append(f"{size}.{code}={width}")
            rgba=Image.new("RGBA",(16,16),(255,255,255,0));rgba.putalpha(glyph)
            atlas.alpha_composite(rgba,((index%16)*16,(index//16)*16))
        atlas.save(ASSETS/f"textures/gui/latin_{size}.png")
        for row,top in {"header":2,"r0":22,"r1":56,"r2":90,"status":122,"nav":108}.items():
            save_json(ASSETS/f"font/label{size}_{row}.json",{"providers":[
                {"type":"space","advances":{" ":max(3,size//2),"\u00a0":max(3,size//2)}},
                {"type":"bitmap","file":f"serverhub:gui/latin_{size}.png","height":16,"ascent":13-top,"chars":chars}
            ]})
    target=ROOT/"paper/src/main/resources/font-widths.properties"
    target.parent.mkdir(parents=True,exist_ok=True);target.write_text("\n".join(widths)+"\n")
    icon=Image.new("RGBA",(16,16),(0,0,0,0))
    for y in (2,7,12):icon.alpha_composite(button.resize((16,4),Image.Resampling.NEAREST),(0,y))
    (ASSETS/"textures/item").mkdir(parents=True,exist_ok=True)
    icon.save(ASSETS/"textures/item/menu_icon.png")
    save_json(ASSETS/"font/hint.json",{"providers":[
        {"type":"bitmap","file":"serverhub:item/menu_icon.png","height":9,"ascent":8,"chars":["\uE100"]},
        {"type":"reference","id":"minecraft:default"}
    ]})
    save_json(ASSETS/"items/menu_icon.json",{"model":{"type":"minecraft:model","model":"serverhub:item/menu_icon"}})
    save_json(ASSETS/"models/item/menu_icon.json",{"parent":"minecraft:item/generated","textures":{"layer0":"serverhub:item/menu_icon"}})
    save_json(ASSETS/"items/invisible.json",{"model":{"type":"minecraft:empty"}})
    save_json(OUT/"pack.mcmeta",{"pack":{"description":"ServerHub · Stein & Holz · Java 26.3","min_format":[97,1],"max_format":[97,1]}})
    shutil.copyfile(src/"FONT-LICENSE.txt",OUT/"FONT-LICENSE.txt")
    (ROOT/"resource-pack-output").mkdir(exist_ok=True)
    zip_path=ROOT/"resource-pack-output/serverhub-pack.zip"
    with zipfile.ZipFile(zip_path,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for path in sorted(OUT.rglob("*")):
            if path.is_file():
                info=zipfile.ZipInfo(str(path.relative_to(OUT)),date_time=(2026,11,16,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
                z.writestr(info,path.read_bytes())
    sha=hashlib.sha1(zip_path.read_bytes()).hexdigest()
    (ROOT/"resource-pack-output/serverhub-pack.sha1").write_text(sha+"\n")
    print(f"Resource pack: {zip_path.name}; SHA-1 {sha}")

if __name__=="__main__":main()
