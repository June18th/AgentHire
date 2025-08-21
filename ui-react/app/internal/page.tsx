"use client"

import React, { useState } from "react";
import { Button } from "@/components/ui/button";
import { Pagination } from "@/components/ui/pagination";
import { useToast } from "@/hooks/use-toast";

// 模拟内推码数据
const referralCodes = [
  { company: "腾讯", code: "ZXH7FEII" },
  { company: "影石Insta360", code: "1XZH4H5" },
  { company: "合合信息", code: "ESVM9B" },
  { company: "远景能源", code: "NTAfaVW" },
  { company: "卡尔动力", code: "XGX5QK6" },
  { company: "同花顺2026届校招提前批", code: "SL5316" },
  { company: "恒玄科技", code: "IZBCV9" },
  { company: "京东", code: "C4PCB" },
  { company: "途游游戏", code: "DStzkk9e" },
];

export default function InternalReferralPage() {
  const [searchQuery, setSearchQuery] = useState("");
const [filteredCodes, setFilteredCodes] = useState(referralCodes);
const [currentPage, setCurrentPage] = useState(1);
const itemsPerPage = 5;
const { toast } = useToast();

  const handleSearch = () => {
    if (!searchQuery.trim()) {
      setFilteredCodes(referralCodes);
      return;
    }

    const filtered = referralCodes.filter(item =>
      item.company.toLowerCase().includes(searchQuery.toLowerCase())
    );
    setFilteredCodes(filtered);
  };

  const handleReset = () => {
    setSearchQuery("");
    setFilteredCodes(referralCodes);
  };

  const copyToClipboard = (code: string) => {
    navigator.clipboard.writeText(code);
    toast({ title: "复制成功", description: `内推码 ${code} 已复制到剪贴板` });
  };

  return (
    <div className="container mx-auto px-4 py-8 max-w-4xl">
      <div className="flex flex-col md:flex-row gap-4 items-center justify-between mb-6">
        <div className="relative w-full md:w-1/2">
          <input
            type="text"
            placeholder="搜索企业名称..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full px-4 py-2 border rounded-l-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div className="flex gap-2 w-full md:w-auto justify-center md:justify-end">
          <Button
            onClick={handleSearch}
            className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2 rounded"
          >
            搜索
          </Button>
          <Button
            onClick={handleReset}
            className="bg-gray-200 hover:bg-gray-300 text-gray-700 px-6 py-2 rounded"
          >
            重置
          </Button>
        </div>
      </div>

      <div className="bg-blue-50 p-4 rounded-md mb-6 text-center">
        <p className="text-blue-700 font-medium">
          共找到 {filteredCodes.length} 条内推码(内推码来自身边朋友+网友投喂)
        </p>
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full bg-white border border-gray-200 rounded-lg">
          <thead>
            <tr className="bg-gray-50 border-b">
              <th className="py-3 px-4 text-left text-gray-700 font-medium">企业名称</th>
              <th className="py-3 px-4 text-left text-gray-700 font-medium">内推码</th>
              <th className="py-3 px-4 text-left text-gray-700 font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            {filteredCodes
              .slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
              .map((item, index) => (
                <tr key={index} className={index % 2 === 0 ? "bg-white" : "bg-gray-50"}>
                  <td className="py-3 px-4 border-b border-gray-200">{item.company}</td>
                  <td className="py-3 px-4 border-b border-gray-200 font-mono bg-gray-50">{item.code}</td>
                  <td className="py-3 px-4 border-b border-gray-200">
                    <Button
                      onClick={() => copyToClipboard(item.code)}
                      size="sm"
                      className="bg-gray-100 hover:bg-gray-200 text-gray-700"
                    >
                      复制
                    </Button>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
        
      </div>
      <div className="flex justify-center mt-4">
        <div className="inline-flex rounded-md shadow-sm" aria-label="Pagination">
          <button
            type="button"
            onClick={() => setCurrentPage(page => Math.max(1, page - 1))}
            disabled={currentPage === 1}
            className="relative inline-flex items-center px-2 py-2 rounded-l-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 focus:z-20 focus:outline-offset-0"
          >
            上一页
          </button>
          {[...Array(Math.ceil(filteredCodes.length / itemsPerPage))].map((_, index) => (
            <button
              key={index}
              type="button"
              onClick={() => setCurrentPage(index + 1)}
              className={`relative inline-flex items-center px-4 py-2 border ${currentPage === index + 1 ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-gray-300 bg-white text-gray-500 hover:bg-gray-50'} text-sm font-medium focus:z-20 focus:outline-offset-0`}
            >
              {index + 1}
            </button>
          ))}
          <button
            type="button"
            onClick={() =>
              setCurrentPage(page =>
                Math.min(Math.ceil(filteredCodes.length / itemsPerPage), page + 1)
              )
            }
            disabled={currentPage === Math.ceil(filteredCodes.length / itemsPerPage)}
            className="relative inline-flex items-center px-2 py-2 rounded-r-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 focus:z-20 focus:outline-offset-0"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  );
}