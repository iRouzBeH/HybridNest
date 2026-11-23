#!/bin/bash

# Define NDK path (Update this to your actual NDK path on Linux)
export NDK_DIR=$HOME/Android/Sdk/ndk/26.3.11579264
export TOOLCHAIN=$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64
export TARGET=aarch64-linux-android
export API=35 # Targeting newer APIs

export AR=$TOOLCHAIN/bin/llvm-ar
export CC=$TOOLCHAIN/bin/${TARGET}${API}-clang
export AS=$CC
export CXX=$TOOLCHAIN/bin/${TARGET}${API}-clang++
export LD=$TOOLCHAIN/bin/ld
export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
export STRIP=$TOOLCHAIN/bin/llvm-strip

# The magic linker flag for 16KB page alignment
export LDFLAGS="-Wl,-z,max-page-size=16384"
export CFLAGS="-fPIC"

# Configure and make
# Note: You need to run this from the openconnect source directory
# This script is a template.
echo "Running configure for $TARGET with 16KB alignment flags..."

./configure --host=$TARGET \
            --disable-shared \
            --enable-static \
            --with-vpnc-script=/system/etc/vpnc/vpnc-script

make clean
make -j4
