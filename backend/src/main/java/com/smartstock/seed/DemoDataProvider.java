package com.smartstock.seed;

import java.util.List;

/**
 * Dữ liệu mẫu ngành hàng tiêu dùng - tạp hóa (FMCG) để sinh dữ liệu demo.
 *
 * <p>Tên sản phẩm, nhà cung cấp và khách hàng đều là tiếng Việt, lấy theo các mặt hàng
 * và doanh nghiệp thực tế trên thị trường để buổi demo thuyết phục hơn.
 *
 * <p>Tách riêng khỏi {@code DemoDataSeeder} để lớp seeder tập trung vào logic sinh dữ
 * liệu, còn phần danh sách tên chỉ là dữ liệu thuần.
 */
public final class DemoDataProvider {

    private DemoDataProvider() {}

    /** 10 loại hàng: mã, tên. */
    public static final List<String[]> CATEGORIES = List.of(
            new String[] {"NGK", "Nước giải khát"},
            new String[] {"BIA", "Bia và nước có cồn"},
            new String[] {"BKE", "Bánh kẹo"},
            new String[] {"SUA", "Sữa và sản phẩm từ sữa"},
            new String[] {"MIG", "Mì - gạo - thực phẩm khô"},
            new String[] {"GIA", "Gia vị - dầu ăn"},
            new String[] {"VSC", "Hóa phẩm - vệ sinh"},
            new String[] {"CSN", "Chăm sóc cá nhân"},
            new String[] {"GDU", "Gia dụng"},
            new String[] {"TES", "Thực phẩm đóng hộp"});

    /** 8 đơn vị tính: mã, tên. */
    public static final List<String[]> UNITS = List.of(
            new String[] {"THUNG", "Thùng"},
            new String[] {"LON", "Lon"},
            new String[] {"CHAI", "Chai"},
            new String[] {"HOP", "Hộp"},
            new String[] {"GOI", "Gói"},
            new String[] {"BAO", "Bao"},
            new String[] {"KG", "Kilôgam"},
            new String[] {"LIT", "Lít"});

    /** 3 kho: mã, tên, địa chỉ, điện thoại. */
    public static final List<String[]> WAREHOUSES = List.of(
            new String[] {"KHN", "Kho Hà Nội", "Số 15 Phạm Hùng, Nam Từ Liêm, Hà Nội", "0243822100"},
            new String[] {"KDN", "Kho Đà Nẵng", "Số 72 Nguyễn Văn Linh, Hải Châu, Đà Nẵng", "0236389200"},
            new String[] {"KHCM", "Kho TP. Hồ Chí Minh", "Số 234 Cộng Hòa, Tân Bình, TP.HCM", "0283845300"});

    /** 10 nhà cung cấp: mã, tên, điện thoại, địa chỉ, mã số thuế. */
    public static final List<String[]> SUPPLIERS = List.of(
            new String[] {
                "NCC001", "Công ty TNHH Thương mại Tân Hiệp Phát", "0274376789",
                "219 Đại lộ Bình Dương, Thuận An, Bình Dương", "3700148748"
            },
            new String[] {
                "NCC002", "Công ty CP Sữa Việt Nam - Vinamilk", "0283827900",
                "10 Tân Trào, Tân Phú, Quận 7, TP.HCM", "0300588569"
            },
            new String[] {
                "NCC003", "Công ty CP Acecook Việt Nam", "0283915800",
                "Lô II-3 KCN Tân Bình, TP.HCM", "0300768255"
            },
            new String[] {
                "NCC004", "Công ty CP Bánh kẹo Hải Hà", "0438632956",
                "25 Trương Định, Hai Bà Trưng, Hà Nội", "0100100104"
            },
            new String[] {
                "NCC005", "Tổng Công ty CP Bia - Rượu - NGK Sài Gòn", "0283829400",
                "187 Nguyễn Chí Thanh, Quận 5, TP.HCM", "0300583659"
            },
            new String[] {
                "NCC006", "Công ty TNHH Quốc tế Unilever Việt Nam", "0283825100",
                "156 Nguyễn Lương Bằng, Quận 7, TP.HCM", "0300762150"
            },
            new String[] {
                "NCC007", "Công ty TNHH Nestlé Việt Nam", "0274378200",
                "KCN Biên Hòa II, Đồng Nai", "3600235979"
            },
            new String[] {
                "NCC008", "Công ty CP Dầu thực vật Tường An", "0283864300",
                "48/5 Phan Huy Ích, Tân Bình, TP.HCM", "0300571277"
            },
            new String[] {
                "NCC009", "Công ty CP Hàng tiêu dùng Masan", "0283625500",
                "Tầng 12 Kumho Asiana, Quận 1, TP.HCM", "0302017440"
            },
            new String[] {
                "NCC010", "Công ty CP Tập đoàn Trung Nguyên", "0283822700",
                "82-84 Bùi Thị Xuân, Quận 1, TP.HCM", "0302077077"
            });

