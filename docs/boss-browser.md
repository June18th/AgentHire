# BOSS 手动登录浏览器

这个工具只打开一个独立的 Edge/Chrome 浏览器资料目录，方便你扫码登录 BOSS 并保留本地会话。它不绕过登录、验证码或风控，也不自动点击页面。

## 首次登录

```powershell
powershell -ExecutionPolicy Bypass -File .\build\boss-browser.ps1 open
```

浏览器打开后，在 BOSS 页面扫码登录。登录态会保存在：

```text
workspace/browser-profiles/boss
```

该目录已加入 `.gitignore`，不要提交或分享。

## 打开搜索页

```powershell
powershell -ExecutionPolicy Bypass -File .\build\boss-browser.ps1 search -Query "Java 实习" -Cities 北京,天津,杭州,深圳,郑州
```

脚本会用同一个资料目录打开多个城市搜索页。你可以在页面里正常滚动、筛选，然后复制岗位列表文本给 JobClaw 解析导入。

## 查看资料目录

```powershell
powershell -ExecutionPolicy Bypass -File .\build\boss-browser.ps1 profile
```

## 只打印搜索链接

```powershell
powershell -ExecutionPolicy Bypass -File .\build\boss-browser.ps1 urls -Query "Java 实习" -Cities 北京,天津,杭州,深圳,郑州
```

## 指定浏览器

如果自动找不到 Edge/Chrome：

```powershell
powershell -ExecutionPolicy Bypass -File .\build\boss-browser.ps1 open -BrowserPath "C:\Program Files\Google\Chrome\Application\chrome.exe"
```
