"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import {
  CheckCircle2,
  Clipboard,
  ExternalLink,
  FileText,
  Link2,
  Plus,
  Star,
  Trash2,
} from "lucide-react"
import { AuthGuard } from "@/components/auth/AuthGuard"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { useLoginUser } from "@/hooks/useLoginUser"
import { useToast } from "@/hooks/use-toast"

const MATERIAL_KIND_OPTIONS = [
  { value: "portfolio", label: "作品集" },
  { value: "attachment", label: "附件" },
  { value: "certificate", label: "证书" },
  { value: "profile", label: "主页" },
] as const

const DEFAULT_CHECKLIST = [
  "已选择匹配岗位方向的简历版本",
  "已根据 JD 调整关键词和项目顺序",
  "作品集、GitHub、在线文档链接可访问",
  "投递话术或求职信已准备",
  "投递截止日期已记录到日历",
  "后续跟进提醒已设置",
]

type MaterialKind = (typeof MATERIAL_KIND_OPTIONS)[number]["value"]

interface ResumeVersion {
  id: string
  name: string
  targetRole: string
  scenario: string
  link: string
  notes: string
  isPrimary: boolean
  updatedAt: number
}

interface MaterialLink {
  id: string
  title: string
  kind: MaterialKind
  url: string
  note: string
  updatedAt: number
}

interface PitchSnippet {
  id: string
  title: string
  scene: string
  content: string
  updatedAt: number
}

interface ChecklistItem {
  id: string
  label: string
  checked: boolean
}

interface MaterialsState {
  resumes: ResumeVersion[]
  links: MaterialLink[]
  snippets: PitchSnippet[]
  checklist: ChecklistItem[]
}

const EMPTY_STATE: MaterialsState = {
  resumes: [],
  links: [],
  snippets: [],
  checklist: DEFAULT_CHECKLIST.map((label, index) => ({
    id: `default-${index}`,
    label,
    checked: false,
  })),
}

const EMPTY_RESUME_FORM = {
  name: "",
  targetRole: "",
  scenario: "校招投递",
  link: "",
  notes: "",
}

const EMPTY_LINK_FORM: Omit<MaterialLink, "id" | "updatedAt"> = {
  title: "",
  kind: "portfolio",
  url: "",
  note: "",
}

const EMPTY_SNIPPET_FORM = {
  title: "",
  scene: "投递备注",
  content: "",
}

function createId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function formatTime(value?: number) {
  if (!value) return "-"
  return new Date(value).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
}

