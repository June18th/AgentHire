import { api, type UserListItem, type UserListQuery, type UserListResponse, fetchUserList } from "@/lib/api"

export type { UserListItem, UserListQuery, UserListResponse }
export { fetchUserList }

export interface RbacRole {
  id: number
  roleCode: string
  roleName: string
  intro?: string
  state: number
  createTime?: string | number
  updateTime?: string | number
}

export interface RbacPermission {
  id: number
  permissionCode: string
  permissionName: string
  resource?: string
  action?: string
  intro?: string
  state: number
  createTime?: string | number
  updateTime?: string | number
}

export async function fetchRbacRoles(): Promise<RbacRole[]> {
  const res = await api.get("/api/admin/user/roles")
  if (res.data && res.data.code === 0) {
    return res.data.data || []
  }
  throw new Error(res.data?.msg || "获取角色列表失败")
}

export async function fetchRbacPermissions(): Promise<RbacPermission[]> {
  const res = await api.get("/api/admin/user/permissions")
  if (res.data && res.data.code === 0) {
    return res.data.data || []
  }
  throw new Error(res.data?.msg || "获取权限列表失败")
}

export async function grantRbacRole(userId: number, roleCode: string): Promise<boolean> {
  const res = await api.post(
    "/api/admin/user/grantRole",
    new URLSearchParams({ userId: String(userId), roleCode }),
    { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
  )
  if (res.data && res.data.code === 0) {
    return res.data.data === true
  }
  throw new Error(res.data?.msg || "授予角色失败")
}

export async function revokeRbacRole(userId: number, roleCode: string): Promise<boolean> {
  const res = await api.post(
    "/api/admin/user/revokeRole",
    new URLSearchParams({ userId: String(userId), roleCode }),
    { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
  )
  if (res.data && res.data.code === 0) {
    return res.data.data === true
  }
  throw new Error(res.data?.msg || "撤销角色失败")
}
