## 五子棋在线对战启动说明

### 环境要求

- **JDK**：1.8
- **Maven**：3.x
- **MySQL**：8.x（或兼容版本）

### 初始化数据库（第一次跑必做）

执行建表脚本：
- `db/schema.sql`

> 脚本已包含创建数据库和使用数据库的语句，无需手动创建数据库

### 配置数据库连接

默认 profile 是 **dev**（见 `application.yml` 的 `spring.profiles.default=dev`），数据库连接配置在：

- `src/main/resources/application-dev.yml`

并且支持用环境变量覆盖（不设则用默认值）：

- `DB_HOST`（默认 `localhost`）
- `DB_PORT`（默认 `3306`）
- `DB_NAME`（默认 `gobang_db`）
- `DB_USERNAME`（默认 `root`）
- `DB_PASSWORD`（默认 `1234`）

### 启动项目

在项目根目录执行：

```powershell
mvn spring-boot:run
```

启动成功后默认端口是 **8080**（见 `application.yml`），访问页面：

- `http://localhost:8080/login.html`（登录页面）
- `http://localhost:8080/register.html`（注册页面）
- `http://localhost:8080/hall.html`（匹配大厅，需先登录）
- `http://localhost:8080/game.html`（游戏页面，需先登录并匹配成功）

### 常见问题

- **端口被占用**：改用其他端口启动，例如：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

- **数据库连不上/库不存在**：确认 MySQL 启动、库已创建、账号密码与环境变量/`application.yml` 一致。