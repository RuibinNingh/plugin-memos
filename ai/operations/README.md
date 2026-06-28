# 运维 / 部署 / 配置

> 本目录回答"我要把 plugin-memos 跑起来/装上去/改配置,该怎么操作"。

## 文件清单

| 文件 | 场景 |
| --- | --- |
| [deploy-dev.md](./deploy-dev.md) | 启动开发 Halo 容器并热加载插件 |
| [deploy-build.md](./deploy-build.md) | 打成可发布 jar |
| [settings-form.md](./settings-form.md) | 插件设置项逐字段含义 |
| [memos-integration.md](./memos-integration.md) | 与本地 memos 容器的对接细节 |

## 速查

| 任务 | 命令 |
| --- | --- |
| 起开发实例 | `sg docker -c './gradlew haloServer'` |
| 全量构建 | `./gradlew clean build --quiet` |
| 只编 Java | `./gradlew :classes -x pnpmInstall -x processUiResources` |
| 只跑前端 dev | `cd ui && corepack pnpm@9.15.9 install && pnpm run dev` |
| 看 Halo 日志 | `sg docker -c 'docker logs -f halo-for-plugin-development'` |
| 看 memos 日志 | `sg docker -c 'docker logs -f memos'` |

## 容器与端口对照

| 角色 | 容器 | 端口 | 备注 |
| --- | --- | --- | --- |
| Halo(开发) | `halo-for-plugin-development` | 8092 | `haloServer` 自动建;**生产在 8090** |
| memos | `memos` | 5230 | docker-compose 在 `/www/server/panel/data/compose/memos` |
| Docker bridge 网关 | — | — | 容器内访问宿主用 `172.17.0.1` |

**注意**:所有"在 Halo 容器内访问宿主 memos"的场景,`baseUrl` 都填 `http://172.17.0.1:5230`,**不要**填 `127.0.0.1`(那是 Halo 容器自己)。