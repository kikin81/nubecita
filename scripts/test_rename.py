"""Self-test for scripts/rename.py.

Runs against a fresh `android create empty-activity` output in a tmpdir and
asserts the rename pipeline produces the expected state. Use:

    python3 -m unittest scripts/test_rename.py

Set RUN_FULL_BUILD=1 to also run `./gradlew :app:assembleDebug` after the
rename; CI always sets this.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
RENAME_SCRIPT = REPO_ROOT / "scripts" / "rename.py"


def _scaffold(dest: Path) -> None:
    """Generate a fresh `android create` project at `dest`."""
    dest.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["android", "create", "empty-activity", "--name=My App", f"--output={dest}"],
        check=True,
    )
    shutil.copy2(RENAME_SCRIPT, dest / "rename.py")


def _run_rename(project: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["python3", str(project / "rename.py"), *args],
        cwd=project,
        capture_output=True,
        text=True,
    )


class RenameCliTest(unittest.TestCase):
    def test_rejects_invalid_package(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "Com.Bad.Case",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertNotEqual(result.returncode, 0, msg=result.stderr)
            self.assertIn("--package", result.stderr)

    def test_preflight_fails_when_no_template_markers(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            project.mkdir()
            shutil.copy2(RENAME_SCRIPT, project / "rename.py")
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertNotEqual(result.returncode, 0, msg=result.stderr)
            self.assertIn("com.example.myapp", result.stderr)


class RenameDirectoriesTest(unittest.TestCase):
    def test_moves_java_dirs_to_new_package(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)
            for sub in ("main", "test", "androidTest"):
                old = project / "app" / "src" / sub / "java" / "com" / "example" / "myapp"
                new = project / "app" / "src" / sub / "java" / "com" / "acme" / "widget"
                self.assertFalse(old.exists(), f"{old} should have been removed")
                self.assertTrue(new.is_dir(), f"{new} should exist")


class RenameContentTest(unittest.TestCase):
    def test_rewrites_placeholders_across_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            # No residual placeholders anywhere.
            for dirpath, _dirs, files in os.walk(project):
                if any(skip in dirpath for skip in (".gradle", "build", ".idea")):
                    continue
                for name in files:
                    if name == "rename.py":
                        continue
                    path = Path(dirpath) / name
                    try:
                        text = path.read_text(encoding="utf-8")
                    except UnicodeDecodeError:
                        continue
                    for needle in (
                        "com.example.myapp",
                        "com/example/myapp",
                        "My App",
                        "MyApplication",
                        "MyApp",
                        "myapp",
                    ):
                        self.assertNotIn(needle, text, f"{needle!r} still in {path}")

            # Spot-check key files have the new identifiers.
            build_gradle = (project / "app" / "build.gradle.kts").read_text()
            self.assertIn('namespace = "com.acme.widget"', build_gradle)
            self.assertIn('applicationId = "com.acme.widget"', build_gradle)

            settings_gradle = (project / "settings.gradle.kts").read_text()
            self.assertIn('rootProject.name = "Acme Widget"', settings_gradle)

            strings_xml = (project / "app" / "src" / "main" / "res" / "values" / "strings.xml").read_text()
            self.assertIn(">Acme Widget<", strings_xml)

            # Theme-name substitution must produce `AcmeWidgetTheme`, not
            # `AcmeWidgetlicationTheme` (regression guard for MyApplication order).
            theme_kt = (
                project / "app" / "src" / "main" / "java" / "com" / "acme" / "widget" / "theme" / "Theme.kt"
            ).read_text()
            self.assertIn("AcmeWidgetTheme", theme_kt)
            self.assertNotIn("AcmeWidgetlication", theme_kt)


class RenameFinalizationTest(unittest.TestCase):
    def test_self_deletes_and_prints_checklist(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)
            self.assertFalse((project / "rename.py").exists(), "rename.py should self-delete")
            self.assertIn("Next steps", result.stdout)
            self.assertIn("git remote add origin", result.stdout)

    def test_keep_script_flag(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
                "--keep-script",
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)
            self.assertTrue((project / "rename.py").exists(), "rename.py should remain with --keep-script")


class RenameDryRunTest(unittest.TestCase):
    def test_dry_run_makes_no_changes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            before = {
                str(p.relative_to(project)): p.read_bytes()
                for p in project.rglob("*")
                if p.is_file()
            }
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
                "--dry-run",
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)
            self.assertIn("DRY RUN", result.stdout)
            after = {
                str(p.relative_to(project)): p.read_bytes()
                for p in project.rglob("*")
                if p.is_file()
            }
            self.assertEqual(before, after, "dry run must not modify the filesystem")


class RenameCollisionTest(unittest.TestCase):
    def test_lower_app_name_substring_of_new_package(self) -> None:
        """New package contains the literal 'myapp' substring — verify no corruption."""
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "com.foo.myapp",
                "--app-name", "Foo My App Project",
                "--app-name-pascal", "FooMyApp",
                "--app-name-lower", "myapp",
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            build_gradle = (project / "app" / "build.gradle.kts").read_text()
            self.assertIn('namespace = "com.foo.myapp"', build_gradle)
            self.assertTrue(
                (project / "app" / "src" / "main" / "java" / "com" / "foo" / "myapp").is_dir(),
            )


@unittest.skipUnless(os.environ.get("RUN_FULL_BUILD") == "1", "set RUN_FULL_BUILD=1 to run")
class RenameFullBuildTest(unittest.TestCase):
    def test_assemble_debug_after_rename(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            rename_result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertEqual(rename_result.returncode, 0, msg=rename_result.stderr)

            gradle_result = subprocess.run(
                ["./gradlew", ":app:assembleDebug", "--no-daemon"],
                cwd=project,
                capture_output=True,
                text=True,
            )
            self.assertEqual(gradle_result.returncode, 0, msg=gradle_result.stderr)


if __name__ == "__main__":
    unittest.main()
