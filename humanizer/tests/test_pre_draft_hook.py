import json
from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
HOOK_CONFIG = ROOT / "hooks" / "hooks.json"
HOOK_SCRIPT = ROOT / "hooks" / "pre_draft.py"
GUIDANCE = ROOT / "references" / "pre-draft.md"


class PreDraftHookTests(unittest.TestCase):
    def test_user_prompt_hook_returns_guidance_as_developer_context(self):
        result = subprocess.run(
            [sys.executable, str(HOOK_SCRIPT)],
            input=json.dumps({"prompt": "请解释为什么暂时不能发布。"}),
            capture_output=True,
            check=False,
            text=True,
        )

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            json.loads(result.stdout),
            {
                "hookSpecificOutput": {
                    "hookEventName": "UserPromptSubmit",
                    "additionalContext": GUIDANCE.read_text(encoding="utf-8").strip(),
                }
            },
        )

    def test_plugin_uses_only_the_pre_draft_hook(self):
        config = json.loads(HOOK_CONFIG.read_text(encoding="utf-8"))

        self.assertEqual(set(config["hooks"]), {"UserPromptSubmit"})
        command = config["hooks"]["UserPromptSubmit"][0]["hooks"][0]["command"]
        self.assertIn("${PLUGIN_ROOT}/hooks/pre_draft.py", command)


if __name__ == "__main__":
    unittest.main()
