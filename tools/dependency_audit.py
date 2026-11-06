#!/usr/bin/env python3
"""Resolve and inventory ToxiFiles runtime dependencies for repeatable review."""
from __future__ import annotations
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
BUILD_FILE = ROOT / "app" / "build.gradle.kts"
REPORT = ROOT / "build" / "reports" / "toxifiles-dependency-audit.md"
COORDINATE = re.compile(r"(?P<group>[A-Za-z0-9_.-]+):(?P<artifact>[A-Za-z0-9_.-]+):(?P<version>[A-Za-z0-9_.+-]+)")
DECLARATION = re.compile(r'(?:implementation|api|runtimeOnly|compileOnly|testImplementation|androidTestImplementation)\("([^"]+)"\)')
DYNAMIC = re.compile(r"(?:\+|latest\.(?:release|integration)|SNAPSHOT)", re.IGNORECASE)

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
    declarations = DECLARATION.findall(BUILD_FILE.read_text(encoding="utf-8"))
    dynamic = [item for item in declarations if DYNAMIC.search(item)]
    if dynamic:
        print("FAIL: dynamic dependency versions are not allowed:", file=sys.stderr)
        for item in dynamic:
            print(f"  {item}", file=sys.stderr)
        return 1
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    rows = [
        "# ToxiFiles dependency inventory", "",
        f"- Resolved runtime modules: **{len(resolved)}**",
        f"- Declared dependency entries: **{len(declarations)}**",
        "- Configuration: :app:debugRuntimeClasspath",
        "- Version policy: direct declarations must use fixed versions.",
        "- License status: **NOT VERIFIED**. Review each resolved artifact's POM/license and bundled notices before distribution.",
        "- Vulnerability status: this report does not replace Dependabot, CodeQL, or Dependency Review.", "",
        "| Group | Artifact | Resolved version | License review |",
        "|---|---|---:|---|",
    ]
    for (group, artifact), version in sorted(resolved.items()):
        rows.append(f"| {group} | {artifact} | {version} | Pending |")
    rows.extend(["", "## Direct declarations", ""])
    rows.extend(f"- {item}" for item in sorted(set(declarations)))
    rows.extend([
        "", "## Review boundary", "",
        "This report is a reproducible dependency inventory only. It deliberately does not infer or approve licenses from package names. Confirm the license text and notice obligations for every resolved artifact, including transitive dependencies, before publishing a release.", "",
    ])
    REPORT.write_text("\n".join(rows), encoding="utf-8")
    print(f"PASS: resolved {len(resolved)} runtime modules; report: {REPORT.relative_to(ROOT)}")
    if not resolved:
        print("FAIL: dependency report contained no resolved external modules", file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
