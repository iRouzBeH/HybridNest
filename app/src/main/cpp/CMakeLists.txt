cmake_minimum_required(VERSION 3.22.1)

project("hybridvpn")

# Add include directory for OpenConnect headers
include_directories(include)

# Add the pre-compiled libopenconnect library as an IMPORTED target
add_library(openconnect SHARED IMPORTED)
set_target_properties(openconnect PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libopenconnect.so)

# Add the pre-compiled libstoken library
add_library(stoken SHARED IMPORTED)
set_target_properties(stoken PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libstoken.so)

# Our project's JNI bridge library
add_library(
        hybridvpn
        SHARED
        native-lib.cpp)

# Force 16 KB page alignment for Android 15+ compatibility (Google Play 2025 Requirement)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# Find the Android log library
find_library(
        log-lib
        log)

# Link everything together
target_link_libraries(
        hybridvpn
        openconnect
        stoken
        ${log-lib})
