# Smart Hex Preview — 设计规格文档

> **版本：** v1.0.0  
> **创建日期：** 2026-07-04  
> **状态：** 已实现 ✅  
> **语言：** Kotlin 1.9.24 + IntelliJ Platform SDK  
> **目标 IDE：** PyCharm 2022.1+（兼容所有 JetBrains IDE）  

---

## 1. 概述

**Smart Hex Preview** 是一个 JetBrains IDE 插件，在编辑器中自动识别被特定包裹符包围的六位十六进制颜色码 `#XXXXXX`，并在行内和 gutter 区域渲染对应颜色的色块。点击 gutter 色块可弹出系统颜色选择器，选中颜色后自动替换 hex 值（保留包裹符）。

---

## 2. 颜色识别规则

### 2.1 支持的包裹模式（默认）

插件内置 4 种默认正则模式，匹配 `# + 6 位十六进制`：

| 序号 | 包裹符 | 正则 | 示例 |
|------|--------|------|------|
| 1 | 方括号 `[]` | `\\[(#[0-9A-Fa-f]{6})\\]` | `[#2563EB]` |
| 2 | 圆括号 `()` | `\\((#[0-9A-Fa-f]{6})\\)` | `(#10B981)` |
| 3 | 单引号 `''` | `'(#[0-9A-Fa-f]{6})'` | `'#EF4444'` |
| 4 | 双引号 `""` | `"(#[0-9A-Fa-f]{6})"` | `"#111827"` |

### 2.2 不匹配的模式

- 3 位 hex（`#ABC`）
- 8 位 hex（`#AABBCCDD`）
- 无包裹符的 hex（除非用户自定义添加此模式）

### 2.3 自定义正则

- 用户可在 **Settings | Tools | Smart Hex Preview** 中添加/删除/重置自定义正则模式
- 每个模式**应包含一个捕获组**包裹 `#XXXXXX`，例如 `color: (#[0-9A-Fa-f]{6})`
- 若模式无捕获组，系统会自动从完整匹配中提取 `#XXXXXX`
- 添加和保存时会**验证正则合法性**，无效正则弹错拒绝

---

## 3. 功能规格

### 3.1 行内色块渲染

| 属性 | 值 |
|------|-----|
| 实现方式 | `MarkupModel.addRangeHighlighter()` |
| 层级 | `HighlighterLayer.SELECTION - 1`（选中文字上方） |
| 背景色 | 匹配的 hex 颜色 |
| 前景色 | 根据亮度自动切换：亮度 > 150 用黑色，否则白色 |
| 亮度公式 | `0.299*R + 0.587*G + 0.114*B` |
| 重叠处理 | 按起始位置排序，重叠时保留第一个匹配 |

### 3.2 Gutter 色块指示器

| 属性 | 值 |
|------|-----|
| 大小 | 12×12 px |
| 圆角 | 3px 圆角矩形 |
| 边框 | 浅灰 `#BBBBBB`（亮主题）/ `#555555`（暗主题） |
| 工具提示 | "Click to change color" |
| 悬停反馈 | 无特殊处理 |

### 3.3 点击调色板

**交互流程：**

```
点击 gutter 色块
  → RangeHighlighter 获取 start/end offset
  → 读取当前文本 → parseColor()
  → ColorChooser.chooseColor() 弹出系统颜色选择器
  → 用户选择新颜色
  → WriteCommandAction.run() 中执行 document.replaceString()
  → DocumentListener 触发 → 250ms 防抖 → 刷新高亮
```

**安全校验：**
- offset 边界检查（`start < 0 || end > document.textLength`）
- 替换前再次验证文本格式是否匹配 `#[0-9A-Fa-f]{6}`
- 所有操作在 `WriteCommandAction` 中执行，支持 Undo

### 3.4 防抖与性能

- 每次文档变更通过 `DocumentListener` 触发
- 使用 `Alarm(Alarm.ThreadToUse.SWING_THREAD)` 设置 250ms 延迟
- 连续输入时仅最后一次触发刷新
- 刷新在 `ReadAction` 中执行（非 EDT 阻塞）

---

## 4. 架构设计

### 4.1 项目结构

```
smart-hex-preview/
├── build.gradle.kts              # Gradle 构建配置
├── settings.gradle.kts            # Gradle 项目设置
├── gradle.properties              # Gradle 属性
├── gradle/wrapper/                # Gradle Wrapper
└── src/main/
    ├── kotlin/smarthex/
    │   ├── HexHighlightingService.kt    # 核心：编辑器高亮管理
    │   ├── HexGutterProvider.kt         # Gutter 色块渲染
    │   ├── HexColorPickerHandler.kt     # 颜色选择器交互
    │   ├── HexPatternConfig.kt          # 持久化配置
    │   ├── HexStartupActivity.kt        # 启动初始化
    │   └── SettingsPanel.kt             # 设置页面
    └── resources/META-INF/
        └── plugin.xml                   # 插件描述符
```

### 4.2 组件职责

#### `HexHighlightingService` — 核心服务
- **类型：** 项目级 Service（`projectService`）
- **职责：**
  - 通过 `EditorFactoryListener` 自动 attach/detach 编辑器
  - 管理每个编辑器的 `EditorState`（highlighters + Alarm）
  - 正则匹配 → 创建 `RangeHighlighter` + `GutterIconRenderer`
  - 文档变更时 250ms 防抖刷新

#### `HexGutterProvider` — Gutter 渲染
- **类型：** `object`（工厂）
- **职责：**
  - 创建 `HexGutterRenderer`（`GutterIconRenderer` 子类）
  - 创建 12×12 `HexColorIcon`
  - 绑定点击 Action → 委托给 `HexColorPickerHandler`

