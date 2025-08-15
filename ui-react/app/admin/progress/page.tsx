"use client"
import { useState, useRef, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Textarea } from "@/components/ui/textarea"
import { submitAIAgentTaskSSE } from "@/lib/api"

// 进度节点类型定义
interface ProgressNode {
  id: string;
  title: string;
  description: string;
}

export default function ProgressPage() {
  // 当前进度节点索引
  const [currentStep, setCurrentStep] = useState(0);

  // 进度节点定义
  const progressNodes: ProgressNode[] = [
    {
      id: "entry",
      title: "录入任务",
      description: "输入任务信息并上传相关文件"
    },
    {
      id: "classification",
      title: "任务分类",
      description: "系统自动分类任务类型"
    },
    {
      id: "extraction",
      title: "数据提取",
      description: "从上传文件中提取关键数据"
    },
    {
      id: "cleaning",
      title: "数据清洗",
      description: "清洗并标准化提取的数据"
    },
    {
      id: "publishing",
      title: "发布上线",
      description: "数据审核通过后发布上线"
    }
  ];

  // 输入框状态
  const [inputValue, setInputValue] = useState("");
  // 上传文件状态
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 处理输入变化
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setInputValue(e.target.value);
  };
  

  // 处理文件选择
  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
    }
  };

  // 处理拖拽上传
  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      setSelectedFile(e.dataTransfer.files[0]);
    }
  };

  // 处理粘贴上传
  const handlePaste = (e: React.ClipboardEvent<HTMLDivElement>) => {
    const items = e.clipboardData?.items;
    if (items) {
      for (let i = 0; i < items.length; i++) {
        if (items[i].kind === "file") {
          setSelectedFile(items[i].getAsFile());
          break;
        }
      }
    }
  };

  // 清除选中的文件
  const clearSelectedFile = () => {
    setSelectedFile(null);
  };

  // 进入下一步
  const handleNextStep = () => {
    if (currentStep < progressNodes.length - 1) {
      setCurrentStep(currentStep + 1);
    }
  };

  const [logs, setLogs] = useState<string[]>([]);

  const handleSubmitTask = async () => {
    if (!inputValue && !selectedFile) return;

    try {
      // 清空之前的日志
      setLogs([]);
      
      // 直接调用SSE连接函数，该函数会处理参数和请求
      await setupSSEConnection();
    } catch (error) {
      addLog(`提交失败: ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  // 添加日志的辅助函数
  const addLog = (message: string, type: 'info' | 'error' = 'info') => {
    setLogs(prevLogs => [...prevLogs, `[${new Date().toLocaleTimeString()}] ${message}`]);
    console.log(`[日志] ${message}`);
  };

  // 设置SSE连接的函数
  const [sseController, setSseController] = useState<AbortController | null>(null);

  // 使用fetch API实现POST方式的SSE
  const setupSSEConnection = async () => {
    // 取消之前的连接
    if (sseController) {
      sseController.abort();
    }

    const newController = new AbortController();
    setSseController(newController);

    try {
      // 准备参数
      const params = {
        model: 'ZhiPu',
        type: '0',
        file: selectedFile,
        content: inputValue
      };

      // 调用SSE API
      submitAIAgentTaskSSE(
        params,
        (line) => {
          console.log('接收到SSE消息:', line);
          try {
            const data = JSON.parse(line);
            addLog(data.message || JSON.stringify(data), data.type);

            // 如果任务完成，关闭连接
            if (data.status === 'completed' || data.status === 'failed') {
              newController.abort();
              setSseController(null);
              addLog(`任务${data.status === 'completed' ? '成功完成' : '失败'}`);
            }
          } catch (error) {
            addLog(line, 'info');
          }
        },
        (error) => {
          // 处理错误
          addLog(`请求失败: ${error.message}`, 'error');
        },
        () => {
          // 完成回调
          setSseController(null);
          addLog('SSE连接已完成');
        },
        newController
      );

      addLog('SSE连接已建立，开始接收任务进度...');

      // const reader = response.body?.getReader();
      // if (!reader) {
      //   throw new Error('没有可读流');
      // }

      // const decoder = new TextDecoder();
      // let buffer = '';

      // while (true) {
      //   const { done, value } = await reader.read();
      //   if (done) break;

      //   buffer += decoder.decode(value, { stream: true });

      //   // 处理接收到的SSE消息
      //   let lineEnd;
      //   while ((lineEnd = buffer.indexOf('\n')) !== -1) {
      //     const line = buffer.substring(0, lineEnd).trim();
      //     buffer = buffer.substring(lineEnd + 1);

      //     if (line.startsWith('data: ')) {
      //       const dataStr = line.substring(6);
      //       if (dataStr === '[DONE]') break;

      //       try {
      //         const data = JSON.parse(dataStr);
      //         addLog(`任务进度: ${data.progress || '未知'}, 状态: ${data.status || '未知'}`);

      //         // 如果任务完成，关闭连接
      //         if (data.status === 'completed' || data.status === 'failed') {
      //           newController.abort();
      //           setSseController(null);
      //           addLog(`任务${data.status === 'completed' ? '成功完成' : '失败'}`);
      //           break;
      //         }
      //       } catch (error) {
      //         addLog(`解析SSE消息失败: ${error instanceof Error ? error.message : String(error)}`);
      //       }
      //     }
      //   }
      // }
    } catch (error) {
      if (!newController.signal.aborted) {
        addLog(`SSE连接错误: ${error instanceof Error ? error.message : '未知错误'}`);
      }
      setSseController(null);
    }
  };

  // 组件卸载时关闭SSE连接
  useEffect(() => {
    return () => {
      if (sseController) {
        sseController.abort();
      }
    };
  }, [sseController]);

  // 返回到上一步
  const handlePrevStep = () => {
    if (currentStep > 0) {
      setCurrentStep(currentStep - 1);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b">
        <div className="full-w mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-16">
            <h1 className="text-2xl font-bold text-gray-900">校招派Agent</h1>
            <h3 className="text-sm text-gray-500">任务录入，全自动实现数据提取、清洗、上线流程</h3>
          </div>
        </div>
      </header>
      <div className="full-w mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {/* 进度图 */}
        <div className="mb-8 bg-white rounded-lg shadow p-6 overflow-x-auto">
          <h2 className="text-xl font-semibold mb-6">任务进度流程</h2>
          <div className="flex flex-row items-center justify-between relative min-w-[600px] w-full">
            {/* 连接线 */}
            <div className="absolute top-1/2 left-0 right-0 h-1 bg-gray-200 transform -translate-y-1/2 z-0"></div>
            <div className={`absolute top-1/2 left-0 h-1 bg-blue-500 transform -translate-y-1/2 z-10`} style={{ width: `${(currentStep / (progressNodes.length - 1)) * 100}%` }}></div>

            {/* 进度节点 */}
            {progressNodes.map((node, index) => (
              <div key={node.id} className="relative z-20 flex flex-col items-center" style={{ flex: '1 1 20%' }}>
                <div
                  className={`w-12 h-12 rounded-full flex items-center justify-center mb-2
                    ${index === currentStep
                      ? 'bg-blue-500 text-white' // 当前节点
                      : index < currentStep
                      ? 'bg-green-500 text-white' // 已完成节点
                      : 'bg-gray-200 text-gray-500'}`} // 未完成节点
                >
                  {index + 1}
                </div>
                <div className="text-center">
                  <h3 className="font-medium text-sm sm:text-base">{node.title}</h3>
                  <p className="text-xs sm:text-sm text-gray-500 mt-1 max-w-[120px] sm:max-w-[150px] whitespace-nowrap overflow-hidden text-ellipsis">{node.description}</p>
                </div>
              </div>
            ))}
          </div>

          {/* 当前节点信息 */}
          {/* <div className="mt-8 p-4 bg-blue-50 rounded-md border border-blue-100">
            <div className="flex items-center">
              <Badge className="mr-2" color="blue">当前步骤</Badge>
              <h3 className="text-lg font-semibold">
                {currentStep + 1}. {progressNodes[currentStep].title}
              </h3>
            </div>
            <p className="mt-2 text-blue-700">{progressNodes[currentStep].description}</p>
          </div> */}
        </div>

        {/* 业务区域 */}
        <div className="bg-white rounded-lg shadow p-6">
          {currentStep === 0 ? (
            // 录入任务节点的业务内容
            <div>
              <h2 className="text-xl font-semibold mb-6">任务信息录入</h2>
              <div className="mb-6">
                <label className="block text-sm font-medium mb-2">任务内容</label>
                <Textarea rows={4} value={inputValue} onChange={e => setInputValue(e.target.value)} placeholder="请粘贴职位JD、简历或其他AI任务内容..." />
              </div>
              <div className="mb-6">
                <label className="block text-sm font-medium mb-2">上传文件</label>
                <div
                  className="border border-dashed border-gray-300 rounded-md flex flex-col items-center justify-center cursor-pointer bg-gray-50 hover:bg-gray-100 transition"
                  style={{ maxHeight: 110 }}
                  onClick={() => fileInputRef.current?.click()}
                  onDrop={handleDrop}
                  onDragOver={handleDragOver}
                  onPaste={handlePaste}
                >
                  <input
                    type="file"
                    ref={fileInputRef}
                    className="hidden"
                    onChange={handleFileSelect}
                  />
                  {selectedFile ? (
                    <div className="flex items-center gap-2 px-5 py-3 w-full justify-center">
                      <div className="text-blue-600 font-medium">已选择文件：{selectedFile.name}</div>
                      <button
                        type="button"
                        className="ml-2 px-2 py-0.5 rounded bg-gray-200 hover:bg-gray-300 text-gray-600 text-xs"
                        onClick={clearSelectedFile}
                        title="清除附件"
                      >
                        ×
                      </button>
                    </div>
                  ) : (
                    <div className="flex flex-col items-center justify-center p-6">
                      <div className="flex items-center gap-4 w-full justify-center">
                        <svg className="w-12 h-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
                        </svg>
                        <div className="text-left">
                          <p className="text-sm text-gray-500 mb-1">支持三种上传方式：</p>
                          <ul className="text-xs text-gray-500 space-y-1">
                            <li>• 点击区域选择文件</li>
                            <li>• 将文件拖拽到区域内</li>
                            <li>• 使用 Ctrl+V 粘贴文件</li>
                          </ul>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
              <div className="flex justify-end mt-4">
            <Button onClick={handleSubmitTask} disabled={!inputValue && !selectedFile}>提交</Button>
          </div>
          
          {/* 日志区域 - 只在录入任务步骤显示 */}
          {currentStep === 0 && (
            <div className="mt-6 bg-gray-50 p-4 rounded-md border border-gray-200 h-64 overflow-y-auto">
              <h3 className="font-medium text-gray-700 mb-2">任务日志</h3>
              {logs.length === 0 ? (
                <p className="text-gray-500 text-sm">暂无日志</p>
              ) : (
                <div className="space-y-1 text-sm">
                  {logs.map((log, index) => (
                    <div key={index} className={`
                      ${log.includes('成功') ? 'text-green-600' : ''}
                      ${log.includes('失败') ? 'text-red-600' : ''}
                      ${log.includes('错误') ? 'text-red-600' : ''}
                    `}>
                      {log}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
            </div>
          ) : (
            // 其他节点的业务内容
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <svg className="w-24 h-24 text-gray-300 mb-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
              <h3 className="text-xl font-semibold mb-2">{progressNodes[currentStep].title}</h3>
              <p className="text-gray-500 max-w-md mb-8">
                {currentStep === 1 && "系统正在根据您提供的信息自动分类任务类型，请稍候..."}
                {currentStep === 2 && "系统正在从上传的文件中提取关键数据，请稍候..."}
                {currentStep === 3 && "系统正在清洗并标准化提取的数据，请稍候..."}
                {currentStep === 4 && "数据已审核通过并成功发布上线！"}
              </p>
              <div className="flex gap-3">
                <Button variant="outline" onClick={handlePrevStep} disabled={currentStep === 0}>
                  上一步
                </Button>
                {currentStep < progressNodes.length - 1 && (
                  <Button onClick={handleNextStep}>继续</Button>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}