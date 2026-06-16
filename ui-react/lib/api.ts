import axios from "axios";

// 根据环境设置BASE_URL， 利用了Next.js在构建时自动设置的环境变量，在执行deploy脚本时，由于会调用 next build ，NODE_ENV会被设置为'production'
// 本地开发时使用http://localhost:8080
// 部署时使用空字符串
const BASE_URL = process.env.NODE_ENV === 'production' ? '' : "http://localhost:8087";

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 360000,
});

export interface LlmOverview { calls:number; successRate:number; averageDurationMs:number; totalTokens:number; estimatedCost:number|string }
export interface LlmCall { id:string; jobClawUserId?:string; agent?:string; operation:string; mode:string; outcome:string; durationMs:number; requestCount:number; totalTokens?:number; estimatedCost?:number|string; createTime:string }
export async function fetchLlmOverview(admin:boolean):Promise<LlmOverview>{const r=await api.get(admin?"/api/admin/llm-monitor/overview":"/api/user/llm-usage/overview");if(r.data?.code===0)return r.data.data;throw new Error(r.data?.msg||"获取模型用量失败")}
export async function fetchLlmCalls(admin:boolean):Promise<{list:LlmCall[]}>{const r=await api.get(admin?"/api/admin/llm-monitor/calls":"/api/user/llm-usage/calls");if(r.data?.code===0)return r.data.data;throw new Error(r.data?.msg||"获取模型调用明细失败")}

// 全局请求拦截器，自动带上 X-OC-TOKEN
api.interceptors.request.use((config) => {
  if (typeof window !== "undefined") {
    const token = localStorage.getItem("oc-token");
    if (token) {
      config.headers = config.headers || {};
      config.headers["X-OC-TOKEN"] = token;
    }
  }
  return config;
});

// 响应拦截器，处理302重定向
api.interceptors.response.use(
  (response) => {
    console.log("响应拦截器", response);
    if (response.data.code == 100403003) {
      // 清除本地缓存的登录信息
      if (typeof window !== "undefined") {
        localStorage.removeItem("oc-token");
        localStorage.removeItem("oc-user");
        window.location.href = "/";
      }
      return Promise.reject("未登录");
    }
    return response;
  },
  (error) => {
    if (
      error.response &&
      error.response.data &&
      error.response.data.code === 100403003
    ) {
      if (typeof window !== "undefined") {
        window.location.href = "/";
      }
    }
    return Promise.reject(error);
  }
);

/**
 * 获取微信扫码登录 SSE 订阅 URL
 */
export function getWxSseUrl() {
  return `${BASE_URL}/api/wx/subscribe`;
}

/**
 * 微信扫码登录 callback
 * @param xml xml 字符串
 * @returns Promise<void>
 */
export async function postWxCallback(xml: string): Promise<void> {
  await api.post("/api/wx/callback", xml, {
    headers: { "content-type": "application/xml" },
  });
}

export interface JobListQuery {
  companyName?: string;
  companyType?: string;
  jobLocation?: string;
  recruitmentType?: string;
  recruitmentTarget?: string;
  position?: string;
  deliveryProgress?: string;
  state?: number;
  page?: number;
  size?: number;
}

export interface JobListResponse {
  list: any[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
  online?: number;
  locked: boolean;
}

export async function fetchJobList(
  params?: JobListQuery
): Promise<JobListResponse> {
  const res = await api.get("/api/oc/list", { params });
  if (res.data && res.data.code === 0) {
    // 将外层的在线人数写到内部
    res.data.data.online = res.data.online;
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取岗位列表失败");
}

export async function jobDetail(id: number) {
  const res = await api.get(`/api/oc/detail?id=${id}`);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取岗位信息失败");
}

export async function fetchAdminJobList(
  params?: JobListQuery
): Promise<JobListResponse> {
  const res = await api.get("/api/admin/oc/list", { params });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取岗位列表失败");
}

export async function execPublishBlogs() {
  const res = await api.get("/api/admin/oc/publish");
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "发布播客失败");
}

export async function submitOcEntry(params: any) {
  const res = await api.post("/api/admin/oc/save", params);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "提交岗位信息失败");
}

export async function updateOcState(params: { id: number; state: number }) {
  console.log("这里啦");
  const res = await api.get(
    `/api/admin/oc/updateState?id=${params.id}&state=${params.state}`
  );
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "更新岗位状态失败");
}

//  ---------------------------- gather 相关

