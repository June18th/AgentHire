"use client";
import { useState, useEffect } from "react";
import { useParams } from "next/navigation";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import { fetchCouponDetail, CouponDetailListResponse } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import { useToast } from "@/hooks/use-toast";
import { useSearchParams } from "next/navigation";
export default function CouponDetailPage() {
  // 获取路由参数
  const searchParams = useSearchParams();
  // 初始加载数据
  useEffect(() => {
    const id = searchParams.get("code");
    console.log("从请求参数中获取code", id);
    setCouponId(id || "");
  }, [searchParams]);

  const [couponId, setCouponId] = useState("");

  // 优惠券使用记录数据
  const [couponDetailList, setCouponDetailList] = useState<any[]>([]);
  // 加载状态
  const [loading, setLoading] = useState(false);
  // 分页状态
  const [pagination, setPagination] = useState({ page: 1, size: 10, total: 0 });
  // 提示框
  const { toast } = useToast();

  // 获取优惠券详情
  const fetchCouponDetailData = async () => {
    setLoading(true);
    try {
      // 调用API获取优惠券详情
      let cid = couponId;
      if (!couponId) {
        cid = searchParams.get("code") || "";
      }
      const response = await fetchCouponDetail({
        coupon: cid,
        page: pagination.page,
        size: pagination.size,
      });
      // 更新优惠券详情列表和分页信息
      setCouponDetailList(response.list);
      setPagination((prev) => ({
        ...prev,
        total: response.total,
      }));
    } catch (error) {
      toast({ title: "获取优惠券详情失败", variant: "destructive" });
      console.error("Failed to fetch coupon detail:", error);
    } finally {
      setLoading(false);
    }
  };

  // 处理分页变更
  const handlePageChange = (newPage: number) => {
    setPagination((prev) => ({
      ...prev,
      page: newPage,
    }));
  };

  // 初始加载数据
  useEffect(() => {
    fetchCouponDetailData();
  }, [pagination.page, pagination.size]);

  return (
    <div className="container mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold mb-6">优惠券详情 - {couponId}</h1>

      <div className="overflow-x-auto mb-6">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>支付ID</TableHead>
              <TableHead>交易号</TableHead>
              <TableHead>充值金额</TableHead>
              <TableHead>充值级别</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>支付时间</TableHead>
              <TableHead>三方交易号</TableHead>
              <TableHead>优惠金额</TableHead>
              <TableHead>用户昵称</TableHead>
              <TableHead>用户头像</TableHead>
              <TableHead>用户角色</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {couponDetailList.map((item, index) => (
              <TableRow key={index}>
                <TableCell>{item.payId}</TableCell>
                <TableCell>{item.tradeNo}</TableCell>
                <TableCell>{item.amount}</TableCell>
                <TableCell>{item.level}</TableCell>
                <TableCell>{item.status === 1 ? "成功" : "失败"}</TableCell>
                <TableCell>{formatDateTimeStr(item.payTime)}</TableCell>
                <TableCell>{item.transactionId}</TableCell>
                <TableCell>{item.promotionAmount}</TableCell>
                <TableCell>{item.user?.nickName || "-"}</TableCell>
                <TableCell>
                  {item.user?.avatar ? (
                    <img
                      src={item.user.avatar}
                      alt="用户头像"
                      className="w-8 h-8 rounded-full"
                    />
                  ) : (
                    "-"
                  )}
                </TableCell>
                <TableCell>
                  {item.user?.role === 0
                    ? "普通用户"
                    : item.user?.role === 1
                    ? "管理员"
                    : "-"}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {/* 分页组件 */}
      <div className="flex justify-center">
        <Pagination>
          <PaginationContent>
            <PaginationItem>
              <PaginationPrevious
                onClick={() => handlePageChange(pagination.page - 1)}
                disabled={pagination.page === 1}
              />
            </PaginationItem>
            {Array.from(
              { length: Math.ceil(pagination.total / pagination.size) },
              (_, i) => i + 1
            ).map((page) => (
              <PaginationItem key={page}>
                <Button
                  variant={page === pagination.page ? "default" : "outline"}
                  size="sm"
                  onClick={() => handlePageChange(page)}
                >
                  {page}
                </Button>
              </PaginationItem>
            ))}
            <PaginationItem>
              <PaginationNext
                onClick={() => handlePageChange(pagination.page + 1)}
                disabled={
                  pagination.page >=
                  Math.ceil(pagination.total / pagination.size)
                }
              />
            </PaginationItem>
          </PaginationContent>
        </Pagination>
      </div>
    </div>
  );
}
