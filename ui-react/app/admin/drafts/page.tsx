"use client"
import { useState, useEffect, useMemo } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { DatabaseZap, ExternalLink, FileSearch, X } from "lucide-react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { fetchDraftList, DraftItem, DraftListQuery, DraftListResponse, batchPublishDrafts, updateDraft, deleteDraft, GlobalConfigItemValue } from "@/lib/api"
import { useToast } from "@/hooks/use-toast"
import { getConfigValue } from "@/lib/config"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import {
    Pagination,
    PaginationContent,
    PaginationEllipsis,
    PaginationItem,
    PaginationLink,
    PaginationNext,
    PaginationPrevious,
} from "@/components/ui/pagination"

const PAGE_SIZE = 10

function sanitizeDraftIds(value: string | null) {
    if (!value) {
        return ""
    }

    return Array.from(
        new Set(
            value
                .split(",")
                .map((item) => item.trim())
                .filter((item) => /^\d+$/.test(item))
        )
    ).join(",")
}

function sanitizeNumberParam(value: string | null) {
    return value && /^\d+$/.test(value) ? value : ""
}

export default function DraftsPage() {
    const router = useRouter()
    const searchParams = useSearchParams()
    const linkedDraftIds = useMemo(() => sanitizeDraftIds(searchParams.get("draftIds")), [searchParams])
    const sourceTaskId = useMemo(() => sanitizeNumberParam(searchParams.get("sourceTaskId")), [searchParams])
    const sourceId = useMemo(() => sanitizeNumberParam(searchParams.get("sourceId")), [searchParams])
    const linkedDraftIdList = useMemo(() => linkedDraftIds ? linkedDraftIds.split(",") : [], [linkedDraftIds])
    const initialFilters = useMemo<DraftListQuery>(
        () => ({
            ...(linkedDraftIds ? { draftIds: linkedDraftIds } : {}),
            ...(sourceId ? { sourceId: Number(sourceId) } : {}),
            ...(sourceTaskId ? { sourceTaskId: Number(sourceTaskId) } : {}),
        }),
        [linkedDraftIds, sourceId, sourceTaskId]
    )
    const [drafts, setDrafts] = useState<DraftItem[]>([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [editingDraft, setEditingDraft] = useState<DraftItem | null>(null)
    const [isAddingNew, setIsAddingNew] = useState(false)
    const [page, setPage] = useState(1)
    const [total, setTotal] = useState(0)
    const [filters, setFilters] = useState<DraftListQuery>(initialFilters)
    const [selectedIds, setSelectedIds] = useState<number[]>([])
    const [publishLoading, setPublishLoading] = useState(false)
    const [publishOneLoadingId, setPublishOneLoadingId] = useState<number | null>(null)
    const { toast } = useToast();
    const [companyTypes, setCompanyTypes] = useState<GlobalConfigItemValue[]>([]);
    const [recruitmentTypes, setRecruitmentTypes] = useState<GlobalConfigItemValue[]>([]);
    const [recruitmentTarget, setRecruitmentTarget] = useState<GlobalConfigItemValue[]>([]);
    const [processStates, setProcessStates] = useState<GlobalConfigItemValue[]>([]);
    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

    const hasSourceContext = Boolean(linkedDraftIds || sourceId || sourceTaskId)

    // AIDEV-NOTE: AI-GENERATED task link filter
    useEffect(() => {
        setFilters((prev) => {
            const next = { ...prev }
            if (linkedDraftIds) {
                next.draftIds = linkedDraftIds
            } else {
                delete next.draftIds
            }
            if (sourceId) {
                next.sourceId = Number(sourceId)
            } else {
                delete next.sourceId
            }
            if (sourceTaskId) {
                next.sourceTaskId = Number(sourceTaskId)
            } else {
                delete next.sourceTaskId
            }
            if (
                prev.draftIds === next.draftIds &&
                prev.sourceId === next.sourceId &&
                prev.sourceTaskId === next.sourceTaskId
            ) {
                return prev
            }
            return next
        })
        setSelectedIds([])
        setPage(1)
    }, [linkedDraftIds, sourceId, sourceTaskId])

    const fetchData = async (params: DraftListQuery = {}) => {
        setLoading(true)
        setError(null)
        try {
            const res: DraftListResponse = await fetchDraftList({ page, size: PAGE_SIZE, ...filters, ...params })
            setDrafts(res.list)
            setTotal(res.total)
        } catch (e: any) {
            setError(e.message)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        fetchData()
        getConfigValue('oc', 'CompanyTypeEnum').then(setCompanyTypes);
        getConfigValue('oc', 'RecruitmentTypeEnum').then(setRecruitmentTypes);
        getConfigValue('oc', 'RecruitmentTargetEnum').then(setRecruitmentTarget);
        getConfigValue('oc', 'DraftProcessEnum').then(setProcessStates);
        // eslint-disable-next-line
    }, [page, filters])

    const handleFilterChange = (key: keyof DraftListQuery, value: string) => {
        if (value == '-1') {
            value = ''
        }
        setFilters((prev) => ({ ...prev, [key]: value }))
        setPage(1)
    }

    const clearTaskDraftFilter = () => {
        const params = new URLSearchParams(searchParams.toString())
        params.delete("draftIds")
        params.delete("sourceTaskId")
        params.delete("sourceId")
        const query = params.toString()
        router.replace(query ? `/admin/drafts?${query}` : "/admin/drafts")
    }

    const handleDelete = async (id: number) => {
        await deleteDraft(id).then(e => {
            // 删除数据
            setDrafts(drafts.filter((draft) => draft.id !== id))
            toast({ title: "删除成功！" })
        }).catch(err => {
            toast({ title: "删除失败！", description: err.message, variant: "destructive" })
        })
    }

    const ifUrlValide = (url: string) => {
        const urlRegex = /^https?:\/\/.+|^$|^-$/;
        return urlRegex.test(url);
    }

    const validateUrl = (url: string) => {
        return ifUrlValide(url) ? url : "-";
    }

    const handleSave = async (draft: DraftItem) => {
        // 在保存时，需要校验链接的合法性
        setEditingDraft(validateDraftUrls(draft));

        if (isAddingNew) {
            setDrafts([
                ...drafts,
                { ...draft, id: Date.now(), updateTime: new Date().toISOString().split("T")[0] },
            ])
            setIsAddingNew(false)
            setEditingDraft(null)
        } else {

            await updateDraft({ ...draft, updateTime: new Date().toISOString().split("T")[0] })
                .then(res => {
                    draft['toProcess'] = 0;
                    setDrafts(
                        drafts.map((d) =>
                            d.id === draft.id ? { ...draft, updateTime: new Date().toISOString().split("T")[0] } : d
                        )
                    )
                    setEditingDraft(null)
                    toast({ title: "保存成功！" })

                }).catch(err => {
                    toast({ title: "保存失败！", description: err.message, variant: "destructive" })
                })
        }
    }

    const validateDraftUrls = (draft: DraftItem | null): DraftItem | null => {
        if (!draft) return null;
        return {
            ...draft,
            relatedLink: validateUrl(draft.relatedLink),
            jobAnnouncement: validateUrl(draft.jobAnnouncement)
        };
    }

    const checkSelectAll = () => {
        // 判断是否全选了
        if (drafts.length > 0) {
            return selectedIds.length === drafts.filter(d => d.toProcess !== 1).length
        } else {
            return false;
        }
    }

    const handleSelectAll = (checked: boolean) => {
        if (checked) {
            setSelectedIds(drafts.filter(d => d.toProcess !== 1).map(d => d.id))
        } else {
            setSelectedIds([])
        }
    }
    const handleSelectOne = (id: number, checked: boolean) => {
        setSelectedIds(prev => checked ? [...prev, id] : prev.filter(i => i !== id))
    }

    const handlePublishOne = async (id: number) => {
        setPublishOneLoadingId(id)
        try {
            await doPublish([id])
        } finally {
            setPublishOneLoadingId(null)
        }
    }

    const handlePublish = async () => {
        if (selectedIds?.length === 0) return
        setPublishLoading(true)
        try {
            doPublish(selectedIds)
            setSelectedIds([])
        } finally {
            setPublishLoading(false)
        }
    }

    const doPublish = async (ids: number[]) => {
        try {
            await batchPublishDrafts(ids)
            toast({ title: "发布成功！" })
            await fetchData() // 发布后刷新列表
        } catch (e: any) {
            toast({ title: "发布失败", description: e?.message || "未知错误", variant: "destructive" })
        }
    }

    return (
        <div className="min-h-screen bg-surface-muted">
            <div className="mx-auto max-w-[1440px] px-6 py-6">
                <div className="mb-4 flex flex-wrap items-center justify-between gap-4 rounded-lg border border-surface-border bg-white p-4 shadow-sm">
                    <div className="flex flex-wrap items-center gap-2">
                    <Input placeholder="公司名称" className="w-32" value={filters.companyName || ''} onChange={e => handleFilterChange('companyName', e.target.value)} />
                    {
                        companyTypes && (
                            <Select value={filters.companyType} onValueChange={value => handleFilterChange('companyType', value)}>
                                <SelectTrigger className="w-32"><SelectValue placeholder="公司类型" /></SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="-1">全部</SelectItem>
                                    {companyTypes.map(option => (
                                        <SelectItem key={option.intro as string} value={option.intro as string}>{option.intro}</SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        )
                    }

                    {recruitmentTypes && (
                        <Select value={filters.recruitmentType || ''} onValueChange={value => handleFilterChange('recruitmentType', value)}>
                            <SelectTrigger className="w-32"><SelectValue placeholder="招聘类型" /></SelectTrigger>
                            <SelectContent>
                                <SelectItem value="-1">全部</SelectItem>
                                {recruitmentTypes.map(type => (
                                    <SelectItem key={type.intro as string} value={type.intro as string}>
                                        {type.intro}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    )}
                    <Input placeholder="工作地点" className="w-24" value={filters.jobLocation || ''} onChange={e => handleFilterChange('jobLocation', e.target.value)} />
                    <Input placeholder="岗位" className="w-24" value={filters.position || ''} onChange={e => handleFilterChange('position', e.target.value)} />
                    <Select value={filters.toProcess || ''} onValueChange={value => handleFilterChange('toProcess', value)}>
                        <SelectTrigger className="w-32"><SelectValue placeholder="处理状态" /></SelectTrigger>
                        <SelectContent>
                            <SelectItem value="-1">全部</SelectItem>
                            {
                                processStates.map(option => (
                                    <SelectItem key={option.value as string} value={option.value as string}>{option.intro}</SelectItem>
                                ))
                            }
                        </SelectContent>
                    </Select>
                    </div>
                    <Button onClick={handlePublish} disabled={publishLoading || selectedIds?.length === 0}>
                        {publishLoading ? "发布中..." : "同步职位"}
                    </Button>
                </div>
                {hasSourceContext && (
                    <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-800">
                        <div className="flex min-w-0 items-center gap-3">
                            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-blue-200 bg-white text-blue-600">
                                <FileSearch className="h-4 w-4" />
                            </div>
                            <div className="min-w-0">
                                <div className="font-medium">
                                    {sourceTaskId ? `来自采集任务 #${sourceTaskId}` : sourceId ? `来自采集源 #${sourceId}` : "来自采集链路"}
                                </div>
                                <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs">
                                    <span className="text-blue-700">
                                        {linkedDraftIds ? `仅显示 ${linkedDraftIdList.length} 条关联草稿` : "按来源/任务筛选草稿"}
                                    </span>
                                    {linkedDraftIdList.slice(0, 8).map((id) => (
                                        <span key={id} className="rounded-md bg-white px-1.5 py-0.5 font-medium text-blue-700">
                                            #{id}
                                        </span>
                                    ))}
                                    {linkedDraftIdList.length > 8 && (
                                        <span className="text-blue-700">+{linkedDraftIdList.length - 8}</span>
                                    )}
                                </div>
                            </div>
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                            {sourceId && (
                                <Button asChild size="sm" variant="outline" className="h-8 gap-1.5 border-blue-200 bg-white text-blue-700 hover:bg-blue-50">
                                    <Link href={`/admin/sources/detail?id=${sourceId}`}>
                                        <DatabaseZap className="h-3.5 w-3.5" />
                                        来源 #{sourceId}
                                    </Link>
                                </Button>
                            )}
                            {sourceTaskId && (
                                <Button asChild size="sm" variant="outline" className="h-8 gap-1.5 border-blue-200 bg-white text-blue-700 hover:bg-blue-50">
                                    <Link href={`/admin/entry?tab=tasks${sourceId ? `&sourceId=${sourceId}` : ""}`}>
                                        <ExternalLink className="h-3.5 w-3.5" />
                                        任务队列
                                    </Link>
                                </Button>
                            )}
                            <Button
                                size="sm"
                                variant="outline"
                                className="h-8 border-blue-200 bg-white text-blue-700 hover:bg-blue-50"
                                onClick={clearTaskDraftFilter}
                            >
                                <X className="h-3.5 w-3.5" />
                                查看全部
                            </Button>
                        </div>
                    </div>
                )}
                {/* 只让表格区域可横向滚动 */}
                <div className="bg-white rounded-lg shadow overflow-x-auto">
                    <Table className="min-w-[1720px] table-fixed text-sm">
                        <TableHeader className="bg-blue-50">
                            <TableRow>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">
                                    <input type="checkbox" checked={checkSelectAll()}
                                        onChange={e => handleSelectAll(e.target.checked)} />
                                </TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">来源</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">公司名称</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">公司类型</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">所属行业</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">工作地点</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">招聘类型</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">招聘对象</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">岗位</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">更新时间</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">状态</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">待处理</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">链接</TableHead>
                                <TableHead className="whitespace-nowrap text-center text-blue-600">公告</TableHead>
                                <TableHead className="sticky right-0 z-10 whitespace-nowrap text-center w-[220px] bg-blue-50 text-blue-600">操作</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {loading ? (
                                <TableRow><TableCell colSpan={14}>加载中...</TableCell></TableRow>
                            ) : error ? (
                                <TableRow><TableCell colSpan={14} className="text-red-600">{error}</TableCell></TableRow>
                            ) : drafts?.length === 0 ? (
                                <TableRow><TableCell colSpan={14}>暂无数据</TableCell></TableRow>
                            ) : (
                                drafts.map((draft) => (
                                    <TableRow key={draft.id}>
                                        <TableCell className="whitespace-nowrap text-center"><input type="checkbox" disabled={draft.toProcess == 1} checked={selectedIds.includes(draft.id)} onChange={e => handleSelectOne(draft.id, e.target.checked)} /></TableCell>
                                        <TableCell className="whitespace-nowrap text-center">
                                            <div className="flex justify-center gap-1.5">
                                                {draft.sourceId ? (
                                                    <Button asChild size="sm" variant="outline" className="h-7 gap-1 border-blue-100 bg-blue-50 px-2 text-xs text-blue-700 hover:bg-blue-100">
                                                        <Link href={`/admin/sources/detail?id=${draft.sourceId}`}>
                                                            <DatabaseZap className="h-3 w-3" />
                                                            #{draft.sourceId}
                                                        </Link>
                                                    </Button>
                                                ) : (
                                                    <span className="text-content-muted">-</span>
                                                )}
                                                {draft.sourceTaskId && (
                                                    <Button asChild size="sm" variant="outline" className="h-7 gap-1 border-slate-200 bg-white px-2 text-xs text-slate-700 hover:bg-slate-50">
                                                        <Link href={`/admin/entry?tab=tasks${draft.sourceId ? `&sourceId=${draft.sourceId}` : ""}`}>
                                                            <ExternalLink className="h-3 w-3" />
                                                            任务
                                                        </Link>
                                                    </Button>
                                                )}
                                            </div>
                                        </TableCell>
                                        <TableCell className="whitespace-nowrap text-center max-w-[320px] truncate break-all" title={draft.companyName}>{draft.companyName}</TableCell>
                                        <TableCell className="whitespace-nowrap text-center"><Badge variant="secondary">{draft.companyType}</Badge></TableCell>
                                        <TableCell className="whitespace-nowrap text-center max-w-[320px] truncate break-all" title={draft.companyIndustry}>{draft.companyIndustry}</TableCell>
                                        <TableCell className="whitespace-nowrap text-center max-w-[240px] truncate" title={draft.jobLocation}>{draft.jobLocation}</TableCell>
                                        <TableCell className="whitespace-nowrap text-center max-w-[240px] truncate" title={draft.recruitmentType}>{draft.recruitmentType}</TableCell>
                                        <TableCell className="whitespace-nowrap text-center max-w-[240px] truncate" title={draft.recruitmentTarget}>{draft.recruitmentTarget}</TableCell>
                                        <TableCell className="whitespace-nowrap text-center max-w-[360px] truncate break-all" title={draft.position}>{draft.position}</TableCell>
                                        <TableCell className="whitespace-nowrap text-center max-w-[240px] truncate" title={draft.lastUpdatedTime}>{draft.lastUpdatedTime}</TableCell>
                                        <TableCell className="whitespace-nowrap text-center"><Badge variant={draft.state == 1 ? "default" : "outline"}>{draft.state == 1 ? '同步过' : '未同步'}</Badge></TableCell>
                                        <TableCell className="whitespace-nowrap text-center"><Badge variant={draft.toProcess == 1 ? "default" : "secondary"}>{draft.toProcess == 1 ? '已更新' : '待更新'}</Badge></TableCell>
                                        <TableCell className="whitespace-nowrap text-center max-w-[360px] truncate break-all" title={draft.relatedLink}>
                                            <a href={draft.relatedLink} className="text-blue-800 underline" target="_blank" rel="noopener noreferrer">{draft.relatedLink}</a>
                                        </TableCell>
                                        <TableCell className="whitespace-nowrap text-center max-w-[360px] truncate break-all" title={draft.jobAnnouncement}>
                                            <a href={draft.jobAnnouncement} className="text-blue-800 underline" target="_blank" rel="noopener noreferrer">{draft.jobAnnouncement}</a>
                                        </TableCell>
                                        <TableCell className='bg-white sticky right-0 z-10 whitespace-nowrap text-center w-[220px]'>
                                            <div className="flex justify-center space-x-2">
                                                {
                                                    draft.toProcess != 1 && (
                                                        <Button size="sm" className="bg-orange-500 hover:bg-orange-600 text-white" onClick={() => handlePublishOne(draft.id)} disabled={publishOneLoadingId === draft.id}>
                                                            {publishOneLoadingId === draft.id ? "发布中..." : "发布"}
                                                        </Button>
                                                    )
                                                }

                                                <Button size="sm" variant="outline" onClick={() => setEditingDraft(draft)}>
                                                    编辑
                                                </Button>
                                                <Button size="sm" variant="destructive" onClick={() => handleDelete(draft.id)}>
                                                    删除
                                                </Button>
                                            </div>
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </div>
            </div>
            {/* 分页 */}
            <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                <div className="text-sm text-content-tertiary">
                    共 <span className="font-semibold text-content-primary">{total}</span> 条草稿
                </div>
                <Pagination>
                    <PaginationContent>
                        <PaginationItem>
                            <PaginationPrevious
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault()
                                    if (page > 1) {
                                        setPage(page - 1)
                                    }
                                }}
                                className={page <= 1 ? "pointer-events-none opacity-50" : ""}
                            />
                        </PaginationItem>
                        <PaginationItem>
                            <span className="text-sm text-muted-foreground">
                                第 {page} /{totalPages} 页
                            </span>
                        </PaginationItem>
                        <PaginationItem>
                            <PaginationNext
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault()
                                    if (page * PAGE_SIZE < total) {
                                        setPage(page + 1)
                                    }
                                }}
                                className={page * PAGE_SIZE >= total ? "pointer-events-none opacity-50" : ""}
                            />
                        </PaginationItem>
                    </PaginationContent>
                </Pagination>
            </div>
            {/* 编辑/新增弹窗保持原样 */}
            {editingDraft && (
                <Dialog
                    open={true}
                    onOpenChange={() => {
                        setEditingDraft(null)
                        setIsAddingNew(false)
                    }}
                >
                    <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
                        <DialogHeader>
                            <DialogTitle>{isAddingNew ? "添加新草稿" : "编辑草稿"}</DialogTitle>
                        </DialogHeader>
                        <div className="grid grid-cols-2 gap-4 py-4">
                            <div>
                                <label className="text-sm font-medium">业务主键ID</label>
                                <Input value={editingDraft.id} disabled />
                            </div>
                            <div>
                                <label className="text-sm font-medium">公司名称</label>
                                <Input value={editingDraft.companyName} onChange={e => setEditingDraft({ ...editingDraft, companyName: e.target.value })} />
                            </div>
                            <div>
                                <label className={companyTypes.some(item => item.intro === editingDraft.companyType) ? "text-sm font-medium" : "text-sm font-medium text-red-500"}>公司类型</label>
                                <Select value={editingDraft?.companyType || ""} onValueChange={(v) => setEditingDraft({ ...editingDraft, companyType: v })}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="请选择公司类型" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {companyTypes.map((type) => (
                                            <SelectItem value={type.intro as string} key={type.intro as string}>{type.intro}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div>
                                <label className="text-sm font-medium">所属行业</label>
                                <Input value={editingDraft.companyIndustry} onChange={e => setEditingDraft({ ...editingDraft, companyIndustry: e.target.value })} />
                            </div>
                            <div>
                                <label className="text-sm font-medium">工作地点</label>
                                <Input value={editingDraft.jobLocation} onChange={e => setEditingDraft({ ...editingDraft, jobLocation: e.target.value })} />
                            </div>
                            <div>
                                <label className={recruitmentTypes.some(item => item.intro === editingDraft.recruitmentType) ? "text-sm font-medium" : "text-sm font-medium text-red-500"}>招聘类型</label>
                                <Select value={editingDraft.recruitmentType || ""} onValueChange={(value) => setEditingDraft({ ...editingDraft, recruitmentType: value })}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="请选择招聘类型" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {recruitmentTypes.map((type) => (
                                            <SelectItem key={type.intro as string} value={type.intro as string}>
                                                {type.intro}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div>
                                <label className={recruitmentTarget.some(item => item.intro === editingDraft.recruitmentTarget) ? "text-sm font-medium" : "text-sm font-medium text-red-500"}>招聘对象</label>
                                <Select value={editingDraft.recruitmentTarget || ""}
                                    onValueChange={(value) => setEditingDraft({ ...editingDraft, recruitmentTarget: value })}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="请选择招聘对象" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {recruitmentTarget.map((type) => (
                                            <SelectItem key={type.intro as string} value={type.intro as string}>
                                                {type.intro}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div>
                                <label className="text-sm font-medium">岗位</label>
                                <Input value={editingDraft.position} onChange={e => setEditingDraft({ ...editingDraft, position: e.target.value })} />
                            </div>
                            <div>
                                <label className="text-sm font-medium">更新时间</label>
                                <Input value={editingDraft.lastUpdatedTime} onChange={e => setEditingDraft({ ...editingDraft, lastUpdatedTime: e.target.value })} />
                            </div>
                            <div>
                                <label className="text-sm font-medium">投递链接</label>
                                <Input value={editingDraft.relatedLink} onChange={e => {
                                    const value = e.target.value;
                                    setEditingDraft({ ...editingDraft, relatedLink: value });
                                }} className={!ifUrlValide(editingDraft.relatedLink) ? "border-red-500" : ""} />
                            </div>
                            <div>
                                <label className="text-sm font-medium">公告链接</label>
                                <Input value={editingDraft.jobAnnouncement} onChange={e => {
                                    const value = e.target.value;
                                    setEditingDraft({ ...editingDraft, jobAnnouncement: value });
                                }} className={!ifUrlValide(editingDraft.jobAnnouncement) ? "border-red-500" : ""} />
                            </div>
                            <div className="w-full">
                                <label className="text-sm font-medium">内推码</label>
                                <Input value={editingDraft.internalReferralCode} onChange={e => setEditingDraft({ ...editingDraft, internalReferralCode: e.target.value })} />
                            </div>
                            <div className="w-full">
                                <label className="text-sm font-medium">备注</label>
                                <Input value={editingDraft.remarks} onChange={e => setEditingDraft({ ...editingDraft, remarks: e.target.value })} />
                            </div>
                        </div>
                        <div className="flex justify-end space-x-2">
                            <Button
                                variant="outline"
                                onClick={() => {
                                    setEditingDraft(null)
                                    setIsAddingNew(false)
                                }}
                            >
                                取消
                            </Button>
                            <Button onClick={() => handleSave(editingDraft)}>保存</Button>
                            <Button className="bg-orange-500 hover:bg-orange-600 text-white" onClick={async () => {
                                // 先保存，然后再发布
                                await handleSave(editingDraft)
                                handlePublishOne(editingDraft.id)
                                setEditingDraft(null)
                            }} disabled={publishOneLoadingId === editingDraft.id}>
                                {publishOneLoadingId === editingDraft.id ? "发布中..." : "发布"}
                            </Button>
                        </div>
                    </DialogContent>
                </Dialog>
            )}
        </div>
    )
}
