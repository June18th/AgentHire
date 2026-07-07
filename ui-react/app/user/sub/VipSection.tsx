import { Button } from "@/components/ui/button";
import {
  GlobalConfigItemValue,
} from "@/lib/api";

interface VipSectionProps {
  userInfo: any;
  vipOptions: GlobalConfigItemValue[];
  rechargeOptions: GlobalConfigItemValue[];
  setMcpConfigDialogOpen: (tag: boolean) => void;
  handleRecharge: (level: string | number) => void;
}

export default function VipSection({ userInfo, vipOptions, rechargeOptions, setMcpConfigDialogOpen, handleRecharge }: VipSectionProps) {
  
  // 会员卡片样式
  const renderVipCard = () => {
    if (!userInfo) return null;
    console.log("userInfo", userInfo);
    const isVip =
      userInfo.role == 2 ||
      (typeof userInfo.vipLevel === "number" && userInfo.vipLevel >= 0);
    // 如果是管理员，则表示终身会员
    const isLife = userInfo.role == 3 || userInfo.vipLevel === 3;
    if (!isVip) {
      // 非会员灰色卡片
      return (
        <div className="relative bg-gradient-to-r from-gray-300 to-gray-400 rounded-2xl shadow text-white p-8 w-full max-w-md mx-auto mb-8 overflow-hidden">
          <div className="text-2xl font-bold mb-2 flex items-center">
            <span className="mr-2">非会员</span>
          </div>
          <div className="text-lg mt-2">{userInfo.displayName}</div>
          <div className="mt-4 flex items-center justify-between">
            <div className="text-sm opacity-80">会员ID: {userInfo.userId}</div>
            <div className="text-sm opacity-80">您还不是会员</div>
          </div>
          <div className="absolute right-6 top-6 text-4xl opacity-10">VIP</div>
        </div>
      );
    }
    // 会员卡片
    const level = isLife ? 3 : userInfo.vipLevel;
    const levelInfo = vipOptions.find((l) => l.value === `${level}`);
    return (
      <div>
        {/* 在右上角，添加一个 MCP配置的按钮 */}
        <div className="flex justify-end">
          <Button
            onClick={() => setMcpConfigDialogOpen(true)}
            className="bg-gradient-to-r from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700 text-white font-bold py-2 px-4 rounded-full shadow-lg hover:shadow-xl transition-all duration-300 transform hover:scale-105"
          >
            MCP配置
          </Button>
        </div>
        <div className="relative bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 rounded-2xl shadow-xl text-white p-8 w-full max-w-md mx-auto mb-8 overflow-hidden">
          <div className="text-2xl font-bold mb-2 flex items-center">
            <span className="mr-2">{levelInfo?.intro}</span>
            {/* <span className="text-lg font-normal">{levelInfo?.intro}</span> */}
          </div>
          <div className="text-lg mt-2">{userInfo.displayName}</div>
          <div className="mt-4 flex items-center justify-between">
            <div className="text-sm opacity-80">会员ID: {userInfo.userId}</div>
            <div className="text-sm opacity-80">
              {isLife
                ? "永久有效"
                : `到期日: ${
                    userInfo.expireTime
                      ? new Date(userInfo.expireTime).toLocaleDateString()
                      : "-"
                  }`}
            </div>
          </div>
          <div className="absolute right-6 top-6 text-4xl opacity-20">VIP</div>
        </div>
      </div>
    );
  };

  // 充值卡片
  const renderRechargeCards = () => (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-2 gap-6 mt-4">
      {userInfo?.role != 3 &&
        rechargeOptions.map((level, index) => (
          <div
            key={`${level.value}`}
            className="relative overflow-hidden rounded-2xl p-1 bg-gradient-to-br from-amber-200/70 via-orange-300/70 to-rose-400/70 shadow-lg hover:shadow-xl transition-all duration-300 hover:-translate-y-1 w-3/4 mx-auto"
          >
            <div className="bg-white/90 backdrop-blur-sm rounded-xl p-6 flex flex-col items-center h-full">
              <div className="text-3xl font-bold mb-2 bg-gradient-to-r from-amber-500 to-rose-500 bg-clip-text text-transparent">
                ￥{level.value}
              </div>
              <div className="text-gray-700 mb-4 text-center">
                {level.intro}
              </div>
              <Button
                className="w-full bg-gradient-to-r from-amber-500 to-rose-500 text-white hover:from-amber-600 hover:to-rose-600 shadow-md"
                onClick={() => handleRecharge(String(level.value))}
              >
                立即充值
              </Button>
            </div>
          </div>
        ))}
    </div>
  );


  return (
    <div>
      {renderVipCard()}
      {/* 只有非终身会员且已是会员，或非会员时显示充值卡片 */}
      {(typeof userInfo?.vipLevel !== "number" || userInfo.vipLevel !== 3) && renderRechargeCards()}
    </div>
  );
}
