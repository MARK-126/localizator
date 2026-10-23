#!/usr/bin/env bash
#
# Valida los commits de un rango de git.
#   - Título de 45 caracteres como máximo.
#   - Sin trailer "Co-Authored-By".
#
# Uso: check-commits.sh <base>..<head>
# Salida: 0 si todos cumplen, 1 en otro caso. Los errores usan el formato de anotaciones de GitHub Actions.

set -euo pipefail
export LC_ALL=C.UTF-8

readonly MAX_SUBJECT_LENGTH=45
range="${1:?uso: check-commits.sh <base>..<head>}"
status=0

while IFS= read -r sha; do
    subject="$(git log -1 --format=%s "$sha")"
    short="$(git rev-parse --short "$sha")"
    if (( ${#subject} > MAX_SUBJECT_LENGTH )); then
        echo "::error title=Título demasiado largo::$short tiene ${#subject} caracteres (máx. $MAX_SUBJECT_LENGTH): $subject"
        status=1
    fi
    if git log -1 --format=%B "$sha" | grep -qi '^co-authored-by:'; then
        echo "::error title=Coautoría no permitida::$short contiene Co-Authored-By: $subject"
        status=1
    fi
done < <(git rev-list "$range")

if (( status == 0 )); then
    echo "$(git rev-list --count "$range") commits válidos en $range"
fi
exit "$status"
