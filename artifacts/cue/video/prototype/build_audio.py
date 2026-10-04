#!/usr/bin/env python3
"""Assemble the supplied voice takes at their film cues. Never renders video."""
from pathlib import Path
import json
import subprocess

VIDEO=Path(__file__).resolve().parent.parent
AUDIO=VIDEO/'audio'
plan=json.loads((AUDIO/'alignment.json').read_text())
sources=list(dict.fromkeys(c['source'] for c in plan['clips']))
args=['ffmpeg','-hide_banner','-y']
for source in sources: args+=['-i',str(AUDIO/source)]
filters=[]
labels=[]
for index,source in enumerate(sources):
    clips=[(n,c) for n,c in enumerate(plan['clips']) if c['source']==source]
    if len(clips)>1:
        branches=[f'input{index}_{n}' for n,_ in clips]
        filters.append(f'[{index}:a]asplit={len(clips)}'+''.join(f'[{b}]' for b in branches))
    else: branches=[f'{index}:a']
    for branch,(number,clip) in zip(branches,clips):
        duration=clip['out']-clip['in']
        label=f'clip{number}'
        filters.append(f'[{branch}]atrim=start={clip["in"]}:end={clip["out"]},asetpts=PTS-STARTPTS,afade=t=in:d=0.004,afade=t=out:st={duration-0.004}:d=0.004,adelay={round(clip["at"]*1000)}:all=1[{label}]')
        labels.append(label)
filters.append(''.join(f'[{s}]' for s in labels)+f'amix=inputs={len(labels)}:duration=longest:normalize=0,apad=whole_dur={plan["durationSeconds"]},atrim=end={plan["durationSeconds"]}[voice]')
args+=['-filter_complex',';'.join(filters),'-map','[voice]','-ar','44100','-ac','1','-c:a','libmp3lame','-q:a','2',str(AUDIO/'narration-preview.mp3')]
result=subprocess.run(args,capture_output=True,text=True)
if result.returncode: raise RuntimeError(result.stderr)
print(json.dumps({'connectedBlocks':plan['connectedBlocks'],'missingBlocks':plan['missingBlocks'],'output':str(AUDIO/'narration-preview.mp3')}))
