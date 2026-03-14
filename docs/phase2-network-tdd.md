# 蘑菇大冒险 Phase 2 — 网络功能技术设计文档（TDD）

**版本**：v1.0
**日期**：2026-03-09
**状态**：草稿，待技术评审
**关联 PRD**：`docs/phase2-network-prd.md`

---

## 一、整体架构

### 1.1 新增模块规划

```
现有 14 个模块，Phase 2 新增 3 个模块：

core-network          — HTTP 客户端、认证拦截器、通用响应模型
service-sync          — 后台同步调度（WorkManager）
feature-account       — 登录/注册 UI + 账号管理 UI
```

排行榜 UI 加入现有的 `feature-game` 模块（避免模块爆炸）。

### 1.2 模块依赖关系

```
app
 ├── feature-account      (新)
 │    ├── core-network    (新)
 │    └── core-domain
 ├── feature-game (扩展排行榜 UI)
 │    └── core-network    (新)
 ├── service-sync         (新)
 │    ├── core-network    (新)
 │    └── core-data
 └── ... (现有模块不变)
```

### 1.3 服务端方案（已确认）

**自建后端 Ktor + PostgreSQL + 腾讯云 COS，部署在腾讯云国内轻量服务器**

| 组件 | 选型 | 备注 |
|------|------|------|
| 后端框架 | Ktor（Kotlin）| 与 Android 同语言，轻量 |
| 数据库 | PostgreSQL | Docker 容器，仅内网暴露 |
| 对象存储 | 腾讯云 COS | 存备份 JSON 文件，与轻量服务器同地域 |
| 短信验证码 | 腾讯云短信服务（SMS）| 国内手机号，与服务器同平台，账号打通方便 |
| 认证 | JWT（RS256）| AccessToken 2h + RefreshToken 30d |
| 反向代理 | Nginx + Let's Encrypt | HTTPS 证书免费自动续期 |
| 部署方式 | Docker Compose | 已确认手机可 ping 通服务器 ✅ |

**部署拓扑：**

```
腾讯云国内轻量服务器
├── Nginx（443/80 → 反向代理）
├── Docker: ktor-server（3000）
├── Docker: postgresql（5432，仅内网）
└── 腾讯云 COS（外部服务，存备份文件）
         ↑
    腾讯云短信服务（外部服务，发验证码）
```

---

## 二、core-network 模块设计

### 2.1 模块定位

- 纯 Android library 模块（`com.android.library`）
- 封装 OkHttp + Retrofit + 认证逻辑
- 不含业务逻辑，供其他模块依赖

### 2.2 目录结构

```
core-network/
├── api/
│   ├── AuthApi.kt           — 登录/注册/刷新 Token 接口
│   ├── BackupApi.kt         — 备份上传/下载接口
│   └── RankApi.kt           — 排行榜查询/提交接口
├── interceptor/
│   ├── AuthInterceptor.kt   — 自动附加 Bearer Token
│   └── TokenRefreshInterceptor.kt  — 401 时自动刷新 Token
├── model/
│   ├── ApiResponse.kt       — 通用响应包装 { code, message, data }
│   └── ApiException.kt      — 网络异常封装
├── token/
│   ├── TokenStore.kt        — 接口
│   └── EncryptedTokenStore.kt  — EncryptedSharedPreferences 实现
└── NetworkModule.kt         — Hilt @Module 提供 OkHttpClient / Retrofit
```

### 2.3 关键代码设计

#### ApiResponse 通用响应

```kotlin
@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null
) {
    val isSuccess get() = code == 0
}
```

#### AuthInterceptor

```kotlin
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.getAccessToken() ?: return chain.proceed(chain.request())
        val req = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(req)
    }
}
```

#### TokenRefreshInterceptor

- 检测到 401 → 调用 `POST /auth/refresh` 用 RefreshToken 换新 AccessToken
- 新 Token 存储后重试原请求
- 若 refresh 也失败 → 发送 `AppEvent.SessionExpired` → MainActivity 跳转到登录页

### 2.4 依赖项（core-network/build.gradle.kts）

