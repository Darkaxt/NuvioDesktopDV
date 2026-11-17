"""Cross-language JNI argument compatibility for both Windows creation paths."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
KOTLIN = ROOT / 'composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/desktop/NativePlayerBridge.kt'
NATIVE = ROOT / 'composeApp/src/desktopMain/native/windows/player_bridge.cpp'


class WindowsPlayerBridgeContract(unittest.TestCase):
    def test_both_entry_points_match_the_kotlin_jni_signature(self):
        types = {'Long': 'jlong', 'String': 'jstring', 'Array<String>': 'jobjectArray',
                 'Boolean': 'jboolean', 'Int': 'jint', 'NativePlayerEventSink': 'jobject'}
        kotlin = KOTLIN.read_text(encoding='utf-8')
        native = NATIVE.read_text(encoding='utf-8')
        for method in ('create', 'createWithStartOptions'):
            with self.subTest(method=method):
                declaration = re.search(r'external fun ' + method + r'\((.*?)\): Long', kotlin, re.S)[1]
                expected = [types[part.split(':', 1)[1].strip()] for part in declaration.split(',') if part.strip()]
                function = re.search(r'NativePlayerBridge_' + method + r'\(\s*JNIEnv \*env,\s*jobject,\s*(.*?)\) \{', native, re.S)[1]
                actual = [part.strip().split()[0] for part in function.split(',')]
                self.assertEqual(expected, actual)
                self.assertIn('nvidiaRtxHdrEnabled', declaration)
                self.assertIn('jboolean nvidiaRtxHdrEnabled', function)


if __name__ == '__main__':
    unittest.main()
