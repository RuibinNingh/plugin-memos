# 构建可发布 jar

## 1. 单条命令

```bash
./gradlew clean build --quiet
```

产物位置:

```text
build/libs/plugin-memos-<version>.jar
```

`version` 来自 `gradle.properties`。

## 2. 构建内容

- **Java 后端**:`build/classes/java/main` → 打入 jar。
- **UI**:`ui/dist/` → 根 `processUiResources` 拷到 `build/resources/main/console/` → 打入 jar。
- **plugin.yaml**:`src/main/resources/plugin.yaml` 复制到 `META-INF/halo/plugin.yaml`(jar 根的 `plugin.yaml` 保留给 devtools)。
- **静态资源**:`logo.png` 位于 jar 根。

## 3. 装到 Halo

把 `plugin-memos-<version>.jar` 放到 Halo 的 `plugins/` 目录(可通过 Console → 插件 → 上传,也可手动拷贝):

```bash
cp build/libs/plugin-memos-*.jar /www/halo/app/plugins/
# 然后重启 Halo,或在 Console 启用并 reload
```

## 4. 不发到生产的检查项

发包前**先在开发 Halo 验证一次** `./gradlew clean build` 出来的 jar:

```bash
sg docker -c 'docker cp build/libs/plugin-memos-*.jar halo-for-plugin-development:/root/.halo2/plugins/'
sg docker -c 'docker exec halo-for-plugin-development /root/.halo2/bin/halo restart'  # 或 reload plugin
```

**不要**直接拷到生产(`/www/halo-prod/plugins/`)。

## 5. 版本号

`gradle.properties` 里的 `version` 决定 jar 名。**升级版本号时**:

- 在 `ai/changelog/decisions.md` 写一行。
- 提交时遵守 `feat:` / `fix:` 前缀,不要混 commit。

## 6. 常见失败

| 报错 | 原因 | 修复 |
| --- | --- | --- |
| `processUiResources FAILED` | UI 没 build | 先 `./gradlew :ui:assemble` 或强制 build: `./gradlew build --rerun-tasks` |
| `plugin.yaml not found in META-INF/halo` | 删了 `build.gradle` 里 `processResources` 的 from 块 | 恢复 `from('src/main/resources/plugin.yaml') { into 'META-INF/halo' }` |
| `No matching plugin found` | Halo 版本不满足 `requires: ">=2.23.0"` | 升 Halo |
| `java: error: release 21 not found` | JDK 不是 21 | `sdk use java 21.x` 或配 `org.gradle.java.home` |