function safeExternalUrl(value: string) {
  const trimmed = value.trim()
  if (!trimmed) return ""
  if (/^https?:\/\//i.test(trimmed)) return trimmed
  return `https://${trimmed}`
}

function materialKindLabel(kind: MaterialKind) {
  return MATERIAL_KIND_OPTIONS.find((item) => item.value === kind)?.label || kind
}

function loadMaterials(storageKey: string): MaterialsState {
  if (typeof window === "undefined") return EMPTY_STATE
  const raw = localStorage.getItem(storageKey)
  if (!raw) return EMPTY_STATE
  try {
    const parsed = JSON.parse(raw) as Partial<MaterialsState>
    return {
      resumes: parsed.resumes || [],
      links: parsed.links || [],
      snippets: parsed.snippets || [],
      checklist: parsed.checklist?.length ? parsed.checklist : EMPTY_STATE.checklist,
    }
  } catch {
    return EMPTY_STATE
  }
}

export default function MaterialsPage() {
  const { userInfo } = useLoginUser()
  const { toast } = useToast()
  const [loaded, setLoaded] = useState(false)
  const [state, setState] = useState<MaterialsState>(EMPTY_STATE)
  const [resumeForm, setResumeForm] = useState(EMPTY_RESUME_FORM)
  const [linkForm, setLinkForm] = useState(EMPTY_LINK_FORM)
  const [snippetForm, setSnippetForm] = useState(EMPTY_SNIPPET_FORM)
  const storageKey = userInfo ? `jobclaw-materials-${userInfo.userId}` : ""
  const displayName = userInfo?.nickname || `用户${userInfo?.userId || ""}`
  const userInitial = displayName.slice(0, 1) || "U"

  useEffect(() => {
    if (!storageKey) return
    setState(loadMaterials(storageKey))
    setLoaded(true)
  }, [storageKey])

  useEffect(() => {
    if (!loaded || !storageKey) return
    localStorage.setItem(storageKey, JSON.stringify(state))
  }, [loaded, state, storageKey])

  const primaryResume = state.resumes.find((item) => item.isPrimary)
  const checkedCount = state.checklist.filter((item) => item.checked).length
  const checklistProgress = state.checklist.length ? Math.round((checkedCount / state.checklist.length) * 100) : 0
  const recentItems = useMemo(() => {
    return [
      ...state.resumes.map((item) => ({ id: item.id, label: item.name, type: "简历", time: item.updatedAt })),
      ...state.links.map((item) => ({ id: item.id, label: item.title, type: materialKindLabel(item.kind), time: item.updatedAt })),
      ...state.snippets.map((item) => ({ id: item.id, label: item.title, type: "话术", time: item.updatedAt })),
    ]
      .sort((a, b) => b.time - a.time)
      .slice(0, 5)
  }, [state.links, state.resumes, state.snippets])

  const addResume = () => {
    if (!resumeForm.name.trim()) {
      toast({ title: "请填写简历名称", variant: "destructive" })
      return
    }
    const next: ResumeVersion = {
      id: createId("resume"),
      name: resumeForm.name.trim(),
      targetRole: resumeForm.targetRole.trim(),
      scenario: resumeForm.scenario.trim() || "校招投递",
      link: resumeForm.link.trim(),
      notes: resumeForm.notes.trim(),
      isPrimary: state.resumes.length === 0,
      updatedAt: Date.now(),
    }
    setState((current) => ({ ...current, resumes: [next, ...current.resumes] }))
    setResumeForm(EMPTY_RESUME_FORM)
    toast({ title: "已添加简历版本" })
  }

  const addLink = () => {
    if (!linkForm.title.trim() || !linkForm.url.trim()) {
      toast({ title: "请填写材料名称和链接", variant: "destructive" })
      return
    }
    const next: MaterialLink = {
      id: createId("link"),
      title: linkForm.title.trim(),
      kind: linkForm.kind,
      url: safeExternalUrl(linkForm.url),
      note: linkForm.note.trim(),
      updatedAt: Date.now(),
    }
    setState((current) => ({ ...current, links: [next, ...current.links] }))
    setLinkForm(EMPTY_LINK_FORM)
    toast({ title: "已添加材料链接" })
  }

  const addSnippet = () => {
    if (!snippetForm.title.trim() || !snippetForm.content.trim()) {
      toast({ title: "请填写话术标题和内容", variant: "destructive" })
      return
    }
    const next: PitchSnippet = {
      id: createId("snippet"),
      title: snippetForm.title.trim(),
      scene: snippetForm.scene.trim() || "投递备注",
      content: snippetForm.content.trim(),
      updatedAt: Date.now(),
    }
    setState((current) => ({ ...current, snippets: [next, ...current.snippets] }))
    setSnippetForm(EMPTY_SNIPPET_FORM)
    toast({ title: "已保存话术" })
  }

  const setPrimaryResume = (id: string) => {
    setState((current) => ({
      ...current,
      resumes: current.resumes.map((item) => ({ ...item, isPrimary: item.id === id, updatedAt: item.id === id ? Date.now() : item.updatedAt })),
    }))
  }

  const deleteResume = (id: string) => {
    setState((current) => {
      const nextResumes = current.resumes.filter((item) => item.id !== id)
      if (nextResumes.length && !nextResumes.some((item) => item.isPrimary)) {
        nextResumes[0] = { ...nextResumes[0], isPrimary: true }
      }
      return { ...current, resumes: nextResumes }
    })
  }

  const copyText = async (text: string) => {
    if (!text.trim()) return
    await navigator.clipboard.writeText(text)
    toast({ title: "已复制到剪贴板" })
  }

  const resetChecklist = () => {
    setState((current) => ({ ...current, checklist: current.checklist.map((item) => ({ ...item, checked: false })) }))
  }

  if (!userInfo) {
    return (
      <AuthGuard title="请先登录" description="登录后可以维护简历版本、作品集链接、常用投递话术和投递前检查清单。">
        <div />
      </AuthGuard>
    )
  }

  return (
    <main className="min-h-[calc(100vh-4rem)] bg-surface-muted">
      <div className="mx-auto max-w-[1440px] px-6 py-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 text-sm font-medium text-blue-700">
              <FileText className="h-4 w-4" />
              简历与材料
            </div>
            <h1 className="mt-1 text-2xl font-semibold text-content-primary">投递材料工作台</h1>
            <p className="mt-2 text-sm text-content-tertiary">
              维护简历版本、作品集链接和常用话术，投递前快速确认材料是否就绪。
            </p>
          </div>
          <Button asChild variant="outline" className="gap-2">
            <Link href="/applications">
              <ExternalLink className="h-4 w-4" />
              查看投递
            </Link>
          </Button>
        </div>

        <section className="mt-5 flex flex-wrap items-center justify-between gap-4 rounded-lg border border-surface-border bg-white p-4 shadow-sm">
          <div className="flex min-w-0 items-center gap-4">
            <Avatar className="h-14 w-14 border">
              <AvatarImage src={userInfo.avatar} alt={displayName} />
              <AvatarFallback>{userInitial}</AvatarFallback>
            </Avatar>
            <div className="min-w-0">
              <div className="truncate text-base font-semibold text-content-primary">{displayName}</div>
              <div className="mt-1 text-sm text-content-tertiary">投递身份 ID：{userInfo.userId}</div>
            </div>
          </div>
          <div className="grid gap-1 text-sm text-content-secondary sm:text-right">
            <span>当前主简历：{primaryResume?.name || "未设置"}</span>
            <span>材料完整度：{checklistProgress}%</span>
          </div>
        </section>

        <section className="mt-5 grid gap-4 lg:grid-cols-4">
          <SummaryBlock label="主简历" value={primaryResume?.name || "未设置"} hint={primaryResume?.targetRole || "添加后可设为默认"} />
          <SummaryBlock label="简历版本" value={`${state.resumes.length}`} hint="按岗位方向维护" />
          <SummaryBlock label="材料链接" value={`${state.links.length}`} hint="作品集 / 附件 / 证书" />
          <SummaryBlock label="检查进度" value={`${checklistProgress}%`} hint={`${checkedCount}/${state.checklist.length} 项已完成`} />
        </section>

        <div className="mt-5 grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
          <div className="grid gap-4">
            <section className="rounded-lg border border-surface-border bg-white shadow-sm">
              <SectionHeader icon={<FileText className="h-4 w-4" />} title="简历版本" count={state.resumes.length} />
              <div className="grid gap-4 p-4 lg:grid-cols-[360px_minmax(0,1fr)]">
                <div className="rounded-lg border border-surface-border bg-gray-50 p-4">
                  <FormTitle title="新增简历" />
                  <div className="mt-3 grid gap-3">
                    <Input placeholder="简历名称，例如：后端开发-校招版" value={resumeForm.name} onChange={(event) => setResumeForm({ ...resumeForm, name: event.target.value })} />
                    <Input placeholder="目标岗位，例如：Java 后端开发" value={resumeForm.targetRole} onChange={(event) => setResumeForm({ ...resumeForm, targetRole: event.target.value })} />
                    <Input placeholder="使用场景，例如：校招投递" value={resumeForm.scenario} onChange={(event) => setResumeForm({ ...resumeForm, scenario: event.target.value })} />
                    <Input placeholder="在线文档或网盘链接" value={resumeForm.link} onChange={(event) => setResumeForm({ ...resumeForm, link: event.target.value })} />
                    <Textarea placeholder="版本说明、关键词、适配岗位" value={resumeForm.notes} onChange={(event) => setResumeForm({ ...resumeForm, notes: event.target.value })} />
                    <Button className="gap-2" onClick={addResume}>
                      <Plus className="h-4 w-4" />
                      添加简历
                    </Button>
                  </div>
                </div>

                <div className="grid gap-3">
                  {state.resumes.map((item) => (
                    <div key={item.id} className="rounded-lg border border-surface-border p-4">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2">
                            <h2 className="truncate text-base font-semibold text-content-primary">{item.name}</h2>
                            {item.isPrimary ? <Badge className="rounded-md">默认</Badge> : null}
                          </div>
                          <div className="mt-1 text-sm text-content-tertiary">
                            {item.targetRole || "未填写岗位"} / {item.scenario || "未填写场景"}
                          </div>
                        </div>
                        <div className="flex shrink-0 gap-2">
                          <Button variant="outline" size="icon" title="设为默认" onClick={() => setPrimaryResume(item.id)}>
                            <Star className={`h-4 w-4 ${item.isPrimary ? "fill-current text-amber-500" : ""}`} />
                          </Button>
                          {item.link ? (
                            <Button asChild variant="outline" size="icon">
                              <a href={safeExternalUrl(item.link)} target="_blank" rel="noreferrer" title="打开链接">
                                <ExternalLink className="h-4 w-4" />
                              </a>
                            </Button>
                          ) : null}
                          <Button variant="outline" size="icon" title="删除" onClick={() => deleteResume(item.id)}>
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                      {item.notes ? <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-content-secondary">{item.notes}</p> : null}
                      <div className="mt-3 text-xs text-content-tertiary">更新于 {formatTime(item.updatedAt)}</div>
                    </div>
                  ))}
                  {!state.resumes.length ? <EmptyPanel text="还没有简历版本，先添加一份主简历。" /> : null}
                </div>
              </div>
            </section>

            <section className="grid gap-4 lg:grid-cols-2">
              <div className="rounded-lg border border-surface-border bg-white shadow-sm">
                <SectionHeader icon={<Link2 className="h-4 w-4" />} title="作品集与附件" count={state.links.length} />
                <div className="p-4">
                  <div className="grid gap-3 rounded-lg border border-surface-border bg-gray-50 p-4">
                    <FormTitle title="新增材料" />
                    <Input placeholder="材料名称" value={linkForm.title} onChange={(event) => setLinkForm({ ...linkForm, title: event.target.value })} />
                    <Select value={linkForm.kind} onValueChange={(value: MaterialKind) => setLinkForm({ ...linkForm, kind: value })}>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {MATERIAL_KIND_OPTIONS.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Input placeholder="链接地址" value={linkForm.url} onChange={(event) => setLinkForm({ ...linkForm, url: event.target.value })} />
                    <Textarea placeholder="备注，例如适用岗位、查看权限" value={linkForm.note} onChange={(event) => setLinkForm({ ...linkForm, note: event.target.value })} />
                    <Button className="gap-2" onClick={addLink}>
                      <Plus className="h-4 w-4" />
                      添加材料
                    </Button>
                  </div>

                  <div className="mt-4 grid gap-3">
                    {state.links.map((item) => (
                      <div key={item.id} className="rounded-lg border border-surface-border p-3">
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                              <h3 className="truncate font-medium text-content-primary">{item.title}</h3>
                              <Badge variant="secondary" className="rounded-md">{materialKindLabel(item.kind)}</Badge>
                            </div>
                            {item.note ? <p className="mt-2 text-sm leading-6 text-content-secondary">{item.note}</p> : null}
                          </div>
                          <div className="flex shrink-0 gap-2">
                            <Button asChild variant="outline" size="icon">
                              <a href={item.url} target="_blank" rel="noreferrer" title="打开链接">
                                <ExternalLink className="h-4 w-4" />
                              </a>
                            </Button>
                            <Button variant="outline" size="icon" title="删除" onClick={() => setState((current) => ({ ...current, links: current.links.filter((link) => link.id !== item.id) }))}>
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </div>
                        </div>
                      </div>
                    ))}
                    {!state.links.length ? <EmptyPanel text="把作品集、GitHub、证书或网盘材料放在这里。" /> : null}
                  </div>
                </div>
              </div>

              <div className="rounded-lg border border-surface-border bg-white shadow-sm">
                <SectionHeader icon={<Clipboard className="h-4 w-4" />} title="常用投递话术" count={state.snippets.length} />
                <div className="p-4">
                  <div className="grid gap-3 rounded-lg border border-surface-border bg-gray-50 p-4">
                    <FormTitle title="新增话术" />
                    <Input placeholder="标题，例如：内推私信开场" value={snippetForm.title} onChange={(event) => setSnippetForm({ ...snippetForm, title: event.target.value })} />
                    <Input placeholder="场景，例如：Boss 直聘 / 邮件 / 内推" value={snippetForm.scene} onChange={(event) => setSnippetForm({ ...snippetForm, scene: event.target.value })} />
                    <Textarea className="min-h-28" placeholder="可直接复制使用的内容" value={snippetForm.content} onChange={(event) => setSnippetForm({ ...snippetForm, content: event.target.value })} />
                    <Button className="gap-2" onClick={addSnippet}>
                      <Plus className="h-4 w-4" />
                      保存话术
                    </Button>
                  </div>

                  <div className="mt-4 grid gap-3">
                    {state.snippets.map((item) => (
                      <div key={item.id} className="rounded-lg border border-surface-border p-3">
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <div className="font-medium text-content-primary">{item.title}</div>
                            <div className="mt-1 text-xs text-content-tertiary">{item.scene}</div>
                          </div>
                          <div className="flex shrink-0 gap-2">
                            <Button variant="outline" size="icon" title="复制" onClick={() => copyText(item.content)}>
                              <Clipboard className="h-4 w-4" />
                            </Button>
                            <Button variant="outline" size="icon" title="删除" onClick={() => setState((current) => ({ ...current, snippets: current.snippets.filter((snippet) => snippet.id !== item.id) }))}>
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </div>
                        </div>
                        <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-content-secondary">{item.content}</p>
                      </div>
                    ))}
                    {!state.snippets.length ? <EmptyPanel text="保存常用开场、投递备注、邮件正文，投递时直接复制。" /> : null}
                  </div>
                </div>
              </div>
            </section>
          </div>

          <aside className="grid gap-4 self-start">
            <section className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="text-base font-semibold text-content-primary">投递前检查</h2>
                  <p className="mt-1 text-xs text-content-tertiary">每次投递前快速过一遍。</p>
                </div>
                <Badge variant="outline" className="rounded-md">{checklistProgress}%</Badge>
              </div>
              <div className="mt-4 grid gap-3">
                {state.checklist.map((item) => (
                  <label key={item.id} className="flex items-start gap-3 rounded-md border border-surface-border p-3 text-sm text-content-secondary">
                    <Checkbox
                      checked={item.checked}
                      onCheckedChange={(checked) =>
                        setState((current) => ({
                          ...current,
                          checklist: current.checklist.map((entry) => (entry.id === item.id ? { ...entry, checked: checked === true } : entry)),
                        }))
                      }
                    />
                    <span className={item.checked ? "text-content-tertiary line-through" : ""}>{item.label}</span>
                  </label>
                ))}
              </div>
              <Button variant="outline" className="mt-4 w-full gap-2" onClick={resetChecklist}>
                <CheckCircle2 className="h-4 w-4" />
                重置检查项
              </Button>
            </section>

            <section className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
              <h2 className="text-base font-semibold text-content-primary">最近更新</h2>
              <div className="mt-3 grid gap-2">
                {recentItems.map((item) => (
                  <div key={item.id} className="rounded-md border border-surface-border px-3 py-2">
                    <div className="flex items-center justify-between gap-3">
                      <span className="truncate text-sm font-medium text-content-primary">{item.label}</span>
                      <Badge variant="secondary" className="shrink-0 rounded-md">{item.type}</Badge>
                    </div>
                    <div className="mt-1 text-xs text-content-tertiary">{formatTime(item.time)}</div>
                  </div>
                ))}
                {!recentItems.length ? <div className="text-sm text-content-tertiary">暂无材料更新</div> : null}
              </div>
            </section>
          </aside>
        </div>
      </div>
    </main>
  )
}

function SummaryBlock({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
      <div className="text-sm font-medium text-content-tertiary">{label}</div>
      <div className="mt-2 truncate text-2xl font-semibold text-content-primary" title={value}>{value}</div>
      <div className="mt-1 truncate text-xs text-content-tertiary" title={hint}>{hint}</div>
    </div>
  )
}

function SectionHeader({ icon, title, count }: { icon: React.ReactNode; title: string; count: number }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-surface-border px-4 py-3">
      <div className="flex items-center gap-2 text-base font-semibold text-content-primary">
        <span className="text-blue-700">{icon}</span>
        {title}
      </div>
      <Badge variant="secondary" className="rounded-md">{count}</Badge>
    </div>
  )
}

function FormTitle({ title }: { title: string }) {
  return <div className="text-sm font-semibold text-content-primary">{title}</div>
}

function EmptyPanel({ text }: { text: string }) {
  return <div className="rounded-lg border border-dashed border-surface-border p-6 text-center text-sm text-content-tertiary">{text}</div>
}
