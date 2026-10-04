#!/usr/bin/env python3
"""Build the offline HTML prototype; no video encoding or network access."""
from pathlib import Path
import base64
import json

HERE = Path(__file__).resolve().parent
VIDEO = HERE.parent
ROOT = HERE.parents[3]

SCENES = [
    ('hook', 0, 4.5, 'Wiadomość Marcela', 'Punkt wyjścia'),
    ('brand', 4.5, 11.5, 'Poznaj Cue', 'Kontekst i Twój styl'),
    ('suggest', 11.5, 18.5, 'W Messengerze', 'Bez kopiowania czatu'),
    ('reply', 18.5, 22, 'Pierwsza odpowiedź', 'Chwila na przeczytanie'),
    ('context', 22, 28.5, 'Tydzień wcześniej', 'Ujawnienie kontekstu'),
    ('sources', 28.5, 33, 'Sprawdź źródło', 'Oryginalna wiadomość'),
    ('direction', 33, 46, 'Marta: cel i ton', 'Flirty → Subtle'),
    ('control', 46, 51, 'Twoja decyzja', 'Wybierz i sprawdź'),
    ('team', 51, 56.5, 'Undefined', 'Dwóch 18-latków'),
    ('end', 56.5, 60, 'Cue', 'Ostatni kadr'),
]
CUES = [
    (0.35,4.14,'You care. But sometimes, you don’t know what to say.'),
    (4.75,11.36,'Meet Cue. An AI keyboard that uses your conversation’s context to help you reply in your own voice.'),
    (11.65,12.80,'Tap Suggest.'),
    (13.18,15.62,'No copying chats into another assistant.'),
    (15.90,18.05,'No explaining the background from scratch.'),
    (22.20,24.80,'A week ago, Marcel mentioned his exams.'),
    (25.15,28.24,'Cue connects that earlier message to what he’s saying now.'),
    (28.70,32.25,'Open the source to see the message behind what Cue remembers.'),
    (33.30,34.50,'Have something in mind?'),
    (35.70,36.55,'Set a goal.'),
    (38.20,39.30,'Choose your tone.'),
    (40.00,42.72,'And keep it subtle—or make it bolder.'),
    (46.20,47.40,'Choose a suggestion.'),
    (47.60,48.10,'Review it.'),
    (48.45,50.07,'You decide what to send.'),
    (51.45,55.16,'Built from scratch at HackYeah by two eighteen-year-olds.'),
    (57.00,59.93,'Cue. The context behind your words.'),
]

def uri(path, mime):
    return 'data:' + mime + ';base64,' + base64.b64encode(path.read_bytes()).decode('ascii')

def stamp(seconds):
    ms = round(seconds * 1000)
    return f'{ms//3600000:02}:{ms//60000%60:02}:{ms//1000%60:02},{ms%1000:03}'

scenes = [dict(id=i,start=s,end=e,label=l,note=n) for i,s,e,l,n in SCENES]
cues = [dict(start=s,end=e,text=t) for s,e,t in CUES]
assert all(a['end'] <= b['start'] for a,b in zip(cues,cues[1:]))
assert all(a['end'] == b['start'] for a,b in zip(scenes,scenes[1:]))
paragraphs = []
for scene in scenes:
    lines = [c['text'] for c in cues if scene['start'] <= c['start'] < scene['end']]
    if lines:
        paragraphs.append(' '.join(lines))
voiceover = '\n\n'.join(paragraphs) + '\n'
alignment = json.loads((VIDEO/'audio/alignment.json').read_text())
remaining = '\n\n'.join(paragraphs[i-1] for i in alignment['missingBlocks'])
config = dict(duration=60,scenes=scenes,cues=cues,voiceover=voiceover,remainingVoiceover=remaining,audio={'embedded':True,'connectedBlocks':alignment['connectedBlocks'],'missingBlocks':alignment['missingBlocks']})
css = (HERE/'film.css').read_text()
html = (HERE/'film.template.html').read_text().replace('__CSS__',css).replace('__JS__',(HERE/'film.js').read_text())
html = html.replace('__AUDIO__',uri(VIDEO/'audio/narration-preview.mp3','audio/mpeg'))
html = html.replace('__TIMELINE__','window.CUE_TIMELINE = ' + json.dumps(config,ensure_ascii=False) + ';')
assets = {
    '__MANROPE__':(ROOT/'assets/fonts/Manrope-800.ttf','font/ttf'),
    '__DM__':(ROOT/'assets/fonts/DMSans-400.ttf','font/ttf'),
    '__DM_MEDIUM__':(ROOT/'assets/fonts/DMSans-500.ttf','font/ttf'),
    '__WAVE__':(ROOT/'assets/mascots/cue-wave.png','image/png'),
    '__READ__':(ROOT/'assets/mascots/cue-read.png','image/png'),
    '__WRITE__':(ROOT/'assets/mascots/cue-write.png','image/png'),
}
# Keep each embedded image once by resolving image tokens from a shared inline data store.
image_data = {}
for token,(path,mime) in assets.items():
    if mime.startswith('font'):
        html = html.replace(token,uri(path,mime))
    else:
        key=token.strip('_').lower()
        html=html.replace(f'src="{token}"',f'data-art="{key}"')
        image_data[key]=uri(path,mime)
