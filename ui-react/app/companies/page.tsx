"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { BriefcaseBusiness, Building2, MapPin, Search, Sparkles } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { fetchJobList, type JobListResponse } from "@/lib/api";

interface CompanySummary {
  companyName: string;
  companyType?: string;
  companyIndustry?: string;
  locations: string[];
  recruitmentTypes: string[];
  positions: string[];
  jobCount: number;
  latestUpdate?: string;
}

const PAGE_SIZE = 80;

function normalizeText(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeDate(value: unknown) {
  const text = normalizeText(value);
  return text.includes("T") ? text.split("T")[0] : text;
}

function normalizeCity(value: unknown) {
  const text = normalizeText(value);
  return text.split(/[·,，、\s/]+/)[0] || "";
}

function hasInternshipType(company: CompanySummary) {
  return company.recruitmentTypes.some((type) => type.includes("实习"));
}

function hasCampusType(company: CompanySummary) {
  return company.recruitmentTypes.some((type) => !type.includes("实习"));
}

function companyJobsHref(company: CompanySummary) {
  const companyName = encodeURIComponent(company.companyName);
  return hasCampusType(company) ? `/?companyName=${companyName}` : `/internship?companyName=${companyName}`;
}

export default function CompaniesPage() {
  const [companies, setCompanies] = useState<CompanySummary[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadCompanies = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const data: JobListResponse = await fetchJobList({
        page: 1,
        size: PAGE_SIZE,
      });
      const summaryByName = new Map<string, CompanySummary>();

      data.list.forEach((item: any) => {
        const companyName = normalizeText(item.companyName);
        if (!companyName) {
          return;
        }

        const current =
          summaryByName.get(companyName) ||
          {
            companyName,
            companyType: normalizeText(item.companyType),
            companyIndustry: normalizeText(item.companyIndustry),
            locations: [],
            recruitmentTypes: [],
            positions: [],
            jobCount: 0,
            latestUpdate: "",
          };

        const location = normalizeText(item.jobLocation);
        const recruitmentType = normalizeText(item.recruitmentType);
        const position = normalizeText(item.position);
        const latestUpdate = normalizeDate(item.lastUpdatedTime);

        if (location && !current.locations.includes(location)) {
          current.locations.push(location);
        }
        if (recruitmentType && !current.recruitmentTypes.includes(recruitmentType)) {
          current.recruitmentTypes.push(recruitmentType);
        }
        if (position && !current.positions.includes(position)) {
          current.positions.push(position);
        }
        if (latestUpdate && (!current.latestUpdate || latestUpdate > current.latestUpdate)) {
          current.latestUpdate = latestUpdate;
        }

        current.jobCount += 1;
        summaryByName.set(companyName, current);
      });

      setCompanies(
        Array.from(summaryByName.values()).sort((a, b) => {
          if (b.jobCount !== a.jobCount) {
            return b.jobCount - a.jobCount;
          }
          return a.companyName.localeCompare(b.companyName, "zh-Hans-CN");
        }),
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "公司库加载失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCompanies();
  }, [loadCompanies]);

  const filteredCompanies = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    if (!text) {
      return companies;
    }

    return companies.filter((company) =>
      [
        company.companyName,
        company.companyType,
        company.companyIndustry,
        ...company.locations,
        ...company.recruitmentTypes,
        ...company.positions,
      ]
        .filter(Boolean)
        .some((item) => String(item).toLowerCase().includes(text)),
    );
  }, [companies, keyword]);

  const totalJobs = companies.reduce((sum, company) => sum + company.jobCount, 0);
  const totalLocations = new Set(
    companies
      .flatMap((company) => company.locations.map(normalizeCity))
      .filter(Boolean),
  ).size;

  return (
    <main className="min-h-[calc(100vh-4rem)] bg-gray-50">
      <section className="border-b bg-white">
        <div className="mx-auto max-w-7xl px-6 py-8">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <div className="flex items-center gap-2 text-sm font-medium text-blue-600">
                <Building2 className="h-4 w-4" />
                公司库
              </div>
              <h1 className="mt-3 text-2xl font-semibold text-gray-950">按公司维度发现机会</h1>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-gray-600">
                汇总岗位中的公司、地点、招聘类型和在招职位，适合先看目标公司，再进入岗位列表精准筛选。
              </p>
            </div>

            <div className="grid grid-cols-3 gap-3 text-center">
              <div className="rounded-lg border bg-gray-50 px-4 py-3">
                <div className="text-xl font-semibold text-gray-950">{companies.length}</div>
                <div className="mt-1 text-xs text-gray-500">公司</div>
              </div>
              <div className="rounded-lg border bg-gray-50 px-4 py-3">
                <div className="text-xl font-semibold text-gray-950">{totalJobs}</div>
                <div className="mt-1 text-xs text-gray-500">岗位</div>
              </div>
              <div className="rounded-lg border bg-gray-50 px-4 py-3">
                <div className="text-xl font-semibold text-gray-950">{totalLocations}</div>
                <div className="mt-1 text-xs text-gray-500">城市</div>
              </div>
            </div>
          </div>

          <div className="mt-6 flex max-w-xl items-center gap-3">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
              <Input
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="搜索公司、行业、城市或岗位"
                className="pl-9"
              />
            </div>
            <Button variant="outline" onClick={loadCompanies} disabled={loading}>
              刷新
            </Button>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-6 py-6">
        {error ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div>
        ) : null}

        {loading ? (
          <div className="rounded-lg border bg-white p-8 text-center text-sm text-gray-500">正在加载公司库...</div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {filteredCompanies.map((company) => (
              <article
                key={company.companyName}
                className="rounded-lg border bg-white p-5 shadow-sm transition hover:border-blue-200 hover:shadow-md"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <Link
                      href={companyJobsHref(company)}
                      className="text-lg font-semibold text-gray-950 hover:text-blue-600 hover:underline"
                    >
                      {company.companyName}
                    </Link>
                    <div className="mt-2 flex flex-wrap gap-2">
                      {company.companyType ? <Badge variant="outline">{company.companyType}</Badge> : null}
                      {company.companyIndustry ? <Badge variant="secondary">{company.companyIndustry}</Badge> : null}
                    </div>
                  </div>
                  <Badge className="shrink-0 rounded-md">{company.jobCount} 岗位</Badge>
                </div>

                <div className="mt-4 flex items-center gap-2 text-sm text-gray-600">
                  <MapPin className="h-4 w-4 text-gray-400" />
                  <span className="line-clamp-1">{company.locations.slice(0, 4).join("、") || "地点待补充"}</span>
                </div>

                <div className="mt-4 space-y-3">
                  <div>
                    <div className="text-xs font-medium text-gray-500">招聘类型</div>
                    <div className="mt-1 text-sm text-gray-800">
                      {company.recruitmentTypes.slice(0, 4).join("、") || "待补充"}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs font-medium text-gray-500">热门岗位</div>
                    <div className="mt-1 line-clamp-2 text-sm leading-6 text-gray-800">
                      {company.positions.slice(0, 5).join("、") || "待补充"}
                    </div>
                  </div>
                </div>

                <div className="mt-5 flex items-center justify-between gap-3 border-t pt-4">
                  <div className="flex items-center gap-1 text-xs text-gray-500">
                    <Sparkles className="h-3.5 w-3.5" />
                    {company.latestUpdate ? `更新 ${company.latestUpdate}` : "持续收录中"}
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {hasCampusType(company) ? (
                      <Button asChild size="sm" variant="outline">
                        <Link href={`/?companyName=${encodeURIComponent(company.companyName)}`}>
                          <BriefcaseBusiness className="mr-1.5 h-3.5 w-3.5" />
                          查看岗位
                        </Link>
                      </Button>
                    ) : null}
                    {hasInternshipType(company) ? (
                      <Button asChild size="sm" variant="outline">
                        <Link href={`/internship?companyName=${encodeURIComponent(company.companyName)}`}>实习岗位</Link>
                      </Button>
                    ) : null}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}

        {!loading && filteredCompanies.length === 0 ? (
          <div className="rounded-lg border bg-white p-8 text-center text-sm text-gray-500">没有匹配的公司</div>
        ) : null}
      </section>
    </main>
  );
}