```kotlin
dependencies {
    implementation(libs.okhttp)                      // com.squareup.okhttp3:okhttp
    implementation(libs.retrofit)                    // com.squareup.retrofit2:retrofit
    implementation(libs.retrofit.serialization)      // kotlinx-serialization converter
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.androidx.security.crypto)    // EncryptedSharedPreferences
    ksp(libs.hilt.compiler)
}
```

---

## 三、账号系统设计

### 3.1 API 定义

```
POST /auth/send-code          — 发送短信验证码（rate limit: 60s/次）
POST /auth/login              — 手机号 + 验证码 → JWT
POST /auth/refresh            — RefreshToken → 新 AccessToken
POST /auth/logout             — 服务端使 RefreshToken 失效
GET  /user/profile            — 获取用户档案
PUT  /user/profile            — 更新昵称/头像
```

### 3.2 Token 存储

使用 `EncryptedSharedPreferences`（AES256-SIV + AES256-GCM）：

```kotlin
interface TokenStore {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String)
    fun clearTokens()
}
```

### 3.3 feature-account 模块

**UI 页面：**

| 页面 | 路由 | 说明 |
|------|------|------|
| `LoginScreen` | `/account/login` | 手机号输入 + 验证码 |
| `ProfileScreen` | `/account/profile` | 账号信息查看/编辑 |

**ViewModel：**

```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    // sendCode() / login() / state: LoginUiState
}
```

**AuthRepository 接口**（在 core-domain 中定义）：

```kotlin
interface AuthRepository {
    suspend fun sendVerifyCode(phone: String): Result<Unit>
    suspend fun login(phone: String, code: String): Result<UserProfile>
    suspend fun logout(): Result<Unit>
    fun getCurrentUser(): UserProfile?
    fun isLoggedIn(): Boolean
}
```

---

## 四、云端备份设计

### 4.1 备份数据格式

复用现有 `BackupPayload`（已在 Phase 1 Sprint 8 实现，`core-data/backup/BackupPayload.kt`），扩展云端元信息：

```kotlin
@Serializable
data class CloudBackupMeta(
    val userId: String,
    val deviceId: String,
    val schemaVersion: Int,
    val createdAt: Long,           // epoch millis
    val taskCount: Int,
    val checkinCount: Int,
    val payloadSizeBytes: Long
)
```

备份文件存储在对象存储中，路径：`backups/{userId}/{timestamp}.json.gz`（gzip 压缩）

### 4.2 API 定义

```
POST /backup/upload            — 上传备份（multipart/form-data 或 presigned URL）
GET  /backup/list              — 获取备份列表（最近 10 条）
GET  /backup/{id}/download     — 下载指定备份（返回 presigned URL）
DELETE /backup/{id}            — 删除备份
```

### 4.3 service-sync 模块（后台自动备份）

使用 `WorkManager` 实现每日自动备份：

```kotlin
class DailyBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupService: BackupService,
    private val cloudBackupRepository: CloudBackupRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!authRepository.isLoggedIn()) return Result.success()  // 未登录跳过
        return try {
            val payload = backupService.export()
            cloudBackupRepository.upload(payload)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<DailyBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            workManager.enqueueUniquePeriodicWork(
                "daily_backup",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
```

### 4.4 SettingsViewModel 扩展

新增方法：
- `uploadBackup()` — 手动立即备份
- `fetchBackupList()` — 查询云端备份列表
- `restoreFromCloud(backupId: String)` — 下载并恢复（需家长网关验证）

---

## 五、排行榜设计

### 5.1 API 定义

```
POST /rank/submit              — 提交分数 { gameType, score }
GET  /rank/list                — 查询排行榜 { gameType, period, page, pageSize }
GET  /rank/me                  — 查询我的排名 { gameType, period }
```

### 5.2 数据模型

**服务端 game_scores 表（新增 userId 关联云端）：**

```sql
CREATE TABLE cloud_game_scores (
    id          BIGSERIAL PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    nickname    VARCHAR(32) NOT NULL,
    game_type   VARCHAR(32) NOT NULL,
    score       INTEGER NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX ON cloud_game_scores(game_type, score DESC);
```

