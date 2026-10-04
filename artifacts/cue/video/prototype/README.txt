Cue film prototype — offline HTML, 60 seconds

Open ../cue-film.html directly, or serve the video directory on localhost.
The HTML embeds every font, illustration, style and script; no CDN is needed.

Source files:
  film.template.html — ten film scenes and review controls
  film.css           — brand styling and fixed 1920 × 1080 composition
  film.js            — deterministic motion and playback controls
  build.py           — scene timing, voiceover cues, packaging

Rebuild from any directory:
  python3 /path/to/prototype/build.py

Deliverables produced in the parent video directory:
  cue-film.html          — complete playable prototype
  voiceover.txt          — exact English narration
  voiceover-guide.txt    — recording direction and timed entries
  voiceover.srt          — timed captions
  voiceover-timing.json  — scene and narration timing

Preview controls: play/pause, seek, chapter selection, subtitles, optional
browser speech, local audio import, playback speed and fullscreen.
Space toggles playback; arrows seek 1 second, Shift+arrows 5 seconds.

Clean film view: cue-film.html?clean=1&t=22
Future renderer interface: await window.CueFilm.ready;
                          await window.CueFilm.frame(seconds);
All scene motion derives from the requested time. No MP4 is generated here.

Conversation and keyboard elements are prototype motion design. Their
containers have data-replace-slot attributes for later real captures.
All nine supplied ElevenLabs takes are embedded and synchronized by default.
The film lasts 60 seconds; original narration speed is preserved.
To rebuild the voice assembly, run build_audio.py before build.py.

The imported audio option expects an assembled track starting at 00:00;
individual replacement takes should be aligned through audio/alignment.json.

Artwork and fonts come from the existing Cue project assets. The original
font licences remain in the repository's assets/fonts directory.
