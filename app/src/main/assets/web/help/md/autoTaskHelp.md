## 定时任务帮助

### Cron 表达式
格式：`分 时 日 月 周`

常用规则：
- `*/5 * * * *` 每 5 分钟
- `0 9 * * *` 每天 09:00
- `0 9 * * 1` 每周一 09:00
- `0 0 1 * *` 每月 1 日 00:00

符号说明：
- `*` 任意值
- `,` 列表
- `-` 范围
- `/` 步长

### 返回协议
脚本返回 JSON（对象或字符串均可），示例：

```json
{
  "actions": [
    {
      "type": "refreshToc",
      "bookUrl": "书架里的 bookUrl",
      "notify": {
        "enable": true,
        "minCount": 5,
        "title": "《{book}》更新提醒",
        "content": "新增 {newCount} 章，最新：{chapter}"
      },
      "cache": {
        "enable": true
      }
    },
    {
      "type": "notify",
      "title": "签到成功",
      "content": "账号A 已签到，{time}",
      "level": "high",
      "id": 1
    }
  ]
}
```

#### 支持动作
- `type: refreshToc`
  - `bookUrl`（必填）
  - `notify`（对象）
    - `enable` `minCount` `title` `content`
  - `cache`（对象）
    - `enable`
- `type: notify`
  - `title` `content`
  - `level`（`high/error/fail/failed/low`）
  - `id`（可选）

#### 占位符
- `refreshToc` 通知：`{book}` `{author}` `{newCount}` `{chapter}` `{time}`
- `notify` 通知：`{task}` `{time}`
