#!/bin/sh
set -eu

CA_PATH=${DATABASE_CA_CERTIFICATE_PATH:-/tmp/rotrack-certs/supabase-db-ca.crt}

: "${DATABASE_URL:?DATABASE_URL is required}"

# Fargate injects secrets as environment variables, not files. Materialize the public provider CA
# into the task's writable tmp volume without ever echoing its value to application logs.
if [ -z "${DATABASE_CA_CERTIFICATE_PEM:-}" ]; then
  printf '%s\n' 'DATABASE_CA_CERTIFICATE_PEM is required' >&2
  exit 1
fi
case "$CA_PATH" in
  /tmp/rotrack-certs/*) ;;
  *)
    printf '%s\n' 'DATABASE_CA_CERTIFICATE_PATH must be under /tmp/rotrack-certs' >&2
    exit 1
    ;;
esac
case "$CA_PATH" in
  *'//'*|*'/../'*|*'/./'*|*'/..'|*'/.'|*/)
    printf '%s\n' 'DATABASE_CA_CERTIFICATE_PATH must not contain empty, dot, or parent path components' >&2
    exit 1
    ;;
esac
QUERY=${DATABASE_URL#*\?}
if [ "$QUERY" = "$DATABASE_URL" ]; then
  printf '%s\n' 'DATABASE_URL must contain explicit TLS query parameters' >&2
  exit 1
fi

# Parse exact parameter names. Substring checks can be bypassed by duplicate or unrelated
# parameters and can leave the JDBC driver using a weaker effective value.
SSL_MODE_COUNT=0
SSL_ROOT_COUNT=0
SSL_MODE=
SSL_ROOT=
set -f
OLD_IFS=$IFS
IFS='&'
set -- $QUERY
IFS=$OLD_IFS
for PARAMETER do
  case "$PARAMETER" in
    sslfactory=*|sslfactoryarg=*|sslhostnameverifier=*)
      printf '%s\n' 'DATABASE_URL must not override PostgreSQL TLS verification implementations' >&2
      exit 1
      ;;
    sslmode=*)
      SSL_MODE_COUNT=$((SSL_MODE_COUNT + 1))
      SSL_MODE=${PARAMETER#sslmode=}
      ;;
    sslrootcert=*)
      SSL_ROOT_COUNT=$((SSL_ROOT_COUNT + 1))
      SSL_ROOT=${PARAMETER#sslrootcert=}
      ;;
  esac
done
if [ "$SSL_MODE_COUNT" -ne 1 ] || [ "$SSL_MODE" != verify-full ]; then
  printf '%s\n' 'DATABASE_URL must contain exactly one sslmode=verify-full parameter' >&2
  exit 1
fi
if [ "$SSL_ROOT_COUNT" -ne 1 ] || [ "$SSL_ROOT" != "$CA_PATH" ]; then
  printf '%s\n' 'DATABASE_URL must contain exactly one sslrootcert matching DATABASE_CA_CERTIFICATE_PATH' >&2
  exit 1
fi
case "$DATABASE_CA_CERTIFICATE_PEM" in
  *'-----BEGIN CERTIFICATE-----'*'-----END CERTIFICATE-----'*) ;;
  *)
    printf '%s\n' 'DATABASE_CA_CERTIFICATE_PEM must contain a PEM certificate' >&2
    exit 1
    ;;
esac

umask 077
mkdir -p "$(dirname "$CA_PATH")"
printf '%s\n' "$DATABASE_CA_CERTIFICATE_PEM" > "$CA_PATH"
unset DATABASE_CA_CERTIFICATE_PEM

exec java -jar /opt/rotrack/application.jar "$@"
