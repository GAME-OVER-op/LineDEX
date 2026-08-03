#!/usr/bin/env sh
set -eu

output=${1:-linedex-dev.jks}
alias_name=${LINEDEX_DEV_KEY_ALIAS:-linedex-dev}
store_password=${LINEDEX_DEV_STORE_PASSWORD:-change-this-development-password}
key_password=${LINEDEX_DEV_KEY_PASSWORD:-$store_password}

if [ -e "$output" ]; then
    printf 'Refusing to overwrite %s\n' "$output" >&2
    exit 1
fi

keytool -genkeypair \
    -keystore "$output" \
    -storepass "$store_password" \
    -keypass "$key_password" \
    -alias "$alias_name" \
    -keyalg RSA \
    -keysize 3072 \
    -validity 10000 \
    -dname "CN=LineDEX Development, OU=Development, O=LineDEX, C=LV"

printf '\nCreated %s\n' "$output"
printf 'GitHub base64 secret:\n'
base64 "$output" | tr -d '\n'
printf '\n'
