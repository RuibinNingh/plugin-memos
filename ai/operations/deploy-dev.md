# 开发环境启动

## 1. 前置条件

- JDK 21(在 `PATH`)。
- Docker(`haloServer` 任务会创建容器)。
- 当前用户 `ruibinningh` 必须能进 `docker` 组,**否则**用 `sg docker -c '...'`(本项目 CLAUDE.md 已强制)。
- Linux,Mem 8 GB+。

## 2. 启动步骤

```bash
cd /home/ruibinningh/projects/halo-plugins/plugin-memos
sg docker -c './gradlew haloServer'
```

执行后:

1. 首次会拉 `run.halo/app:2.25.0`(来自 `halo { version = '2.25.0' }`)等基础镜像。
2. 在容器中启动 Halo,加载 `fixedPluginPath` 指向的本仓库(`build.gradle` 里 devtools 自动配置)。
3. 控制台地址:`http://192.168.0.145:8092/console/`,默认账号 `admin` / 密码由 `haloServer` 任务首次启动时控制台输出。

**改 Java 代码后**:devtools 检测到 `build/classes/java/main` 变更,自动 reload 插件,**不需要重启容器**。

**改 UI 代码后**:`ui/dist` 重新生成 → 根 `processUiResources` 拷到 `build/resources/main/console` → Halo 重新加载 console 静态资源。Console 浏览器需 hard refresh(`Ctrl+Shift+R`)。

## 3. 改插件设置

在 Halo Console → 插件 → plugin-memos → 设置:

| 字段 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `baseUrl` | 是 | `http://127.0.0.1:5230` | 见下文 |
| `accessToken` | 否 | 空 | memos 实例强制鉴权时填 |
| `owner` | 否 | `admin` | 头像/昵称用的 Halo 用户 |
| `title` | 否 | `瞬间` | 主题页标题 |
| `pageSize` | 否 | 10 | 主题页每页条数 |

详见 [settings-form.md](./settings-form.md)。

## 4. 验证三件事

启动后必须确认 3 件事都通,才算"开发环境就绪":

### 4.1 插件本身已加载

Halo Console → 插件 → 看到 "Memos 反向代理",状态为 `Running`。

### 4.2 Console 页面能打开

浏览器访问 `http://192.168.0.145:8092/console/memos`,能看到 memos 列表(或空列表 + 错误提示)。

### 4.3 主题端 `/moments` 能渲染

进入 `thyuu-xingdu` 主题所在的博客,访问 `http://192.168.0.145:8092/memos`:

- 看到瞬间列表
- 点击某条进 `/moments/memos-xxx` 详情页
- 评论系统对该条 moment 可挂载

## 5. 常见问题

| 现象 | 排查 |
| --- | --- |
| `permission denied` 访问 `/var/run/docker.sock` | 用 `sg docker -c '...'` |
| `haloServer` 卡在 "Pulling fs layer" | 网络问题,重试或换镜像源 |
| Console 看不到 "Memos" 菜单 | UI 没构建,跑 `./gradlew :ui:assemble` |
| `/moments` 404 | 主题没绑到 Halo,或路径没被 `MomentRouter` 拦截(查 `MomentRouterTest`) |
| `/moments` 渲染空白 | 主题模板没消费 `${moments.listResult}`,看 thyuu-xingdu 模板 |

## 6. 关闭 / 重启

```bash
# 停 Halo 容器
sg docker -c 'docker stop halo-for-plugin-development'

# 删容器(下次 haloServer 会重建)
sg docker -c 'docker rm halo-for-plugin-development'

# 强制 rebuild 镜像(谨慎)
sg docker -c 'docker image prune -a'
```

`haloServer` 任务**不会自动重启**;Ctrl+C 后容器还在跑,要重启请 `docker start halo-for-plugin-development`。