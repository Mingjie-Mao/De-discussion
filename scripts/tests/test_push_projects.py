import importlib.util
import io
from pathlib import Path
import unittest
from unittest.mock import patch

source = Path(__file__).resolve().parents[1] / 'push-projects.py'
spec = importlib.util.spec_from_file_location('project_publication', source)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PublicationCheckTest(unittest.TestCase):
    def test_default_inspects_dirty_multiple_commits_without_token_or_network(self):
        def git_output(command, **options):
            if 'status' in command: return ' M local.md\n'
            if 'rev-list' in command: return '0\t2\n'
            self.fail('Unexpected command')
        with patch.object(module.sys, 'argv', [str(source)]), \
             patch.object(module.shutil, 'which', return_value='/fixture/git'), \
             patch.object(module.subprocess, 'check_output', side_effect=git_output), \
             patch.object(module.subprocess, 'run', side_effect=AssertionError('No fetch/push expected')), \
             patch.object(module.getpass, 'getpass', side_effect=AssertionError('No token expected')), \
             patch('sys.stdout', io.StringIO()) as output:
            module.main()
        self.assertIn('"commitsAhead": 2', output.getvalue())
        self.assertIn('Check only.', output.getvalue())

    def test_publication_cannot_upload_uncommitted_changes(self):
        with patch.object(module.sys, 'argv', [str(source), '--push']), \
             patch.object(module.sys.stdin, 'isatty', return_value=True), \
             patch.object(module.shutil, 'which', return_value='/fixture/git'), \
             patch.object(module.subprocess, 'check_output', return_value=' M local.md\n'), \
             patch.object(module.subprocess, 'run', side_effect=AssertionError('No fetch/push expected')), \
             patch.object(module.getpass, 'getpass', side_effect=AssertionError('No token expected')):
            with self.assertRaisesRegex(SystemExit, 'Uncommitted changes'):
                module.main()

    def test_backend_target_is_current_desktop_checkout(self):
        self.assertEqual(module.PROJECTS[1][0], module.ROOT.parent / 'De-moderation')

    def test_credential_prompt_cannot_send_token_to_lookalike_domain(self):
        with patch.object(module.sys, 'argv', [str(source), '--askpass', "Password for 'https://github.com.evil.example':"]), \
             patch.object(module.socket, 'socket', side_effect=AssertionError('Must reject before connecting')):
            self.assertEqual(1, module.askpass())


if __name__ == '__main__':
    unittest.main()
