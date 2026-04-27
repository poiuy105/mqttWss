cmake_minimum_required(VERSION 3.22.1)
project("kittentts")

# 设置 C++ 标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加 espeak-ng 头文件路径
include_directories(${CMAKE_SOURCE_DIR}/espeak-ng/src/include)

# 查找 ONNX Runtime
find_library(onnxruntime-lib onnxruntime)

# 添加源文件
add_library(
    kittentts-native
    SHARED
    native-lib.cpp
)

# 链接库
target_link_libraries(
    kittentts-native
    ${onnxruntime-lib}
    android
    log
)
