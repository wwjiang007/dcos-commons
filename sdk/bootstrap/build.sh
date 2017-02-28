#!/usr/bin/env bash

# exit immediately on failure
set -e

BOOTSTRAP_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT_DIR="$(dirname $(dirname $BOOTSTRAP_DIR))"

if [ -z "$GOPATH" -o -z "$(which go)" ]; then
  echo "Missing GOPATH environment variable or 'go' executable. Please configure a Go build environment."
  exit 1
fi

REPO_NAME=dcos-commons # CI dir does not match repo name
GOPATH_MESOSPHERE="$GOPATH/src/github.com/mesosphere"
GOPATH_BOOTSTRAP="$GOPATH_MESOSPHERE/$REPO_NAME/sdk/bootstrap"
EXE_FILENAME=bootstrap

GO_VERSION=$(go version | awk '{print $3}')
UPX_BINARY="" # only enabled for go1.7+
case "$GO_VERSION" in
    go1.[7-9]*|go1.1[0-9]*|go[2-9]*) # go1.7+, go2+ (must come before go1.0-go1.4: support e.g. go1.10)
        echo "Detected Go 1.7.x+: $(which go) $GO_VERSION"
        UPX_BINARY="$(which upx || which upx-ucl || echo '')" # avoid error code if upx isn't installed
        ;;
    go0.*|go1.[0-4]*) # go0.*, go1.0-go1.4
        echo "Detected Go <=1.4. This is too old, please install Go 1.5+: $(which go) $GO_VERSION"
        exit 1
        ;;
    go1.5*) # go1.5
        echo "Detected Go 1.5.x: $(which go) $GO_VERSION"
        export GO15VENDOREXPERIMENT=1
        ;;
    go1.6*) # go1.6
        echo "Detected Go 1.6.x: $(which go) $GO_VERSION"
        # no experiment, but also no UPX
        ;;
    *) # ???
        echo "Unrecognized go version: $(which go) $GO_VERSION"
        exit 1
        ;;
esac

if [ -n "$UPX_BINARY" ]; then
    echo "Binary CLI compression enabled: $($UPX_BINARY -V | head -n 1)"
else
    echo "Binary CLI compression disabled"
fi

# Configure GOPATH with dcos-commons symlink (rather than having it pull master):
echo "Creating GOPATH symlink into dcos-commons: $GOPATH"
rm -rf "$GOPATH_MESOSPHERE/$REPO_NAME"
mkdir -p "$GOPATH_MESOSPHERE"
pushd "$GOPATH_MESOSPHERE"
ln -s "$REPO_ROOT_DIR" $REPO_NAME
popd
echo "Created symlink $GOPATH_MESOSPHERE/$REPO_NAME -> $REPO_ROOT_DIR"

# run get/build from within GOPATH:
pushd "$GOPATH_BOOTSTRAP"
go get
echo "Building ${EXE_FILENAME}"

# optimization: build a native version of the executable and check if the sha1 matches a
# previous native build. if the sha1 matches, then we can skip the rebuild.
SHA1SUM_FILENAME="${EXE_FILENAME}.native.sha1sum"
go build -o ${EXE_FILENAME}.native
BUILD_SHA1=$(sha1sum ${EXE_FILENAME}.native | awk '{ print $1 }')

PKG_FILENAME=${EXE_FILENAME}.zip
if [ -f $SHA1SUM_FILENAME -a -f $PKG_FILENAME -a "$BUILD_SHA1" = "$(cat $SHA1SUM_FILENAME)" ]; then
    # build output hasn't changed. skip.
    echo "Skipping rebuild of ${PKG_FILENAME}"
else
    # build output is missing, or native build changed. build.
    echo "Native SHA1 mismatch or missing ${PKG_FILENAME}, rebuilding ${PKG_FILENAME}"
    echo $BUILD_SHA1 > $SHA1SUM_FILENAME

    # available GOOS/GOARCH permutations are listed at:
    # https://golang.org/doc/install/source#environment
    CGO_ENABLED=0 GOOS=linux GOARCH=386 go build -ldflags="-s -w"

    # use upx if available and if golang's output doesn't have problems with it:
    if [ -n "$UPX_BINARY" ]; then
        $UPX_BINARY -q --best ${EXE_FILENAME}
    fi

    rm -f ${PKG_FILENAME}
    zip ${PKG_FILENAME} ${EXE_FILENAME}
fi
echo $(pwd)/${PKG_FILENAME}
popd
