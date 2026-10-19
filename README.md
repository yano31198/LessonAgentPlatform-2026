# 备课搭子：教案设计多智能体支持系统

`LessonAgentPlatform v1.1.2` 将四个教学智能体整合到统一 Vue Web 工作台：

- F1 智能评价：单份/批量评价、对比、批注、六维评分与等级；
- F2 设计修改：生成或优化教案、教师复核、版本保存与 Word/PDF 导出；
- F3 课堂推演：多角色动态课堂模拟、问题分析与行动建议；
- F4 协同共析：按章节生成建议、人机决策、修订与写回；
- 统一教案空间：以 `lessonId/versionId` 管理原始文件、结构化内容和版本；
- 工作流与公共问题池：支持跨模块上下文传递和结果复用；
- 账号系统：支持注册、登录、用户隔离和管理员功能。

## 目录结构

```text
LessonAgentPlatform-v1.1.2-source/
├─ Lessongen-main/             # Spring Boot、Vue、F2 Engine 与平台桥接层
├─ System-v1.2/                # F1 智能评价 API
├─ SimClass-main/              # F3 课堂推演 API
├─ NoviceTeacher-AI-main/      # F4 协同共析 API
├─ scripts/                    # Windows/Linux 启动、停止与健康检查
├─ deploy/nginx/               # Nginx 配置示例
├─ docs/                       # Windows/Linux 安装说明
├─ .env.example                # 无真实密钥的统一配置模板
└─ README.md
```

## 技术栈与端口

| 组件 | 技术 | 默认地址 |
|---|---|---|
| F4 协同共析 | FastAPI + SQLite | `127.0.0.1:8000` |
| F2 Engine | Python | `127.0.0.1:8001` |
| F1 智能评价 | FastAPI | `127.0.0.1:8002` |
| F3 课堂推演 | FastAPI | `127.0.0.1:8003` |
| 平台后端 | Spring Boot + MySQL | `127.0.0.1:8080` |
| Web 前端 | Vue 3 + Vite | `127.0.0.1:5177`（开发模式） |

## 环境要求

- Python 3.11 或 3.12；
- JDK 17–22（发布验收使用 JDK 22）；
- Node.js 20/22 LTS；
- MySQL 8；
- Windows PowerShell 5.1+，或 Ubuntu 22.04/Linux Bash；
- 正式 PDF 导出需要 LibreOffice。

项目不依赖 Docker。Windows 建议解压到纯英文短路径，例如：

```text
C:\LessonAgentPlatform
```

## 安装

1. 阅读 [`docs/INSTALL_WINDOWS.md`](docs/INSTALL_WINDOWS.md) 或 [`docs/INSTALL_LINUX.md`](docs/INSTALL_LINUX.md)。
2. 复制 `.env.example` 为 `.env`，填写模型密钥、数据库密码和内部令牌。
3. 创建 MySQL 数据库 `lesoongen`。
4. 为 F1、F2 Engine、F3、F4 分别创建 Python 虚拟环境并安装依赖。
5. 执行 F4 数据库迁移和已核验知识库导入。
6. 执行 Maven 测试/构建与 Vue 构建。
7. 使用根目录脚本启动并运行健康检查。

F4 首次安装完成依赖与数据库迁移后，必须执行一次知识库导入：

```powershell
cd NoviceTeacher-AI-main\backend
.\.venv\Scripts\python.exe .\tools\import_verified_knowledge.py
```

Linux 使用 `.venv/bin/python tools/import_verified_knowledge.py`。该命令可安全重复执行。

## 已核验知识库

v1.1.2 新增人工抽查通过的教学资料：

- 经典教案 10 份，整理为 30 个检索片段；
- 小学数学、英语学科教学法论文 30 篇，整理为 226 个检索片段；
- F1 在生成细化建议时检索经典教案和论文依据；
- F4 通过现有 `KnowledgeItem` 和 `MetadataRetriever` 检索，仅使用 `verified` 条目；
- 所有条目保留来源定位和内容哈希，不把摘要冒充原文引用。

具体说明见 [`docs/F1-F4知识库正式接入说明.md`](docs/F1-F4知识库正式接入说明.md)。

## 快速操作

Windows：

```powershell
powershell -NoProfile -File .\scripts\start-all.ps1
powershell -NoProfile -File .\scripts\health-check.ps1
powershell -NoProfile -File .\scripts\stop-all.ps1
```

Linux：

```bash
chmod +x scripts/*.sh Lessongen-main/web/mvnw
./scripts/start-all.sh
./scripts/health-check.sh
./scripts/stop-all.sh
```

服务器由 Nginx 托管前端时，仅启动后端：

```bash
./scripts/start-all.sh --skip-frontend
```

## 依赖清单

- F1：`System-v1.2/requirements.txt`
- F2 Engine：`Lessongen-main/paper4_pipeline/pyproject.toml` 及 `requirements*.txt`
- F3：`SimClass-main/requirements.txt`
- F4：`NoviceTeacher-AI-main/backend/requirements.txt`、`requirements.lock.txt`
- Vue：`Lessongen-main/web/frontend/package.json`、`package-lock.json`
- Spring Boot：`Lessongen-main/web/pom.xml`、Maven Wrapper

## 数据位置

| 数据 | 默认位置 |
|---|---|
| 平台教案、版本、任务、公共问题与用户 | MySQL `lesoongen` |
| 原始 DOCX 与平台制品 | `Lessongen-main/web/var/storage` |
| F2 Engine 状态与制品 | `Lessongen-main/paper4_pipeline/var` |
| F3 Session/Event/Result | `SimClass-main/runtime/f3_sessions` |
| F4 会话数据库 | `NoviceTeacher-AI-main/backend/pedago_loop.db` |
| 统一日志和 PID | `.runtime/` |

生产环境应定期备份 MySQL、上传文件目录、F3 runtime 和 F4 SQLite。

## 发布验收

```powershell
cd Lessongen-main\web
.\mvnw.cmd test
.\mvnw.cmd clean package

cd frontend
npm ci
npm run build
```

还应验证四个 Python 模块可导入、五个后端健康接口返回 `200`，并用脱敏 DOCX 完成导入、评价、优化、推演、共析和版本下载。

## 安全说明

- 真实密钥仅写入本地 `.env`，不得提交 Git；
- 不要公开 `8000–8003`、`8080`、`3306` 等内部端口；
- 正式公网部署建议使用 Nginx、HTTPS、防火墙与定期备份；
- 教案和日志应脱敏，模型输出必须由教师复核；
- 本源码包不包含真实 `.env`、数据库、用户教案、虚拟环境、`node_modules` 或运行日志。

## 已知限制

- 正式导入以 DOCX 为主，PDF OCR 尚未纳入本版本正式入口；
- REAL 模式依赖外部模型服务、网络和调用额度；
- 极端复杂或异常 DOCX 仍可能需要人工核对章节和表格解析；
- `MAX_CONTEXT_TOKENS` 是本地预算，不能突破模型服务自身上下文限制。

版本详情见 [`RELEASE_NOTES.md`](RELEASE_NOTES.md)。
