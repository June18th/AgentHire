"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { RefreshCw, Search, ShieldCheck, UserCog } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useToast } from "@/hooks/use-toast"
import {
  fetchRbacPermissions,
  fetchRbacRoles,
  fetchUserList,
  grantRbacRole,
  revokeRbacRole,
  type RbacPermission,
  type RbacRole,
  type UserListItem,
} from "@/lib/rbac-api"

const PAGE_SIZE = 10

function formatDate(value?: string | number | null) {
  if (!value) return "-"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return "-"
  return date.toLocaleString("zh-CN", { hour12: false })
}

function getUserName(user: UserListItem) {
  return user.displayName || user.email || `用户 ${user.userId}`
}

function isActiveState(state?: number) {
  return state === undefined || state === 1
}

export default function AdminPermissionsPage() {
  const { toast } = useToast()
  const [users, setUsers] = useState<UserListItem[]>([])
  const [roles, setRoles] = useState<RbacRole[]>([])
  const [permissions, setPermissions] = useState<RbacPermission[]>([])
  const [keyword, setKeyword] = useState("")
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [userLoading, setUserLoading] = useState(false)
  const [savingKey, setSavingKey] = useState<string | null>(null)

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  const permissionGroups = useMemo(() => {
    const groups = new Map<string, RbacPermission[]>()
    permissions.forEach((permission) => {
      const resource = permission.resource || "未分组"
      groups.set(resource, [...(groups.get(resource) || []), permission])
    })
    return Array.from(groups.entries()).sort(([a], [b]) => a.localeCompare(b, "zh-CN"))
  }, [permissions])

  const loadUsers = useCallback(async () => {
    setUserLoading(true)
    try {
      const res = await fetchUserList({
        displayName: keyword.trim() || undefined,
        page,
        size: PAGE_SIZE,
      })
      setUsers(res.list || [])
      setTotal(res.total || 0)
    } catch (error) {
      toast({
        title: "用户列表加载失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setUserLoading(false)
    }
  }, [keyword, page, toast])

  const loadMetadata = useCallback(async () => {
    setLoading(true)
    try {
      const [roleList, permissionList] = await Promise.all([fetchRbacRoles(), fetchRbacPermissions()])
      setRoles(roleList)
      setPermissions(permissionList)
    } catch (error) {
      toast({
        title: "权限数据加载失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    loadMetadata()
  }, [loadMetadata])

  useEffect(() => {
    loadUsers()
  }, [loadUsers])

  const handleSearch = () => {
    setPage(1)
    if (page === 1) {
      loadUsers()
    }
  }

  const handleToggleRole = async (user: UserListItem, role: RbacRole, checked: boolean) => {
    const key = `${user.userId}:${role.roleCode}`
    setSavingKey(key)
    try {
      const ok = checked
        ? await grantRbacRole(user.userId, role.roleCode)
        : await revokeRbacRole(user.userId, role.roleCode)
      if (!ok) {
        throw new Error(checked ? "授予角色失败" : "撤销角色失败")
      }

      setUsers((current) =>
        current.map((item) => {
          if (item.userId !== user.userId) return item
          const roleCodes = new Set(item.roleCodes || [])
          if (checked) {
            roleCodes.add(role.roleCode)
          } else {
            roleCodes.delete(role.roleCode)
          }
          return { ...item, roleCodes: Array.from(roleCodes) }
        })
      )
      toast({
        title: checked ? "角色已授予" : "角色已撤销",
        description: `${getUserName(user)} / ${role.roleName || role.roleCode}`,
      })
    } catch (error) {
      toast({
        title: "角色变更失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setSavingKey(null)
    }
  }

  return (
    <div className="min-h-screen bg-surface-muted">
      <div className="mx-auto flex max-w-[1440px] flex-col gap-4 px-6 py-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-xl font-semibold text-content-primary">权限管理</h1>
            <p className="mt-1 text-sm text-content-tertiary">管理用户 RBAC 角色，查看系统角色与权限资源。</p>
          </div>
          <Button
            variant="outline"
            className="h-9 gap-2"
            onClick={() => {
              loadMetadata()
              loadUsers()
            }}
            disabled={loading || userLoading}
          >
            <RefreshCw className={`h-4 w-4 ${loading || userLoading ? "animate-spin" : ""}`} />
            刷新
          </Button>
        </div>

        <div className="grid gap-3 md:grid-cols-3">
          <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm text-content-tertiary">用户数</span>
              <UserCog className="h-4 w-4 text-blue-600" />
            </div>
            <div className="mt-2 text-2xl font-semibold text-content-primary">{total}</div>
          </div>
          <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm text-content-tertiary">角色数</span>
              <ShieldCheck className="h-4 w-4 text-emerald-600" />
            </div>
            <div className="mt-2 text-2xl font-semibold text-content-primary">{roles.length}</div>
          </div>
          <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm text-content-tertiary">权限点</span>
              <ShieldCheck className="h-4 w-4 text-violet-600" />
            </div>
            <div className="mt-2 text-2xl font-semibold text-content-primary">{permissions.length}</div>
          </div>
        </div>

        <Tabs defaultValue="users" className="w-full">
          <TabsList className="bg-white">
            <TabsTrigger value="users">用户授权</TabsTrigger>
            <TabsTrigger value="catalog">角色与权限</TabsTrigger>
          </TabsList>

          <TabsContent value="users" className="mt-4">
            <div className="rounded-lg border border-surface-border bg-white shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-surface-border p-4">
                <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
                  <div className="relative w-full max-w-sm">
                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-content-tertiary" />
                    <Input
                      value={keyword}
                      onChange={(event) => setKeyword(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") handleSearch()
                      }}
                      placeholder="按昵称搜索"
                      className="h-9 pl-9"
                    />
                  </div>
                  <Button className="h-9 gap-2" onClick={handleSearch} disabled={userLoading}>
                    <Search className="h-4 w-4" />
                    查询
                  </Button>
                </div>
                <div className="text-sm text-content-tertiary">
                  第 <span className="font-medium text-content-primary">{page}</span> / {totalPages} 页
                </div>
              </div>

              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow className="bg-blue-50">
                      <TableHead className="w-[90px] text-blue-600">用户 ID</TableHead>
                      <TableHead className="min-w-[180px] text-blue-600">用户</TableHead>
                      <TableHead className="min-w-[220px] text-blue-600">当前角色</TableHead>
                      <TableHead className="min-w-[360px] text-blue-600">授权</TableHead>
                      <TableHead className="w-[180px] text-blue-600">加入时间</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {users.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={5} className="h-28 text-center text-content-tertiary">
                          {userLoading ? "加载中..." : "暂无用户"}
                        </TableCell>
                      </TableRow>
                    ) : (
                      users.map((user) => {
                        const userRoleCodes = new Set(user.roleCodes || [])
                        return (
                          <TableRow key={user.userId} className="hover:bg-gray-50">
                            <TableCell className="font-mono text-xs text-content-secondary">{user.userId}</TableCell>
                            <TableCell>
                              <div className="flex items-center gap-3">
                                {user.avatar ? (
                                  <img
                                    src={user.avatar}
                                    alt=""
                                    className="h-8 w-8 rounded-full border border-surface-border object-cover"
                                  />
                                ) : (
                                  <div className="flex h-8 w-8 items-center justify-center rounded-full border border-surface-border bg-surface-muted text-xs text-content-tertiary">
                                    {getUserName(user).slice(0, 1)}
                                  </div>
                                )}
                                <div className="min-w-0">
                                  <div className="truncate font-medium text-content-primary">{getUserName(user)}</div>
                                  <div className="truncate text-xs text-content-tertiary">{user.email || "-"}</div>
                                </div>
                              </div>
                            </TableCell>
                            <TableCell>
                              <div className="flex flex-wrap gap-1.5">
                                {userRoleCodes.size > 0 ? (
                                  Array.from(userRoleCodes).map((roleCode) => (
                                    <Badge key={roleCode} variant="secondary" className="rounded-md">
                                      {roles.find((role) => role.roleCode === roleCode)?.roleName || roleCode}
                                    </Badge>
                                  ))
                                ) : (
                                  <span className="text-sm text-content-tertiary">未分配</span>
                                )}
                              </div>
                            </TableCell>
                            <TableCell>
                              <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                                {roles.map((role) => {
                                  const key = `${user.userId}:${role.roleCode}`
                                  const checked = userRoleCodes.has(role.roleCode)
                                  return (
                                    <label
                                      key={role.roleCode}
                                      className="flex h-9 items-center gap-2 rounded-md border border-surface-border px-2 text-sm"
                                    >
                                      <Checkbox
                                        checked={checked}
                                        disabled={savingKey === key || !isActiveState(role.state)}
                                        onCheckedChange={(value) => handleToggleRole(user, role, value === true)}
                                      />
                                      <span className="min-w-0 truncate">{role.roleName || role.roleCode}</span>
                                    </label>
                                  )
                                })}
                              </div>
                            </TableCell>
                            <TableCell className="text-sm text-content-secondary">{formatDate(user.createTime)}</TableCell>
                          </TableRow>
                        )
                      })
                    )}
                  </TableBody>
                </Table>
              </div>

              <div className="flex items-center justify-between gap-3 border-t border-surface-border p-4">
                <div className="text-sm text-content-tertiary">
                  共 <span className="font-medium text-content-primary">{total}</span> 个用户
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    className="h-9"
                    disabled={page <= 1 || userLoading}
                    onClick={() => setPage((current) => Math.max(1, current - 1))}
                  >
                    上一页
                  </Button>
                  <Button
                    variant="outline"
                    className="h-9"
                    disabled={page >= totalPages || userLoading}
                    onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
                  >
                    下一页
                  </Button>
                </div>
              </div>
            </div>
          </TabsContent>

          <TabsContent value="catalog" className="mt-4">
            <div className="grid gap-4 lg:grid-cols-[360px_1fr]">
              <div className="rounded-lg border border-surface-border bg-white shadow-sm">
                <div className="border-b border-surface-border p-4">
                  <h2 className="font-semibold text-content-primary">角色</h2>
                </div>
                <div className="divide-y divide-surface-border">
                  {roles.length === 0 ? (
                    <div className="p-4 text-sm text-content-tertiary">暂无角色</div>
                  ) : (
                    roles.map((role) => (
                      <div key={role.roleCode} className="p-4">
                        <div className="flex items-center justify-between gap-3">
                          <div className="min-w-0">
                            <div className="truncate font-medium text-content-primary">{role.roleName}</div>
                            <div className="mt-1 truncate font-mono text-xs text-content-tertiary">{role.roleCode}</div>
                          </div>
                          <Badge variant={isActiveState(role.state) ? "green" : "outline"} className="rounded-md">
                            {isActiveState(role.state) ? "启用" : "停用"}
                          </Badge>
                        </div>
                        {role.intro ? <p className="mt-2 text-sm text-content-secondary">{role.intro}</p> : null}
                      </div>
                    ))
                  )}
                </div>
              </div>

              <div className="rounded-lg border border-surface-border bg-white shadow-sm">
                <div className="border-b border-surface-border p-4">
                  <h2 className="font-semibold text-content-primary">权限资源</h2>
                </div>
                <div className="divide-y divide-surface-border">
                  {permissionGroups.length === 0 ? (
                    <div className="p-4 text-sm text-content-tertiary">暂无权限点</div>
                  ) : (
                    permissionGroups.map(([resource, items]) => (
                      <div key={resource} className="p-4">
                        <div className="mb-3 flex items-center justify-between gap-3">
                          <h3 className="font-medium text-content-primary">{resource}</h3>
                          <span className="text-xs text-content-tertiary">{items.length} 个权限</span>
                        </div>
                        <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
                          {items.map((permission) => (
                            <div
                              key={permission.permissionCode}
                              className="rounded-md border border-surface-border p-3"
                            >
                              <div className="flex items-center justify-between gap-2">
                                <span className="truncate text-sm font-medium text-content-primary">
                                  {permission.permissionName}
                                </span>
                                <Badge variant={isActiveState(permission.state) ? "green" : "outline"} className="rounded-md">
                                  {isActiveState(permission.state) ? "启用" : "停用"}
                                </Badge>
                              </div>
                              <div className="mt-2 truncate font-mono text-xs text-content-tertiary">
                                {permission.permissionCode}
                              </div>
                              {permission.action ? (
                                <div className="mt-2 text-xs text-content-secondary">动作：{permission.action}</div>
                              ) : null}
                              {permission.intro ? (
                                <div className="mt-1 text-xs text-content-tertiary">{permission.intro}</div>
                              ) : null}
                            </div>
                          ))}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  )
}
