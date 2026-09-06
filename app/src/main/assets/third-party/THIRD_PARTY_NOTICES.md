# Third-Party Notices

本 APK 捆绑以下第三方二进制组件。各组件版权归属其原作者，按自身许可证分发。

## proot (v5.1.107.92)

- 来源：Termux 软件包仓库 `packages.termux.dev`（pool/main/p/proot，版本 5.1.107.92，NDK r29 构建）
- 文件：`lib/<abi>/libproot.so`（proot 主程序）、`lib/<abi>/libproot_loader.so`（proot 内建 ELF loader）
- 许可证：GPL-2.0-or-later（与本项目 GPL-3.0-or-later 单向兼容）
- 上游源码：https://github.com/proot-me/proot 、Termux 构建脚本 https://github.com/termux/termux-packages/tree/master/packages/proot
- 本地修改：仅重命名（proot → libproot.so，loader → libproot_loader.so）以符合 Android jniLibs 打包约定；运行时通过 `PROOT_LOADER` 环境变量指向 loader 真实路径，二进制内容未改动。
- sha256（arm64-v8a/libproot.so）：`ea47e17da8e6ff4882c169c6508861e5b4be9227e477c6020f4f14facc85c10d`

## libtalloc (v2.4.3)

- 来源：Termux 软件包仓库（pool/main/libt/libtalloc，版本 2.4.3）
- 文件：`lib/<abi>/libtalloc.so`（原名 libtalloc.so.2.4.3，SONAME 为 libtalloc.so.2，由动态链接器按 SONAME 解析）
- 许可证：LGPL-3.0-or-later
- 上游源码：https://talloc.samba.org/
- 本地修改：仅重命名以符合 jniLibs 约定。

## libandroid-shmem (v0.7)

- 来源：Termux 软件包仓库（pool/main/liba/libandroid-shmem，版本 0.7）
- 文件：`lib/<abi>/libandroid-shmem.so`
- 许可证：MIT
- 上游源码：https://github.com/termux/libandroid-shmem
- 本地修改：无。

## Alpine Linux mini rootfs (v3.20.3)

- 不随 APK 分发；由用户在应用内显式触发下载自 https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/（含 sha256 校验）
- 许可证：各包按自身许可证分发（Alpine 主体为 GPL-2.0 等）
- 仅解压至应用私有目录供 proot 使用。
