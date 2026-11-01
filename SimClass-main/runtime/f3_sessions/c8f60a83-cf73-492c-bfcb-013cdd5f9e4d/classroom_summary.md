# F3 课堂模拟简报

- 运行状态：**INTERRUPTED**
- 开始时间：2026-11-01T03:55:41.720Z
- 结束时间：2026-11-01T03:56:06.517Z
- lessonId：`01M55AP41PHCNWHNJ56Z8BWGXP`
- versionId：`01M57WZ3GS85KF2H8PSV8WYZA0`
- F3 sessionId：`c8f60a83-cf73-492c-bfcb-013cdd5f9e4d`
- 模型模式：**REAL**
- 模型 Provider：`openai-compatible`
- 模型名称：`deepseek-flash`
- 课堂材料数：6
- 课堂事件数：1
- 完成事件数：1
- 停止原因：`MANUAL_END`

## 课堂材料

- 1. 教学环节 1 (materialId=`r-67c41eca-de97-4b68-ad5e-35b5dc5aac33`, sourceSectionId=`b-d638675d-9f58-4305-8b32-15f5df3821ed`)
- 2. 教学环节 2 (materialId=`r-7433bc10-ab64-4196-97c0-f31017e91fd8`, sourceSectionId=`b-d638675d-9f58-4305-8b32-15f5df3821ed`)
- 3. Model a mini dialogue (materialId=`r-c0ca0172-8c7a-476e-a7e0-7617870da8b0`, sourceSectionId=`b-d638675d-9f58-4305-8b32-15f5df3821ed`)
- 4. 教学环节 4 (materialId=`r-fa05d2ab-1a05-46d2-a58e-76427cfc1f89`, sourceSectionId=`b-d638675d-9f58-4305-8b32-15f5df3821ed`)
- 5. 教学环节 5 (materialId=`r-38fdb8a6-1b2d-484d-bb2d-28cf49e4dd6c`, sourceSectionId=`b-d638675d-9f58-4305-8b32-15f5df3821ed`)
- 6. 教学环节 6 (materialId=`r-36e858f6-8e63-4737-ad8e-e878c29fd092`, sourceSectionId=`b-d638675d-9f58-4305-8b32-15f5df3821ed`)

## 各轮记录

### 第 1 轮

- 触发：课堂启动，开始当前教学材料。
- 对应材料：教学环节 1 (materialId=`r-67c41eca-de97-4b68-ad5e-35b5dc5aac33`, sourceSectionId=`b-d638675d-9f58-4305-8b32-15f5df3821ed`)
- 发言角色：教师 (`teacher`)
- 动作类型：`teach`
- Manager 决策：`teacher / teach`；原因：课堂刚刚开始，教学环节 1 尚未讲授，由教师先进行问候、展示 4 张图片并引导 'Can you sing? Can you dance?' 的问答与动作回应。
- 回复：

Good morning, class! Nice to see you. Look at the screen—here are four quick pictures. Picture 1: singing. Picture 2: dancing. Picture 3: drawing. Picture 4: kung fu.

Now listen and answer. “Can you sing?” If yes, say “Yes, I can” and show me a singing action. If no, say “No, I can’t” and shake your head. Let’s try: Can you sing?

Next: “Can you dance?” Yes or no—plus a dance action. You may answer in English first; if you need help, use the sentence card. I’ll ask a few students to answer, and we’ll all do the actions together. Ready? Let’s begin with “Can you sing?”

- 本轮结束状态：`COMPLETED`
- 真实系统执行耗时：6935 ms

## 课堂统计

- 事件数：1
- 系统真实执行耗时（秒）：6.935
- 估算教学时长：未估算（不得用模型请求耗时替代）
- 角色覆盖：teacher
- 材料覆盖：教学环节 1

### 角色计数

- 教师 (`teacher`): 1

### Function 计数

- `teach`: 1

## REAL / MOCK 边界

- F3 模型模式：`REAL`
- Provider：`openai-compatible`
- 模型：`deepseek-flash`

> 本简报中的课堂轮次直接来自 EventStore 的真实事件；wallClockDurationMs 仅表示系统/模型执行耗时，不代表真实课堂教学时长。