asset_script='<script>const cueArt='+json.dumps(image_data)+';document.querySelectorAll("[data-art]").forEach(el=>el.src=cueArt[el.dataset.art]);</script>'
html=html.replace('<script>window.CUE_TIMELINE',asset_script+'\n<script>window.CUE_TIMELINE')
assert '__CSS__' not in html and '__WAVE__' not in html
(VIDEO/'cue-film.html').write_text(html)
(VIDEO/'voiceover.txt').write_text(voiceover)
if remaining:
    (VIDEO/'voiceover-remaining.txt').write_text(remaining+'\n')
else:
    (VIDEO/'voiceover-remaining.txt').unlink(missing_ok=True)
(VIDEO/'voiceover-timing.json').write_text(json.dumps(config,ensure_ascii=False,indent=2)+'\n')
(VIDEO/'voiceover.srt').write_text('\n\n'.join(f'{i+1}\n{stamp(c["start"])} --> {stamp(c["end"])}\n{c["text"]}' for i,c in enumerate(cues))+'\n')
guide = '''CUE — VOICEOVER / 60 SECONDS\n\nJĘZYK: angielski.\nGŁOS: młody dorosły, ciepły, swobodny, z lekkim uśmiechem.\nDOSTARCZENIE: naturalna rozmowa; bez patosu i bez głosu reklamowego.\nWYMOWA: Cue = queue /kjuː/.\n\nWszystkie dziewięć nagrań ElevenLabs jest osadzonych w HTML i dopasowanych do scen.\nOryginalne tempo głosu zostało zachowane. Fragment 6 rozdzielono w pauzach między zdaniami.\nvoiceover.txt zawiera tekst narracji; poniżej znajdują się zsynchronizowane napisy.\n\n18.5–22 s: chwila na pierwszą odpowiedź. 42.5–46 s: chwila na propozycję dla Marty.\n\n'''
for cue in cues:
    guide += f'{cue["start"]:05.2f}–{cue["end"]:05.2f} s\n{cue["text"]}\n\n'
(VIDEO/'voiceover-guide.txt').write_text(guide)
production_path=VIDEO/'production.json'
plan=json.loads(production_path.read_text())
plan['durationSeconds']=60
plan['status']='Playable 60-second HTML prototype with all nine supplied ElevenLabs takes embedded and aligned. Real screen captures and MP4 export are pending.'
plan['htmlPrototype']={'file':'cue-film.html','resolution':'1920x1080','durationSeconds':60,'controls':'Play, pause, seek, chapters, subtitles, optional browser narration and local audio import.','cleanPreview':'cue-film.html?clean=1&t=22','deterministicSeek':'window.CueFilm.seek(seconds)','prototypeAssets':'Illustrated conversations, keyboard interactions and example replies. Replace capture slots with actual footage after review.'}
plan['audio']={'alignment':'audio/alignment.json','previewTrack':'audio/narration-preview.mp3','connectedBlocks':alignment['connectedBlocks'],'missingBlocks':alignment['missingBlocks'],'note':alignment['alignment']}
plan['creativeDirection']='Friendly minimalism: warm off-white, lavender, large Manrope typography, restrained transitions, and the original Cue mascot in reading, writing and waving poses. Marcel’s week-old context is revealed after the reply. Marta’s everyday weekend exchange demonstrates the saved coffee goal, Flirty tone and Subtle intensity.'
for shot in plan['shots']:
    scene=next(s for s in scenes if s['id']==shot['type'])
    shot['start']=scene['start']
    shot['end']=scene['end']
    shot['voice']=' '.join(c['text'] for c in cues if scene['start']<=c['start']<scene['end'])
control=next(s for s in plan['shots'] if s['type']=='control')
control.update(headline='Choose. Review. Send.',visual='Insert the selected suggestion unchanged, review it, and hold on the send control. Editing remains optional.',purpose='Show a ready-to-use suggestion and the user’s final decision.')
plan['captureList']=[line.replace('insertion into the compose field and a small edit','insertion into the compose field and review of the unchanged suggestion') for line in plan['captureList']]
production_path.write_text(json.dumps(plan,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'htmlBytes':len(html.encode()),'duration':60,'voiceWords':len(voiceover.split()),'timedVoiceCues':len(cues)}))
