import io
import json
from pathlib import Path
import tempfile
import unittest
import wave
import zipfile
import replay_report

class ReportTest(unittest.TestCase):
    def test_small_punctuation_replays_revised_pause_stale_result_and_undo(self):
        events = [
            {'type':'session_start', 'metadata':{'preceding_context':''}},
            {'type':'formatting_config', 'fast_enabled':True, 'language':'en', 'protected_words':'', 'small_punctuation':True},
            {'type':'asr_partial','text':'When I pause for'},
            {'type':'punctuation_applied','source':'When I pause for','before':'','input':'when i pause for',
             'context_words':0,'generation':0,'output':'When I pause for?','accepted':True},
            {'type':'draft_render','source':'punctuation_apply','punctuation_complete':False,'text':'When I pause for'},
            {'type':'asr_segment','text':'When I pause for'},
            {'type':'asr_partial','text':'A second'},
            {'type':'punctuation_applied','source':'When I pause for','before':'','input':'when i pause for',
             'context_words':0,'generation':0,'output':'When I pause for?','accepted':False},
            {'type':'draft_render','source':'asr_partial','punctuation_complete':False,'text':'When I pause for a second'},
            {'type':'punctuation_applied','source':'When I pause for A second','before':'','input':'when i pause for a second',
             'context_words':0,'generation':0,'output':'When I pause for a second.','accepted':True},
            {'type':'utterance_finished','text':'A second'},
            {'type':'draft_render','source':'asr_final','punctuation_complete':True,'text':'When I pause for a second.'},
            {'type':'undo_cleanup'},
            {'type':'draft_render','source':'undo','punctuation_complete':True,'text':'When I pause for A second'},
        ]
        import base64
        with tempfile.TemporaryDirectory() as directory:
            output=Path(directory); fixture=output/'punctuation.tsv'
            fixture.write_text(''.join(op[0]+''.join('\t'+base64.b64encode(v.encode()).decode() for v in op[1:])+'\n'
                                       for op in replay_report.operations(events)))
            replay_report.replay(fixture,output)

    def test_custom_protected_capitals_survive_replay_and_new_utterance_boundaries(self):
        events = [
            {'type':'session_start', 'metadata':{'preceding_context':''}},
            {'type':'formatting_config', 'fast_enabled':True, 'language':'en-US', 'case_policy':'denylist', 'protected_words':'zelda'},
            {'type':'asr_segment','text':'I met Zelda and it is still'},
            {'type':'asr_partial','text':'Taking a Long time'},
            {'type':'draft_render','source':'asr_partial','text':'I met Zelda and it is still taking a long time'},
            {'type':'asr_segment','text':'Taking a Long time'},
            {'type':'ai_started','job':1,'input':'I met Zelda and it is still taking a long time', 'original':'I met Zelda and it is still Taking a Long time'},
            {'type':'ai_result','job':1,'code':'SUCCESS','payload':'I met Zelda and it is still Taking a Long time.'},
            {'type':'ai_applied','job':1,'accepted':True},
            {'type':'draft_render','source':'ai_apply','text':'I met Zelda and it is still taking a long time.'},
            {'type':'undo_cleanup'},
            {'type':'draft_render','source':'undo','text':'I met Zelda and it is still Taking a Long time'},
        ]
        import base64
        with tempfile.TemporaryDirectory() as directory:
            output=Path(directory); fixture=output/'denylist.tsv'
            fixture.write_text(''.join(op[0]+''.join('\t'+base64.b64encode(v.encode()).decode() for v in op[1:])+'\n'
                                       for op in replay_report.operations(events)))
            replay_report.replay(fixture,output)

    def test_fast_formatting_replays_with_late_cleanup_and_undo(self):
        events = [
            {'type':'session_start', 'metadata':{'preceding_context':''}},
            {'type':'formatting_config', 'fast_enabled':True, 'language':'en-US'},
            {'type':'asr_segment','text':'I was relieved'},
            {'type':'ai_started','job':1,'input':'I was relieved','original':'I was relieved'},
            {'type':'asr_segment','text':'It arrived'},
            {'type':'asr_partial','text':'And I could leave'},
            {'type':'draft_render','source':'asr_partial','text':'I was relieved it arrived and I could leave'},
            {'type':'ai_result','job':1,'code':'SUCCESS','payload':'I was relieved.'},
            {'type':'ai_applied','job':1,'accepted':True},
            {'type':'draft_render','source':'ai_apply','text':'I was relieved. It arrived and I could leave'},
            {'type':'undo_cleanup'},
            {'type':'draft_render','source':'undo','text':'I was relieved It arrived And I could leave'},
        ]
        import base64
        with tempfile.TemporaryDirectory() as directory:
            output=Path(directory); fixture=output/'fast.tsv'
            fixture.write_text(''.join(op[0]+''.join('\t'+base64.b64encode(v.encode()).decode() for v in op[1:])+'\n'
                                       for op in replay_report.operations(events)))
            replay_report.replay(fixture,output)

    def test_audio_events_generate_replay_without_executing_archive_paths(self):
        events = [
            {'type':'session_start', 'metadata':{'preceding_context':'earlier text'}},
            {'type':'asr_partial','text':'I cut'},
            {'type':'draft_render','source':'asr_partial','text':'I cut'},
            {'type':'asr_segment','text':'I cut home'},
            {'type':'draft_render','source':'asr_segment','text':'I cut home'},
            {'type':'ai_started','job':1,'input':'I cut home','original':'I cut home'},
            {'type':'asr_partial','text':'And'},
            {'type':'draft_render','source':'asr_partial','text':'I cut home And'},
            {'type':'asr_partial','text':'and then'},
            {'type':'draft_render','source':'asr_partial','text':'I cut home and then'},
            {'type':'asr_segment','text':'and then I made dinner'},
            {'type':'draft_render','source':'asr_segment','text':'I cut home and then I made dinner'},
            {'type':'ai_result','job':1,'code':'SUCCESS','payload':'I got home.'},
            {'type':'ai_applied','job':1,'accepted':True},
            {'type':'draft_render','source':'ai_apply','text':'I got home. and then I made dinner'},
            {'type':'editor_result','committed':True,'expected':'I got home. and then I made dinner','observed_suffix':'I got home. and then I made dinner'},
            {'type':'user_marker'},
            {'type':'audio_unavailable','reason':'compatibility'},
            {'type':'ai_started','job':2,'revision':2,'input':'I got home. and then I made dinner','original':'I cut home and then I made dinner'},
            {'type':'session_closed'},
        ]
        for index,e in enumerate(events): e['t_ns'] = index * 100000000
        wav = io.BytesIO()
        with wave.open(wav,'wb') as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000); w.writeframes(b'\x00\x00' * 100)
        with tempfile.TemporaryDirectory() as t:
            report = Path(t)/'report.zip'; out=Path(t)/'out'
            with zipfile.ZipFile(report,'w') as z:
                z.writestr('manifest.json',json.dumps({'schema':1}))
                z.writestr('events.jsonl','\n'.join(json.dumps(e) for e in events))
                z.writestr('audio.wav',wav.getvalue())
                z.writestr('../should-not-extract','ignored')
            fixture=replay_report.write_report(report,out)
            self.assertFalse((Path(t)/'should-not-extract').exists())
            self.assertIn('APPLY\t',fixture.read_text())
            summary=json.loads((out/'summary.json').read_text())
            self.assertEqual([],summary['editor_mismatches'])
            self.assertEqual(1,len(summary['audio_gaps']))
            self.assertEqual([{'job':2,'revision':2,'reason':'voice_session_closed'}],summary['unfinished_jobs_at_capture_end'])
            replay_report.replay(fixture,out)

if __name__=='__main__': unittest.main()
