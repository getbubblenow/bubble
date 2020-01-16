#!/bin/bash

echo "$@" > /tmp/init.args

LOG=/dev/null

function die {
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "${1}" >> ${LOG}
}

export LANG="en_US.UTF-8"
export LANGUAGE="en_US.UTF-8"
export LC_CTYPE="en_US.UTF-8"
export LC_NUMERIC="en_US.UTF-8"
export LC_TIME="en_US.UTF-8"
export LC_COLLATE="en_US.UTF-8"
export LC_MONETARY="en_US.UTF-8"
export LC_MESSAGES="en_US.UTF-8"
export LC_PAPER="en_US.UTF-8"
export LC_NAME="en_US.UTF-8"
export LC_ADDRESS="en_US.UTF-8"
export LC_TELEPHONE="en_US.UTF-8"
export LC_MEASUREMENT="en_US.UTF-8"
export LC_IDENTIFICATION="en_US.UTF-8"
export LC_ALL=en_US.UTF-8

if [[ "$(whoami)" != "postgres" ]] ; then
  echo "Must be run as postgres user"
  exit 1
fi

DB_NAME=${1:?no db name provided}
DB_USER=${2:?no db user provided}
IS_FORK=${3:?no fork argument provided}
INSTALL_MODE=${4:?no install mode provided}
DROP_AND_RECREATE=${5}

BUBBLE_HOME=/home/bubble
BUBBLE_JAR=/home/bubble/current/bubble.jar
if [[ ! -f ${BUBBLE_JAR} ]] ; then
  die "Bubble jar not found: ${BUBBLE_JAR}"
fi

function user_exists {
  username="${1}"
  num_users="$(echo "select count(*) from pg_user where usename='${username}'" | psql -qt | egrep -v '^$')"
  if [[ -z "${num_users}" || ${num_users} -eq 0 ]] ; then
    echo "0"
  else
    echo "1"
  fi
}

function db_exists {
  dbname="${1}"
  num_dbs="$(echo "select count(*) from pg_database where datname='${dbname}'" | psql -qt | egrep -v '^$')"
  if [[ -z "${num_dbs}" || ${num_dbs} -eq 0 ]] ; then
    echo "0"
  else
    echo "1"
  fi
}

function count_table_rows {
  dbname="${1}"
  tname="${2}"
  num_rows="$(echo "select count(*) from ${tname}" | psql -qt ${dbname} | egrep -v '^$')"
  if [[ -z "${num_rows}" ]] ; then
    die "count_table_rows: error counting rows for table ${tname}"
  fi
  echo ${num_rows}
}

if [[ ! -z "${DROP_AND_RECREATE}" && "${DROP_AND_RECREATE}" == "drop" ]] ; then
  dropdb ${DB_NAME} || echo "error dropping DB ${DB_NAME} (will continue)"
  dropuser ${DB_USER} || echo "error dropping DB user ${DB_USER} (will continue)"
  uuid > ${BUBBLE_HOME}/.BUBBLE_PG_PASSWORD
fi

if [[ $(user_exists ${DB_USER}) -eq 0 ]] ; then
  log "Creating user ${DB_USER}"
  if [[ "$(echo ${IS_FORK} | tr [[:upper:]] [[:lower:]])" == "true" ]] ; then
    createuser --createdb --no-createrole --no-superuser --no-replication ${DB_USER} || die "Error creating user"
  else
    createuser --no-createdb --no-createrole --no-superuser --no-replication ${DB_USER} || die "Error creating user"
  fi
  DB_PASS="$(cat ${BUBBLE_HOME}/.BUBBLE_PG_PASSWORD)"
  echo "ALTER USER bubble WITH PASSWORD '${DB_PASS}'" | psql || die "Error setting user password"
fi

if [[ $(db_exists ${DB_NAME}) -eq 0 ]] ; then
  log "Creating DB ${DB_NAME}"
  createdb --encoding=UTF-8 ${DB_NAME} || die "Error creating DB"
fi

if [[ $(count_table_rows ${DB_NAME} account 2> /dev/null) -eq 0 ]] ; then
    TEMP_DB="${DB_NAME}_$(uuid | tr -d '-')"
    log "Creating tempDB ${TEMP_DB}"
    createdb --encoding=UTF-8 ${TEMP_DB} || die "Error creating temp DB"
    log "Populating tempDB ${TEMP_DB} with bubble.sq.gz"
    zcat /home/bubble/sql/bubble.sql.gz | psql ${TEMP_DB} || die "Error writing database schema/data"
    DB_KEY="$(cat ${BUBBLE_HOME}/.BUBBLE_DB_ENCRYPTION_KEY)"
    TO_KEY="$(uuid)"
    if [[ -z "${TO_KEY}" ]] ; then
      dropdb ${TEMP_DB}
      die "${BUBBLE_HOME}/.BUBBLE_DB_ENCRYPTION_KEY does not exist or is empty"
    fi
    log "Dumping schema from ${TEMP_DB} -> ${DB_NAME}"
    pg_dump --schema-only ${TEMP_DB} | psql ${DB_NAME}
    # log "Rekeying: fromKey=${DB_KEY}, toKey=${TO_KEY}"
    java -cp ${BUBBLE_JAR} bubble.main.RekeyDatabaseMain \
        --jar ${BUBBLE_JAR} \
        --db-user ${DB_USER} \
        --db-password "${DB_PASS}" \
        --from-db ${TEMP_DB} \
        --from-key "${DB_KEY}" \
        --to-db ${DB_NAME} \
        --to-key "${TO_KEY}" 2>&1 || (dropdb ${TEMP_DB} ; die "Error re-keying database")
#        --to-key "${TO_KEY}" 2>&1 | tee -a ${LOG} || (dropdb ${TEMP_DB} ; die "Error re-keying database")
    log "Rekey successful, dropping ${TEMP_DB}"
    dropdb ${TEMP_DB}
    log "Saving ${TO_KEY} to ${BUBBLE_HOME}/.BUBBLE_DB_ENCRYPTION_KEY"
    echo -n "${TO_KEY}" > ${BUBBLE_HOME}/.BUBBLE_DB_ENCRYPTION_KEY
fi

echo "DELETE FROM bubble_node_key WHERE node IN (SELECT uuid FROM bubble_node WHERE ip4='127.0.0.1' OR ip4='' OR ip4 IS NULL)" | psql ${DB_NAME} \
  || die "Error removing bubble_node_keys with remote_host=127.0.0.1"
echo "DELETE FROM bubble_node WHERE ip4='127.0.0.1'" | psql ${DB_NAME} \
  || die "Error removing bubble_nodes with ip4=127.0.0.1"

if [[ "${INSTALL_MODE}" == "node" ]] ; then
  echo "UPDATE account SET locked=true" | psql ${DB_NAME} \
    || die "Error locking accounts"
fi
