#!/usr/bin/env python3
"""Install managed OSINT CLI tools into the skill runtime."""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path


TOOLS = {
    "Photon": "https://github.com/s0md3v/Photon.git",
    "theHarvester": "https://github.com/laramies/theHarvester.git",
}


def run(command: list[str], *, cwd: Path | None = None, env: dict[str, str] | None = None) -> None:
    print("+ " + " ".join(command), flush=True)
    subprocess.run(command, cwd=cwd, env=env, check=True)


def clone_or_update(name: str, url: str, vendor_dir: Path) -> Path:
    destination = vendor_dir / name
    if (destination / ".git").exists():
        run(["git", "-C", str(destination), "pull", "--ff-only"])
    elif destination.exists():
        shutil.rmtree(destination)
        run(["git", "clone", "--depth", "1", url, str(destination)])
    else:
        run(["git", "clone", "--depth", "1", url, str(destination)])
    return destination


def write_wrapper(path: Path, body: str) -> None:
    path.write_text(body, encoding="utf-8")
    path.chmod(0o755)


def shell_wrapper(tool_root: Path, command: str) -> str:
    return f"""#!/bin/sh
set -eu
TOOL_ROOT={quote(str(tool_root))}
STATE_HOME="${{OSINT_TOOL_HOME:-$TOOL_ROOT/state/home}}"
mkdir -p "$STATE_HOME"
export HOME="$STATE_HOME"
exec {command} "$@"
"""


def quote(value: str) -> str:
    return "'" + value.replace("'", "'\"'\"'") + "'"


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tool-root", required=True, type=Path)
    parser.add_argument("--venv", required=True, type=Path, help="Root directory for per-tool venvs.")
    parser.add_argument("--bin-dir", required=True, type=Path)
    parser.add_argument("--vendor-dir", required=True, type=Path)
    parser.add_argument("--python", required=True)
    return parser.parse_args(argv)


def venv_python(venv_dir: Path) -> Path:
    return venv_dir / ("Scripts/python.exe" if os.name == "nt" else "bin/python")


def ensure_tool_venv(name: str, venv_root: Path, python: str) -> Path:
    directory = venv_root / name
    executable = venv_python(directory)
    version_check = "import sys; raise SystemExit(0 if sys.version_info[:2] == (3, 12) else 1)"
    if executable.exists():
        if subprocess.run([str(executable), "-c", version_check], check=False).returncode == 0:
            return executable
        shutil.rmtree(directory)
    run([python, "-m", "venv", str(directory)])
    return executable


def pip_install(python: Path, args: list[str], env: dict[str, str], cwd: Path | None = None) -> None:
    run([str(python), "-m", "pip", "install", "-U", "pip"], env=env)
    run([str(python), "-m", "pip", "install", *args], cwd=cwd, env=env)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    tool_root = args.tool_root.expanduser().resolve()
    venv_root = args.venv.expanduser().resolve()
    bin_dir = args.bin_dir.expanduser().resolve()
    vendor_dir = args.vendor_dir.expanduser().resolve()
    python = shutil.which(args.python) or args.python

    if not shutil.which("git"):
        print("git is required to install managed OSINT tools", file=sys.stderr)
        return 2
    version_check = "import sys; raise SystemExit(0 if sys.version_info[:2] == (3, 12) else 1)"
    if subprocess.run([python, "-c", version_check], check=False).returncode != 0:
        print("Python 3.12.x is required for managed OSINT tools", file=sys.stderr)
        return 2

    tool_root.mkdir(parents=True, exist_ok=True)
    bin_dir.mkdir(parents=True, exist_ok=True)
    vendor_dir.mkdir(parents=True, exist_ok=True)
    (tool_root / "state" / "home").mkdir(parents=True, exist_ok=True)

    legacy_root_venv = venv_python(venv_root)
    if legacy_root_venv.exists():
        print(f"Removing legacy shared venv at {venv_root}", flush=True)
        shutil.rmtree(venv_root)
    venv_root.mkdir(parents=True, exist_ok=True)
    for obsolete in (
        bin_dir / "spiderfoot",
        venv_root / "spiderfoot",
        vendor_dir / "spiderfoot",
    ):
        if obsolete.is_dir():
            shutil.rmtree(obsolete)
        elif obsolete.exists():
            obsolete.unlink()

    pip_env = os.environ.copy()
    pip_env["PIP_DISABLE_PIP_VERSION_CHECK"] = "1"
    sherlock_python = ensure_tool_venv("sherlock", venv_root, python)
    pip_install(sherlock_python, ["-U", "sherlock-project"], pip_env)

    maigret_python = ensure_tool_venv("maigret", venv_root, python)
    pip_install(maigret_python, ["-U", "maigret"], pip_env)

    photon_dir = clone_or_update("Photon", TOOLS["Photon"], vendor_dir)
    photon_python = ensure_tool_venv("photon", venv_root, python)
    pip_install(photon_python, ["-r", str(photon_dir / "requirements.txt")], pip_env)

    theharvester_dir = clone_or_update("theHarvester", TOOLS["theHarvester"], vendor_dir)
    theharvester_python = ensure_tool_venv("theHarvester", venv_root, python)
    pip_install(theharvester_python, ["."], pip_env, cwd=theharvester_dir)

    sherlock_bin = sherlock_python.parent
    maigret_bin = maigret_python.parent
    theharvester_bin = theharvester_python.parent
    write_wrapper(
        bin_dir / "sherlock",
        shell_wrapper(tool_root, quote(str(sherlock_bin / "sherlock"))),
    )
    write_wrapper(
        bin_dir / "maigret",
        shell_wrapper(tool_root, quote(str(maigret_bin / "maigret"))),
    )
    write_wrapper(
        bin_dir / "theHarvester",
        shell_wrapper(tool_root, quote(str(theharvester_bin / "theHarvester"))),
    )
    write_wrapper(
        bin_dir / "photon",
        shell_wrapper(tool_root, f"{quote(str(photon_python))} {quote(str(photon_dir / 'photon.py'))}"),
    )
    print(f"Installed managed OSINT tools to {tool_root}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