function getSubmitPath(async: boolean) {
  if (async) {
    // 异步执行
    return "/api/admin/gather/asyncSubmit";
  } else {
    // 同步执行
    return "/api/admin/gather/submit";
  }
}

export async function submitAIEntry(params: {
  content: string;
  model: string;
  type: string;
  file: any;
}) {
  const async = true;
  if (params.file) {
    // 传文件的方式
    const formData = new FormData();
    formData.append("file", params.file);
    formData.append("model", params.model);
    formData.append("type", params.type);
    const ans = await api.post(getSubmitPath(async), formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    if (ans.data && ans.data.code === 0) {
      return ans.data.data;
    } else {
      throw new Error(ans.data?.msg || "AI录入失败");
    }
  }

  const res = await api.post(getSubmitPath(async), params, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "AI录入失败");
}

/**
 * 提交AI代理任务（非SSE版本）
 */
export async function submitAIAgentTaskSimple(params: {
  content: string;
  model: string;
  type: string;
  file: any;
}) {
  const async = true;
  if (params.file) {
    // 传文件的方式
    const formData = new FormData();
    formData.append("file", params.file);
    formData.append("model", "ZhiPu");
    formData.append("type", "0");
    const ans = await api.post(getSubmitPath(async), formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    if (ans.data && ans.data.code === 0) {
      return ans.data.data;
    } else {
      throw new Error(ans.data?.msg || "AI录入失败");
    }
  }

  const res = await api.post("/api/admin/gather/agentSubmit", params, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "AI录入失败");
}

/**
 * 提交AI代理任务并获取任务ID
 * @param params 请求参数
 * @param onSuccess 获取任务ID成功回调
 * @param onError 错误处理回调
 * @param controller AbortController实例，用于取消请求
 */
export function submitAIAgentTask(
  params: { content: string; model: string; type: string; file: any },
  onSuccess: (taskId: string) => void,
  onError: (error: Error) => void,
  controller?: AbortController
) {
  // 用于文件上传的表单数据
  const formData = new FormData();
  Object.keys(params).forEach((key) => {
    if (params[key] !== undefined && params[key] !== null) {
      formData.append(key, params[key]);
    }
  });

  // 创建axios请求配置
  const config = {
    method: "post",
    url: "/api/admin/gather/agentSubmit",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data",
    },
    signal: controller?.signal,
  };

  // 发送请求
  api(config)
    .then((response) => {
      if (!response?.data?.data) {
        throw new Error("未能获取有效的任务ID");
      }
      onSuccess(response.data.data);
    })
    .catch((error) => {
      if (!controller?.signal.aborted) {
        console.error("提交任务失败:", error);
        onError(new Error(`提交任务失败: ${error.message}`));
      }
    });
}

/**
 * 通过任务ID建立SSE连接获取任务结果
 * @param taskId 任务ID
 * @param onMessage 消息处理回调
 * @param onError 错误处理回调
 * @param onComplete 完成处理回调
 * @returns 用于关闭连接的函数
 */
export function connectSSEByTaskId(
  taskId: string,
  onMessage: (data: string) => void,
  onError: (error: Error) => void,
  onComplete?: () => void
) {
  let controller: AbortController | null = new AbortController();
  let isClosed = false;

  async function startFetch() {
    try {
      // 创建请求头，包含X-OC-TOKEN
      const headers = new Headers();
      const token =
        typeof window !== "undefined" ? localStorage.getItem("oc-token") : null;
      if (token) {
        headers.append("X-OC-TOKEN", token);
      }

      // 创建fetch请求
      const url = `${BASE_URL}/api/admin/gather/autoInvoke?taskId=${encodeURIComponent(
        taskId
      )}`;
      const response = await fetch(url, {
        method: "GET",
        headers: headers,
        signal: controller?.signal,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      if (!response.body) {
        throw new Error("ReadableStream not supported in this browser");
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      // 读取流数据
      while (!isClosed) {
        const { done, value } = await reader.read();
        if (done) {
          if (buffer) {
            // 处理剩余的缓冲区数据
            processMessage(buffer);
          }
          if (onComplete && !isClosed) {
            onComplete();
          }
          close();
          break;
        }

        const directResponse = decoder.decode(value, { stream: true });
        processBuffer(directResponse);
        // buffer += directResponse;
        // processBuffer(buffer);
      }
    } catch (error) {
      if (
        !isClosed &&
        !(error instanceof DOMException && error.name === "AbortError")
      ) {
        // console.error('SSE连接错误:', error);
        onError(
          new Error(
            `SSE连接发生错误: ${
              error instanceof Error ? error.message : String(error)
            }`
          )
        );
        close();
      }
    }
  }

  // 处理缓冲区数据，识别以data:开头的行作为新数据
  function processBuffer(buffer: string) {
    // 按换行符分割所有行
    const lines = buffer.split("\n");

    let currentMessage = "";

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();

      // 如果行为空，则放在当前消息中
      if (line === "") {
        currentMessage += line;
        continue;
      }

      // 检查是否以data:开头
      if (line.startsWith("data:")) {
        // 如果已有未处理的消息，先处理它
        if (currentMessage) {
          processMessage(currentMessage);
        }
        // 提取data:后面的内容
        currentMessage = line.substring(5).trim(); // 5 是 "data:" 的长度
      } else if (currentMessage) {
        // 如果不是以data:开头，但已有当前消息，将其添加到当前消息
        currentMessage += "\n" + line;
      } else {
        // 如果没有当前消息，但行不为空，直接处理它（兼容原有格式）
        processMessage(line);
      }
    }

    // 处理最后一条消息（如果有）
    if (currentMessage) {
      processMessage(currentMessage);
    }
  }

  // 处理单条消息
  function processMessage(data: string) {
    if (isClosed) return;
    try {
      // console.log('接收到SSE消息:', data);
      onMessage(data);
    } catch (error) {
      // console.error('处理SSE消息时出错:', error);
      onError(
        new Error(
          `处理SSE消息时出错: ${
            error instanceof Error ? error.message : String(error)
          }`
        )
      );
    }
  }

  // 关闭连接的函数
  function close() {
    if (isClosed) return;
    isClosed = true;
    if (controller) {
      controller.abort();
      controller = null;
    }
    console.log("SSE连接已关闭");
  }

  // 启动fetch请求
  startFetch();

  return close;
}

/**
 * 提交AI代理任务并建立SSE连接（整合版）
 * @param params 请求参数
 * @param onMessage 消息处理回调
 * @param onError 错误处理回调
 * @param onComplete 完成处理回调
 * @param controller AbortController实例，用于取消请求
 * @returns 用于关闭SSE连接的函数
 */
export function submitAIAgentTaskSSE(
  params: { content: string; model: string; type: string; file: any },
  onMessage: (data: string) => void,
  onError: (error: Error) => void,
  onComplete?: () => void,
  controller?: AbortController
) {
  let closeSSE: (() => void) | null = null;

  // 提交任务获取taskId
  submitAIAgentTask(
    params,
    (taskId) => {
      console.log("成功获取任务ID:", taskId);
      // 使用taskId建立SSE连接
      closeSSE = connectSSEByTaskId(taskId, onMessage, onError, onComplete);
    },
    onError,
    controller
  );

  // 返回关闭函数
  return () => {
    if (closeSSE) {
      closeSSE();
    }
  };
}

export interface TaskListQuery {
  page?: number;
  size?: number;
  taskId?: number;
  model?: string;
  type?: number;
  state?: number;
}

export interface TaskListItem {
  taskId: number;
  type: number;
  model: string;
  state: number;
  content: string;
  cnt: number;
  result: string;
  processTime: string;
  createTime: string;
  updateTime: string;
}

export interface TaskListResponse {
  list: TaskListItem[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

export async function fetchTaskList(
  params: TaskListQuery
): Promise<TaskListResponse> {
  const res = await api.post("/api/admin/gather/list", params, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取任务列表失败");
}

export async function reRunTask(taskId: number): Promise<boolean> {
  const res = await api.get(`/api/admin/gather/reRun?taskId=${taskId}`);
  if (res.data && res.data.code === 0) {
    return res.data.data === true;
  }
  throw new Error(res.data?.msg || "重跑任务失败");
}

export interface DraftListQuery {
  page?: number;
  size?: number;
  companyName?: string;
  companyType?: string;
  jobLocation?: string;
  recruitmentType?: string;
  recruitmentTarget?: string;
  position?: string;
  lastUpdatedTimeAfter?: number;
  lastUpdatedTimeBefore?: number;
  state?: number;
  toProcess?: string;
}

export interface DraftItem {
  id: number;
  companyName: string;
  companyType: string;
  companyIndustry: string;
  jobLocation: string;
  recruitmentType: string;
  recruitmentTarget: string;
  position: string;
  deliveryProgress: string;
  lastUpdatedTime: string;
  deadline: string;
  relatedLink: string;
  jobAnnouncement: string;
  internalReferralCode: string;
  remarks: string;
  state: number;
  toProcess: number;
  createTime: string;
  updateTime: string;
}

export interface DraftListResponse {
  list: DraftItem[];
  total: number;
}

export async function fetchDraftList(
  params: DraftListQuery
): Promise<DraftListResponse> {
  const res = await api.get("/api/admin/draft/list", { params });
  if (res.data && res.data.code === 0) {
    // 兼容 data 直接为数组或为对象
    if (Array.isArray(res.data.data)) {
      return { list: res.data.data, total: res.data.data?.length };
    }
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取草稿列表失败");
}

/**
 * 批量发布草稿
 * @param ids 草稿 id 数组
 * @returns 发布结果
 */
export async function batchPublishDrafts(ids: number[]): Promise<void> {
  const res = await api.post("/api/admin/draft/toOc", ids);
  if (res.data && res.data.code === 0) {
    return;
  }
  throw new Error(res.data?.msg || "发布失败");
}

/**
 * 更新草稿
 * @param draft DraftItem
 * @returns 是否成功
 */
export async function updateDraft(draft: DraftItem): Promise<boolean> {
  const res = await api.post("/api/admin/draft/update", draft);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "草稿更新失败");
}

/**
 * 删除草稿数据
 * @param id
 * @returns
 */
export async function deleteDraft(id: number): Promise<boolean> {
  const res = await api.get("/api/admin/draft/delete?draftId=" + id);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "草稿删除失败");
}

// -------------------------- 用户相关

export interface UserListQuery {
  userId?: number;
  displayName?: string;
  role?: number;
  page?: number;
  size?: number;
}

export interface UserListItem {
  userId: number;
  displayName: string;
  avatar: string;
  wxId: string;
  role: number;
  state: number;
  email: string;
  intro: string;
  expireTime: number | null;
  createTime: number;
  updateTime: number;
}

export interface UserListResponse {
  list: UserListItem[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

export async function fetchUserList(
  params?: UserListQuery
): Promise<UserListResponse> {
  const res = await api.get("/api/admin/user/list", { params });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取用户列表失败");
}

/**
 * 更新用户角色
 * @param params 包含 userId, role, expireTime
 * @returns 是否成功
 */
export async function updateUserRole(params: {
  userId: number;
  role: number;
  expireTime: number;
}): Promise<boolean> {
  const formData = new URLSearchParams();
  formData.append("userId", String(params.userId));
  formData.append("role", String(params.role));
  formData.append("expireTime", String(params.expireTime));
  const res = await api.post("/api/admin/user/updateRole", formData, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  if (res.data && res.data.code === 0) {
    return res.data.data === true;
  }
  throw new Error(res.data?.msg || "用户角色更新失败");
}

// -------------------------- 字典相关

export interface DictListQuery {
  app?: string;
  key?: string;
  page?: number;
  size?: number;
}

export interface DictListItem {
  id: number;
  app: string;
  scope: number;
  key: string;
  value: string;
  intro: string;
  remark: string;
  state: number;
  createTime: number;
  updateTime: number;
}

export interface DictListResponse {
  list: DictListItem[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

export async function fetchDictList(
  params?: DictListQuery
): Promise<DictListResponse> {
  const res = await api.get("/api/admin/dict/list", { params });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取字典列表失败");
}

export interface DictSaveReq {
  id?: number;
  app: string;
  scope: number;
  key: string;
  value: string;
  intro: string;
  remark: string;
  state: number;
}

export async function saveDict(params: DictSaveReq): Promise<boolean> {
  const res = await api.post("/api/admin/dict/save", params);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "保存字典失败");
}

export async function updateDictState(
  id: number,
  state: number
): Promise<boolean> {
  const res = await api.post(
    "/api/admin/dict/updateState",
    {
      id: id,
      state: state,
    },
    {
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
    }
  );
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "更新状态失败");
}

export async function deleteDict(id: number): Promise<boolean> {
  const res = await api.get("/api/admin/dict/delete?id=" + id);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "删除失败");
}

// ---------------- 个人用户相关

export interface UserSaveReq {
  userId?: number;
  displayName?: string;
  avatar?: string;
  email?: string;
  intro?: string;
  dingDingId?: string;
  feiShuId?: string;
}

export async function execLogout() {
  const res = await api.get("/api/user/logout");
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "退出登录失败");
}

export async function getUserDetail() {
  const res = await api.get("/api/user/detail");
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取用户信息失败");
}

export interface UserModelConfig {
  name: string;
  type: string;
  multimodal?: boolean;
}

export interface UserProviderConfig {
  provider?: string;
  apiKey?: string;
  apiStyle?: string;
  baseUrl?: string;
  completionsPath?: string;
  embeddingsPath?: string;
  imagesPath?: string;
  speechPath?: string;
  transcriptionPath?: string;
  models?: UserModelConfig[];
}

export interface UserPreferenceModels {
  vision?: string;
  text?: string;
  image?: string;
  video?: string;
  embedding?: string;
  asr?: string;
  tts?: string;
}

export interface UserPreference {
  collector?: string;
  channels?: string[];
  models?: UserPreferenceModels;
  providers?: Record<string, UserProviderConfig>;
}

export interface UpdateProviderReq {
  provider: string;
  apiKey?: string;
  apiStyle?: string;
  baseUrl?: string;
  completionsPath?: string;
  embeddingsPath?: string;
  imagesPath?: string;
  speechPath?: string;
  transcriptionPath?: string;
}

export interface UpdateModelReq {
  name: string;
  type: string;
  multimodal?: boolean;
}

export interface UpdateUserPreferenceReq {
  collector?: string;
  channels?: string[];
  models?: UserPreferenceModels;
  provider?: UpdateProviderReq;
  deleteProvider?: boolean;
  model?: UpdateModelReq;
  deleteModel?: boolean;
}

export async function getUserPreference(): Promise<UserPreference> {
  const res = await api.get("/api/user/preference");
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取用户偏好配置失败");
}

export async function updateUserPreference(req: UpdateUserPreferenceReq): Promise<boolean> {
  const res = await api.post("/api/user/preference/update", req);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "更新用户偏好配置失败");
}

export async function submitUserInterest(text: string | String) {
  const res = await api.get("/api/user/interest?text=" + text);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "提交个人订阅偏好失败");
}

export async function fetchUserInterestRecommend({page, size}: {
  page?: number;
  size?: number;
}) {
  const res = await api.post("/api/user/recommend", {
    page: page || 1, size: size || 10
  }, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
   if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "更新用户信息失败");
}

export async function updateUserDetail(params: UserSaveReq) {
  const res = await api.post("/api/user/update", params);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "更新用户信息失败");
}

export async function toPay(
  rechargeLevel: number | string | String,
  vipAmount: number | string | String,
  couponCode: string = ""
) {
  let url = `/api/recharge/toPay`;
  if (rechargeLevel != "") {
    url += `?vipLevel=${rechargeLevel}`;
  } else {
    url += `?vipPrice=${vipAmount}`;
  }
  if (couponCode) {
    url += `&couponCode=${encodeURIComponent(couponCode)}`;
  }
  const res = await api.get(url);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取支付信息失败");
}

export async function markPaying(id: any) {
  // 告诉后端已经支付成功
  const res = await api.get(`/api/recharge/paying?rechargeId=${id}`);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "同步支付状态失败~ 到购买记录看看吧");
}

export async function refreshPay(id: any) {
  // 告诉后端已经支付成功
  const res = await api.get(`/api/recharge/refreshPay?rechargeId=${id}`);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "重置状态失败~");
}

export interface RechageListItem {
  payId: number;
  tradeNo: string;
  amount: string;
  level: number;
  status: number;
  payTime: number;
  transactionId: string;
}

export interface RechageListResponse {
  list: RechageListItem[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

// 查询用户充值记录
export async function getRechargeList(): Promise<RechageListResponse> {
  const res = await api.get("/api/recharge/listRecords");
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取用户信息失败");
}

// ---------------- 全局配置

export interface GlobalConfigItem {
  app: String;
  items: GlobalConfigItemValue[];
}

export interface GlobalConfigItemValue {
  key: String;
  value: String;
  intro: String;
}

export async function getGlobalConfig(): Promise<{
  [key: string]: GlobalConfigItem;
}> {
  const res = await api.get("/api/common/dict");
  if (res.data && res.data.code === 0) {
    const data = res.data.data;
    const result: { [key: string]: GlobalConfigItem } = {};
    for (const item of data) {
      result[item.app] = item;
    }
    return result;
  }
  throw new Error(res.data?.msg || "获取全局配置失败");
}

// ----------------- 优惠券

export interface CouponListItem {
  couponId?: number;
  // 优惠券code
  couponCode?: string;
  // 优惠券类型
  couponType: number;
  // 优惠券金额/百分比
  couponValue: string;
  // 作用域
  scope: number;
  // 优惠券数量
  couponCount: number;
  // 使用数量
  useCount?: number;
  // 扩展信息
  extra?: string;
  // 开始时间
  startTime: number;
  // 结束时间
  endTime: number;
}

export interface CouponListResponse {
  list: CouponListItem[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

export interface CouponListQuery {
  page?: number;
  size?: number;
  code?: string;
  type?: number;
}

export interface UserBo {
  userId: number;
  nickName: string;
  avatar: string;
  role: string;
}

export interface CouponDetail {
  coupon: string;
  payId: number;
  tradeNo: string;
  amount: string;
  level: number;
  status: number;
  payTime: number;
  transactionId: string;
  promotionAmount: string; // 优惠金额
  user: UserBo;
}

export interface CouponDetailListResponse {
  list: CouponDetail[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

export async function fetchCouponList(
  params?: CouponListQuery
): Promise<CouponListResponse> {
  const res = await api.get("/api/admin/coupon/list", { params });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取优惠券列表失败");
}

export async function fetchCouponDetail({
  coupon,
  page,
  size,
}: {
  coupon: string;
  page?: number;
  size?: number;
}): Promise<CouponDetailListResponse> {
  const res = await api.get(
    "/api/admin/coupon/useDetail?couponCode=" +
      coupon +
      "&page=" +
      page +
      "&size=" +
      size
  );
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取优惠券详情失败");
}

export async function fetchCouponSave(params: CouponListItem) {
  const res = await api.post("/api/admin/coupon/create", params);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "保存优惠券失败");
}

export async function fetchCouponDelete(id: number) {
  const res = await api.get("/api/admin/coupon/delete?couponId=" + id);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "删除优惠券失败");
}

export async function fetchCouponUpdate(params: CouponListItem) {
  const res = await api.post("/api/admin/coupon/update", params);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "更新优惠券失败");
}

export interface ChannelConfig {
  appId?: string;
  appSecret?: string;
  mode?: string;
  state?: string;
  scope?: string;
  ownerJobClawUserId?: string;
  botName?: string;
  aiCardId?: string;
}

export async function fetchDingTalkList(): Promise<ChannelConfig[]> {
  const res = await api.get("/api/dingding/list");
  if (res.data && res.data.code === 0) {
    return res.data.data || [];
  }
  return [];
}

export async function fetchDingTalkBind(params: ChannelConfig): Promise<boolean> {
  const res = await api.post("/api/dingding/bind", params);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "绑定钉钉机器人失败");
}

export async function fetchFeiShuList(): Promise<ChannelConfig[]> {
  const res = await api.get("/api/feishu/list");
  if (res.data && res.data.code === 0) {
    return res.data.data || [];
  }
  return [];
}

export async function fetchFeiShuBind(params: ChannelConfig): Promise<boolean> {
  const res = await api.post("/api/feishu/bind", params);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "绑定飞书机器人失败");
}

export async function fetchWeChatBindQrCode(): Promise<{ qrCode: string; qrUrl?: string }> {
  const res = await api.post("/api/wechat/clawbot/qrcode");
  if (res.data && res.data.code === 0) {
    return res.data.data || { qrCode: "" };
  }
  throw new Error(res.data?.msg || "获取二维码失败");
}

export async function fetchWeChatList(): Promise<WeChatAccount[]> {
  const res = await api.get("/api/wechat/clawbot/list");
  if (res.data && res.data.code === 0) {
    return res.data.data || [];
  }
  throw new Error(res.data?.msg || "获取微信绑定信息失败");
}

export interface WeChatAccount {
  appId?: string;
  appSecret?: string;
  userId?: string;
  mode?: string;
  state?: string;
}

export async function fetchWeChatBindStatus(qrCode: string): Promise<{ success: boolean; message?: string; accountId?: string }> {
  const res = await api.get("/api/wechat/clawbot/status", { params: { qrCode } });
  if (res.data && res.data.code === 0) {
    return res.data.data || { success: false };
  }
  return { success: false, message: res.data?.msg };
}