#### `HexColorPickerHandler` — 颜色修改
- **类型：** `object`
- **职责：**
  - 调用 `ColorChooser.chooseColor()` 打开系统调色板
  - 在 `WriteCommandAction` 中安全替换文本

#### `HexPatternConfig` — 配置持久化
- **类型：** 应用级 Service（`applicationService`）
- **职责：**
  - 使用 `PersistentStateComponent` 保存/加载正则列表
  - 版本号递增机制（`myVersion`），供 `HexHighlightingService` 检测变更
  - 4 个默认正则

#### `SettingsPanel` — 设置 UI
- **类型：** `Configurable`
- **职责：**
  - 位于 **Settings | Tools | Smart Hex Preview**
  - 添加/删除/恢复默认正则模式
  - 添加和保存时正则语法验证
  - Apply 后广播 `refreshAll()` 到所有打开的项目

#### `HexStartupActivity` — 启动初始化
- **类型：** `postStartupActivity`
- **职责：**
  - IDE 启动后调用 `attachExistingEditors()`，确保已打开的文件也被高亮

### 4.3 插件扩展点

```xml
<extensions defaultExtensionNs="com.intellij">
    <applicationService serviceImplementation="smarthex.HexPatternConfig"/>
    <projectService serviceImplementation="smarthex.HexHighlightingService"/>
    <postStartupActivity implementation="smarthex.HexStartupActivity"/>
    <applicationConfigurable parentId="tools"
                             id="smarthex.settings"
                             instance="smarthex.SettingsPanel"
                             displayName="Smart Hex Preview"/>
</extensions>
```

### 4.4 核心数据流

```
用户输入 / 打开文件
  → DocumentListener.documentChanged()
  → Alarm 250ms 防抖
  → HexHighlightingService.refresh()
  → ReadAction.run() { doRefresh() }
      → compiledPatterns() 获取正则列表（带缓存）
      → pattern.matcher(text) 遍历匹配
      → 提取 hex → parseColor() → 计算 luminance
      → markup.addRangeHighlighter() 创建行内高亮
      → highlighter.setGutterIconRenderer() 添加色块

点击 gutter 色块
  → HexGutterRenderer.getClickAction()
  → HexColorPickerHandler.chooseAndReplace()
  → ColorChooser.chooseColor() 弹出系统选择器
  → WriteCommandAction.run() { document.replaceString() }
  → 文档变更 → 自动触发刷新（回到顶部）
```

---

## 5. 构建与部署

### 5.1 构建配置

| 配置项 | 值 |
|--------|-----|
| Gradle 版本 | 8.9 |
| gradle-intellij-plugin | 1.17.4 |
| Kotlin | 1.9.24 (jvmTarget = 11) |
| JDK 兼容 | Java 11 |
| 目标平台 | PyCharm Community (PC) 2022.1 |
| 版本范围 | `since-build="221"`（无上限） |
| `instrumentCode` | 已禁用（无 GUI Designer，且绕开 Windows+Microsoft JDK 的 Packages does not exist 问题） |

### 5.2 构建命令

```bash
cd smart-hex-preview
./gradlew buildPlugin
```

### 5.3 构建产物

```
build/distributions/smart-hex-preview-1.0.0.zip
```

在 IDE 中通过 **Settings | Plugins | Install Plugin from Disk...** 安装。

---

## 6. safety & 边界情况处理

### 6.1 正则安全性
- 添加/保存自定义正则时通过 `Pattern.compile()` 验证语法
- 无效正则弹出错误对话框，拒绝保存
- 运行时编译失败的 pattern 静默跳过（`runCatching`），不影响其他 pattern

### 6.2 高亮安全
- 高亮范围重叠时跳过后续匹配（`if (match.start < lastEnd) continue`）
- 清理 highlighters 时检查 `isValid` 并使用 `runCatching` 包裹
- Editor 释放时自动 detach 并清理所有高亮

### 6.3 颜色替换安全
- 替换前验证 offset 边界
- 替换前再次验证文本格式
- 在 `WriteCommandAction` 内执行，支持 Undo

### 6.4 性能安全
- 250ms 防抖避免频繁刷新
- `ReadAction` 执行刷新（非 EDT 阻塞）
- 正则列表缓存带版本号，仅变更时重新编译

---

## 7. 已知限制

| 限制 | 说明 | 后续方向 |
|------|------|----------|
| 仅 6 位 hex | 不支持 3 位（`#ABC`）或 8 位（`#AABBCCDD`） | 可添加选项 |
| 无透明度 | 不支持 `rgba` 或带透明度的 hex | 需扩展颜色解析 |
| 无 PSI 感知 | 纯文本匹配，无法区分字符串字面量 vs 注释 | 对注释中的假 hex 不适用 |
| 单一颜色替换 | 一次只能改一个颜色 | N/A |
| 不支持 HSL/HSV | 仅 hex 格式 | 可添加 |

---

## 8. 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| 1.0.0 | 2026-07-04 | 初始版本：识别、高亮、点击修改、自定义正则 |

## 9. 附录：纯函数参考

以下函数不依赖 IntelliJ SDK，适合单元测试：

```kotlin
// HexHighlightingService
fun parseColor(hex: String): Color?  // "#FF8800" → Color(255,136,0)
fun luminance(c: Color): Int          // Color.RED → 76

// HexColorPickerHandler
fun toHex(c: Color): String           // Color(0x10,0xB9,0x81) → "#10B981"
```