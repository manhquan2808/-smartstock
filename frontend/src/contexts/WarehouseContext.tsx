import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { warehouseApi } from '@/api/endpoints';
import { useAuth } from './AuthContext';
import type { WarehouseResponse } from '@/types';

/**
 * Kho đang làm việc, chọn ở bộ chọn trên thanh tiêu đề.
 *
 * Vì sao cần trạng thái dùng chung: hầu hết màn hình (tồn kho, lập phiếu, báo cáo,
 * dashboard) đều phụ thuộc vào "đang làm việc ở kho nào". Giữ ở một chỗ giúp người dùng
 * đổi kho một lần là mọi trang cùng đổi theo, không phải chọn lại ở từng trang.
 *
 * Giá trị được ghi vào localStorage để mở lại trang vẫn đúng kho đang làm việc.
 */

const STORAGE_KEY = 'smartstock-warehouse';

interface WarehouseContextValue {
  /** Danh sách kho người dùng được phép làm việc. */
  warehouses: WarehouseResponse[];
  /** Kho đang chọn; null nghĩa là "tất cả kho" (chỉ ADMIN/MANAGER nhiều kho mới có). */
  currentWarehouseId: number | null;
  currentWarehouse: WarehouseResponse | null;
  setCurrentWarehouseId: (id: number | null) => void;
  isLoading: boolean;
  /** Người dùng có nhiều hơn một kho hay không (để ẩn bộ chọn khi chỉ có một kho). */
  hasMultipleWarehouses: boolean;
}

const WarehouseContext = createContext<WarehouseContextValue | undefined>(undefined);

export function WarehouseProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [currentWarehouseId, setCurrentWarehouseIdState] = useState<number | null>(null);

  const { data: warehouses = [], isLoading } = useQuery({
    queryKey: ['warehouses', 'mine'],
    queryFn: warehouseApi.listMine,
    // Chỉ gọi khi đã đăng nhập
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000,
  });

  // Chọn kho mặc định khi danh sách kho vừa tải xong
  useEffect(() => {
    if (warehouses.length === 0) {
      return;
    }

    const stored = window.localStorage.getItem(STORAGE_KEY);
    const storedId = stored ? Number(stored) : null;

    // Ưu tiên kho đã lưu nếu người dùng vẫn còn quyền với kho đó
    if (storedId && warehouses.some((warehouse) => warehouse.id === storedId)) {
      setCurrentWarehouseIdState(storedId);
      return;
    }

    // Người dùng chỉ có một kho: chọn luôn kho đó
    if (warehouses.length === 1) {
      setCurrentWarehouseIdState(warehouses[0].id);
      return;
    }

    // Nhiều kho và chưa có lựa chọn cũ: mặc định xem tất cả
    setCurrentWarehouseIdState(null);
  }, [warehouses]);

  const setCurrentWarehouseId = useCallback((id: number | null) => {
    setCurrentWarehouseIdState(id);
    if (id === null) {
      window.localStorage.removeItem(STORAGE_KEY);
    } else {
      window.localStorage.setItem(STORAGE_KEY, String(id));
    }
  }, []);

  const value = useMemo<WarehouseContextValue>(() => {
    const current = warehouses.find((warehouse) => warehouse.id === currentWarehouseId) ?? null;
    return {
      warehouses,
      currentWarehouseId,
      currentWarehouse: current,
      setCurrentWarehouseId,
      isLoading,
      hasMultipleWarehouses: warehouses.length > 1,
    };
  }, [warehouses, currentWarehouseId, setCurrentWarehouseId, isLoading]);

  return <WarehouseContext.Provider value={value}>{children}</WarehouseContext.Provider>;
}

export function useWarehouse(): WarehouseContextValue {
  const context = useContext(WarehouseContext);
  if (!context) {
    throw new Error('useWarehouse phải được dùng bên trong WarehouseProvider');
  }
  return context;
}
