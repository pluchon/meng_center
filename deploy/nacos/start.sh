#!/bin/bash
set -euo pipefail

# The official entrypoint enables shell tracing, which echoes auth secrets.
# Preserve its startup logic while removing that trace instruction.
sed '/^set -x$/d' /home/nacos/bin/docker-startup.sh > /tmp/meng-nacos-startup.sh
exec bash /tmp/meng-nacos-startup.sh