### 5.3 GameScore 提交逻辑

在 `feature-game` 的 `GameViewModel` 中，游戏结束后：

```kotlin
// 本地保存（已有）
gameRepository.saveScore(gameType, score)

// 云端提交（新增，仅登录且未关闭隐私模式）
if (authRepository.isLoggedIn() && !settingsRepository.isRankPrivacyMode()) {
    launch(Dispatchers.IO) {
        rankRepository.submitScore(gameType, score).onFailure {
            // 静默失败，不影响用户体验
        }
    }
}
```

### 5.4 RankScreen UI

新增 `RankScreen`，接入 `feature-game`，包含：
- TabRow：本周 / 本月 / 历史最佳
- LazyColumn：排行列表（名次、头像占位、昵称、分数）
- 底部固定展示"我的排名"（若未进 Top 100）
- 未登录时顶部提示横幅"登录后参与排名"

---

## 六、数据库与迁移

本次 Phase 2 **不修改本地 Room 数据库 schema**，所有新增数据（Token、备份元信息、排行榜缓存）存储于：

| 数据 | 存储位置 |
|------|----------|
| AccessToken / RefreshToken | EncryptedSharedPreferences |
| 用户档案缓存 | DataStore（Preferences） |
| 上次备份时间 | DataStore |
| 排行榜数据缓存 | 内存（StateFlow，不持久化） |

---

## 七、安全设计

| 风险 | 措施 |
|------|------|
| Token 明文存储 | EncryptedSharedPreferences（AES256） |
| 中间人攻击 | 强制 HTTPS + Certificate Pinning（可选，Phase 2.5） |
| 排行榜刷分 | 服务端校验分数合理范围；同一用户同一游戏 1 分钟内只接受一次提交 |
| 备份数据泄露 | 服务端存储加密；presigned URL 限时（15 分钟） |
| 会话劫持 | AccessToken 2h 有效期；RefreshToken 30d，单设备单 Token |

---

## 八、错误处理策略

| 场景 | 处理方式 |
|------|----------|
| 无网络 | 显示 SnackBar 提示，本地功能正常 |
| 401 Unauthorized | 自动刷新 Token，失败则跳转登录 |
| 服务端 5xx | 重试 3 次（指数退避），失败提示"服务暂时不可用" |
| 备份上传超时 | WorkManager 自动重试，最多 3 次 |
| 排行榜加载失败 | 显示空态 + 重试按钮，不影响游戏功能 |

---

## 九、工作量估算

| Sprint | 模块 | 主要工作 | 估算 |
|--------|------|----------|------|
| P2-S1 | core-network + feature-account | HTTP 客户端、Token 管理、登录/注册 UI | 5~7天 |
| P2-S2 | service-sync + BackupApi | 手动/自动备份、数据恢复 UI | 4~6天 |
| P2-S3 | RankApi + RankScreen | 排行榜提交、展示、筛选 | 3~4天 |
| P2-S4 | 联调 + 回归 | 全量测试、安全检查 | 3天 |

---

## 十、已确认决策

| 问题 | 决策 |
|------|------|
| 服务端方案 | ✅ 自建 Ktor，腾讯云国内轻量服务器，Docker 部署，手机已验证可连通 |
| 短信服务 | ✅ 腾讯云短信服务（国内手机号，与服务器同平台） |
| 备份存储 | ✅ 腾讯云 COS（与服务器同地域） |
| 账号方式 | ✅ 手机号 + 验证码，不做 OAuth |

## 十一、待决策项

1. **排行榜范围**：只做全球榜，还是也支持家庭内部排行榜？
2. **图片备份**：Phase 2 做还是 Phase 3？
3. **备份保留策略**：保留最近 N 份？还是按时间（如 30 天内）？
4. ~~**多孩子支持**~~ ✅ 已确认：一账号一孩子，数据库 schema 无需多孩子扩展字段

---

*文档作者：AI 辅助生成，需技术 Owner 评审确认*
