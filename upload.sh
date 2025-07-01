#!/bin/bash

set -e

# 变量配置
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
LOCAL_PROPERTIES="$PROJECT_ROOT/local.properties"
GRADLE_PROPERTIES="$PROJECT_ROOT/gradle.properties"
MODULE_NAME="filepicker"
BUILD_DIR="$PROJECT_ROOT/$MODULE_NAME/build/staging-deploy"

# 读取local.properties指定字段
function getProperty() {
    grep "^$1=" "$LOCAL_PROPERTIES" | cut -d'=' -f2-
}

# 读取gradle.properties指定字段
function getGradleProperty() {
    grep "^$1=" "$GRADLE_PROPERTIES" | cut -d'=' -f2-
}

# 获取Token和版本号
TOKEN=$(getProperty "sonatype.token")
PUBLISH_VERSION=$(getGradleProperty "PUBLISH_VERSION")

# 校验参数
if [[ -z "$TOKEN" ]]; then
  echo "错误：local.properties 缺少 sonatype.token 配置"
  exit 1
fi

if [[ -z "$PUBLISH_VERSION" ]]; then
  echo "错误：gradle.properties 缺少 PUBLISH_VERSION 配置"
  exit 1
fi

ZIP_NAME="filepicker_${PUBLISH_VERSION}.zip"
ZIP_FILE="$BUILD_DIR/$ZIP_NAME"

# 打印参数信息
echo "========== 参数信息 =========="
echo "项目根目录           ：$PROJECT_ROOT"
echo "local.properties路径 ：$LOCAL_PROPERTIES"
echo "gradle.properties路径：$GRADLE_PROPERTIES"
echo "Module名称           ：$MODULE_NAME"
echo "构建输出目录         ：$BUILD_DIR"
echo "压缩文件名           ：$ZIP_NAME"
echo "压缩文件完整路径     ：$ZIP_FILE"
echo "Sonatype Token前10位 ：${TOKEN:0:10}***（部分隐藏）"
echo "PUBLISH_VERSION      ：$PUBLISH_VERSION"
echo "=============================="
echo ""

echo "========== 开始流程 =========="

# Step 1: clean
echo "执行 gradlew clean..."
cd "$PROJECT_ROOT"
./gradlew clean

# Step 2: assembleRelease
echo "构建 $MODULE_NAME 模块 release 包..."
./gradlew :$MODULE_NAME:assembleRelease

# Step 3: publishAllPublicationsToMavenRepository
echo "发布 $MODULE_NAME 模块到本地 staging 目录..."
./gradlew :$MODULE_NAME:publishAllPublicationsToMavenRepository

# Step 4: 压缩 staging-deploy 目录
echo "压缩 $BUILD_DIR 目录为 $ZIP_FILE..."
cd "$BUILD_DIR"

if [[ -f "$ZIP_FILE" ]]; then
    rm "$ZIP_FILE"
fi

zip -r "$ZIP_NAME" *

# Step 5: 上传 zip 文件
echo "上传 $ZIP_FILE 到 Sonatype..."

curl --request POST \
  --verbose \
  --header "Authorization: Bearer $TOKEN" \
  --form "bundle=@$ZIP_FILE" \
  https://central.sonatype.com/api/v1/publisher/upload

echo "========== 上传完成 =========="
