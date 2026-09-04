#!/bin/sh
set -eu

psql --username "$POSTGRES_USER" --dbname postgres --set ON_ERROR_STOP=1 <<-'EOSQL'
    create role eto_crm_platform login password 'local-platform-only';
    create database eto_crm_platform owner eto_crm_platform;
    revoke connect on database eto_crm_platform from public;
    grant connect on database eto_crm_platform to eto_crm_platform;
    create role eto_crm_r_hyper_tooling login password 'local-r-hyper-tooling-only';
    create database eto_crm_r_hyper_tooling owner eto_crm_r_hyper_tooling;
    revoke connect on database eto_crm_r_hyper_tooling from public;
    grant connect on database eto_crm_r_hyper_tooling to eto_crm_r_hyper_tooling;
EOSQL
