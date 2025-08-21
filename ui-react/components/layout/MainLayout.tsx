import React from 'react';

export default function MainLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* 顶部导航栏 */}
      <header className="bg-white border-b">
        <div className="px-10">
          <div className="flex justify-between items-center h-16">
            <div className="flex items-center space-x-8">
              <div className="flex items-center">
                <a href="/" className="text-2xl font-bold text-blue-600">🚦校招派</a>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* 正文内容 */}
      <main className="flex-grow">{children}</main>
    </div>
  );
}