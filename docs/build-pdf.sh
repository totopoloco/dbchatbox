#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
pandoc spring-ai-2-training.md -o spring-ai-2-training.md.pdf
echo "PDF generated: docs/spring-ai-2-training.md.pdf"
