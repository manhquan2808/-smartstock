import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * Lưu bộ lọc và phân trang TRÊN URL thay vì trong state của component.
 *
 * Vì sao đặc tả yêu cầu điều này: khi bộ lọc nằm trên URL, người dùng có thể
 * - bấm F5 mà không mất bộ lọc đang xem,
 * - gửi đường dẫn cho đồng nghiệp và họ thấy đúng dữ liệu đó,
 * - dùng nút Back/Forward của trình duyệt như mong đợi.
 *
 * Cách dùng:
 * ```ts
 * const { filters, setFilter, setFilters, resetFilters, page, size, setPagination } =
 *   useUrlFilters({ keyword: '', status: '' });
 * ```
 */

export interface UrlFilterResult<T extends Record<string, string>> {
  /** Giá trị hiện tại của các bộ lọc (luôn là chuỗi, đọc từ URL). */
  filters: T;
  /** Đổi một bộ lọc; tự động quay về trang 1 vì tập kết quả đã khác. */
  setFilter: (key: keyof T, value: string | number | undefined | null) => void;
  /** Đổi nhiều bộ lọc cùng lúc. */
  setFilters: (values: Partial<Record<keyof T, string | number | undefined | null>>) => void;
  /** Xóa toàn bộ bộ lọc, trở về giá trị mặc định. */
  resetFilters: () => void;
  /** Trang hiện tại, đếm từ 0 như backend. */
  page: number;
  /** Số bản ghi mỗi trang. */
  size: number;
  /** Thứ tự sắp xếp dạng "field,asc" hoặc "field,desc"; rỗng nghĩa là theo mặc định. */
  sort: string;
  setPagination: (page: number, size: number, sort?: string) => void;
}

export function useUrlFilters<T extends Record<string, string>>(
  defaults: T,
  defaultSize = 20,
): UrlFilterResult<T> {
  const [searchParams, setSearchParams] = useSearchParams();

  /** Đọc giá trị bộ lọc từ URL, thiếu thì lấy giá trị mặc định. */
  const filters = useMemo(() => {
    const result = { ...defaults };
    (Object.keys(defaults) as Array<keyof T>).forEach((key) => {
      const value = searchParams.get(String(key));
      if (value !== null) {
        result[key] = value as T[keyof T];
      }
    });
    return result;
    // defaults là đối tượng hằng do trang truyền vào, không cần theo dõi thay đổi
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const page = Number(searchParams.get('page') ?? 0);
  const size = Number(searchParams.get('size') ?? defaultSize);
  const sort = searchParams.get('sort') ?? '';

  const setFilters = useCallback(
    (values: Partial<Record<keyof T, string | number | undefined | null>>) => {
      const next = new URLSearchParams(searchParams);
      Object.entries(values).forEach(([key, value]) => {
        if (value === undefined || value === null || value === '') {
          next.delete(key);
        } else {
          next.set(key, String(value));
        }
      });
      // Đổi bộ lọc thì phải về trang đầu, nếu không người dùng có thể thấy trang trống
      next.delete('page');
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const setFilter = useCallback(
    (key: keyof T, value: string | number | undefined | null) => {
      setFilters({ [key]: value } as Partial<Record<keyof T, string | number | undefined | null>>);
    },
    [setFilters],
  );

  const resetFilters = useCallback(() => {
    setSearchParams(new URLSearchParams(), { replace: true });
  }, [setSearchParams]);

  const setPagination = useCallback(
    (nextPage: number, nextSize: number, nextSort?: string) => {
      const next = new URLSearchParams(searchParams);
      next.set('page', String(nextPage));
      next.set('size', String(nextSize));
      if (nextSort !== undefined) {
        if (nextSort) {
          next.set('sort', nextSort);
        } else {
          next.delete('sort');
        }
      }
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  return { filters, setFilter, setFilters, resetFilters, page, size, sort, setPagination };
}

/** Chuyển giá trị bộ lọc dạng chuỗi sang số, trả undefined nếu rỗng (để không gửi lên API). */
export function toNumberOrUndefined(value: string | undefined): number | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isNaN(parsed) ? undefined : parsed;
}

/** Trả undefined nếu chuỗi rỗng, giúp không gửi tham số rỗng lên API. */
export function toStringOrUndefined(value: string | undefined): string | undefined {
  return value && value.trim().length > 0 ? value.trim() : undefined;
}
