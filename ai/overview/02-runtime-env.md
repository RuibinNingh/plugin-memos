# 02 · 运行环境

> 这一节把"在哪跑、怎么起、用什么版本"列清楚,**所有端口/容器名/账号都已硬编码在多个模块里**,改之前先回到这里同步。

## 1. 主机清单

| 角色 | 地址 | 用途 |
| --- | --- | --- |
| 开发 Halo | `http://192.168.0.145:8092/console/` | `haloServer` 任务拉起的容器,挂载插件源码 |
| 生产 Halo | `http://192.168.0.145:8090/`(假定) | **不要碰**,用户明确分离 |
| 本地 memos | `http://127.0.0.1:5230` | Docker 容器 `memos`,**容器内访问用 `http://172.17.0.1:5230`** |
| 开发主题 | `thyuu-xingdu` | bind-mount 进开发 Halo,验证主题端模板 |

## 2. 工具链版本(写死在配置里,改会炸)

| 工具 | 版本 | 出处 |
| --- | --- | --- |
| JDK | 21 | `build.gradle` `toolchain.languageVersion` |
| Halo Plugin Platform | 2.25.0 | `build.gradle` `implementation platform(...)` |
| Halo 插件 devtools | 0.6.2 | `build.gradle` `plugins {}` |
| Node | 20 | `package.json` 隐含;`build.gradle` 注 corepack |
| pnpm | 9.15.9 | `package.json` `packageManager` + `ui/build.gradle` |
| Gradle | 9.4.0(来自 wrapper) | `gradle/wrapper/` |
| memos(对接目标) | 0.29.1 | `CLAUDE.md` 顶层 |

## 3. 关键容器 / 数据

| 名称 | 说明 |
| --- | --- |
| `halo-for-plugin-development` | `haloServer` 自动创建的容器,挂载当前项目根目录 |
| `memos` | 自托管 memos,Docker Compose 路径 `/www/server/panel/data/compose/memos/docker-compose.yaml`,数据 `/www/server/panel/data/compose/memos/data` |

## 4. Docker 权限(常见坑)

- 在老 SSH 会话里直接 `./gradlew haloServer` 会因为 `docker.sock` 权限失败。
- 解决:用 `sg docker -c './gradlew haloServer'`(本项目 CLAUDE.md 已规定)。

## 5. 常用命令速查

```bash
# 起开发 Halo(自动构建并挂载插件)
sg docker -c './gradlew haloServer'

# 全量构建(jar + UI)
./gradlew clean build --quiet

# 只跑 Java 编译,跳过前端(改 Java 时省时间)
./gradlew :classes -x pnpmInstall -x processUiResources

# 只跑 UI dev 模式(改 Vue 时)
cd ui && corepack pnpm@9.15.9 install && pnpm run dev

# 单元测试
./gradlew test
( cd ui && corepack pnpm@9.15.9 run test:unit )

# 看 Console 实时日志(从 `sg docker -c 'docker logs -f halo-for-plugin-development'`)
sg docker -c 'docker logs -f halo-for-plugin-development'
```

## 6. 插件在 Halo 中的身份

| 字段 | 值 |
| --- | --- |
| `metadata.name` | `memos` |
| `spec.requires` | `>=2.23.0` |
| `spec.settingName` | `memos-settings` → `extensions/settings.yaml` |
| `spec.configMapName` | `memos-configMap` |
| API Group | `api.memos.plugin.halo.run / v1alpha1`(代理路由前缀) |
| 内存对象 GVK | `moment.halo.run / v1alpha1 / Moment`(不入 SchemeManager) |

## 7. 与 CLAUDE.md / docs/vendor 的关系

- 根 `CLAUDE.md` 是项目根级说明,描述开发规范、Memos 集成约束(不要硬编码 token 等)。
- `docs/vendor/memos-api/README.md` 是 memos 0.29.1 API 摘要;改 `MemosClient` 字段前必须先翻它;有网时还要对照 `https://usememos.com/docs/api/latest`。
- 本文件 **不重复** Memos 集成约束细节,只放与运行时有关的环境信息。