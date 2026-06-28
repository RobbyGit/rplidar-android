# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.22.1)

# Declares and names the project.
project("rplidar_native")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
add_library(
        rplidar_native
        SHARED
        native-lib.cpp
)

# Searches for a specified prebuilt library and stores the path as a variable.
find_library(
        log-lib
        log
)

# Specifies libraries CMake should link to your target library.
target_link_libraries(
        rplidar_native
        ${log-lib}
)