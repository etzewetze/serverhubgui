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
    button=Image.open(source/'buttons-source.png').convert('RGBA').resize((140,20),Image.Resampling.NEAREST)
    wood=Image.new('RGBA',(140,20),'#62431F');wood.alpha_composite(button)
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
    save_json(A/'font/gui.json',{'providers':[
        {'type':'bitmap','file':'serverhub:gui/scroll.png','height':30,'ascent':21,'chars':['\uE000']},
        {'type':'bitmap','file':'serverhub:gui/button.png','height':20,'ascent':13,'chars':['\uE001']}
    ]})
    advances={}
    for bit in range(11):
        advances[chr(0xF100+bit)]=-(1<<bit);advances[chr(0xF200+bit)]=1<<bit
    save_json(A/'font/space.json',{'providers':[{'type':'space','advances':advances}]})
    codes=list(range(32,128))+list(range(160,256))
    chars=[''.join(chr(c) for c in codes[i:i+16]) for i in range(0,len(codes),16)]
    widths=[]
    for size in (8,10,12):
        atlas=Image.new('RGBA',(256,16*len(chars)),(255,255,255,0))
        font=ImageFont.truetype(str(source/'ServerHubSans-Bold.ttf'),size)
        for index,code in enumerate(codes):
            if code in (32,160):widths.append(f'{size}.{code}={max(3,size//2)}');continue
            glyph=Image.new('L',(16,16),0)
            ImageDraw.Draw(glyph).text((1,12),chr(code),font=font,fill=255,anchor='ls')
            glyph=glyph.point(lambda v:255 if v>=100 else 0)
            box=glyph.getbbox();advance=(box[2]if box else 0)+1
            widths.append(f'{size}.{code}={advance}')
            rgba=Image.new('RGBA',(16,16),(255,255,255,0));rgba.putalpha(glyph)
            atlas.alpha_composite(rgba,((index%16)*16,(index//16)*16))
        atlas.save(gui/f'latin_{size}.png')
        save_json(A/f'font/label{size}.json',{'providers':[
            {'type':'space','advances':{' ':max(3,size//2),'\u00a0':max(3,size//2)}},
            {'type':'bitmap','file':f'serverhub:gui/latin_{size}.png','height':16,'ascent':11,'chars':chars}
        ]})
    (ROOT/'paper/src/main/resources/font-widths.properties').write_text('\n'.join(widths)+'\n')
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
    save_json(OUT/'pack.mcmeta',{'pack':{'description':'ServerHub 2.2 · Native Dialoge · Java 26.3','min_format':[97,1],'max_format':[97,1]}})
    shutil.copyfile(source/'FONT-LICENSE.txt',OUT/'FONT-LICENSE.txt')
    package()

if __name__=='__main__':main()
