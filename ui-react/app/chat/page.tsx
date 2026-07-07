"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { Bot, Building2, ClipboardList, Loader2, MessageSquare, Plus, Search, Send, Sparkles, User } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { AuthGuard } from "@/components/auth/AuthGuard";
import { MarkdownMessage } from "@/components/chat/MarkdownMessage";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/use-toast";
import { useLoginUser } from "@/hooks/useLoginUser";
import {
  ChatAgent,
  fetchChatAgents,
  sendChatMessage,
  streamChatMessage,
} from "@/lib/api";

type ChatRole = "user" | "assistant";

interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
}

const AUTO_AGENT = "__auto__";
const STORAGE_CONVERSATION_ID = "jobclaw-web-chat-conversation-id";
const STORAGE_MESSAGES = "jobclaw-web-chat-messages";

const initialMessages: ChatMessage[] = [
  {
    id: "welcome",
    role: "assistant",
    content:
      "你好，我是求职派助手。可以帮你梳理求职目标、查询岗位、分析简历方向，也可以继续追问项目里的 Agent 设计。",
  },
];

function createMessageId() {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function isChatMessageList(value: unknown): value is ChatMessage[] {
  return (
    Array.isArray(value) &&
    value.every(
      (item) =>
        item &&
        typeof item === "object" &&
        typeof (item as ChatMessage).id === "string" &&
        ((item as ChatMessage).role === "user" ||
          (item as ChatMessage).role === "assistant") &&
        typeof (item as ChatMessage).content === "string",
    )
  );
}

export default function ChatPage() {
  const { toast } = useToast();
  const { userInfo } = useLoginUser();
  const [agents, setAgents] = useState<ChatAgent[]>([]);
  const [selectedAgent, setSelectedAgent] = useState(AUTO_AGENT);
  const [conversationId, setConversationId] = useState<string>();
  const [messages, setMessages] = useState<ChatMessage[]>(initialMessages);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [storageReady, setStorageReady] = useState(false);

  // AI-GENERATED AIDEV-NOTE: contain chat scrolling
  const messagesRef = useRef<HTMLDivElement>(null);
  const shouldStickToBottomRef = useRef(true);
  const hasHydratedMessagesRef = useRef(false);
  const abortControllerRef = useRef<AbortController | null>(null);

  const scrollMessagesToBottom = useCallback((behavior: ScrollBehavior = "smooth") => {
    window.requestAnimationFrame(() => {
      const messagesEl = messagesRef.current;
      if (!messagesEl) {
        return;
      }

      messagesEl.scrollTo({
        top: messagesEl.scrollHeight,
        behavior,
      });
    });
  }, []);

  const handleMessagesScroll = () => {
    const messagesEl = messagesRef.current;
    if (!messagesEl) {
      return;
    }

    const distanceToBottom =
      messagesEl.scrollHeight - messagesEl.scrollTop - messagesEl.clientHeight;
    shouldStickToBottomRef.current = distanceToBottom < 96;
  };

  useEffect(() => {
    const storedConversationId = localStorage.getItem(STORAGE_CONVERSATION_ID);
    const storedMessages = localStorage.getItem(STORAGE_MESSAGES);

    if (storedConversationId) {
      setConversationId(storedConversationId);
    }

    if (storedMessages) {
      try {
        const parsed: unknown = JSON.parse(storedMessages);
        if (isChatMessageList(parsed) && parsed.length > 0) {
          setMessages(parsed);
        }
      } catch {
        localStorage.removeItem(STORAGE_MESSAGES);
      }
    }

    setStorageReady(true);
  }, []);

  useEffect(() => {
    if (!userInfo) {
      setAgents([]);
      return;
    }

    fetchChatAgents()
      .then(setAgents)
      .catch((error) => {
        toast({
          title: "Agent 加载失败",
          description: error instanceof Error ? error.message : "请稍后重试",
          variant: "destructive",
        });
      });
  }, [toast, userInfo]);

  useEffect(() => {
    if (!storageReady) {
      return;
    }

    localStorage.setItem(STORAGE_MESSAGES, JSON.stringify(messages));

    if (!hasHydratedMessagesRef.current) {
      hasHydratedMessagesRef.current = true;
      scrollMessagesToBottom("auto");
      return;
    }

    if (shouldStickToBottomRef.current) {
      scrollMessagesToBottom();
    }
  }, [messages, scrollMessagesToBottom, storageReady]);

  useEffect(() => {
    if (loading && shouldStickToBottomRef.current) {
      scrollMessagesToBottom();
    }
  }, [loading, scrollMessagesToBottom]);

  const selectedAgentLabel = useMemo(() => {
    if (selectedAgent === AUTO_AGENT) {
      return "自动选择";
    }
    return agents.find((agent) => agent.agentId === selectedAgent)?.intro || selectedAgent;
  }, [agents, selectedAgent]);

  const handleReset = () => {
    abortControllerRef.current?.abort();
    shouldStickToBottomRef.current = true;
    setConversationId(undefined);
    setMessages(initialMessages);
    localStorage.removeItem(STORAGE_CONVERSATION_ID);
    localStorage.removeItem(STORAGE_MESSAGES);
  };

  const handleSend = async () => {
    const content = input.trim();
    if (!content || loading) {
      return;
    }
    if (!userInfo) {
      return;
    }

    const userMessage: ChatMessage = {
      id: createMessageId(),
      role: "user",
      content,
    };

    shouldStickToBottomRef.current = true;
    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setLoading(true);

    const assistantMessageId = createMessageId();
    setMessages((prev) => [
      ...prev,
      {
        id: assistantMessageId,
        role: "assistant",
        content: "",
      },
    ]);

    const appendAssistantContent = (nextContent: string) => {
      setMessages((prev) =>
        prev.map((message) =>
          message.id === assistantMessageId
            ? { ...message, content: `${message.content}${nextContent}` }
            : message,
        ),
      );
    };

    try {
      const controller = new AbortController();
      abortControllerRef.current = controller;

      await streamChatMessage(
        {
          message: content,
          conversationId,
          agentId: selectedAgent === AUTO_AGENT ? undefined : selectedAgent,
        },
        {
          signal: controller.signal,
          onEvent: (event) => {
            if (event.conversationId) {
              setConversationId(event.conversationId);
              localStorage.setItem(STORAGE_CONVERSATION_ID, event.conversationId);
            }

            if (event.type === "chunk") {
              appendAssistantContent(event.content || event.toolResult || "");
            }

            if (event.type === "error") {
              throw new Error(event.error || "流式对话失败");
            }
          },
        },
      );
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        return;
      }

      try {
        const resp = await sendChatMessage({
          message: content,
          conversationId,
          agentId: selectedAgent === AUTO_AGENT ? undefined : selectedAgent,
        });
        setConversationId(resp.conversationId);
        localStorage.setItem(STORAGE_CONVERSATION_ID, resp.conversationId);
        setMessages((prev) =>
          prev.map((message) =>
            message.id === assistantMessageId
              ? { ...message, content: resp.content || "我没有拿到有效回复，请稍后再试。" }
              : message,
          ),
        );
        return;
      } catch (fallbackError) {
        setMessages((prev) =>
          prev.map((message) =>
            message.id === assistantMessageId
              ? {
                  ...message,
                  content: "这次调用没有成功。可以先检查登录状态、模型 API Key，或者稍后再发一次。",
                }
              : message,
          ),
        );
        error = fallbackError;
      }

      toast({
        title: "消息发送失败",
        description: error instanceof Error ? error.message : "请检查登录状态和模型配置",
        variant: "destructive",
      });
    } finally {
      abortControllerRef.current = null;
      setLoading(false);
    }
  };

  if (!userInfo) {
    return (
      <AuthGuard title="请先登录" description="登录后可以使用 Web 对话、Agent 路由和个性化上下文能力。">
        <div />
      </AuthGuard>
    );
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] bg-gray-50">
      <div className="mx-auto grid h-[calc(100vh-8.5rem)] min-h-[640px] max-w-7xl grid-cols-1 gap-4 px-4 py-4 lg:grid-cols-[280px_minmax(0,1fr)] lg:px-8">
        <aside className="h-fit rounded-lg border bg-white p-4 shadow-sm lg:sticky lg:top-20">
          <div className="flex items-center gap-2 text-base font-semibold text-gray-900">
            <MessageSquare className="h-5 w-5 text-blue-600" />
            AI 对话
          </div>
          <p className="mt-2 text-sm leading-6 text-gray-500">
            支持 Agent 路由、上下文记忆和流式回复。
          </p>

          <div className="mt-5 space-y-4">
            <div>
              <div className="mb-2 text-sm font-medium text-gray-700">Agent</div>
              <Select value={selectedAgent} onValueChange={setSelectedAgent}>
                <SelectTrigger>
                  <SelectValue placeholder="选择 Agent" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={AUTO_AGENT}>自动选择</SelectItem>
                  {agents.map((agent) => (
                    <SelectItem key={agent.agentId} value={agent.agentId}>
                      {agent.intro || agent.agentId}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="rounded-md border bg-gray-50 p-3">
              <div className="text-xs text-gray-500">当前模式</div>
              <div className="mt-1 flex items-center gap-2">
                <Badge variant="outline" className="rounded-md">
                  {selectedAgentLabel}
                </Badge>
              </div>
            </div>

            <div className="rounded-md border border-blue-100 bg-blue-50 p-3 text-sm text-blue-800">
              <div className="flex items-center gap-2 font-medium">
                <Sparkles className="h-4 w-4" />
                流式输出已启用
              </div>
              <div className="mt-1 text-xs leading-5 text-blue-700">
                回答会边生成边展示，长回答无需等待完整返回。
              </div>
            </div>

            <div className="grid gap-2 rounded-md border bg-white p-3">
              <div className="text-sm font-medium text-gray-800">求职工作台</div>
              <Button asChild variant="outline" className="justify-start">
                <Link href="/">
                  <Search className="h-4 w-4" />
                  找校招岗位
                </Link>
              </Button>
              <Button asChild variant="outline" className="justify-start">
                <Link href="/companies">
                  <Building2 className="h-4 w-4" />
                  看公司库
                </Link>
              </Button>
              <Button asChild variant="outline" className="justify-start">
                <Link href="/applications">
                  <ClipboardList className="h-4 w-4" />
                  管投递进度
                </Link>
              </Button>
            </div>

            <Button variant="outline" className="w-full" onClick={handleReset}>
              <Plus className="mr-2 h-4 w-4" />
              新会话
            </Button>
          </div>
        </aside>

        <section className="flex min-h-0 flex-col overflow-hidden rounded-lg border bg-white shadow-sm">
          <div className="flex items-center justify-between border-b px-4 py-3">
            <div>
              <h1 className="text-lg font-semibold text-gray-900">求职派 AI 助手</h1>
              <div className="mt-1 text-sm text-gray-500">
                用户 {userInfo.nickname || userInfo.userId}
              </div>
            </div>
          </div>

          <div
            ref={messagesRef}
            onScroll={handleMessagesScroll}
            className="flex-1 space-y-4 overflow-y-auto overscroll-contain px-4 py-5"
          >
            {messages.map((message) => (
              <div
                key={message.id}
                className={`flex gap-3 ${message.role === "user" ? "justify-end" : "justify-start"}`}
              >
                {message.role === "assistant" && (
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-blue-50 text-blue-700">
                    <Bot className="h-5 w-5" />
                  </div>
                )}
                <div
                  className={`max-w-[min(800px,82vw)] rounded-lg px-4 py-3 text-sm leading-6 ${
                    message.role === "user"
                      ? "whitespace-pre-wrap bg-blue-600 text-white"
                      : "bg-gray-100 text-gray-900"
                  }`}
                >
                  {message.role === "assistant" ? (
                    message.content ? (
                      <MarkdownMessage content={message.content} />
                    ) : (
                      <div className="flex items-center gap-2 text-gray-500">
                        <Loader2 className="h-4 w-4 animate-spin" />
                        正在思考...
                      </div>
                    )
                  ) : (
                    message.content
                  )}
                </div>
                {message.role === "user" && (
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-gray-100 text-gray-700">
                    <User className="h-5 w-5" />
                  </div>
                )}
              </div>
            ))}
          </div>

          <div className="border-t bg-white p-4">
            <div className="flex gap-3">
              <Textarea
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    handleSend();
                  }
                }}
                placeholder="输入你的问题，例如：帮我分析一个 Java 后端求职路线"
                className="max-h-32 min-h-[72px] resize-none"
                disabled={loading}
              />
              <Button className="h-[72px] w-14 shrink-0" onClick={handleSend} disabled={loading || !input.trim()}>
                {loading ? <Loader2 className="h-5 w-5 animate-spin" /> : <Send className="h-5 w-5" />}
              </Button>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
