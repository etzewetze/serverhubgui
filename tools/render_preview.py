#!/usr/bin/env python3
"""Design reference using the pack's exact glyph advances; not an in-game screenshot."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageChops
import random

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
    def native_button(canvas,width,x,y):
        image=Image.new('RGBA',(width,20),'#7D7D7D');d=ImageDraw.Draw(image);rng=random.Random(25)
        for yy in range(1,19):
            for xx in range(1,width-1):
                value=rng.randrange(111,132);d.point((xx,yy),fill=(value,value,value,255))
        d.rectangle((0,0,width-1,19),outline='#161616');d.line((1,1,width-2,1),fill='#C6C6C6');d.line((1,1,1,18),fill='#ACACAC');d.line((1,18,width-2,18),fill='#4D4D4D');d.line((width-2,2,width-2,18),fill='#505050')
        canvas.alpha_composite(image,(x,y))
    canvas=Image.new('RGBA' ,(900,390),'#20242A');draw=ImageDraw.Draw(canvas)
    caption=ImageFont.truetype(str(ROOT/'art-source/ServerHubSans-Bold.ttf'),9)
    for col,(family,title)in enumerate((('sans','Sans'),('serif','Serif'),('mono','Mono'))):
        center=145+300*col
        draw.text((center,8),title,font=caption,anchor='mt',fill='#D9DDE4')
        text='Serverauswahl';roll=max(48,measure(text,family,12)+96)
        background(canvas,'scroll',roll,center-roll//2,28);label(canvas,text,family,12,center,43,'#3D2817')
        warning_x=center+roll//2+4;draw.rectangle((warning_x,34,warning_x+19,53),fill='#707070',outline='#222222');draw.polygon(((warning_x+10,38),(warning_x+3,50),(warning_x+17,50)),fill='#F6D957');draw.line((warning_x+10,42,warning_x+10,46),fill='#333333');draw.point((warning_x+10,48),fill='#333333')
        for i,(name,color,state)in enumerate([('Lobby','#A8E091','#62D86B'),('Survival','#FFF4D6','#FFD15C'),('Creative','#9FDCF0','#F05A56'),('Challenges','#FFF4D6','#62D86B'),('Ynn','#FFF4D6','#A7ADB4'),('Test','#F0A79A','#62D86B')]):
            x=center-112;y=90+i*25
            native_button(canvas,224,x,y)
            if family=='mono' and name=='Challenges':
                canvas.alpha_composite(Image.open(A/'textures/backgrounds/generated/blue_round.png').convert('RGBA').resize((224,20)),(x,y))
            label(canvas,fit(name,family,12,168),family,12,center,y+6,color)
            if name=='Survival':canvas.alpha_composite(Image.open(A/'textures/gui/news.png').convert('RGBA'),(x+208,y+5))
            draw.ellipse((x+10,y+7,x+15,y+12),fill=state)
        native_button(canvas,140,center-70,262);label(canvas,'Schließen',family,10,center,268,'#FFF4D6')
    # Schematic hotbar documents intended anchoring, not an actual client HUD.
    for col in range(9):
        x=360+col*20
        draw.rectangle((x,316,x+19,337),fill='#4E4E4E',outline='#AAA7A0')
    canvas.alpha_composite(Image.open(A/'textures/item/menu_icon.png').convert('RGBA'),(556,308))
    draw.text((450,365),'Designreferenz · beispielhafte Statuswerte · kein Ingame-Screenshot',font=ImageFont.truetype(str(ROOT/'art-source/ServerHubSans-Bold.ttf'),8),anchor='mt',fill='#B7BFCA')
    output=ROOT/'preview';output.mkdir(exist_ok=True)
    canvas.resize((1800,780),Image.Resampling.NEAREST).save(output/'Menue-Vorschau.png')
    print('Designreferenz:',output/'Menue-Vorschau.png')

if __name__=='__main__':main()
