"use client";
import { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import {
  getUserDetail,
  updateUserDetail,
  getRechargeList,
  UserSaveReq,
} from "@/lib/api";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { QRCodeCanvas } from "qrcode.react";
import { Bell, User, ChevronDown } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import Link from "next/link";
import { useLoginUser } from "@/hooks/useLoginUser";
import { useRouter } from "next/navigation";
import { useToast } from "@/hooks/use-toast";
import { getConfigValue } from "@/lib/config";
import {
  GlobalConfigItemValue,
  toPay,
  markPaying,
  refreshPay,
} from "@/lib/api";
import { Badge } from "@/components/ui/badge";

const MENU = [
  { key: "vip", label: "我的会员", icon: "💎" },
  { key: "orders", label: "购买记录", icon: "🛒" },
  { key: "fav", label: "订阅收藏", icon: "⭐" },
  // { key: "post", label: "职位录入", icon: "🏬" },
  { key: "profile", label: "基本资料", icon: "📄" },
];

const newUserInitValue: UserSaveReq = {
  userId: 0,
  displayName: "",
  email: "",
  intro: "",
  avatar: "",
};

export default function UserPage() {
  const { toast } = useToast();
  const [userInfo, setUserInfo] = useState<any>(null);
  const [activeMenu, setActiveMenu] = useState("vip");
  const [form, setForm] = useState<UserSaveReq>(newUserInitValue);
  // 充值相关
  const [payInfo, setPayInfo] = useState<any>(null);
  const [payDialogOpen, setPayDialogOpen] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [paying, setPaying] = useState(false);
  const [loading, setLoading] = useState(false);
  const [rechargeList, setRechargeList] = useState<any[]>([]);
  // 优惠券码弹窗相关
  const [couponDialogOpen, setCouponDialogOpen] = useState(false);
  const [couponCode, setCouponCode] = useState("");
  const [selectedVipAmount, setSelectedVipAmount] = useState<
    number | string | String
  >("");
  const [selectRechargeLevel, setSelectRechargeLevel] = useState<
    number | string | String
  >("");

  // MCP配置相关
  const [mcpConfigDialogOpen, setMcpConfigDialogOpen] = useState(false);
  const [mcpConfig, setMcpConfig] = useState("");

  // 充值业务数据
  const [rechargeOptions, setRechargeOptions] = useState<
    GlobalConfigItemValue[]
  >([]);
  const [vipOptions, setVipOptions] = useState<GlobalConfigItemValue[]>([]);
  // 支付状态定义字典
  const [rechargeStatusOptions, setRechargeStatusOptions] = useState<
    GlobalConfigItemValue[]
  >([]);

  const {
    userInfo: loginUserInfo,
    setUserInfo: setLoginUserInfo,
    logout: loginLogout,
  } = useLoginUser();
  const [loginOpen, setLoginOpen] = useState(false);
  const [mounted, setMounted] = useState(false);
  const router = useRouter();

  const getVipLevelLabel = (level: number) => {
    const item = vipOptions.find((v) => v.value == `${level}`);
    return item?.intro;
  };
  const getPayStatusText = (status: number) => {
    const item = rechargeStatusOptions.find((v) => v.value == `${status}`);
    return item?.intro;
  };

  useEffect(() => {
    setMounted(true);
    getConfigValue("recharge", "vipPrice").then(setRechargeOptions);
    getConfigValue("user", "RechargeStatusEnum").then(setRechargeStatusOptions);
    getConfigValue("user", "RechargeLevelEnum").then(setVipOptions);
  }, []);

  const fetchRechargeList = async () => {
    setLoading(true);
    try {
      const response = await getRechargeList();
      console.log("发起记录查询");
      setRechargeList(response.list);
    } catch (error) {
      console.error("获取充值记录失败:", error);
    } finally {
      setLoading(false);
    }
  };

  // 处理复制MCP配置到剪贴板
  const handleCopyMcpConfig = () => {
    navigator.clipboard
      .writeText(mcpConfig)
      .then(() => {
        toast({
          title: "成功",
          description: "配置已复制到剪贴板",
        });
        setMcpConfigDialogOpen(false);
      })
      .catch((err) => {
        toast({
          title: "失败",
          description: "复制失败: " + err.message,
          variant: "destructive",
        });
      });
  };

  useEffect(() => {
    getUserDetail().then((data) => {
      // 根据用户信息，构建vip登记
      if (data.role == 3) {
        // 终身会员
        data.vipLevel = 3;
      } else if (data.role == 2) {
        // fixme 这里是一个简单的做法，根据剩余时间来判断等级
        // 是会员，根据到期时间与当前时间之间的间隔
        const period = data.expireTime - Date.now();
        if (period <= 0) {
          // 会员过期
          data.vipLevel = -1;
        } else if (period < 31 * 86400 * 1000) {
          // 小于一个月，月会员
          data.vipLevel = 0;
        } else if (period < 4 * 31 * 86400 * 1000) {
          // 季度
          data.vipLevel = 1;
        } else if (period < 3 * 366 * 31 * 86400 * 1000) {
          data.vipLevel = 2;
        } else {
          data.vipLevel = 3;
        }
      } else {
        // 不是会员
        data.vipLevel = -1;
      }

      setUserInfo(data);

      // 完成mcp相关配置
      let mcpConfigs = {
        mcpServers: {
          校招派: data.config,
        },
      };
      setMcpConfig(JSON.stringify(mcpConfigs, null, 2));

      if (activeMenu === "orders") {
        console.log("当前切换为充值记录了");
        fetchRechargeList();
      }

      setForm({
        userId: data.userId || 0,
        displayName: data.displayName || "",
        avatar: data.avatar || "",
        email: data.email || "",
        intro: data.intro || "",
      });
    });
  }, [activeMenu]);

  useEffect(() => {
    if (payInfo && payInfo.prePayExpireTime) {
      const timer = setInterval(() => {
        const left = Math.max(
          0,
          Math.floor((payInfo.prePayExpireTime - Date.now()) / 1000)
        );
        setCountdown(left);
        if (left === 0) clearInterval(timer);
      }, 1000);
      return () => clearInterval(timer);
    }
  }, [payInfo]);

  const handleSaveUserInfo = async () => {
    await updateUserDetail(form)
      .then((res) => {
        console.log("保存成功");
        toast({
          title: "成功",
          description: "个人信息更新成功",
        });
      })
      .catch((err) => {
        toast({
          title: "保存失败",
          description: err.message,
          variant: "destructive",
        });
      });
  };

  // 格式化倒计时为 mm:ss
  const formatCountdown = (sec: number) => {
    const h = Math.floor(sec / 3600)
      .toString()
      .padStart(2, "0");
    const m = Math.floor((sec % 3600) / 60)
      .toString()
      .padStart(2, "0");
    const s = (sec % 60).toString().padStart(2, "0");
    return `${h}:${m}:${s}`;
  };

  // 支付确认
  const handlePaying = async () => {
    if (!payInfo?.payId) return;
    setPaying(true);
    await markPaying(payInfo?.payId)
      .then((res) => {
        toast({
          title: "支付提醒",
          description: "支付状态变更会有一定的延时，到购买记录确认状态吧~",
        });
      })
      .catch((err) => {
        toast({
          title: "支付提醒",
          description: err.message,
          variant: "destructive",
        });
      })
      .finally(() => {
        setPaying(false);
        setPayDialogOpen(false);
      });
  };

  const handleMarkFailed = async (id: number) => {
    await refreshPay(id)
      .then((res) => {
        toast({
          title: "支付提醒",
          description: "状态刷新成功~",
        });

        if (activeMenu === "orders") {
          fetchRechargeList();
        }
      })
      .catch((err) => {
        toast({
          title: "支付提醒",
          description: err.message,
          variant: "destructive",
        });
      });
  };

  const handleFormChange = (key: string, value: string) => {
    setForm((f) => ({ ...f, [key]: value }));
  };

  // 处理充值按钮点击 - 先显示优惠券码弹窗
  const handleRecharge = async (vipAmount: number | string | String) => {
    setSelectRechargeLevel("");
    setSelectedVipAmount(vipAmount);
    setCouponDialogOpen(true);
  };

  // 重新充值的场景
  const handleReRecharge = async (rechargeLevel: number | string | String) => {
    setSelectedVipAmount("");
    setSelectRechargeLevel(rechargeLevel);
    setCouponDialogOpen(true);
  };

  // 处理优惠券码提交
  const handleCouponSubmit = async (
    rechargeLevel?: number | string | String,
    price?: number | string | String,
    code?: string
  ) => {
    // 关闭优惠券码弹窗
    setCouponDialogOpen(false);
    if (!price) {
      price = selectedVipAmount;
    }
    if (!code) {
      code = couponCode;
    }
    if (!rechargeLevel) {
      rechargeLevel = selectRechargeLevel;
    }

    // 调用支付接口，传入优惠券码
    await toPay(selectRechargeLevel, price, code)
      .then((res) => {
        setPayInfo(res);
        setPayDialogOpen(true);
        // 重置优惠券码
        setCouponCode("");
      })
      .catch((e) => {
        toast({
          title: "唤起支付失败了~",
          description: e.message,
          variant: "destructive",
        });
      });
  };

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
                onClick={() => handleRecharge(level.value)}
              >
                立即充值
              </Button>
            </div>
          </div>
        ))}
    </div>
  );

  return (
    <div className="min-h-screen bg-[#f5f7fa]">
      {/* 顶部导航栏 */}
      {/* <header className="bg-white border-b">
        <div className="px-10">
          <div className="flex justify-between items-center h-16">
            <div className="flex items-center space-x-8">
              <div className="flex items-center">
                <a href="/" className="text-2xl font-bold text-blue-600">
                  🚦校招派
                </a>
              </div>
              <nav className="flex space-x-6">
                <a href="/" className="text-gray-700 hover:text-blue-600">
                  招聘
                </a>
              </nav>
            </div>
            <div className="flex items-center space-x-4">
              <Bell className="h-5 w-5 text-gray-500" />
              {loginUserInfo ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <span className="flex items-center cursor-pointer">
                      <img
                        src={loginUserInfo.avatar}
                        alt="avatar"
                        className="w-8 h-8 rounded-full cursor-pointer"
                        title={
                          loginUserInfo.nickname ||
                          `用户${loginUserInfo.userId}`
                        }
                      />
                      <ChevronDown className="w-4 h-4 ml-1 text-gray-500" />
                    </span>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <div className="px-3 py-2">
                      <div className="font-medium">
                        {loginUserInfo.nickname ||
                          `用户${loginUserInfo.userId}`}
                      </div>
                      <div className="text-xs text-gray-500">
                        {loginUserInfo.role === 1
                          ? "普通用户"
                          : loginUserInfo.role === 2
                          ? "VIP用户"
                          : loginUserInfo.role === 3
                          ? "管理员"
                          : "未知"}
                      </div>
                    </div>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      onClick={() => {
                        router.push("/user");
                      }}
                    >
                      个人信息
                    </DropdownMenuItem>
                    {loginUserInfo.role === 3 && (
                      <DropdownMenuItem onClick={() => router.push("/admin")}>
                        管理后台
                      </DropdownMenuItem>
                    )}
                    <DropdownMenuSeparator />
                    {(userInfo?.role === 2 || userInfo?.role === 3) && (
                      <>
                        <DropdownMenuItem
                          onClick={() => setMcpConfigDialogOpen(true)}
                        >
                          MCP配置
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                      </>
                    )}
                    <DropdownMenuItem onClick={loginLogout}>
                      退出
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : null}
            </div>
          </div>
        </div>
      </header> */}
      {/* 原有顶部横幅 */}
      <div className="bg-white shadow-sm">
        <div className="max-w-6xl mx-auto flex items-center justify-between px-8 py-4">
          <div className="flex items-center space-x-4">
            <Avatar className="w-16 h-16 border-4 border-white shadow">
              <AvatarImage
                src={userInfo?.avatar}
                alt={userInfo?.displayName || "avatar"}
              />
              <AvatarFallback>
                {userInfo?.displayName?.[0] || "U"}
              </AvatarFallback>
            </Avatar>
            <div>
              <div className="text-xl font-bold">
                {userInfo?.displayName || `用户${userInfo?.userId}`}
              </div>
              <div className="flex items-center space-x-2 mt-1">
                <span
                  className={`text-base font-semibold ${
                    userInfo?.role === 2 ? "text-yellow-500" : "text-gray-400"
                  }`}
                >
                  {userInfo?.role === 2 ? "VIP会员" : "普通"}
                </span>
              </div>
            </div>
          </div>
          {/* <Button variant="outline" className="text-gray-700" onClick={loginLogout}>退出登录</Button> */}
        </div>
      </div>

      {/* 主体区域 */}
      <div className="max-w-7xl mx-auto flex mt-8 gap-6">
        {/* 左侧菜单 */}
        <div className="w-64">
          <Card className="mb-4">
            <CardContent className="py-4">
              <div className="font-bold text-gray-600 mb-2">会员中心</div>
              <ul>
                {MENU.map((item) => (
                  <li key={item.key}>
                    <Button
                      variant={activeMenu === item.key ? "secondary" : "ghost"}
                      className="w-full justify-start mb-1"
                      onClick={() => setActiveMenu(item.key)}
                    >
                      <span className="mr-2">{item.icon}</span>
                      {item.label}
                    </Button>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>

        {/* 右侧内容区 */}
        <div className="flex-1">
          <Card>
            <CardContent className="py-8 min-h-[300px]">
              {activeMenu === "profile" ? (
                <div className="max-w-3xl mx-auto">
                  <div className="font-bold text-lg mb-6">个人基本信息</div>
                  <div className="flex items-start gap-8 mb-6">
                    <Avatar className="w-20 h-20 border-4 border-white shadow">
                      <AvatarImage
                        src={userInfo?.avatar}
                        alt={userInfo?.displayName || "avatar"}
                      />
                      <AvatarFallback>
                        {userInfo?.displayName?.[0] || "U"}
                      </AvatarFallback>
                    </Avatar>
                    <div className="flex-1 grid grid-cols-2 gap-6">
                      <div>
                        <div className="mb-1 text-sm text-gray-600">账号ID</div>
                        <Input
                          value={form.userId}
                          disabled
                          className="bg-blue-50"
                        />
                      </div>
                      <div>
                        <div className="mb-1 text-sm text-gray-600">昵称</div>
                        <Input
                          value={form.displayName}
                          className="bg-blue-50"
                        />
                      </div>
                      <div>
                        <div className="mb-1 text-sm text-gray-600">邮箱</div>
                        <Input
                          value={form.email}
                          onChange={(e) =>
                            handleFormChange("email", e.target.value)
                          }
                          className="bg-blue-50"
                        />
                      </div>
                    </div>
                  </div>
                  <div className="mb-6">
                    <div className="mb-1 text-sm text-gray-600">介绍</div>
                    <Textarea
                      value={form.intro}
                      onChange={(e) =>
                        handleFormChange("intro", e.target.value)
                      }
                      className="bg-blue-50 min-h-[100px]"
                      placeholder="请输入个人介绍："
                    />
                  </div>
                  <div className="flex justify-end">
                    <Button onClick={handleSaveUserInfo}>保存个人信息</Button>
                  </div>
                </div>
              ) : activeMenu === "vip" ? (
                <div>
                  {renderVipCard()}
                  {/* 只有非终身会员且已是会员，或非会员时显示充值卡片 */}
                  {(typeof userInfo?.vipLevel !== "number" ||
                    userInfo.vipLevel !== 3) &&
                    renderRechargeCards()}
                </div>
              ) : activeMenu === "orders" ? (
                <>
                  {loading ? (
                    <div className="flex flex-col items-center justify-center min-h-[300px] text-gray-400 text-lg">
                      加载中...
                    </div>
                  ) : rechargeList?.length === 0 ? (
                    <div className="flex flex-col items-center justify-center min-h-[300px] text-gray-400 text-lg">
                      暂无充值记录
                    </div>
                  ) : (
                    <div className="overflow-x-auto">
                      <Table className="min-w-full text-sm">
                        <TableHeader>
                          <TableRow className="bg-gray-100">
                            <TableHead>支付ID</TableHead>
                            <TableHead>交易号</TableHead>
                            <TableHead>金额(元)</TableHead>
                            <TableHead>会员等级</TableHead>
                            <TableHead>支付状态</TableHead>
                            <TableHead>支付时间</TableHead>
                            <TableHead>交易ID</TableHead>
                            <TableHead>优惠券</TableHead>
                            <TableHead>编辑</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {rechargeList.map((item) => (
                            <TableRow
                              key={item.payId}
                              className="hover:bg-gray-50"
                            >
                              <TableCell>{item.payId}</TableCell>
                              <TableCell>{item.tradeNo}</TableCell>
                              <TableCell>{item.amount}</TableCell>
                              <TableCell>
                                {getVipLevelLabel(item.level)}
                              </TableCell>
                              <TableCell>
                                {item.status === 0 ? (
                                  <Badge className="px-1 py-1 text-xs">
                                    {getPayStatusText(item.status)}
                                  </Badge>
                                ) : item.status === 1 ? (
                                  <Badge
                                    variant="orange"
                                    className="px-1 py-1 text-xs"
                                  >
                                    {getPayStatusText(item.status)}
                                  </Badge>
                                ) : item.status === 2 ? (
                                  <Badge
                                    variant="green"
                                    className="px-1 py-1 text-xs"
                                  >
                                    {getPayStatusText(item.status)}
                                  </Badge>
                                ) : (
                                  <Badge
                                    variant="destructive"
                                    className="px-1 py-1 text-xs"
                                  >
                                    {getPayStatusText(item.status)}
                                  </Badge>
                                )}
                              </TableCell>
                              <TableCell>
                                {new Date(item.payTime).toLocaleString()}
                              </TableCell>
                              <TableCell>{item.transactionId}</TableCell>
                              <TableCell>
                                {item.couponCode}
                                {item.couponCode ? (
                                  <div>优惠:{item.promotionAmount}元</div>
                                ) : null}
                              </TableCell>

                              <TableCell>
                                {item.status === 0 && (
                                  <Button
                                    variant="promotion"
                                    size="xs"
                                    onClick={async () => {
                                      console.log(
                                        "输入金额=",
                                        item.amount,
                                        item.promotionAmount,
                                        item.couponCode
                                      );
                                      await handleCouponSubmit(
                                        item.level,
                                        Number(item.amount) +
                                          Number(item.promotionAmount),
                                        item.couponCode
                                      );
                                    }}
                                  >
                                    去支付
                                  </Button>
                                )}
                                {item.status === 1 && (
                                  <Button
                                    variant="share"
                                    size="xs"
                                    onClick={() => handleMarkFailed(item.payId)}
                                  >
                                    刷新
                                  </Button>
                                )}
                                {item.status === 3 && (
                                  <Button
                                    variant="outline"
                                    size="xs"
                                    onClick={() => handleReRecharge(item.level)}
                                  >
                                    重新充值
                                  </Button>
                                )}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </div>
                  )}
                </>
              ) : (
                <div className="flex flex-col items-center justify-center min-h-[300px] text-gray-400 text-lg">
                  暂无记录
                </div>
              )}
            </CardContent>
          </Card>

          {/* 支付弹窗 */}
          <Dialog open={payDialogOpen} onOpenChange={setPayDialogOpen}>
            <DialogContent className="max-w-xs">
              <DialogHeader>
                <DialogTitle>微信支付</DialogTitle>
              </DialogHeader>
              {payInfo && (
                <div className="flex flex-col items-center">
                  <QRCodeCanvas value={payInfo.prePayId} size={180} />
                  <div className="mt-4 text-sm">交易号：{payInfo.tradeNo}</div>
                  <div className="mt-1 text-sm">
                    充值金额：{payInfo.amount} 元
                  </div>
                  <div className="mt-1 text-sm text-red-500">
                    二维码有效期：{formatCountdown(countdown)}
                  </div>
                  {Number(payInfo.amount) == 0 ? (
                    <Button className="mt-4 w-full" onClick={() => setPayDialogOpen(false)}>
                      支付成功
                    </Button>
                  ) : (
                    <Button className="mt-4 w-full" onClick={handlePaying}>
                      {paying ? "处理中..." : "我已支付"}
                    </Button>
                  )}
                </div>
              )}
            </DialogContent>
          </Dialog>
          {/* MCP配置弹窗 */}
          <Dialog
            open={mcpConfigDialogOpen}
            onOpenChange={setMcpConfigDialogOpen}
          >
            <DialogContent className="max-w-2xl">
              <DialogHeader>
                <DialogTitle>MCP Server配置</DialogTitle>
              </DialogHeader>
              <div className="py-4">
                <div className="mb-4">
                  <label className="block text-sm font-medium mb-2">
                    配置信息
                  </label>
                  <textarea
                    readOnly
                    value={mcpConfig}
                    className="w-full h-64 p-3 border rounded-md font-mono text-sm bg-gray-50"
                  />
                </div>
                <div className="flex justify-end space-x-3">
                  <Button
                    variant="outline"
                    onClick={() => setMcpConfigDialogOpen(false)}
                  >
                    取消
                  </Button>
                  <Button onClick={handleCopyMcpConfig}>复制</Button>
                </div>
              </div>
            </DialogContent>
          </Dialog>

          {/* 优惠券码输入弹窗 */}
          <Dialog open={couponDialogOpen} onOpenChange={setCouponDialogOpen}>
            <DialogContent className="sm:max-w-md">
              <DialogHeader>
                <DialogTitle>输入优惠券码</DialogTitle>
              </DialogHeader>
              <div className="mt-4 flex flex-col gap-4">
                <Input
                  className="focus-visible:ring-orange-500"
                  placeholder="请输入优惠券码（选填）"
                  value={couponCode}
                  onChange={(e) => setCouponCode(e.target.value)}
                />
                <div className="flex gap-3 mt-4">
                  <Button
                    variant="promotion"
                    onClick={() => {
                      setCouponCode("");
                      handleCouponSubmit();
                    }}
                    className="flex-1"
                  >
                    直接支付
                  </Button>
                  <Button
                    className="flex-1 bg-gradient-to-r from-amber-500 to-rose-500 text-white hover:from-amber-600 hover:to-rose-600 shadow-md"
                    onClick={() => handleCouponSubmit()}
                  >
                    确认使用
                  </Button>
                </div>
              </div>
            </DialogContent>
          </Dialog>
        </div>
      </div>
    </div>
  );
}
