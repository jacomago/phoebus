#!/bin/bash
# Launch an existing Maven build; build first with mvn -DskipTests clean install.
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

usage() {
    printf '%s\n' \
        'Usage: run_maven.sh [TARGET] [OPTIONS] [-- APPLICATION_ARGS...]' \
        '' \
        'Targets (default: product):' \
        '  product          Phoebus UI' \
        '  alarm-server     Alarm server' \
        '  scan-server      Scan server' \
        '  archive-engine   Archive engine' \
        '' \
        'Options:' \
        '  --ini            Import phoebus.ini from the repository root' \
        '  --settings FILE  Import a specific INI or exported XML settings file' \
        '  --no-settings    Do not explicitly import settings (default)' \
        '  -h, --help       Show this help' \
        '' \
        'Relative --settings paths are resolved from your current directory.' \
        'Application arguments after -- are passed unchanged; relative paths' \
        'in those arguments are resolved from the selected target directory.' \
        'Default/bundled settings and saved preferences are not disabled.' \
        'Use JAVA_TOOL_OPTIONS for JVM options.' \
        '' \
        'Examples:' \
        '  ./run_maven.sh' \
        '  ./run_maven.sh product --ini' \
        '  ./run_maven.sh product --settings ./site.ini -- -nosplash' \
        '  ./run_maven.sh alarm-server --ini -- -config MyAlarm' \
        '  ./run_maven.sh scan-server --no-settings' \
        '  ./run_maven.sh archive-engine -- -help'
}

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

target=product
if [[ $# -gt 0 && $1 != -* ]]; then
    target=$1
    shift
fi

case "$target" in
    product)
        target_dir="$ROOT/phoebus-product/target"
        artifact=product
        ;;
    alarm-server|scan-server|archive-engine)
        target_dir="$ROOT/services/$target/target"
        artifact="service-$target"
        ;;
    *) fail "Unknown target '$target'. Use --help for supported targets." ;;
esac

settings=
while [[ $# -gt 0 ]]; do
    case "$1" in
        --ini) settings="$ROOT/phoebus.ini"; shift ;;
        --settings)
            [[ $# -ge 2 && -n $2 && $2 != -* ]] || fail '--settings requires a file path.'
            settings=$2
            shift 2
            ;;
        --no-settings) settings=; shift ;;
        -h|--help) usage; exit 0 ;;
        --) shift; break ;;
        *) fail "Unknown option '$1'. Put application arguments after --." ;;
    esac
done

if [[ -n $settings ]]; then
    [[ -f $settings && -r $settings ]] || fail "Settings file is not readable: $settings"
    # Resolve before changing to the target directory (including paths with spaces).
    settings="$(cd -- "$(dirname -- "$settings")" && pwd)/$(basename -- "$settings")"
    set -- -settings "$settings" "$@"
fi

[[ -d $target_dir ]] || fail "Build directory not found: $target_dir. Build with mvn -DskipTests clean install."
shopt -s nullglob
jars=()
for jar in "$target_dir/$artifact-"*.jar; do
    case "$jar" in
        *-sources.jar|*-javadoc.jar|*-tests.jar) continue ;;
    esac
    jars+=("$jar")
done
[[ ${#jars[@]} -gt 0 ]] || fail "No $artifact JAR found in $target_dir. Build with mvn -DskipTests clean install."
[[ ${#jars[@]} -eq 1 ]] || fail "Multiple $artifact JARs found in $target_dir. Clean and rebuild to select one version."

cd -- "$target_dir"
exec java --add-modules=ALL-SYSTEM -jar "${jars[0]}" "$@"
