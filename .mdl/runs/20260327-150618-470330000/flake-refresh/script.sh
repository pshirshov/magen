#!/usr/bin/env bash
# Source Mudyla runtime from package
export MDL_OUTPUT_JSON="/Users/caparow/git/magen/.mdl/runs/20260327-150618-470330000/flake-refresh/output.json"
source "/nix/store/pxcn3abdv1zk1rgiga9i0n9nd445wvzh-mudyla-0.5.3/lib/python3.12/site-packages/mudyla/runtime.sh"

function do_update() {
  sed -i -E \
      -e "s/version\s*=\s*\"([a-z0-9-]\.?)+\";/version = \"$1\";/g" \
      $2
}


PKG_VERSION=$(cat build.sbt | sed -r 's/.*\"(.*)\".**/\1/' | head -1 | sed -E "s/-SNAPSHOT//")

nix flake update

do_update "$PKG_VERSION" ./flake.nix

squish-lockfile lockfile-config.json > deps.lock.json

git add . || true

nix flake check

ret success:bool=true