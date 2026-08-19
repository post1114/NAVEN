# Naven-Modern

一个基于 Minecraft Forge 1.20.1 的现代化模组客户端，提供丰富的游戏增强功能。

## 🚀 特性

### 核心功能
- **模块化架构**: 可扩展的模块系统，支持动态加载和管理
- **事件驱动系统**: 高效的事件管理机制
- **命令系统**: 完整的命令行界面支持
- **配置管理**: 灵活的配置文件系统，支持热重载
- **通知系统**: 实时消息通知

### 主要模块

#### 🎯 战斗模块 (Combat)
- **AimAssist**: 自动瞄准辅助，支持多种目标优先级
- **Aura**: 自动攻击功能
- **AutoClicker**: 自动点击器
- **AttackCrystal**: 末影水晶攻击优化
- **AntiBots**: 反机器人检测

#### 🎨 渲染模块 (Render)
- **HUD**: 自定义 HUD 界面
- **ClickGUI**: 点击式图形用户界面
- **MotionBlur**: 动态模糊效果
- **Projectile**: 弹道轨迹渲染
- **ChestESP**: 箱子透视
- **AntiBlindness**: 反失明效果
- **AntiNausea**: 反恶心效果

#### 🏃 移动模块 (Movement)
- **Blink**: 瞬移功能，支持数据包暂停
- **AutoMLG**: 自动 MLG 操作
- **FastWeb**: 快速破网
- **Jesus**: 水上行走
- **NoSlow**: 移除减速效果

#### 🔧 杂项模块 (Misc)
- **InventoryManager**: 智能背包管理
- **ChestStealer**: 自动偷取箱子物品
- **AutoTools**: 自动工具切换
- **AntiFireball**: 反火球攻击
- **Helper**: 各种辅助功能

## 📋 系统要求

- **Minecraft**: 1.20.1
- **Forge**: 47.3.0+
- **Java**: 17+
- **内存**: 建议 4GB+

## 🛠️ 安装指南

### 前置要求
1. 安装 Java 17 或更高版本
2. 安装 Minecraft Forge 1.20.1 (版本 47.3.0 或更高)

### 构建步骤
```bash
# 克隆项目
git clone https://github.com/jiuxianqwq/Naven-Modern.git
cd Naven-Modern

# 构建项目
./gradlew build
```

构建完成后，模组文件将位于 `build/libs/` 目录中。

### 安装模组
1. 将生成的 `.jar` 文件复制到 Minecraft 的 `mods` 文件夹
2. 启动游戏即可使用

## ⚙️ 配置

### 基本设置
模组支持以下配置文件：
- `settings.json` - 主要配置文件
- `binds.json` - 按键绑定配置
- `friends.json` - 好友列表

### 验证系统
客户端内置用户名/密码验证系统，Fluent Design 风格登录界面，用于防止未授权使用。

#### 配置方式
在 `Naven.java` 中修改以下常量：
```java
public static final boolean VERIFY_ENABLED = false;   // 是否开启验证（默认关闭）
public static final String VERIFY_SERVER = "http://localhost:8080/verify"; // 验证服务器地址
```

#### 工作流程
1. 开启验证后 (`VERIFY_ENABLED = true`)，客户端启动时显示 Fluent Design 风格登录界面
2. 用户输入用户名和密码，客户端发送至验证服务器
3. 服务器校验凭据，验证成功返回 Token
4. 客户端收到 Token 后完成验证，自动进入游戏
5. 验证失败显示错误信息，关闭客户端时自动退出

#### 验证服务器
仓库附带 `verify_server.py`，提供 GUI 管理界面 + REST API（无额外依赖）。

```bash
python verify_server.py --port 8080          # 带 GUI 启动
python verify_server.py --port 8080 --no-gui # 无头模式
```

##### GUI 界面
Fluent Design 风格深色主题，包含：
- **用户管理**：添加、编辑、删除用户，设置有效天数，搜索过滤
- **API 文档**：内置接口说明

用户数据存储在 `users.json` 中。

##### REST API
| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/verify` | 验证登录 | `{"username","password"}` | Token 字符串 |
| GET | `/api/users` | 获取用户列表 | — | `[{username,token,created,expires,active}]` |
| POST | `/api/users` | 创建用户 | `{"username","password","days":30}` | `{token,expires}` |
| PUT | `/api/users` | 更新用户 | `{"username","password?","days?"}` | `{token,expires}` |
| DELETE | `/api/users` | 删除用户 | `{"username"}` | `{ok:true}` |
| GET | `/health` | 健康检查 | — | `{status:"ok"}` |

##### 防破解说明
- 用户名/密码通过服务端验证，客户端不存储凭据
- 验证成功后返回 Token，后续操作可携带 Token
- 密码通过 SHA-256 哈希存储，不暴露明文
- 用户有效期可配置，到期自动失效
- 关闭验证 (`VERIFY_ENABLED = false`) 时客户端直接进入游戏

### 快捷键
- **ClickGUI**: `右Shift` (默认)
- **模块切换**: 可在 ClickGUI 中自定义绑定

## 🎮 使用说明

### ClickGUI 使用
1. 按下 `右Shift` 打开 ClickGUI
2. 点击不同分类查看对应模块
3. 点击模块名称来启用/禁用功能
4. 点击设置图标配置模块参数

### 命令系统
模组内置命令前缀为 `.`，主要命令包括：
- `.bind <模块> <按键>` - 绑定快捷键
- `.config <操作>` - 配置管理
- `.language <语言>` - 切换语言

## 🔧 开发

### 项目结构
```
src/main/java/com/heypixel/heypixelmod/obsoverlay/
├── commands/          # 命令系统
├── events/           # 事件系统
├── files/            # 文件管理
├── modules/          # 功能模块
│   ├── impl/
│   │   ├── combat/   # 战斗模块
│   │   ├── misc/     # 杂项模块
│   │   ├── move/     # 移动模块
│   │   └── render/   # 渲染模块
├── ui/               # 用户界面
├── utils/            # 工具类
└── values/           # 配置值系统
```

### 添加新模块
1. 在对应分类目录下创建新的模块类
2. 继承 `Module` 类并添加 `@ModuleInfo` 注解
3. 实现所需的事件处理方法
4. 在 `ModuleManager` 中注册新模块

### API 使用
```java
// 创建新模块示例
@ModuleInfo(
    name = "MyModule",
    description = "模块描述",
    category = Category.MISC
)
public class MyModule extends Module {
    @EventTarget
    public void onUpdate(EventUpdate event) {
        // 实现功能逻辑
    }
}
```

## 📝 版本信息

- **当前版本**: Modern-Beta
- **构建版本**: 1337
- **Minecraft 版本**: 1.20.1
- **Forge 版本**: 47.4.6

## ⚖️ 许可证

本项目采用 All Rights Reserved 许可证。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来改进项目。

## ⚠️ 免责声明

本模组仅供学习和研究目的使用。使用者需自行承担使用风险，开发者不对任何因使用本模组导致的问题负责。请遵守游戏服务器的规则和条款。

## 📞 联系方式

- 共享者: 玖弦下划线巴卡
- 项目仓库: [jiuxianqwq/Naven-Modern](https://github.com/jiuxianqwq/Naven-Modern)

---

*Naven-Modern - 让你的 Minecraft 体验更加现代化*
