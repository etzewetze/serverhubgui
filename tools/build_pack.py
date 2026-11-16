#!/usr/bin/env python3
"""Deterministic native-dialog artwork, readable font and three-line HUD badge.

Requires Pillow. Minecraft's global GUI sprites and shaders are untouched.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json, random, shutil
from package_pack import main as package

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'resource-pack'
A=OUT/'assets/serverhub'

def save_json(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def main():
    if OUT.exists():shutil.rmtree(OUT)
    source=ROOT/'art-source'
    gui=A/'textures/gui';gui.mkdir(parents=True,exist_ok=True)
    button=Image.open(source/'buttons-source.png').convert('RGBA').crop((6,2,58,16)).resize((140,20),Image.Resampling.NEAREST)
    wood=Image.new('RGBA',(140,20),'#62431F');wood.alpha_composite(button)
    ImageDraw.Draw(wood).rectangle((0,0,139,19),outline='#493016',width=2)
    wood.save(gui/'button.png')
    scroll=Image.new('RGBA',(220,30),(0,0,0,0));d=ImageDraw.Draw(scroll)
    d.rectangle((10,3,209,26),fill='#DEC18A',outline='#6A4829',width=1)
    d.line((12,4,207,4),fill='#F7E4B0');d.line((12,25,207,25),fill='#A77B42')
    rng=random.Random(220)
    for _ in range(190):
        x=rng.randrange(13,207);y=rng.randrange(6,24)
        d.point((x,y),fill=rng.choice(('#D7B980','#E7CC97','#D2B27A')))
    for left,right in ((0,13),(206,219)):
        d.rounded_rectangle((left,1,right,28),radius=4,fill='#B68B4C',outline='#604020')
        d.line((left+3,4,left+3,25),fill='#F5D59A',width=2)
        d.line((right-2,4,right-2,25),fill='#8E622F')
    scroll.save(gui/'scroll.png')
    info=Image.new('RGBA',(140,20),'#D8BD89');di=ImageDraw.Draw(info)
    di.rectangle((0,0,139,19),outline='#8C663A');di.line((1,1,138,1),fill='#EDD7A9')
    info.save(gui/'info.png')
    dot=Image.new('RGBA',(6,6),(255,255,255,0));ImageDraw.Draw(dot).ellipse((0,0,5,5),fill='white');dot.save(gui/'status.png')
    save_json(A/'font/status.json',{'providers':[{'type':'bitmap','file':'serverhub:gui/status.png','height':6,'ascent':6,'chars':['\uE300']}]})
    # Nine-slice glyphs let the client build a roll/button of the measured text width.
    parts=[('scroll_left',scroll.crop((0,0,12,30)),0xE400,18),('scroll_mid',scroll.crop((110,0,111,30)),0xE401,18),('scroll_right',scroll.crop((208,0,220,30)),0xE402,18),
           ('button_left',wood.crop((0,0,4,20)),0xE410,13),('button_mid',wood.crop((70,0,71,20)),0xE411,13),('button_right',wood.crop((136,0,140,20)),0xE412,13),
           ('info_left',info.crop((0,0,4,20)),0xE420,13),('info_mid',info.crop((70,0,71,20)),0xE421,13),('info_right',info.crop((136,0,140,20)),0xE422,13)]
    providers=[]
    for name,image,code,ascent in parts:
        image.save(gui/(name+'.png'));providers.append({'type':'bitmap','file':'serverhub:gui/'+name+'.png','height':image.height,'ascent':ascent,'chars':[chr(code)]})
    providers.insert(0,{'type':'space','advances':{'\uF0FF':-1}})
    save_json(A/'font/gui.json',{'providers':providers})
    advances={}
    for bit in range(11):
        advances[chr(0xF100+bit)]=-(1<<bit);advances[chr(0xF200+bit)]=1<<bit
    save_json(A/'font/space.json',{'providers':[{'type':'space','advances':advances}]})
    codes=list(range(32,128))+list(range(160,256))
    chars=[''.join(chr(c) for c in codes[i:i+16]) for i in range(0,len(codes),16)]
    widths=[]
    families={'sans':'ServerHubSans-Bold.ttf','serif':'ServerHubSerif.ttf','mono':'ServerHubMono.ttf'}
    (A/'fonts').mkdir(parents=True,exist_ok=True)
    for family,font_file in families.items():
        shutil.copyfile(source/font_file,A/f'fonts/{family}.ttf')
        for size in (8,10,12,14,16):
            atlas=Image.new('RGBA',(384,24*len(chars)),(255,255,255,0))
            font=ImageFont.truetype(str(source/font_file),size)
            for index,code in enumerate(codes):
                if code in (32,160):widths.append(f'{family}.{size}.{code}={max(3,size//2)}');continue
                glyph=Image.new('L',(24,24),0)
                ImageDraw.Draw(glyph).text((1,18),chr(code),font=font,fill=255,anchor='ls')
                glyph=glyph.point(lambda v:255 if v>=100 else 0)
                box=glyph.getbbox();advance=(box[2]if box else 0)+1
                widths.append(f'{family}.{size}.{code}={advance}')
                rgba=Image.new('RGBA',(24,24),(255,255,255,0));rgba.putalpha(glyph)
                atlas.alpha_composite(rgba,((index%16)*24,(index//16)*24))
            atlas.save(gui/f'latin_{family}_{size}.png')
            for shadow in (False,True):
                save_json(A/f"font/label_{family}_{size}{'_shadow'if shadow else''}.json",{'providers':[
                    {'type':'space','advances':{' ':max(3,size//2),'\u00a0':max(3,size//2)}},
                    {'type':'bitmap','file':f'serverhub:gui/latin_{family}_{size}.png','height':24,'ascent':16 if shadow else 17,'chars':chars}
                ]})
    metrics='\n'.join(widths)+'\n'
    (ROOT/'paper/src/main/resources/font-widths.properties').write_text(metrics)
    (OUT/'serverhub-font-widths.properties').write_text(metrics)
    badge=Image.new('RGBA',(30,30),(0,0,0,0));bd=ImageDraw.Draw(badge)
    small=ImageFont.truetype(str(source/'ServerHubSans-Bold.ttf'),7)
    for index,text in enumerate(('Shift','+','F')):
        tile=wood.resize((30,9),Image.Resampling.NEAREST)
        badge.alpha_composite(tile,(0,index*10))
        box=bd.textbbox((0,0),text,font=small)
        x=(30-(box[2]-box[0]))//2-box[0];y=index*10+(9-(box[3]-box[1]))//2-box[1]
        bd.text((x+1,y+1),text,font=small,fill='#28190E')
        bd.text((x,y),text,font=small,fill='#FFF4D6')
    (A/'textures/item').mkdir(parents=True,exist_ok=True)
    badge.save(A/'textures/item/menu_icon.png')
    save_json(A/'font/hint.json',{'providers':[
        {'type':'bitmap','file':'serverhub:item/menu_icon.png','height':30,'ascent':-34-offset,'chars':[chr(0xE120+offset)]}
        for offset in range(-32,33)
    ]})
    save_json(A/'items/menu_icon.json',{'model':{'type':'minecraft:model','model':'serverhub:item/menu_icon'}})
    save_json(A/'models/item/menu_icon.json',{'parent':'minecraft:item/generated','textures':{'layer0':'serverhub:item/menu_icon'}})
    save_json(OUT/'pack.mcmeta',{'pack':{'description':'ServerHub 2.4 · Native Dialoge · Java 26.3','min_format':[97,1],'max_format':[97,1]}})
    shutil.copyfile(source/'FONT-LICENSE.txt',OUT/'FONT-LICENSE.txt')
    package()

if __name__=='__main__':main()
