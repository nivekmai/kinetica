#!/usr/bin/env python3
"""Unpack an explicitly shared diagnostic report and replay recorded edits.

Usage: python3 tools/voice-smoke/replay_report.py REPORT.zip OUTPUT_DIR [--replay]
--replay needs compiled debug Kotlin classes and the local JDK (JAVA_HOME or javac/java on PATH).
The ZIP is read by fixed filenames; no archive paths are extracted. No network requests.
Generated files contain private text/audio. Do not commit real reports without explicit review.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


def decode_report(path):
    with zipfile.ZipFile(path) as z:
        for name in ('events.jsonl', 'manifest.json', 'audio.wav'):
            if z.getinfo(name).file_size > 70 * 1024 * 1024:
                raise ValueError('Report member exceeds capture budget')
        manifest = json.loads(z.read('manifest.json'))
        if manifest.get('schema') != 1:
            raise ValueError('Unsupported report schema')
        events = [json.loads(line) for line in z.read('events.jsonl').decode().splitlines() if line.strip()]
        audio = z.read('audio.wav')
    return manifest, events, audio


def operations(events):
    results = {}
    for event in events:
        kind = event['type']
        if kind == 'session_start':
            yield ('BEFORE', event.get('metadata', {}).get('preceding_context') or '')
        elif kind == 'formatting_config':
            settings = ('FORMATTING', str(event['fast_enabled']).lower(), event['language'])
            if 'small_punctuation' in event:
                yield settings + (event.get('protected_words', ''), str(event['small_punctuation']).lower())
            else:
                yield settings + (event['protected_words'],) if 'protected_words' in event else settings
        elif kind == 'punctuation_applied' and event.get('output') is not None:
            yield ('PUNCTUATION', event['source'], event['before'], event['input'], str(event['context_words']),
                   str(event['generation']), event['output'], str(event['accepted']).lower())
        elif kind == 'asr_partial':
            yield ('PARTIAL', event['text'])
        elif kind in ('asr_segment', 'utterance_finished'):
            yield ('FINAL', event['text'])
        elif kind == 'ai_started':
            yield ('SNAPSHOT', str(event['job']), event['input'], event['original'])
        elif kind == 'ai_result':
            results[event['job']] = event
        elif kind == 'ai_applied':
            result = results.get(event['job'])
            if result is None:
                raise ValueError('Missing AI result: incomplete trace')
            if result['code'] == 'SUCCESS':
                yield ('APPLY', str(event['job']), result['payload'], str(event['accepted']).lower())
        elif kind == 'undo_cleanup':
            yield ('UNDO',)
        elif kind == 'draft_render':
            if event['source'] == 'asr_error_final':
                yield ('FINAL', '')
            if 'punctuation_complete' in event:
                yield ('PUNCTUATION_COMPLETE', str(event['punctuation_complete']).lower())
            yield ('EXPECT', event['text'])


def write_report(report, destination):
    manifest, events, audio = decode_report(report)
    destination.mkdir(parents=True, exist_ok=True)
    (destination / 'audio.wav').write_bytes(audio)
    (destination / 'events.jsonl').write_text(''.join(json.dumps(e, ensure_ascii=False) + '\n' for e in events))
    (destination / 'replay.tsv').write_text(''.join(op[0] + ''.join('\t' + base64.b64encode(v.encode()).decode() for v in op[1:]) + '\n' for op in operations(events)))
    mismatches = [e for e in events if e['type'] == 'editor_result' and e.get('committed') and
                  e.get('observed_suffix') is not None and e['observed_suffix'] != e['expected'][-e.get('readback_limit', 6000):]]
    started = {e['job']:e for e in events if e['type'] == 'ai_started'}
    completed = {e['job'] for e in events if e['type'] == 'ai_result'}
    closed = any(e['type'] == 'session_closed' for e in events)
    unfinished = [dict(job=job, revision=e.get('revision'),
                       reason='voice_session_closed' if closed else 'capture_ended_before_result')
                  for job,e in started.items() if job not in completed]
    deliveries = [e for e in events if e['type'] == 'audio_delivery']
    summary = dict(manifest, event_count=len(events), markers=[e['t_ns'] for e in events if e['type'] == 'user_marker'],
                   audio_gaps=[e for e in events if e['type'] in ('audio_unavailable', 'audio_error')],
                   failures=[e for e in events if e['type'] == 'ai_result' and e['code'] != 'SUCCESS'],
                   editor_mismatches=mismatches, unfinished_jobs_at_capture_end=unfinished,
                   captured_bytes_with_delivery_events=sum(e.get('captured_bytes',0) for e in deliveries),
                   undelivered_bytes_in_delivery_events=sum(e.get('captured_bytes',0)-e.get('bytes',0) for e in deliveries))
    (destination / 'summary.json').write_text(json.dumps(summary, indent=2, ensure_ascii=False))
    # Exact payloads stay visible, including the model request needed to repeat an inference.
    timeline = ['Voice diagnostic timeline (times relative to session start)\n']
    for e in events:
        if e['type'] not in ('audio_chunk', 'audio_delivery'):
            timeline.append(f"{e.get('t_ns', 0)/1e9:9.3f}s {e['type']} " + json.dumps({k:v for k,v in e.items() if k not in ('t_ns','seq','type')}, ensure_ascii=False))
    (destination / 'timeline.txt').write_text('\n'.join(timeline))
    return destination / 'replay.tsv'


def replay(fixture, destination):
    repo = Path(__file__).resolve().parents[2]
    gradle = Path.home() / '.gradle/caches'
    stdlib = next(gradle.rglob('kotlin-stdlib-1.9.24.jar'))
    cp = os.pathsep.join((str(repo / 'app/build/tmp/kotlin-classes/debug'), str(stdlib)))
    java_home = os.environ.get('JAVA_HOME')
    def tool(name):
        return str(Path(java_home) / 'bin' / name) if java_home else shutil.which(name)
    subprocess.run([tool('javac'), '-cp', cp, '-d', str(destination), str(repo / 'tools/voice-smoke/ReplayTrace.java')], check=True)
    subprocess.run([tool('java'), '-cp', str(destination) + os.pathsep + cp, 'com.kinetica.keyboard.voice.ReplayTrace', str(fixture)], check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('destination', type=Path)
    parser.add_argument('--replay', action='store_true')
    args = parser.parse_args()
    fixture = write_report(args.report, args.destination)
    if args.replay:
        replay(fixture, args.destination)
    print('Diagnostic timeline, WAV, summary and replay fixture written to', args.destination)
