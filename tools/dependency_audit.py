#!/usr/bin/env python3
"""Resolve and inventory ToxiFiles runtime dependencies and their published license metadata."""
from __future__ import annotations
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
BUILD_FILE = ROOT / "app" / "build.gradle.kts"
REPORT = ROOT / "build" / "reports" / "toxifiles-dependency-audit.md"
GRADLE_CACHE = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1"
COORDINATE = re.compile(r"(?P<group>[A-Za-z0-9_.-]+):(?P<artifact>[A-Za-z0-9_.-]+):(?P<version>[A-Za-z0-9_.+-]+)")
DECLARATION = re.compile(
    r'(?:implementation|api|runtimeOnly|compileOnly|testImplementation|androidTestImplementation)\("([^"]+)"\)'
    r'|add\("(?:implementation|api|runtimeOnly|compileOnly|testImplementation|androidTestImplementation)",\s*"([^"]+)"\)'
)
DYNAMIC = re.compile(r"(?:\+|latest\.(?:release|integration)|SNAPSHOT)", re.IGNORECASE)
NOTICE_PREFIXES = ("license", "licence", "notice", "copying", "copyright")


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def pom_licenses(pom: Path) -> list[tuple[str, str]]:
    try:
        root = ET.parse(pom).getroot()
    except (OSError, ET.ParseError):
        return []
    found: list[tuple[str, str]] = []
    for node in root.iter():
        if local_name(node.tag) != "license":
            continue
        values = {local_name(child.tag): (child.text or "").strip() for child in list(node)}
        name = values.get("name", "").strip()
        url = values.get("url", "").strip()
        if name or url:
            pair = (name or "Unnamed license", url)
            if pair not in found:
                found.append(pair)
    return found


def bundled_notices(cache_dir: Path) -> list[str]:
    found: set[str] = set()
    if not cache_dir.is_dir():
        return []
    for archive in cache_dir.rglob("*"):
        if not archive.is_file() or archive.suffix.lower() not in {".jar", ".aar"}:
            continue
        try:
            with zipfile.ZipFile(archive) as package:
                for name in package.namelist():
                    base = PurePosixPath(name).name
                    if base.lower().startswith(NOTICE_PREFIXES) and not name.endswith("/"):
                        found.add(name)
        except (OSError, zipfile.BadZipFile):
            continue
    return sorted(found)


def declared_dependencies(source: str) -> list[str]:
    return [direct or added for direct, added in DECLARATION.findall(source)]


def safe_cell(value: str) -> str:
    return " ".join(value.replace("|", r"\|").split())


def inspect_license(group: str, artifact: str, version: str) -> tuple[list[tuple[str, str]], list[str]]:
    cache_dir = GRADLE_CACHE / group / artifact / version
    licenses: list[tuple[str, str]] = []
    if cache_dir.is_dir():
        for pom in sorted(cache_dir.rglob("*.pom")):
            licenses.extend(item for item in pom_licenses(pom) if item not in licenses)
    return licenses, bundled_notices(cache_dir)


def main() -> int:
    if not GRADLE.is_file() or not BUILD_FILE.is_file():
        print("FAIL: Gradle wrapper or app/build.gradle.kts is missing", file=sys.stderr)
        return 1
    result = subprocess.run(
        [str(GRADLE), "--no-daemon", "--console=plain", ":app:dependencies", "--configuration", "debugRuntimeClasspath"],
        cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False,
    )
    if result.returncode:
        print(result.stdout[-12000:], file=sys.stderr)
        print("FAIL: Gradle could not resolve debugRuntimeClasspath", file=sys.stderr)
        return result.returncode
    resolved: dict[tuple[str, str], str] = {}
    for line in result.stdout.splitlines():
        match = COORDINATE.search(line)
        if not match:
            continue
        group, artifact, requested = match.group("group", "artifact", "version")
        version = requested
        arrow = re.search(r"\s->\s+([^\s()]+)", line[match.end():])
        if arrow:
            version = arrow.group(1)
        resolved[(group, artifact)] = version
    declarations = declared_dependencies(BUILD_FILE.read_text(encoding="utf-8"))
    dynamic = [item for item in declarations if DYNAMIC.search(item)]
    if dynamic:
        print("FAIL: dynamic dependency versions are not allowed:", file=sys.stderr)
        for item in dynamic:
            print(f"  {item}", file=sys.stderr)
        return 1
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    audit = {key: inspect_license(key[0], key[1], version) for key, version in resolved.items()}
    metadata_count = sum(bool(licenses) for licenses, _ in audit.values())
    notice_count = sum(bool(notices) for _, notices in audit.values())
    rows = [
        "# ToxiFiles dependency inventory", "",
        f"- Resolved runtime modules: **{len(resolved)}**",
        f"- Declared dependency entries: **{len(declarations)}**",
        "- Configuration: :app:debugRuntimeClasspath",
        "- Version policy: direct declarations must use fixed versions.",
        f"- POM license metadata: **{metadata_count}/{len(resolved)}** resolved modules.",
        f"- Bundled LICENSE/NOTICE files: **{notice_count}/{len(resolved)}** resolved modules.",
        "- Legal approval: **OWNER REVIEW REQUIRED**. POM metadata and bundled notices are evidence, not legal approval.",
        "- Vulnerability status: this report does not replace Dependabot, CodeQL, or Dependency Review.", "",
        "| Group | Artifact | Resolved version | POM license metadata | Bundled notice files |",
        "|---|---|---:|---|---|",
    ]
    for (group, artifact), version in sorted(resolved.items()):
        licenses, notices = audit[(group, artifact)]
        license_text = "; ".join(name + (f" ({url})" if url else "") for name, url in licenses) or "Missing POM license metadata"
        notice_text = "; ".join(notices) or "No bundled notice found"
        rows.append(f"| {safe_cell(group)} | {safe_cell(artifact)} | {safe_cell(version)} | {safe_cell(license_text)} | {safe_cell(notice_text)} |")
    rows.extend(["", "## Direct declarations", ""])
    rows.extend(f"- {item}" for item in sorted(set(declarations)))
    rows.extend([
        "", "## Review boundary", "",
        "This report inventories the resolved runtime graph and extracts license declarations from cached Maven POMs and bundled LICENSE/NOTICE files from JAR/AAR artifacts. Missing metadata is reported explicitly. It does not determine license compatibility, grant distribution rights, or replace inclusion of required license texts/notices. The project owner must approve the project license and distribution notices before publishing a release.", "",
    ])
    REPORT.write_text("\n".join(rows), encoding="utf-8")
    print(f"PASS: resolved {len(resolved)} runtime modules; POM license metadata {metadata_count}/{len(resolved)}; report: {REPORT.relative_to(ROOT)}")
    if not resolved:
        print("FAIL: dependency report contained no resolved external modules", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
