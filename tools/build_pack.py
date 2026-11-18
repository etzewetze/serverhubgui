#!/usr/bin/env python3
"""Deterministic native-dialog artwork, readable font and three-line HUD badge.

Requires Pillow. Minecraft's global GUI sprites and shaders are untouched.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageChops
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
    original=Image.open(source/'title-banner.png').convert('RGBA')
    bounds=original.getchannel('A').point(lambda a:255 if a>=32 else 0).getbbox()
    original=original.crop(bounds)
    scroll=original.resize((round(original.width*30/original.height),30),Image.Resampling.LANCZOS)
    scroll.save(gui/'scroll.png')
    info=Image.new('RGBA',(140,20),'#D8BD89');di=ImageDraw.Draw(info)
    di.rectangle((0,0,139,19),outline='#8C663A');di.line((1,1,138,1),fill='#EDD7A9')
    info.save(gui/'info.png')
    dot=Image.new('RGBA',(6,6),(255,255,255,0));ImageDraw.Draw(dot).ellipse((0,0,5,5),fill='white');dot.save(gui/'status.png')
    save_json(A/'font/status.json',{'providers':[{'type':'bitmap','file':'serverhub:gui/status.png','height':6,'ascent':6,'chars':['\uE300']}]})
    # End decorations sit three pixels below the middle so the inner paper edges meet.
    # Glyph geometry keeps the center strip and title text fixed.
    parts=[('scroll_left',scroll.crop((0,0,40,30)),0xE400,15),('scroll_mid',scroll.crop((scroll.width//2,0,scroll.width//2+1,30)),0xE401,18),('scroll_right',scroll.crop((scroll.width-40,0,scroll.width,30)),0xE402,15),
           ('button_left',wood.crop((0,0,4,20)),0xE410,13),('button_mid',wood.crop((70,0,71,20)),0xE411,13),('button_right',wood.crop((136,0,140,20)),0xE412,13),
           ('info_left',info.crop((0,0,4,20)),0xE420,13),('info_mid',info.crop((70,0,71,20)),0xE421,13),('info_right',info.crop((136,0,140,20)),0xE422,13)]
    providers=[]
    for name,image,code,ascent in parts:
        image.save(gui/(name+'.png'));providers.append({'type':'bitmap','file':'serverhub:gui/'+name+'.png','height':image.height,'ascent':ascent,'chars':[chr(code)]})
    providers.insert(0,{'type':'space','advances':{'\uF0FF':-1}})
    save_json(A/'font/gui.json',{'providers':providers})
    # Column glyphs preserve the entire image when a button changes width.
    src=A/'textures/backgrounds/source';gen=A/'textures/backgrounds/generated';src.mkdir(parents=True,exist_ok=True);gen.mkdir(parents=True,exist_ok=True)
    blue=Image.new('RGBA',(140,20),'#315E89');bd=ImageDraw.Draw(blue)
    bd.rectangle((0,0,139,19),outline='#142D49');bd.line((1,1,138,1),fill='#82AECF')
    examples={'blue':blue,'paper':info,'wood':wood}
    skin_providers=[{'type':'space','advances':{'\uF8FF':-1}}];catalog={'roll.left':'40','roll.right':'40','icons':'true'}
    for index,(name,image)in enumerate(sorted(examples.items())):
        image.save(src/(name+'.png'))
        for rounded in (False,True):
            item=image.copy();file=name+('_round'if rounded else'')+'.png'
            if rounded:
                mask=Image.new('L',item.size);ImageDraw.Draw(mask).rounded_rectangle((0,0,item.width-1,19),radius=4,fill=255);item.putalpha(ImageChops.multiply(item.getchannel('A'),mask))
            item.save(gen/file);code=0xE000+index*384+(192 if rounded else 0)
            key=name+('.round'if rounded else'');catalog['skin.'+key]=str(code)+','+str(item.width)
            skin_providers.append({'type':'bitmap','file':'serverhub:backgrounds/generated/'+file,'height':20,'ascent':12,'chars':[''.join(chr(code+i)for i in range(item.width))]})
    save_json(A/'font/backgrounds.json',{'providers':skin_providers})
    news=Image.new('RGBA',(10,10));nd=ImageDraw.Draw(news);nd.rounded_rectangle((0,0,9,9),radius=2,fill='white')
    for y in (2,4,6):nd.line((2,y,7,y),fill=(255,255,255,0))
    news.save(gui/'news.png');save_json(A/'font/icons.json',{'providers':[{'type':'bitmap','file':'serverhub:gui/news.png','height':10,'ascent':8,'chars':['\uE540']}]})
    ui='\n'.join(k+'='+v for k,v in sorted(catalog.items()))+'\n';(OUT/'serverhub-ui.properties').write_text(ui);(ROOT/'paper/src/main/resources/ui.properties').write_text(ui)
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
    save_json(OUT/'pack.mcmeta',{'pack':{'description':'ServerHub 2.5.3 · Native Dialoge · Java 26.3','min_format':[97,1],'max_format':[97,1]}})
    shutil.copyfile(source/'FONT-LICENSE.txt',OUT/'FONT-LICENSE.txt')
    package()

if __name__=='__main__':main()
