#!/usr/bin/env bash

cd "$( dirname "${BASH_SOURCE[0]}" )"


get_password ()
{
  lpass show --password "mdrees flattened Global Registry Prod"
}

export PASSWORD=$(get_password)

java \
  -classpath "target/*" \
  org.cru.globalreg.renotifier.Main \
  --username mdrees \
  --database-name gr_flat \
  --database-host pg-gr-flat.aws.cru.org \
  --triggeredBy siebel \
  --entity-type designation \
  --subscription-url http://prodauth.aws.cru.org:4502/bin/crugive/designation-updates

