"""Deterministic launcher previews with fictional content, EN/IT and transparent margins."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
root=Path(__file__).resolve().parents[1]/'app/src/main/res'
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
def generate(kind, rows, italian):
    w,h=640,rows*150
    im=Image.new('RGBA',(w,h));d=ImageDraw.Draw(im)
    def box(b,fill='#303034',r=20):d.rounded_rectangle(b,r,fill=fill)
    def text(x,y,t,size=22,fill='#eeeeef'):d.text((x,y),t,font=ImageFont.truetype(font,size),fill=fill)
    box((8,8,w-8,h-8),'#171719',30)
    colors=['#668da9','#d17d9f','#7fa889','#d2ac58']
    if kind=='habit':
        titles=['Bere acqua','Leggere','Passeggiata','Meditazione'] if italian else ['Drink water','Read','Walk','Meditation']
        for i,t in enumerate(titles):
            x=22+(i%2)*302;y=24+(i//2)*130
            box((x,y,x+288,y+116))
            d.ellipse((x+16,y+32,x+62,y+78),fill=colors[i]);text(x+29,y+38,['◉','+','✓','○'][i],22,'#ffffff')
            text(x+76,y+27,t,19);text(x+76,y+61,['2 / 4 · 50%','0 / 1 · 0%','1 / 1 · 100%','0 / 1 · 0%'][i],16,'#bfc0c4')
    elif kind=='matrix':
        colors=['#EF5964','#F1BE37','#607DE2','#28C6A3']
        text(28,22,'Matrice di Eisenhower' if italian else 'Eisenhower matrix',26)
        titles=['Fare','Pianificare','Delegare','Eliminare'] if italian else ['Do','Plan','Delegate','Eliminate']
        for i,t in enumerate(titles):
            x=24+i%2*300;y=72+i//2*178
            box((x,y,x+286,y+164),'#27272a')
            text(x+16,y+16,t,22,colors[i]);text(x+16,y+57,'□  '+(['Progetto','Allenamento','Chiamata','Notifiche'] if italian else ['Project','Workout','Phone call','Notifications'])[i],17)
            text(x+16,y+95,'□  '+('Attività' if italian else 'Task'),17,'#aaaab1')
    elif kind=='task':
        text(28,24,'Attività' if italian else 'Tasks',28);text(556,24,'+',28)
        titles=['Preparare il progetto','Fare la spesa','Leggere un capitolo','Prenotare la visita','Passeggiata'] if italian else ['Prepare project','Buy groceries','Read a chapter','Book appointment','Go for a walk']
        for i,t in enumerate(titles):
            y=84+i*65;d.rounded_rectangle((30,y+3,50,y+23),4,outline=colors[i%4],width=2);text(68,y,t,21)
            d.line((68,y+48,602,y+48),fill='#3a3a40',width=1)
    elif kind=='calendar_week':
        text(24,18,'‹    Settimana    ›' if italian else '‹    This week    ›',25)
        for i,t in enumerate(['L','M','M','G','V','S','D'] if italian else ['M','T','W','T','F','S','S']):
            x=32+i*85;text(x,63,t,15,'#a5a5b0');
            if i==2:box((x-9,86,x+44,126),'#a4436d',14)
            text(x,90,str(12+i),23)
        text(28,151,'09:00   '+('Riunione' if italian else 'Meeting'),21);text(28,202,'14:30   '+('Progetto' if italian else 'Project'),21)
        d.line((28,186,606,186),fill='#3a3a40',width=1)
    else:
        text(28,25,'‹   Ottobre 2026   ›' if italian else '‹   October 2026   ›',27)
        for i,t in enumerate(['L','M','M','G','V','S','D'] if italian else ['M','T','W','T','F','S','S']):text(35+i*85,89,t,16,'#aaaab0')
        for j in range(6):
            y=132+j*123
            for i in range(7):
                n=j*7+i-2
                if 1<=n<=31:text(32+i*85,y,str(n),20)
            if j<5:
                box((30,y+40,265,y+66),colors[j%4],4);text(38,y+41,('Progetto' if italian else 'Project'),15,'#16161b')
                d.line((375,y+83,375,y+104),fill=colors[(j+1)%4],width=4);text(385,y+82,('Visita' if italian else 'Visit'),14)
    folder=root/('drawable-it-nodpi' if italian else 'drawable-nodpi');folder.mkdir(exist_ok=True)
    im.save(folder/f'widget_preview_{kind}.png')
for lang in [False,True]:
    for kind,rows in [('habit',2),('task',3),('calendar_week',2),('month',6),('matrix',3)]:generate(kind,rows,lang)
