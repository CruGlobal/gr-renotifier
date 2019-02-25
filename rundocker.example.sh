#!/usr/bin/env bash

# This is an example for how to run this tool using docker.
# Copy and tweak.

cd "$( dirname "${BASH_SOURCE[0]}" )"


get_password ()
{
  lpass show --password "mdrees flattened Global Registry Prod"
}

export PASSWORD=$(get_password)

docker run \
  --rm \
  --env PASSWORD \
  gr-renotifier \
  --username mdrees \
  --database-name gr_flat \
  --database-host pg-gr-flat.aws.cru.org \
  --triggered-by siebel \
  --entity-type designation \
  --owned-by 8194da28-fbc9-11e3-bd50-12543788cf06 \
  --updated-after "2019-02-25 00:00:00" \
  --subscription-url http://prodauth.aws.cru.org:4502/bin/crugive/designation-updates \
  --limit 5 \
  --max-concurrent-requests 2
