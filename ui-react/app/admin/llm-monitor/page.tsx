"use client";
import {useEffect, useState} from "react";
import {fetchLlmCalls, fetchLlmOverview, LlmCall, LlmOverview} from "@/lib/api";

export default function LlmMonitorPage() {
    const [o, setO] = useState<LlmOverview | null>(null);
    const [calls, setCalls] = useState<LlmCall[]>([]);
    useEffect(() => {
        fetchLlmOverview(true).then(setO);
        fetchLlmCalls(true).then(r => setCalls(r.list))
    }, []);
    return <main className="p-8 space-y-6"><h1 className="text-2xl font-bold">大模型监控</h1>
        <div
            className="grid grid-cols-4 gap-4">{[["调用次数", o?.calls ?? 0], ["成功率", `${(o?.successRate ?? 0).toFixed(1)}%`], ["平均耗时", `${(o?.averageDurationMs ?? 0).toFixed(0)} ms`], ["预估费用", `$${o?.estimatedCost ?? 0}`]].map(([k, v]) =>
            <div key={k} className="rounded border bg-white p-4">
                <div className="text-gray-500">{k}</div>
                <div className="text-2xl font-bold">{v}</div>
            </div>)}</div>
        <div className="rounded border bg-white overflow-x-auto">
            <table className="w-full text-sm">
                <thead>
                <tr className="border-b text-left">
                    <th className="p-3">时间</th>
                    <th>用户</th>
                    <th>Agent</th>
                    <th>用途</th>
                    <th>模式</th>
                    <th>状态</th>
                    <th>请求数</th>
                    <th>耗时</th>
                    <th>Token</th>
                    <th>费用</th>
                </tr>
                </thead>
                <tbody>{calls.map(c => <tr className="border-b" key={c.id}>
                    <td className="p-3">{new Date(c.createTime).toLocaleString()}</td>
                    <td>{c.jobClawUserId || "-"}</td>
                    <td>{c.agent || "-"}</td>
                    <td>{c.operation}</td>
                    <td>{c.mode}</td>
                    <td>{c.outcome}</td>
                    <td>{c.requestCount}</td>
                    <td>{c.durationMs} ms</td>
                    <td>{c.totalTokens ?? "-"}</td>
                    <td>{c.estimatedCost == null ? "-" : `$${c.estimatedCost}`}</td>
                </tr>)}</tbody>
            </table>
        </div>
    </main>
}
