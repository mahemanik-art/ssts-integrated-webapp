#!/usr/bin/env bash
# Launch the app locally with DB/env vars pulled from .vscode/launch.json.
# Usage: ./scripts/run-dev.sh   (optional: pass -- <gradle args>)
set -euo pipefail

cd "$(dirname "$0")/.."

# launch.json is JSONC (it has // comments), so strip them before strict JSON parsing.
read -r -a env_pairs < <(python3 -c "
import json, re
s = open('.vscode/launch.json').read()
s = re.sub(r'^\s*//.*$', '', s, flags=re.M)
env = json.loads(s)['configurations'][0]['env']
print(' '.join(f'{k}={v}' for k, v in env.items()))
")

exec env "${env_pairs[@]}" ./gradlew bootRun "$@"
