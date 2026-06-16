"use client";
import { useEffect, useState } from "react";
import { fetchLlmCalls, fetchLlmOverview, LlmCall, LlmOverview } from "@/lib/api";
export default function LlmUsageSection() {
  const [overview,setOverview]=useState<LlmOverview|null>(null); const [calls,setCalls]=useState<LlmCall[]>([]);
  useEffect(()=>{fetchLlmOverview(false).then(setOverview);fetchLlmCalls(false).then(r=>setCalls(r.list))},[]);
  return <div className="space-y-6"><h2 className="text-xl font-bold">模型用量</h2>
    <div className="grid grid-cols-4 gap-4"><Stat label="调用次数" value={overview?.calls??0}/><Stat label="成功率" value={`${(overview?.successRate??0).toFixed(1)}%`}/><Stat label="平均耗时" value={`${(overview?.averageDurationMs??0).toFixed(0)} ms`}/><Stat label="预估费用" value={`$${overview?.estimatedCost??0}`}/></div>
    <div className="overflow-x-auto"><table className="w-full text-sm"><thead><tr className="border-b text-left"><th className="p-2">时间</th><th>Agent</th><th>用途</th><th>状态</th><th>耗时</th><th>Token</th><th>预估费用</th></tr></thead><tbody>{calls.map(c=><tr key={c.id} className="border-b"><td className="p-2">{new Date(c.createTime).toLocaleString()}</td><td>{c.agent||"-"}</td><td>{c.operation}</td><td>{c.outcome}</td><td>{c.durationMs} ms</td><td>{c.totalTokens??"-"}</td><td>{c.estimatedCost==null?"-":`$${c.estimatedCost}`}</td></tr>)}</tbody></table></div>
  </div>;
}
function Stat({label,value}:{label:string,value:string|number}){return <div className="rounded border bg-white p-4"><div className="text-gray-500">{label}</div><div className="mt-2 text-2xl font-bold">{value}</div></div>}
