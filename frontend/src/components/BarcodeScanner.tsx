import { useEffect, useRef, useState } from 'react';
import { Alert, Input, Modal, Space, Tabs, Typography } from 'antd';
import { Html5Qrcode, Html5QrcodeSupportedFormats } from 'html5-qrcode';
import type { Html5QrcodeResult } from 'html5-qrcode/esm/core';

const { Text, Paragraph } = Typography;

/**
 * Hộp thoại quét mã vạch / QR.
 *
 * Hỗ trợ HAI cách nhập mã, vì thực tế kho dùng cả hai:
 *
 * 1. **Quét bằng camera** (chủ yếu dùng trên điện thoại khi đi trong kho). Dùng thư viện
 *    html5-qrcode. Lưu ý: trình duyệt CHỈ cho phép truy cập camera trên HTTPS hoặc
 *    localhost - đây là quy định bảo mật của trình duyệt, không phải lỗi của ứng dụng.
 *
 * 2. **Máy quét USB** (dùng trên máy tính tại bàn làm việc). Loại máy này hoạt động như
 *    một bàn phím: nó "gõ" dãy số rồi gửi phím Enter. Vì vậy ta chỉ cần một ô nhập đang
 *    được focus và xử lý sự kiện Enter - không cần thư viện gì thêm.
 */

interface BarcodeScannerProps {
  open: boolean;
  onClose: () => void;
  /** Được gọi khi có mã hợp lệ. Mã đã được loại bỏ khoảng trắng hai đầu. */
  onScan: (code: string) => void;
  title?: string;
}

/** Id của thẻ div chứa khung hình camera - html5-qrcode cần một id cố định. */
const SCANNER_ELEMENT_ID = 'smartstock-barcode-reader';

export default function BarcodeScanner({ open, onClose, onScan, title }: BarcodeScannerProps) {
  const [activeTab, setActiveTab] = useState<'camera' | 'manual'>('camera');
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [manualCode, setManualCode] = useState('');
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const manualInputRef = useRef<React.ComponentRef<typeof Input>>(null);

  // Khởi động / dừng camera theo trạng thái hộp thoại và tab đang chọn
  useEffect(() => {
    if (!open || activeTab !== 'camera') {
      return;
    }

    let cancelled = false;
    setCameraError(null);

    const startCamera = async () => {
      try {
        const scanner = new Html5Qrcode(SCANNER_ELEMENT_ID, {
          // Giới hạn các định dạng cần nhận để tăng tốc nhận dạng
          formatsToSupport: [
            Html5QrcodeSupportedFormats.EAN_13,
            Html5QrcodeSupportedFormats.EAN_8,
            Html5QrcodeSupportedFormats.CODE_128,
            Html5QrcodeSupportedFormats.CODE_39,
            Html5QrcodeSupportedFormats.QR_CODE,
          ],
          verbose: false,
        });
        scannerRef.current = scanner;

        await scanner.start(
          // facingMode environment = camera sau của điện thoại
          { facingMode: 'environment' },
          { fps: 10, qrbox: { width: 260, height: 160 } },
          (decodedText: string, _result: Html5QrcodeResult) => {
            if (cancelled) {
              return;
            }
            // Quét được: trả mã về cho trang gọi rồi đóng hộp thoại
            onScan(decodedText.trim());
            void stopCamera();
            onClose();
          },
          () => {
            // Mỗi khung hình không nhận ra mã cũng gọi hàm này - bỏ qua cho đỡ nhiễu
          },
        );
      } catch (error) {
        if (!cancelled) {
          const message = error instanceof Error ? error.message : String(error);
          setCameraError(
            message.includes('Permission') || message.includes('NotAllowed')
              ? 'Bạn chưa cho phép truy cập camera. Vui lòng cấp quyền camera cho trang web rồi thử lại.'
              : 'Không mở được camera. Trình duyệt chỉ cho phép dùng camera trên HTTPS hoặc localhost. ' +
                  'Bạn có thể dùng tab "Nhập mã bằng tay" bên cạnh.',
          );
        }
      }
    };

    void startCamera();

    return () => {
      cancelled = true;
      void stopCamera();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, activeTab]);

  /** Dừng camera và giải phóng thiết bị. Gọi nhiều lần cũng an toàn. */
  const stopCamera = async () => {
    const scanner = scannerRef.current;
    if (!scanner) {
      return;
    }
    try {
      await scanner.stop();
      scanner.clear();
    } catch {
      // Camera có thể đã dừng trước đó; không cần xử lý thêm
    } finally {
      scannerRef.current = null;
    }
  };

  const handleManualSubmit = () => {
    const code = manualCode.trim();
    if (code.length === 0) {
      return;
    }
    onScan(code);
    setManualCode('');
    onClose();
  };

  return (
    <Modal
      open={open}
      title={title ?? 'Quét mã vạch sản phẩm'}
      onCancel={() => {
        void stopCamera();
        onClose();
      }}
      footer={null}
      width={460}
      destroyOnHidden
      afterOpenChange={(visible) => {
        // Tự focus vào ô nhập để máy quét USB "gõ" được ngay
        if (visible && activeTab === 'manual') {
          manualInputRef.current?.focus();
        }
      }}
    >
      <Tabs
        activeKey={activeTab}
        onChange={(key) => {
          setActiveTab(key as 'camera' | 'manual');
          if (key === 'manual') {
            void stopCamera();
            setTimeout(() => manualInputRef.current?.focus(), 100);
          }
        }}
        items={[
          {
            key: 'camera',
            label: 'Quét bằng camera',
            children: (
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                {cameraError && <Alert type="warning" showIcon message={cameraError} />}
                <div
                  id={SCANNER_ELEMENT_ID}
                  style={{
                    width: '100%',
                    minHeight: 240,
                    borderRadius: 8,
                    overflow: 'hidden',
                    background: '#000',
                  }}
                />
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Đưa mã vạch vào khung hình. Hệ thống tự nhận dạng mã EAN-13, Code 128 và QR.
                </Text>
              </Space>
            ),
          },
          {
            key: 'manual',
            label: 'Nhập mã bằng tay',
            children: (
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                <Input
                  ref={manualInputRef}
                  value={manualCode}
                  onChange={(event) => setManualCode(event.target.value)}
                  onPressEnter={handleManualSubmit}
                  placeholder="Nhập hoặc quét mã vạch rồi nhấn Enter"
                  size="large"
                  allowClear
                  autoFocus
                />
                <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0 }}>
                  Dùng được với máy quét USB: máy quét hoạt động như bàn phím, nó tự gõ mã và nhấn
                  Enter. Chỉ cần con trỏ đang ở trong ô này.
                </Paragraph>
              </Space>
            ),
          },
        ]}
      />
    </Modal>
  );
}
