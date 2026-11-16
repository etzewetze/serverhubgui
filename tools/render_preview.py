#!/usr/bin/env python3
"""Design reference using the pack's exact glyph advances; not an in-game screenshot."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageChops

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'resource-pack/assets/serverhub'
CODES=list(range(32,128))+list(range(160,256))

def main():
    widths=dict(line.split('=')for line in (ROOT/'paper/src/main/resources/font-widths.properties').read_text().splitlines())
    atlases={(f,s):Image.open(A/f'textures/gui/latin_{f}_{s}.png').convert('RGBA')for f in ('sans','serif','mono')for s in (8,10,12,14,16)}
    positions={c:i for i,c in enumerate(CODES)}
    def measure(text,family,size):return sum(int(widths[f'{family}.{size}.{ord(c)}'])for c in text)
    def fit(text,family,size,maximum):
        if measure(text,family,size)<=maximum:return text
        while text and measure(text+'..',family,size)>maximum:text=text[:-1]
        return text+'..'
    def label(canvas,text,family,size,center,origin,color,shadow=True):
        start=center-measure(text,family,size)//2
        for dx,dy,tint in ([(1,1,'#24180E')]if shadow else[])+[(0,0,color)]:
            x=start+dx
            for char in text:
                i=positions[ord(char)]
                if char not in (' ','\u00a0'):
                    glyph=atlases[family,size].crop((i%16*24,i//16*24,i%16*24+24,i//16*24+24))
                    ink=Image.new('RGBA',glyph.size,tint);ink.putalpha(glyph.getchannel('A'));canvas.alpha_composite(ink,(x,origin-10+dy))
                x+=int(widths[f'{family}.{size}.{ord(char)}'])
    def background(canvas,kind,width,x,y,tint='#FFFFFF'):
        parts=[Image.open(A/f'textures/gui/{kind}_{p}.png').convert('RGBA')for p in ('left','mid','right')]
        left,middle,right=parts
        image=Image.new('RGBA',(width,left.height));image.alpha_composite(left)
        image.alpha_composite(middle.resize((width-left.width-right.width,middle.height)),(left.width,0));image.alpha_composite(right,(width-right.width,0))
        if tint!='#FFFFFF':image=ImageChops.multiply(image,Image.new('RGBA',image.size,tint))
        canvas.alpha_composite(image,(x,y))
    canvas=Image.new('RGBA',(640,350),'#20242A');draw=ImageDraw.Draw(canvas)
    caption=ImageFont.truetype(str(ROOT/'art-source/ServerHubSans-Bold.ttf'),9)
    for col,(family,title)in enumerate((('sans','Sans'),('serif','Serif'),('mono','Mono'))):
        center=110+210*col
        draw.text((center,8),title,font=caption,anchor='mt',fill='#D9DDE4')
        text='Serverauswahl';roll=max(48,measure(text,family,12)+24)
        background(canvas,'scroll',roll,center-roll//2,30);label(canvas,text,family,12,center,41,'#3D2817')
        for i,(name,color,state)in enumerate([('Lobby','#A8E091','#62D86B'),('Survival','#F2C06D','#FFD15C'),('Creative','#9FDCF0','#F05A56'),('Event','#FFF4D6','#62D86B'),('Kalle','#F0A79A','#A7ADB4')]):
            x=center-70;y=82+i*25
            background(canvas,'button',140,x,y);label(canvas,fit(name,family,12,100),family,12,center,y+6,color)
            draw.ellipse((x+10,y+7,x+15,y+12),fill=state)
        background(canvas,'button',140,center-70,226);label(canvas,'Schließen',family,10,center,232,'#FFF4D6')
    # Schematic hotbar documents intended anchoring, not an actual client HUD.
    for col in range(9):
        x=230+col*20
        draw.rectangle((x,290,x+19,311),fill='#4E4E4E',outline='#AAA7A0')
    canvas.alpha_composite(Image.open(A/'textures/item/menu_icon.png').convert('RGBA'),(426,282))
    draw.text((320,330),'Designreferenz · beispielhafte Statuswerte · kein Ingame-Screenshot',font=ImageFont.truetype(str(ROOT/'art-source/ServerHubSans-Bold.ttf'),8),anchor='mt',fill='#B7BFCA')
    output=ROOT/'preview';output.mkdir(exist_ok=True)
    canvas.resize((1920,1050),Image.Resampling.NEAREST).save(output/'Menue-Vorschau.png')
    print('Designreferenz:',output/'Menue-Vorschau.png')

if __name__=='__main__':main()
