import { ChangeEvent, DragEvent, useEffect, useRef, useState } from "react";
import { AlertCircle, Camera, RotateCcw, ShieldCheck, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

const MAX_AVATAR_BYTES = 5 * 1024 * 1024;
const ALLOWED_AVATAR_TYPES = ["image/jpeg", "image/png"];

interface ProfileSectionProps {
  userInfo: any;
  form: any;
  avatarUploading: boolean;
  handleAvatarUpload: (file: File) => Promise<void>;
  handleFormChange: (field: string, value: string) => void;
  handleSaveUserInfo: () => void;
}

export default function ProfileSection({
  userInfo,
  form,
  avatarUploading,
  handleAvatarUpload,
  handleFormChange,
  handleSaveUserInfo,
}: ProfileSectionProps) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const previewUrlRef = useRef("");
  const [previewUrl, setPreviewUrl] = useState("");
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [avatarError, setAvatarError] = useState("");
  const [dragActive, setDragActive] = useState(false);

  const avatarUrl = previewUrl || form.avatar || userInfo?.avatar;
  const displayName = form.displayName || userInfo?.displayName || "U";

  useEffect(() => {
    return () => {
      if (previewUrlRef.current) {
        URL.revokeObjectURL(previewUrlRef.current);
      }
    };
  }, []);

  const clearPreview = () => {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current);
      previewUrlRef.current = "";
    }
    setPreviewUrl("");
  };

  const setPreviewFromFile = (file: File) => {
    clearPreview();
    const nextPreviewUrl = URL.createObjectURL(file);
    previewUrlRef.current = nextPreviewUrl;
    setPreviewUrl(nextPreviewUrl);
  };

  const validateAvatarFile = (file: File) => {
    if (!ALLOWED_AVATAR_TYPES.includes(file.type)) {
      return "仅支持 JPG / PNG 图片";
    }
    if (file.size > MAX_AVATAR_BYTES) {
      return "头像文件不能超过 5MB";
    }
    return "";
  };

  const uploadFile = async (file: File) => {
    const error = validateAvatarFile(file);
    if (error) {
      setAvatarError(error);
      return;
    }

    setAvatarError("");
    setPendingFile(file);
    setPreviewFromFile(file);
    try {
      await handleAvatarUpload(file);
      setPendingFile(null);
      clearPreview();
    } catch (err) {
      setAvatarError(err instanceof Error ? err.message : "头像上传失败，请重试");
    }
  };

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const input = event.currentTarget;
    const file = event.target.files?.[0];
    if (file) {
      await uploadFile(file);
    }
    input.value = "";
  };

  const handleDrop = async (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragActive(false);
    const file = event.dataTransfer.files?.[0];
    if (file) {
      await uploadFile(file);
    }
  };

  const handleRetry = async () => {
    if (pendingFile) {
      await uploadFile(pendingFile);
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-content-primary">个人基本资料</h2>
        <p className="mt-1 text-sm text-content-tertiary">维护对外展示的头像、昵称和联系信息。</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[280px_minmax(0,1fr)]">
        <section
          className={`rounded-lg border bg-white p-5 shadow-sm transition ${
            dragActive ? "border-blue-400 bg-blue-50/60" : "border-surface-border"
          }`}
          onDragOver={(event) => {
            event.preventDefault();
            setDragActive(true);
          }}
          onDragLeave={() => setDragActive(false)}
          onDrop={handleDrop}
        >
          <div className="flex flex-col items-center text-center">
            <div className="relative">
              <Avatar className="h-28 w-28 border-4 border-white shadow">
                <AvatarImage src={avatarUrl} alt={displayName} />
                <AvatarFallback className="text-2xl">{displayName.slice(0, 1)}</AvatarFallback>
              </Avatar>
              <Button
                type="button"
                size="icon"
                className="absolute bottom-0 right-0 h-9 w-9 rounded-full shadow"
                onClick={() => fileInputRef.current?.click()}
                disabled={avatarUploading}
                title="更换头像"
              >
                <Camera className="h-4 w-4" />
              </Button>
            </div>

            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png"
              className="hidden"
              onChange={handleFileChange}
            />

            <h3 className="mt-4 text-base font-semibold text-content-primary">{displayName}</h3>
            <p className="mt-1 text-sm text-content-tertiary">支持 JPG / PNG，最大 5MB</p>

            <Button
              type="button"
              variant="outline"
              className="mt-4 w-full gap-2"
              onClick={() => fileInputRef.current?.click()}
              disabled={avatarUploading}
            >
              <Upload className="h-4 w-4" />
              {avatarUploading ? "上传中..." : "上传新头像"}
            </Button>

            {avatarError && (
              <div className="mt-3 flex w-full items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-left text-xs leading-5 text-red-700">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                <div className="min-w-0 flex-1">
                  <div>{avatarError}</div>
                  {pendingFile && !avatarUploading && (
                    <button
                      type="button"
                      className="mt-1 inline-flex items-center gap-1 font-medium text-red-800 underline-offset-2 hover:underline"
                      onClick={handleRetry}
                    >
                      <RotateCcw className="h-3.5 w-3.5" />
                      重试上传
                    </button>
                  )}
                </div>
              </div>
            )}

            <div className="mt-4 flex items-start gap-2 rounded-md bg-blue-50 p-3 text-left text-xs leading-5 text-blue-700">
              <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" />
              <span>服务端会校验真实图片类型，重新裁剪、压缩并移除原始图片元信息。</span>
            </div>
          </div>
        </section>

        <section className="rounded-lg border border-surface-border bg-white p-5 shadow-sm">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <div className="mb-1 text-sm font-medium text-content-secondary">账号 ID</div>
              <Input value={form.userId || ""} disabled className="bg-blue-50" />
            </div>
            <div>
              <div className="mb-1 text-sm font-medium text-content-secondary">昵称</div>
              <Input
                value={form.displayName || ""}
                onChange={(event) => handleFormChange("displayName", event.target.value)}
                className="bg-blue-50"
              />
            </div>
            <div className="sm:col-span-2">
              <div className="mb-1 text-sm font-medium text-content-secondary">邮箱</div>
              <Input
                value={form.email || ""}
                onChange={(event) => handleFormChange("email", event.target.value)}
                className="bg-blue-50"
              />
            </div>
          </div>

          <div className="mt-5">
            <div className="mb-1 text-sm font-medium text-content-secondary">介绍</div>
            <Textarea
              value={form.intro || ""}
              onChange={(event) => handleFormChange("intro", event.target.value)}
              className="min-h-[120px] bg-blue-50"
              placeholder="写一段简短介绍，方便后续生成求职材料和推荐偏好。"
            />
          </div>

          <div className="mt-6 flex justify-end">
            <Button onClick={handleSaveUserInfo}>保存个人资料</Button>
          </div>
        </section>
      </div>
    </div>
  );
}