    /** 20 khách hàng: mã, tên, điện thoại, địa chỉ. */
    public static final List<String[]> CUSTOMERS = List.of(
            new String[] {"KH001", "Cửa hàng tạp hóa Minh Anh", "0912345601", "45 Láng Hạ, Đống Đa, Hà Nội"},
            new String[] {"KH002", "Siêu thị mini Hồng Phúc", "0912345602", "128 Cầu Giấy, Hà Nội"},
            new String[] {"KH003", "Tạp hóa Bà Bảy", "0912345603", "67 Nguyễn Trãi, Thanh Xuân, Hà Nội"},
            new String[] {"KH004", "Cửa hàng Thành Đạt", "0912345604", "210 Trần Duy Hưng, Hà Nội"},
            new String[] {"KH005", "Chuỗi cửa hàng An Khang", "0912345605", "15 Lê Duẩn, Hải Châu, Đà Nẵng"},
            new String[] {"KH006", "Siêu thị Phương Nam", "0912345606", "88 Hùng Vương, Đà Nẵng"},
            new String[] {"KH007", "Tạp hóa Hải Yến", "0912345607", "34 Ngô Quyền, Sơn Trà, Đà Nẵng"},
            new String[] {"KH008", "Cửa hàng Tiện lợi 24h", "0912345608", "156 Nguyễn Văn Linh, Đà Nẵng"},
            new String[] {"KH009", "Siêu thị Co.op Mart Tân Bình", "0912345609", "571 Cộng Hòa, Tân Bình, TP.HCM"},
            new String[] {"KH010", "Cửa hàng Bách Hóa Xanh Q7", "0912345610", "99 Nguyễn Thị Thập, Quận 7, TP.HCM"},
            new String[] {"KH011", "Tạp hóa Thuận Phát", "0912345611", "23 Phan Văn Trị, Gò Vấp, TP.HCM"},
            new String[] {"KH012", "Cửa hàng Vạn Lợi", "0912345612", "78 Lê Văn Việt, Quận 9, TP.HCM"},
            new String[] {"KH013", "Siêu thị mini Gia Đình", "0912345613", "12 Hoàng Văn Thụ, Phú Nhuận, TP.HCM"},
            new String[] {"KH014", "Cửa hàng Ngọc Hà", "0912345614", "56 Xô Viết Nghệ Tĩnh, Bình Thạnh, TP.HCM"},
            new String[] {"KH015", "Tạp hóa Tâm Đức", "0912345615", "89 Quang Trung, Hà Đông, Hà Nội"},
            new String[] {"KH016", "Cửa hàng Hoàng Gia", "0912345616", "145 Giải Phóng, Hoàng Mai, Hà Nội"},
            new String[] {"KH017", "Siêu thị Bình Minh", "0912345617", "32 Trưng Nữ Vương, Đà Nẵng"},
            new String[] {"KH018", "Cửa hàng Phú Quý", "0912345618", "201 Điện Biên Phủ, Quận 3, TP.HCM"},
            new String[] {"KH019", "Tạp hóa Kim Liên", "0912345619", "64 Nguyễn Oanh, Gò Vấp, TP.HCM"},
            new String[] {"KH020", "Chuỗi bán lẻ Sao Việt", "0912345620", "7 Trần Phú, Hà Đông, Hà Nội"});

