#!/usr/bin/env bash
# 把 native/echnet(Go)编译成 app/libs/echnet.aar。
#
# 需要:Go 1.23+(ECH 客户端)、gomobile / gobind(go install golang.org/x/mobile/cmd/gomobile@latest
# 与 .../gobind@latest)、Android SDK 与 NDK。路径可用环境变量覆盖。
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/.." && pwd)"

export ANDROID_HOME="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME"/ndk/* | sort -V | tail -1)"
fi
export ANDROID_NDK_HOME
# gomobile 会把 Go 的中文文档注释抄进生成的 Java 源码;Windows 上 javac 默认按 GBK 读会报错。
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dfile.encoding=UTF-8"

cd "$here/echnet"
go vet ./...
go test ./...

# x86 模拟器里的 arm64 翻译层(Berberis / Houdini,MuMu 等)会声明 HWCAP_CPUID 与 HWCAP_DIT,
# 却执行不了对应的系统寄存器读取,Go 运行时一碰到就 SIGILL:
#   1. MRS MIDR_EL1:只用来识别 Neoverse 服务器核,安卓上没有意义;
#   2. MRS DIT:每次 cgo 调用都会读,用于保存调用方的 DIT 状态。Go 只有在代码显式调用
#      crypto/subtle.WithDataIndependentTiming 时才会打开 DIT,echnet 与 crypto/tls 都不调用,
#      所以在安卓上报告"不支持 DIT"真机上也没有损失。
# 用 -overlay 换掉标准库里这一个文件;不改动已安装的 Go。
goroot="$(go env GOROOT)"
hwcap_src="$goroot/src/internal/cpu/cpu_arm64_hwcap.go"
overlay_dir="$here/build/overlay"
mkdir -p "$overlay_dir"
patched="$overlay_dir/cpu_arm64_hwcap.go"
for needle in $'\tif ARM64.HasCPUID {' $'\tARM64.HasDIT = isSet(HWCap, hwcap_DIT)'; do
  if [ "$(grep -cxF "$needle" "$hwcap_src")" != "1" ]; then
    echo "当前 Go 版本的 $hwcap_src 与补丁预期不符(找不到: $needle),请检查后再构建" >&2
    exit 1
  fi
done
sed -e "s/^\tif ARM64.HasCPUID {\$/\tif ARM64.HasCPUID \&\& os != \"android\" {/" \
    -e "s/^\tARM64.HasDIT = isSet(HWCap, hwcap_DIT)\$/\tARM64.HasDIT = isSet(HWCap, hwcap_DIT) \&\& os != \"android\"/" \
    "$hwcap_src" > "$patched"
python -I - "$hwcap_src" "$patched" "$overlay_dir/overlay.json" <<'EOF'
import json, os, sys
src, dst, out = (os.path.normpath(p) for p in sys.argv[1:4])
json.dump({"Replace": {src: dst}}, open(out, "w"))
EOF
overlay_json="$overlay_dir/overlay.json"
if command -v cygpath >/dev/null; then overlay_json="$(cygpath -w "$overlay_json")"; fi
export GOFLAGS="${GOFLAGS:-} -overlay=$overlay_json"
# 只打 arm64:应用本身的 abiFilters 就是 arm64-v8a。-trimpath 与 -s -w 让产物可复现、体积更小。
gomobile bind \
  -target=android/arm64 \
  -androidapi 26 \
  -javapkg org.laoyou \
  -trimpath \
  -ldflags="-s -w" \
  -o "$repo/app/libs/echnet.aar" \
  .
rm -f "$repo/app/libs/echnet-sources.jar"
echo "built $repo/app/libs/echnet.aar"
