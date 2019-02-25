#!/usr/bin/env bash

cd "$( dirname "${BASH_SOURCE[0]}" )"


docker build \
  --tag gr-renotifier \
  .
