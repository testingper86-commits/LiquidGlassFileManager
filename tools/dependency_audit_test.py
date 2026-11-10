#!/usr/bin/env python3
"""Unit tests for dependency license metadata extraction."""
from pathlib import Path
import importlib.util
import tempfile
import unittest
import zipfile

MODULE_PATH = Path(__file__).with_name("dependency_audit.py")
SPEC = importlib.util.spec_from_file_location("toxifiles_dependency_audit", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


class DependencyAuditTests(unittest.TestCase):
    def test_pom_license_metadata_with_maven_namespace(self):
        with tempfile.TemporaryDirectory() as temporary:
            pom = Path(temporary) / "sample.pom"
            pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><licenses><license><name>Apache License 2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0.txt</url></license></licenses></project>', encoding="utf-8")
            self.assertEqual([("Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0.txt")], AUDIT.pom_licenses(pom))

    def test_bundled_license_and_notice_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            cache = Path(temporary)
            with zipfile.ZipFile(cache / "sample.jar", "w") as archive:
                archive.writestr("META-INF/LICENSE.txt", "license")
                archive.writestr("META-INF/NOTICE", "notice")
            self.assertEqual(["META-INF/LICENSE.txt", "META-INF/NOTICE"], AUDIT.bundled_notices(cache))

    def test_fixed_dependency_declarations_and_dynamic_version_detection(self):
        coordinate = "androidx.core:core:1.19.1"
        self.assertEqual([coordinate], AUDIT.declared_dependencies('implementation("' + coordinate + '")'))
        self.assertEqual([coordinate], AUDIT.declared_dependencies('add("implementation", "' + coordinate + '")'))
        self.assertEqual([coordinate], AUDIT.declared_dependencies('add("testImplementation", "' + coordinate + '")'))
        dynamic = AUDIT.declared_dependencies('add("implementation", "androidx.core:core:1.+")')
        self.assertEqual(["androidx.core:core:1.+"], dynamic)
        self.assertIsNone(AUDIT.DYNAMIC.search(coordinate))
        self.assertIsNotNone(AUDIT.DYNAMIC.search(dynamic[0]))

    def test_gradle_resolution_parser_uses_selected_versions(self):
        output = """
+--- org.apache.commons:commons-lang3:3.16.0 -> 3.18.0
+--- com.google.guava:guava:33.7.2-jre
+--- org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10
"""
        parsed = AUDIT.parse_resolved_versions(output)
        self.assertEqual({"3.18.0"}, parsed[("org.apache.commons", "commons-lang3")])
        self.assertEqual({"33.7.2-jre"}, parsed[("com.google.guava", "guava")])
        self.assertEqual({"2.2.10"}, parsed[("org.jetbrains.kotlin", "kotlin-gradle-plugin")])

    def test_buildscript_version_policy_detects_unpatched_selection(self):
        resolved = {key: {version} for key, version in AUDIT.BUILD_SCRIPT_EXPECTED.items()}
        self.assertEqual([], AUDIT.buildscript_version_issues(resolved))
        resolved[("org.apache.commons", "commons-lang3")] = {"3.16.0"}
        issues = AUDIT.buildscript_version_issues(resolved)
        self.assertTrue(any("commons-lang3" in issue and "3.16.0" in issue for issue in issues))

    def test_markdown_table_cells_escape_pipes_and_newlines(self):
        self.assertEqual("Apache " + chr(92) + "| MIT license", AUDIT.safe_cell("Apache | MIT" + chr(10) + "license"))


if __name__ == "__main__":
    unittest.main()
