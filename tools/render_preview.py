#!/usr/bin/env python3
"""Artwork/layout reference for the native dialog; not a running-client screenshot."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'resource-pack/assets/serverhub'
CODES=list(range(32,128))+list(range(160,256))

def main():
    widths=dict(line.split('=')for line in (ROOT/'paper/src/main/resources/font-widths.properties').read_text().splitlines())
    atlases={s:Image.open(A/f'textures/gui/latin_{s}.png').convert('RGBA')for s in (8,10,12)}
    positions={c:i for i,c in enumerate(CODES)}
    def label(canvas,text,size,center,baseline,color):
        width=sum(int(widths[f'{size}.{ord(c)}'])for c in text)
        x=center-width//2
        for char in text:
            i=positions[ord(char)]
            if char!=' ':
                glyph=atlases[size].crop((i%16*16,i//16*16,i%16*16+16,i//16*16+16))
                ink=Image.new('RGBA',glyph.size,color);ink.putalpha(glyph.getchannel('A'));canvas.alpha_composite(ink,(x,baseline-4))
            x+=int(widths[f'{size}.{ord(char)}'])
    canvas=Image.new('RGBA',(360,320),'#20242A');draw=ImageDraw.Draw(canvas)
    scroll=Image.open(A/'textures/gui/scroll.png').convert('RGBA')
    button=Image.open(A/'textures/gui/button.png').convert('RGBA')
    canvas.alpha_composite(scroll,(70,20));label(canvas,'Serverauswahl',12,180,34,'#3D2817')
    for i,(text,color,state)in enumerate([('Lobby','#A8E091','#62D86B'),('Survival','#F2C06D','#FFD15C'),('Creative','#9FDCF0','#F05A56'),('Event','#FFF4D6','#62D86B'),('Kalle','#F0A79A','#A7ADB4')]):
        x=110;y=74+i*25
        canvas.alpha_composite(button,(x,y));label(canvas,text,12,x+74,y+6,color)
        draw.ellipse((x+9,y+7,x+14,y+12),fill=state)
    canvas.alpha_composite(button,(110,220));label(canvas,'Schließen',10,180,226,'#FFF4D6')
    # A schematic hotbar solely to document the intended HUD anchoring.
    for col in range(9):
        x=89+col*20
        draw.rectangle((x,291,x+19,312),fill='#4E4E4E',outline='#AAA7A0')
    badge=Image.open(A/'textures/item/menu_icon.png').convert('RGBA');canvas.alpha_composite(badge,(286,283))
    output=ROOT/'preview';output.mkdir(exist_ok=True)
    canvas.resize((1080,960),Image.Resampling.NEAREST).save(output/'Menue-Vorschau.png')
    print('Designreferenz, kein Ingame-Screenshot:',output/'Menue-Vorschau.png')

if __name__=='__main__':main()
