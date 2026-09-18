# 前端可视化模块(frontend)

电力场站设备巡检数据集成平台的 Web 前端, 对应《03-架构设计文档》⑦ 可视化前端模块。

## 技术栈

- Vue 3(`<script setup>`) + Vite
- Element Plus 2(UI 组件库) + @element-plus/icons-vue
- Vue Router 4(路由/守卫) + Pinia(登录态)
- ECharts 6(统计图表) + axios(HTTP)
- JavaScript(不使用 TypeScript, 与脚手架一致)

## 目录结构

```
src/
├── api/
│   ├── request.js      # axios 实例: JWT 注入、信封解包、401 跳登录、429 提示
│   └── index.js        # 19 个后端接口的封装(与《04-接口文档》v1.2 一一对应)
├── constants/dict.js   # 枚举字典(设备/任务/告警类型等, 值与接口文档 §1.5 一致)
├── utils/format.js     # 时间/文件大小格式化
├── stores/auth.js      # 登录态(token 持久化 localStorage)
├── router/index.js     # 路由表 + 登录守卫
├── layout/MainLayout.vue  # 侧边栏 + 顶栏(含后端实例轮询展示)
├── components/
│   ├── EChart.vue      # ECharts 封装(自适应尺寸)
│   ├── DeviceMap.vue   # 设备 GIS 平面图(SVG, 无需外网地图)
│   └── FileImage.vue   # 带 JWT 的图片预览(blob)
└── views/
    ├── Login.vue       # 登录(内置账号 admin/admin123)
    ├── Dashboard.vue   # 状态大屏: 指标卡 + 设备地图 + 4 个统计图(轮询)
    ├── Devices.vue     # 设备台账: 增删改查 + 详情(8s 轮询)
    ├── Tasks.vue       # 巡检任务: 创建下发(Kafka 指令) + 执行进度
    ├── Alarms.vue      # 告警中心: 分级筛选 + 处置
    ├── Events.vue      # 巡检事件检索(设备/时间/类型/geo 组合)
    ├── Reports.vue     # 巡检报告: 生成 + 打印
    └── Files.vue       # 影像文件: 上传 HDFS + 列表 + 预览下载
```

## 开发运行(前端组日常)

前置: Node.js 18+(本机已验证 Node 24)。

```bash
cd frontend
npm install        # 首次拉依赖
npm run dev        # 启动开发服务器 http://localhost:5173
```

开发服务器把 `/api` 代理到 `http://localhost:80`(Nginx 统一入口, 见
`vite.config.js` 与接口文档附录 C), 因此联调前请先按 `deploy/README.md`
用 `docker compose up -d --build` 起好后端环境。

> Windows PowerShell 若提示"禁止运行脚本 npm.ps1", 改用 `npm.cmd install` / `npm.cmd run dev`。

## 构建与部署

```bash
npm run build
```

构建产物直接输出到 `../deploy/nginx/html/`(vite.config.js 中配置 `build.outDir`),
Nginx 容器以只读卷挂载该目录(docker-compose.yml), 因此:

1. 先 `npm run build`;
2. 再 `docker compose up -d`(或 `docker compose restart nginx`);
3. 浏览器访问 http://localhost 即为最新前端。

## 约定与注意事项

- 一切接口契约以 `backend/docs/04-接口文档.md` v1.2 为准; 改接口先改文档。
- 业务状态只看响应信封的 `code`(0 成功), 不看 HTTP 状态码。
- `/api/search/**` 有限流 10r/s: 页面不做自动重试, 触发 429 只提示"稍后再试"。
- 本版本无 WebSocket: 大屏设备列表 5s 轮询、台账 8s、告警 10s(接口文档附录 C)。
- 文件下载/图片预览需要 JWT, 不能直接用 `<img src>`, 见 `FileImage.vue` 的 blob 方案。
