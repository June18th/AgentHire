# JobClaw 上传文件存储说明

公开文件能力统一通过后端 `FileStorageService` 处理，当前已接入用户头像和 `/oc/img/**` 图片代理读取。

## 默认本地存储

轻量 Docker 组合默认使用本地持久化存储：

```text
./workspace:/app/workspace
JOBCLAW_IMG_STORAGE_TYPE=local
JOBCLAW_IMG_ABS_TMP_PATH=/app/workspace/storage/
JOBCLAW_IMG_WEB_IMG_PATH=/oc/img/
```

因此重建 `jobclaw` 容器不会丢失头像文件。

## MinIO 多 bucket 存储

如果叠加 `docker/compose/compose.minio.yml` 并设置：

```text
JOBCLAW_IMG_STORAGE_TYPE=minio
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=jobclaw
MINIO_BUCKET_PUBLIC_IMAGE=jobclaw-public
MINIO_BUCKET_PRIVATE_MATERIAL=jobclaw-private
MINIO_BUCKET_TEMP=jobclaw-temp
MINIO_BUCKET_EXPORT=jobclaw-export
```

同一套上传逻辑会按文件用途写入不同 bucket：

| 场景 | bucket | 用途 | 访问方式 |
| --- | --- | --- | --- |
| `PUBLIC_IMAGE` | `jobclaw-public` | 头像、岗位图片、站点公开图 | 浏览器通过 `/oc/img/**` 访问，后端代理读取 |
| `PRIVATE_MATERIAL` | `jobclaw-private` | 简历、证书、求职材料附件 | 必须走后端鉴权下载 |
| `TEMP_UPLOAD` | `jobclaw-temp` | 临时上传、AI 解析中间文件 | 建议配置生命周期自动清理 |
| `EXPORT_FILE` | `jobclaw-export` | 批量导出、报表、简历包 | 建议短期签名 URL 或鉴权下载 |

`MINIO_BUCKET` 仍作为兼容兜底；如果某个用途 bucket 未单独配置，会退回到 `MINIO_BUCKET`。

## 当前头像策略

- 仅允许 JPG / PNG。
- 最大上传大小为 5MB。
- 最大解码尺寸为 4096x4096。
- 服务端统一居中裁剪并输出 512x512 JPG。
- 每个用户头像目录保留最近 3 个 JPG 文件。
- MinIO 模式下头像写入 `PUBLIC_IMAGE` bucket。
