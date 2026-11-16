#!/usr/bin/env python3
"""Render an exact atlas-based design preview, not an in-game screenshot."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import yaml

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/"resource-pack/assets/serverhub"

def main():
    settings=yaml.safe_load((ROOT/"paper/src/main/resources/config.yml").read_text())
    entries=list(settings["servers"].values())[:6]
    panel=Image.open(A/f"textures/gui/menu_{len(entries)}.png").convert("RGBA")
    widths=dict(line.split("=")for line in (ROOT/"paper/src/main/resources/font-widths.properties").read_text().splitlines())
    codes=list(range(32,128))+list(range(160,256));positions={c:i for i,c in enumerate(codes)}
    atlases={size:Image.open(A/f"textures/gui/latin_{size}.png").convert("RGBA")for size in (8,10,12)}
    def width(text,size):return sum(int(widths[f"{size}.{ord(c)}"])for c in text)
    def label(text,size,top,center,max_width,color):
        if width(text,size)>max_width:
            while text and width(text+"..",size)>max_width:text=text[:-1]
            text+=".."
        x=center-width(text,size)//2
        for char in text:
            i=positions[ord(char)]
            if char!=" ":
                glyph=atlases[size].crop((i%16*16,i//16*16,i%16*16+16,i//16*16+16))
                tint=Image.new("RGBA",glyph.size,color);tint.putalpha(glyph.getchannel("A"));panel.alpha_composite(tint,(x,top))
            x+=int(widths[f"{size}.{ord(char)}"])
    label(settings["menu"]["title"],10,2,88,152,settings["menu"]["title-color"])
    for i,e in enumerate(entries):label(e["name"],e.get("font-size",settings["menu"]["font-size"]),22+i//2*34,44+i%2*88,60,e["color"])
    label("Seite 1 / 1",8,122,132,64,"#5A4D3A")
    label("Inventar",8,124,27,55,"#404040")
    for i,text in enumerate(("Zurück","Schließen","Weiter")):label(text,8,108,34+i*54,40,"#FFF4D6"if i==1 else"#C5B6A0")
    output=ROOT/"preview";output.mkdir(exist_ok=True)
    panel.resize((704,888),Image.Resampling.NEAREST).save(output/"Menue-Vorschau.png")
    print(output/"Menue-Vorschau.png")

if __name__=="__main__":main()
