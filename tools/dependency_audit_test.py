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
        self.assertEqual(["androidx.core:core:1.19.1"], AUDIT.DECLARATION.findall('implementation("androidx.core:core:1.19.1")'))
        self.assertIsNone(AUDIT.DYNAMIC.search("1.19.1"))
        self.assertIsNotNone(AUDIT.DYNAMIC.search("1.+"))

    def test_markdown_table_cells_escape_pipes_and_newlines(self):
        self.assertEqual("Apache " + chr(92) + "| MIT license", AUDIT.safe_cell("Apache | MIT" + chr(10) + "license"))


if __name__ == "__main__":
    unittest.main()
