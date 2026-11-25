from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
for xml_file in root.rglob("*.xml"):
    ET.parse(xml_file)

manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert "android.permission.INTERNET" not in manifest
assert "BIND_INPUT_METHOD" in manifest
assert "targetSdk 36" in (root / "app/build.gradle").read_text(encoding="utf-8")

java_files = list(root.rglob("*.java"))
for java_file in java_files:
    source = java_file.read_text(encoding="utf-8")
    stripped = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', "", source, flags=re.S)
    assert stripped.count("{") == stripped.count("}"), java_file
    assert stripped.count("(") == stripped.count(")"), java_file

words = (root / "app/src/main/assets/words.txt").read_text(encoding="utf-8").splitlines()
assert len(words) > 50000
assert words == sorted(set(words))
print(f"Project checks passed: {len(java_files)} Java files, XML valid, {len(words):,} local dictionary entries, no INTERNET permission.")
