"use client";

import { useState, useCallback, useEffect } from "react";
import { useLoginModal } from "@/hooks/useLoginModal";
import { Search, Lock } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";

import Link from "next/link";
import {
  fetchJobList,
  JobListResponse,
  GlobalConfigItemValue,
  getUserDetail,
} from "@/lib/api";
import { useRouter } from "next/navigation";
import { useLoginUser } from "@/hooks/useLoginUser";
import { getConfigValue } from "@/lib/config";
import { useToast } from "@/hooks/use-toast";

interface JobOffer {
  id: string | number;
  companyName: string;
  companyType: string;
  companyIndustry: string;
  location: string;
  recruitmentType: string;
  recruitmentTarget: string;
  position: string;
  applicationProgress: string;
  updateTime: string;
  deadline: string;
  relatedLinks: string;
  recruitmentNotice: string;
  referralCode: string;
  notes: string;
  locked?: boolean;
}

const ALL_TAG = "-1";

export default function HomePage() {
  const { toast } = useToast();
  const [currentView, setCurrentView] = useState<"frontend" | "admin">(
    "frontend"
  );
  const [filteredOffers, setFilteredOffers] = useState<JobOffer[]>([]);
  const [searchFilters, setSearchFilters] = useState({
    companyName: "",
    companyType: "", // 公司类型搜索
    companyIndustry: "", // 行业搜索
    location: "",
    recruitmentType: "",
    recruitmentTarget: "",
    position: "",
  });
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 10;
  const [total, setTotal] = useState(0);
  const [locked, setLocked] = useState(false);
  const [online, setOnline] = useState(1);
  const [queryParams, setQueryParams] = useState<any>({});
  const router = useRouter();
  const { setLoginOpen } = useLoginModal();
  const { userInfo, setUserInfo } = useLoginUser();

  const [env, setEnv] = useState<GlobalConfigItemValue[]>([]);
  const [companyTypes, setCompanyTypes] = useState<GlobalConfigItemValue[]>([]);
  const [recruitmentTypes, setRecruitmentTypes] = useState<
    GlobalConfigItemValue[]
  >([]);
  const [recruitmentTarget, setRecruitmentTarget] = useState<
    GlobalConfigItemValue[]
  >([]);

  useEffect(() => {
    getConfigValue("site", "env").then(setEnv);
    getConfigValue("oc", "CompanyTypeEnum").then(setCompanyTypes);
    getConfigValue("oc", "RecruitmentTypeEnum").then(setRecruitmentTypes);
    getConfigValue("oc", "RecruitmentTargetEnum").then(setRecruitmentTarget);
  }, []);

  // 请求岗位数据（带分页）
  const loadJobList = (params: any = {}, page = currentPage) => {
    if (!params.recruitmentType) {
      params.recruitmentType = "实习";
    }

    // 查询实习相关的岗位
    fetchJobList({
      page,
      size: itemsPerPage,
      ...params,
    })
      .then((data: JobListResponse) => {
        const mapped = data.list.map((item: any) => ({
          id: item.id,
          companyName: item.companyName,
          companyType: item.companyType,
          companyIndustry: item.companyIndustry,
          location: item.jobLocation,
          recruitmentType: item.recruitmentType,
          recruitmentTarget: item.recruitmentTarget,
          position: item.position,
          applicationProgress: item.deliveryProgress,
          updateTime: item.lastUpdatedTime
            ? item.lastUpdatedTime.split("T")[0]
            : "",
          deadline: item.deadline,
          relatedLinks: item.relatedLink,
          recruitmentNotice: item.jobAnnouncement,
          referralCode: item.internalReferralCode,
          notes: item.remarks,
        }));
        setLocked(data.locked);
        setFilteredOffers(mapped);
        setTotal(data.total);
        console.log("当前在线人数:", data.online);
        setOnline(data.online ? data.online : 1);
      })
      .catch((err: any) => {
        console.error("获取岗位数据失败", err);
      });
  };

  useEffect(() => {
    handleSearch();
  }, [currentPage, searchFilters]);

  const handleSearch = () => {
    const params = {
      companyName: searchFilters.companyName || undefined,
      companyType:
        (searchFilters.companyType == ALL_TAG
          ? undefined
          : searchFilters.companyType) || undefined,
      jobLocation: searchFilters.location || undefined,
      recruitmentType:
        (searchFilters.recruitmentType == ALL_TAG
          ? undefined
          : searchFilters.recruitmentType) || undefined,
      recruitmentTarget:
        (searchFilters.recruitmentTarget == ALL_TAG
          ? undefined
          : searchFilters.recruitmentTarget) || undefined,
      position: searchFilters.position || undefined,
      companyIndustry: searchFilters.companyIndustry || undefined,
    };
    setQueryParams(params);
    loadJobList(params, currentPage);
  };

  const handleReset = () => {
    setSearchFilters({
      companyName: "",
      companyType: "",
      companyIndustry: "",
      location: "",
      recruitmentType: "",
      recruitmentTarget: "",
      position: "",
    });
    setQueryParams({});
    setCurrentPage(1);
  };

  const totalPages = Math.ceil(total / itemsPerPage);
  const paginatedOffers = filteredOffers; // 直接用接口返回的分页数据

  if (currentView === "admin" && userInfo?.role === 3) {
    // 使用 userInfo 判断
    router.push("/admin");
    return null;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Search Filters */}
      <div className="mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-6 gap-4 mb-4">
            <Input
              placeholder="公司名称"
              value={searchFilters.companyName}
              onChange={(e) =>
                setSearchFilters((prev) => ({
                  ...prev,
                  companyName: e.target.value,
                }))
              }
            />

            {companyTypes && (
              <Select
                value={searchFilters.companyType}
                onValueChange={(value) =>
                  setSearchFilters((prev) => ({ ...prev, companyType: value }))
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="公司类型" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_TAG}>全部公司类型</SelectItem>
                  {companyTypes.map((option) => (
                    <SelectItem
                      key={option.intro as string}
                      value={option.intro as string}
                    >
                      {option.intro}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}

            {recruitmentTypes && (
              <Select
                value={searchFilters.recruitmentType || ""}
                onValueChange={(value) =>
                  setSearchFilters((prev) => ({
                    ...prev,
                    recruitmentType: value,
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="招聘类型" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_TAG}>全部招聘类型</SelectItem>
                  {recruitmentTypes.map((type) => {
                    if (type.intro.includes("实习")) {
                      return (
                        <SelectItem
                          key={type.intro as string}
                          value={type.intro as string}
                        >
                          {type.intro}
                        </SelectItem>
                      );
                    }
                  })}
                </SelectContent>
              </Select>
            )}

            {recruitmentTarget && (
              <Select
                value={searchFilters.recruitmentTarget || ""}
                onValueChange={(value) =>
                  setSearchFilters((prev) => ({
                    ...prev,
                    recruitmentTarget: value,
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="招聘对象" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_TAG}>全部招聘对象</SelectItem>
                  {recruitmentTarget.map((type) => (
                    <SelectItem
                      key={type.intro as string}
                      value={type.intro as string}
                    >
                      {type.intro}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}

            <Input
              placeholder="所属行业"
              value={searchFilters.companyIndustry}
              onChange={(e) =>
                setSearchFilters((prev) => ({
                  ...prev,
                  companyIndustry: e.target.value,
                }))
              }
            />
            <Input
              placeholder="工作地点"
              value={searchFilters.location}
              onChange={(e) =>
                setSearchFilters((prev) => ({
                  ...prev,
                  location: e.target.value,
                }))
              }
            />
            <Input
              placeholder="岗位名称"
              value={searchFilters.position}
              onChange={(e) =>
                setSearchFilters((prev) => ({
                  ...prev,
                  position: e.target.value,
                }))
              }
            />
            <div className="flex space-x-4">
              <Button
                onClick={handleSearch}
                className="bg-blue-500 hover:bg-blue-600"
              >
                <Search className="h-4 w-4 mr-2" />
                查询
              </Button>
              <Button variant="outline" onClick={handleReset}>
                重置
              </Button>
            </div>
          </div>
        </div>

        {/* Job Listings Table */}
        <div className="bg-white rounded-lg shadow overflow-x-auto">
          <Table className="min-w-[1200px] table-fixed">
            <TableHeader>
              <TableRow>
                <TableHead className="w-32 break-words">公司名称</TableHead>
                <TableHead className="w-32 break-words">公司类型</TableHead>
                <TableHead className="w-32 break-words">所属行业</TableHead>
                <TableHead className="w-32 break-words">工作地点</TableHead>
                <TableHead className="w-24 break-words">招聘类型</TableHead>
                <TableHead className="w-28 break-words">招聘对象</TableHead>
                <TableHead className="w-56 break-words">
                  岗位(大都不限专业)
                </TableHead>
                {/* <TableHead className="w-20 break-words">投递进度</TableHead> */}
                <TableHead className="w-24 break-words">更新时间</TableHead>
                <TableHead className="w-24 break-words">投递截止</TableHead>
                <TableHead className="w-32 break-words">相关链接</TableHead>
                <TableHead className="w-32 break-words">招聘公告</TableHead>
                <TableHead className="w-24 break-words">内推码</TableHead>
                <TableHead className="w-32 break-words">备注</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {paginatedOffers.map((offer) => (
                <TableRow
                  key={String(offer.id)}
                  className={`hover:bg-gray-50 cursor-pointer relative ${
                    locked ? "opacity-70" : ""
                  }`}
                  onClick={(e) => {
                    if (locked) {
                      e.preventDefault();
                      e.stopPropagation();
                      if (!userInfo) {
                        setLoginOpen(true);
                      } else {
                        router.push("/user");
                      }
                    }
                  }}
                >
                  <TableCell className="font-medium break-words w-32 relative">
                    <Link
                      href={locked ? "#" : `/job?id=${offer.id}`}
                      className="text-blue-600 hover:underline"
                    >
                      {offer.companyName}
                    </Link>
                  </TableCell>
                  <TableCell className="break-words w-32">
                    <Badge
                      className="rounded-md"
                      variant={
                        offer.companyType === "民企"
                          ? "default"
                          : offer.companyType === "央国企"
                          ? "secondary"
                          : "outline"
                      }
                    >
                      {offer.companyType}
                    </Badge>
                  </TableCell>
                  <TableCell className="break-words w-32">
                    {offer.companyIndustry}
                  </TableCell>
                  <TableCell className="break-words w-32">
                    {offer.location}
                  </TableCell>
                  <TableCell className="break-words w-24">
                    <Badge
                      variant="outline"
                      className="text-pink-600 border-pink-600 rounded-md"
                    >
                      {offer.recruitmentType}
                    </Badge>
                  </TableCell>
                  <TableCell className="break-words w-28">
                    <Badge
                      variant="outline"
                      className="text-blue-600 border-blue-600 rounded-md"
                    >
                      {offer.recruitmentTarget}
                    </Badge>
                  </TableCell>
                  <TableCell className="max-w-xs break-words w-56">
                    <div
                      className="whitespace-pre-line break-words"
                      title={offer.position}
                    >
                      {offer.position}
                    </div>
                  </TableCell>
                  {/* <TableCell className="break-words w-20">{offer.applicationProgress}</TableCell> */}
                  <TableCell className="break-words w-24">
                    {offer.updateTime}
                  </TableCell>
                  <TableCell className="break-words w-24">
                    {offer.deadline}
                  </TableCell>
                  <TableCell className="break-words w-32">
                    <a
                      href={offer.relatedLinks}
                      className="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none bg-blue-500 hover:bg-blue-600 text-white h-8 px-3"
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      投递
                    </a>
                  </TableCell>
                  <TableCell className="break-words w-32">
                    <div className="flex space-x-2">
                      <Button
                        size="sm"
                        variant="outline"
                        className="text-green-600 border-green-600 hover:bg-green-50 bg-transparent"
                      >
                        公告
                      </Button>
                    </div>
                  </TableCell>
                  <TableCell className="break-words w-24">
                    {offer.referralCode}
                  </TableCell>
                  <TableCell className="break-words w-32">
                    {offer.notes}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
            {locked && (
              <div className="absolute inset-0 flex flex-col items-center justify-center bg-gray-200/70 pointer-events-none">
                <Lock className="h-8 w-8 text-red-600 mb-2" />
                <span className="text-sm font-medium text-gray-800">
                  加入会员，即刻解锁全部招聘信息
                </span>
              </div>
            )}
          </Table>
        </div>

        {/* Pagination */}
        <div className="mt-6 flex flex-col items-center">
          <div className="text-sm text-gray-700 mb-2">
            共 {total} 条记录 当前在线人数: {online}
          </div>
          <div>
            <Pagination>
              <PaginationContent>
                <PaginationItem>
                  <PaginationPrevious
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      if (currentPage > 1) setCurrentPage(currentPage - 1);
                    }}
                  />
                </PaginationItem>
                {Array.from({ length: Math.min(5, totalPages) }, (_, i) => (
                  <PaginationItem key={i + 1}>
                    <PaginationLink
                      href="#"
                      isActive={currentPage === i + 1}
                      onClick={(e) => {
                        e.preventDefault();
                        setCurrentPage(i + 1);
                      }}
                    >
                      {i + 1}
                    </PaginationLink>
                  </PaginationItem>
                ))}
                <PaginationItem>
                  <PaginationNext
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      if (currentPage < totalPages)
                        setCurrentPage(currentPage + 1);
                    }}
                  />
                </PaginationItem>
              </PaginationContent>
            </Pagination>
          </div>
        </div>
      </div>
    </div>
  );
}