    /**
     * 150 sản phẩm theo mẫu: {chỉ số loại hàng, chỉ số ĐVT, tên, giá nhập, giá bán, tồn tối thiểu}.
     *
     * <p>Giá đặt theo mặt bằng thị trường Việt Nam để số liệu báo cáo trông hợp lý.
     */
    public static final List<Object[]> PRODUCTS = List.of(
            // ---------- Nước giải khát (0) ----------
            new Object[] {0, 0, "Nước ngọt Coca-Cola 330ml (thùng 24 lon)", 185000, 230000, 20},
            new Object[] {0, 0, "Nước ngọt Pepsi 330ml (thùng 24 lon)", 180000, 225000, 20},
            new Object[] {0, 0, "Nước ngọt 7Up 330ml (thùng 24 lon)", 178000, 220000, 15},
            new Object[] {0, 0, "Nước ngọt Mirinda cam 330ml (thùng 24 lon)", 176000, 218000, 15},
            new Object[] {0, 0, "Nước ngọt Sprite 330ml (thùng 24 lon)", 182000, 226000, 15},
            new Object[] {0, 0, "Trà xanh Không Độ 500ml (thùng 24 chai)", 150000, 192000, 25},
            new Object[] {0, 0, "Trà Dr Thanh 350ml (thùng 24 chai)", 148000, 190000, 20},
            new Object[] {0, 0, "Nước tăng lực Number 1 330ml (thùng 24 lon)", 165000, 210000, 20},
            new Object[] {0, 0, "Nước tăng lực Red Bull 250ml (thùng 24 lon)", 265000, 320000, 15},
            new Object[] {0, 0, "Nước khoáng Lavie 500ml (thùng 24 chai)", 78000, 100000, 30},
            new Object[] {0, 0, "Nước khoáng Aquafina 500ml (thùng 24 chai)", 82000, 105000, 30},
            new Object[] {0, 2, "Nước cam ép Twister 455ml", 9500, 13000, 40},
            new Object[] {0, 2, "Nước yến ngân nhĩ Wonderfarm 240ml", 11000, 15000, 30},
            new Object[] {0, 0, "Nước dừa Cocoxim 330ml (thùng 24 hộp)", 195000, 245000, 12},
            new Object[] {0, 0, "Sữa trái cây Nutriboost 297ml (thùng 24 chai)", 168000, 210000, 15},
            // ---------- Bia (1) ----------
            new Object[] {1, 0, "Bia Saigon Lager 330ml (thùng 24 lon)", 265000, 315000, 25},
            new Object[] {1, 0, "Bia Saigon Special 330ml (thùng 24 lon)", 310000, 370000, 20},
            new Object[] {1, 0, "Bia 333 Export 330ml (thùng 24 lon)", 275000, 330000, 25},
            new Object[] {1, 0, "Bia Heineken 330ml (thùng 24 lon)", 410000, 480000, 20},
            new Object[] {1, 0, "Bia Tiger Bạc 330ml (thùng 24 lon)", 340000, 400000, 20},
            new Object[] {1, 0, "Bia Hà Nội 450ml (thùng 24 lon)", 245000, 295000, 15},
            new Object[] {1, 0, "Bia Budweiser 330ml (thùng 24 lon)", 395000, 465000, 10},
            new Object[] {1, 0, "Bia Strongbow táo 330ml (thùng 24 lon)", 385000, 455000, 10},
            // ---------- Bánh kẹo (2) ----------
            new Object[] {2, 3, "Bánh quy AFC vị lúa mì 200g", 18000, 24000, 40},
            new Object[] {2, 3, "Bánh Oreo vani 133g", 15000, 20000, 40},
            new Object[] {2, 3, "Bánh Cosy marie 288g", 32000, 42000, 30},
            new Object[] {2, 3, "Bánh Danisa bơ 454g", 125000, 158000, 15},
            new Object[] {2, 3, "Bánh Choco-Pie Orion 12 cái", 52000, 66000, 25},
            new Object[] {2, 4, "Kẹo dẻo Haribo 80g", 22000, 29000, 30},
            new Object[] {2, 4, "Kẹo Alpenliebe sữa 115g", 19000, 25000, 30},
            new Object[] {2, 4, "Bánh gạo One One 150g", 21000, 28000, 25},
            new Object[] {2, 3, "Bánh Custas trứng 12 cái", 48000, 61000, 20},
            new Object[] {2, 3, "Socola KitKat 4 thanh", 28000, 36000, 25},
            new Object[] {2, 4, "Bánh Snack Oishi tôm 42g", 6500, 9000, 50},
            new Object[] {2, 4, "Bánh Poca khoai tây 54g", 11000, 15000, 40},
            new Object[] {2, 3, "Bánh trung thu Kinh Đô 2 trứng", 95000, 125000, 5},
            // ---------- Sữa (3) ----------
            new Object[] {3, 0, "Sữa tươi Vinamilk 100% 180ml (thùng 48 hộp)", 285000, 345000, 25},
            new Object[] {3, 0, "Sữa tươi Dalat Milk 1L (thùng 12 hộp)", 330000, 400000, 15},
            new Object[] {3, 3, "Sữa đặc Ông Thọ 380g", 24000, 31000, 40},
            new Object[] {3, 3, "Sữa bột Dielac Alpha 900g", 215000, 268000, 15},
            new Object[] {3, 3, "Sữa bột Nan Optipro 800g", 485000, 580000, 10},
            new Object[] {3, 0, "Sữa chua Vinamilk có đường (thùng 48 hộp)", 235000, 290000, 20},
            new Object[] {3, 0, "Sữa Milo 180ml (thùng 48 hộp)", 275000, 335000, 20},
            new Object[] {3, 0, "Sữa đậu nành Fami 200ml (thùng 36 hộp)", 148000, 185000, 20},
            new Object[] {3, 3, "Phô mai Con Bò Vui Vẻ 8 miếng", 32000, 42000, 25},
            new Object[] {3, 3, "Bơ lạt Anchor 227g", 78000, 98000, 12},
            // ---------- Mì - gạo - thực phẩm khô (4) ----------
            new Object[] {4, 0, "Mì Hảo Hảo tôm chua cay (thùng 30 gói)", 112000, 142000, 30},
            new Object[] {4, 0, "Mì Omachi xốt bò hầm (thùng 30 gói)", 185000, 230000, 25},
            new Object[] {4, 0, "Mì 3 Miền tôm chua cay (thùng 30 gói)", 98000, 126000, 25},
            new Object[] {4, 0, "Mì Kokomi đại tôm chua cay (thùng 30 gói)", 105000, 134000, 25},
            new Object[] {4, 0, "Phở Đệ Nhất bò (thùng 30 gói)", 168000, 210000, 15},
            new Object[] {4, 0, "Cháo thịt bằm Cây Thị (thùng 30 gói)", 132000, 168000, 12},
            new Object[] {4, 5, "Gạo Jasmine túi 5kg", 128000, 158000, 20},
            new Object[] {4, 5, "Gạo ST25 túi 5kg", 185000, 228000, 15},
            new Object[] {4, 5, "Gạo nếp cái hoa vàng 5kg", 165000, 205000, 10},
            new Object[] {4, 4, "Bún khô Safoco 500g", 24000, 31000, 25},
            new Object[] {4, 4, "Bánh phở khô Safoco 500g", 26000, 33000, 20},
            new Object[] {4, 6, "Đậu xanh cà vỏ 1kg", 42000, 53000, 15},
            new Object[] {4, 6, "Đường trắng Biên Hòa 1kg", 23000, 29000, 30},
            new Object[] {4, 6, "Muối i-ốt Bạc Liêu 1kg", 7000, 10000, 25},
            // ---------- Gia vị - dầu ăn (5) ----------
            new Object[] {5, 2, "Dầu ăn Tường An 1L", 46000, 57000, 30},
            new Object[] {5, 2, "Dầu ăn Simply đậu nành 1L", 58000, 72000, 25},
            new Object[] {5, 2, "Dầu ăn Neptune 1L", 52000, 65000, 25},
            new Object[] {5, 2, "Nước mắm Nam Ngư 500ml", 28000, 36000, 30},
            new Object[] {5, 2, "Nước mắm Chin-su Nam Ngư 900ml", 42000, 53000, 25},
            new Object[] {5, 2, "Nước tương Chin-su 500ml", 22000, 29000, 30},
            new Object[] {5, 2, "Tương ớt Chin-su 250g", 16000, 21000, 35},
            new Object[] {5, 2, "Tương cà Chin-su 250g", 16000, 21000, 30},
            new Object[] {5, 3, "Hạt nêm Knorr thịt heo 400g", 38000, 48000, 25},
            new Object[] {5, 3, "Bột ngọt Ajinomoto 454g", 36000, 45000, 25},
            new Object[] {5, 2, "Giấm gạo Ottogi 500ml", 19000, 25000, 15},
            new Object[] {5, 4, "Bột canh Hải Châu 190g", 6000, 8500, 30},
            new Object[] {5, 4, "Tiêu đen xay Dh Foods 40g", 24000, 31000, 20},
            new Object[] {5, 2, "Dầu hào Lee Kum Kee 510g", 48000, 60000, 12},
            // ---------- Hóa phẩm - vệ sinh (6) ----------
            new Object[] {6, 4, "Bột giặt Omo 4.5kg", 185000, 228000, 20},
            new Object[] {6, 4, "Bột giặt Aba 4.5kg", 142000, 178000, 20},
            new Object[] {6, 2, "Nước giặt Ariel 3.5L", 215000, 265000, 15},
            new Object[] {6, 2, "Nước xả Comfort 3.8L", 198000, 245000, 15},
            new Object[] {6, 2, "Nước rửa chén Sunlight 3.6kg", 92000, 115000, 20},
            new Object[] {6, 2, "Nước lau sàn Gift 3.8L", 85000, 108000, 15},
            new Object[] {6, 2, "Nước tẩy Javel Mỹ Hảo 1L", 24000, 31000, 20},
            new Object[] {6, 2, "Nước tẩy bồn cầu Vim 880ml", 42000, 53000, 18},
            new Object[] {6, 4, "Túi rác tự phân hủy 3 cuộn", 32000, 42000, 25},
            new Object[] {6, 4, "Giấy vệ sinh Pulppy 10 cuộn", 68000, 86000, 25},
            new Object[] {6, 4, "Khăn giấy rút Bless You 180 tờ", 28000, 36000, 25},
            // ---------- Chăm sóc cá nhân (7) ----------
            new Object[] {7, 2, "Dầu gội Clear bạc hà 630ml", 142000, 178000, 18},
            new Object[] {7, 2, "Dầu gội Sunsilk óng mượt 650g", 128000, 162000, 18},
            new Object[] {7, 2, "Dầu gội Head & Shoulders 625ml", 158000, 195000, 15},
            new Object[] {7, 2, "Sữa tắm Dove dưỡng ẩm 900g", 185000, 228000, 15},
            new Object[] {7, 2, "Sữa tắm Lifebuoy 800g", 142000, 178000, 18},
            new Object[] {7, 3, "Kem đánh răng P/S trà xanh 230g", 32000, 42000, 30},
            new Object[] {7, 3, "Kem đánh răng Colgate 180g", 29000, 38000, 30},
            new Object[] {7, 3, "Bàn chải Oral-B 2 cái", 42000, 54000, 25},
            new Object[] {7, 2, "Nước rửa tay Lifebuoy 450ml", 52000, 66000, 20},
            new Object[] {7, 4, "Xà bông Lifebuoy 90g x4", 38000, 48000, 25},
            new Object[] {7, 4, "Băng vệ sinh Diana 8 miếng", 26000, 34000, 30},
            new Object[] {7, 4, "Khăn giấy ướt Bobby 100 tờ", 32000, 42000, 25},
            new Object[] {7, 2, "Nước hoa hồng Simple 200ml", 118000, 148000, 10},
            new Object[] {7, 3, "Lăn khử mùi Nivea 50ml", 68000, 86000, 15},
            // ---------- Gia dụng (8) ----------
            new Object[] {8, 3, "Nồi inox 3 đáy 24cm Sunhouse", 285000, 360000, 8},
            new Object[] {8, 3, "Chảo chống dính 26cm Elmich", 215000, 275000, 8},
            new Object[] {8, 3, "Bộ 6 ly thủy tinh Union", 125000, 160000, 10},
            new Object[] {8, 3, "Bộ 10 đĩa sứ Minh Long", 285000, 365000, 6},
            new Object[] {8, 3, "Hộp nhựa đựng thực phẩm 5 cái", 98000, 125000, 12},
            new Object[] {8, 3, "Bình giữ nhiệt Lock&Lock 500ml", 245000, 310000, 8},
            new Object[] {8, 4, "Màng bọc thực phẩm 30cm x 20m", 32000, 42000, 20},
            new Object[] {8, 4, "Giấy bạc nướng 30cm x 5m", 38000, 48000, 15},
            new Object[] {8, 3, "Rổ nhựa đa năng cỡ lớn", 45000, 58000, 15},
            new Object[] {8, 3, "Thùng rác nhựa có nắp 15L", 128000, 165000, 8},
            // ---------- Thực phẩm đóng hộp (9) ----------
            new Object[] {9, 3, "Cá hộp Ba Cô Gái 155g", 22000, 29000, 30},
            new Object[] {9, 3, "Cá ngừ ngâm dầu Sea Crown 185g", 32000, 42000, 25},
            new Object[] {9, 3, "Thịt hộp Spam 340g", 98000, 125000, 15},
            new Object[] {9, 3, "Pate gan Hạ Long 170g", 26000, 34000, 25},
            new Object[] {9, 3, "Xúc xích Vissan 500g", 68000, 86000, 20},
            new Object[] {9, 3, "Bắp non đóng hộp 425g", 28000, 36000, 18},
            new Object[] {9, 3, "Nấm đông cô đóng hộp 400g", 34000, 44000, 15},
            new Object[] {9, 3, "Đậu Hà Lan đóng hộp 400g", 24000, 32000, 18},
            new Object[] {9, 3, "Sốt mì Ý Prego 350g", 52000, 66000, 12},
            new Object[] {9, 3, "Ngô ngọt hạt Mỹ 340g", 26000, 34000, 15},
            // ---------- Bổ sung cho đủ 150 ----------
            new Object[] {0, 2, "Nước ép táo Vfresh 1L", 32000, 42000, 15},
            new Object[] {0, 2, "Nước ép nho Vfresh 1L", 34000, 44000, 15},
            new Object[] {0, 0, "Soda Schweppes 330ml (thùng 24 lon)", 178000, 222000, 10},
            new Object[] {2, 4, "Bánh Chocopie mini 10 cái", 32000, 42000, 20},
            new Object[] {2, 4, "Hạt điều rang muối 250g", 125000, 158000, 12},
            new Object[] {2, 4, "Hạt dưa đỏ 200g", 42000, 54000, 15},
            new Object[] {3, 3, "Sữa chua uống Yakult 5 chai", 32000, 41000, 25},
            new Object[] {3, 3, "Kem Merino hộp 450ml", 58000, 74000, 10},
            new Object[] {4, 4, "Ngũ cốc Nestlé Koko Krunch 330g", 78000, 98000, 12},
            new Object[] {4, 4, "Yến mạch Quaker 800g", 115000, 145000, 12},
            new Object[] {5, 4, "Bột chiên giòn Tài Ký 150g", 12000, 16000, 25},
            new Object[] {5, 4, "Bột mì đa dụng Meizan 1kg", 24000, 31000, 20},
            new Object[] {6, 2, "Nước lau kính Gift 500ml", 32000, 42000, 15},
            new Object[] {6, 4, "Viên tẩy lồng giặt 5 viên", 48000, 62000, 12},
            new Object[] {7, 2, "Sữa rửa mặt Hazeline 100g", 48000, 62000, 15},
            new Object[] {7, 3, "Kem chống nắng Sunplay SPF50", 125000, 158000, 10},
            new Object[] {8, 3, "Dao thái inox Zwilling", 185000, 235000, 6},
            new Object[] {8, 3, "Thớt gỗ tre 30x20cm", 78000, 98000, 10},
            new Object[] {9, 3, "Cá mòi sốt cà Sumaco 155g", 20000, 27000, 25},
            new Object[] {9, 3, "Chả lụa Vissan 500g", 92000, 118000, 12},
            new Object[] {1, 0, "Bia Sapporo Premium 330ml (thùng 24 lon)", 425000, 495000, 8},
            new Object[] {1, 0, "Rượu vang Đà Lạt đỏ 750ml", 145000, 185000, 6},
            new Object[] {0, 0, "Nước ngọt Fanta cam 330ml (thùng 24 lon)", 180000, 224000, 15},
            new Object[] {3, 0, "Sữa tươi TH True Milk 180ml (thùng 48 hộp)", 298000, 360000, 20},
            new Object[] {4, 0, "Mì Indomie xào (thùng 30 gói)", 125000, 158000, 15},
            new Object[] {5, 2, "Mật ong rừng Tây Nguyên 500ml", 148000, 188000, 10},
            new Object[] {6, 2, "Nước xả Downy 3L", 225000, 278000, 12},
            new Object[] {7, 2, "Dầu xả Pantene 600ml", 132000, 168000, 12},
            new Object[] {8, 3, "Bộ 2 hộp cơm giữ nhiệt", 165000, 210000, 8},
            new Object[] {9, 3, "Tương đen Hoisin 240g", 32000, 42000, 12},
            new Object[] {0, 2, "Nước chanh muối Chương Dương 390ml", 12000, 16000, 20});
}
