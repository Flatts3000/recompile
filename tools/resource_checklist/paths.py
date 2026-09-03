#!/usr/bin/env python3
"""Where every stage of the checklist pipeline reads and writes.

One module so no stage carries an absolute path. `WORK` sits under `build/`, which is gitignored,
because the intermediates are a few megabytes of extracted vanilla data and regenerating them is
cheap. The only committed output is the markdown.
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))

WORK = os.environ.get("RESOURCE_CHECKLIST_WORK",
                      os.path.join(REPO, "build", "resource_checklist"))
MCDATA = os.path.join(WORK, "mcdata")            # vanilla data unpacked from the client jar
MOD_DATA = os.path.join(REPO, "src", "main", "resources", "data")
MOD_JAVA = os.path.join(REPO, "src", "main", "java", "com", "flatts", "recompile")
OUTPUT = os.path.join(REPO, "docs", "vanilla_resource_checklist.md")

os.makedirs(WORK, exist_ok=True)
