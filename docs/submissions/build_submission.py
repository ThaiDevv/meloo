#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script tự động biên soạn và xuất bản Hồ sơ Thuyết minh chi tiết thiết kế hệ thống Meloo (Mốc 2).
Sử dụng: python3 docs/submissions/build_submission.py
Kết xuất:
  - docs/submissions/thuyet-minh-thiet-ke-chi-tiet.md
  - docs/submissions/MELOO-Thuyet-minh-thiet-ke-chi-tiet.docx
  - docs/submissions/MELOO-Thuyet-minh-thiet-ke-chi-tiet.pdf
"""

import os
import re
import subprocess
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(BASE_DIR, "../.."))

MD_FILE = os.path.join(BASE_DIR, "thuyet-minh-thiet-ke-chi-tiet.md")
DOCX_FILE = os.path.join(BASE_DIR, "MELOO-Thuyet-minh-thiet-ke-chi-tiet.docx")
PDF_FILE = os.path.join(BASE_DIR, "MELOO-Thuyet-minh-thiet-ke-chi-tiet.pdf")

def generate_markdown():
    lines = []
    
    # Header & Metadata
    lines.append("# BỘ HỒ SƠ THUYẾT MINH CHI TIẾT THIẾT KẾ HỆ THỐNG")
    lines.append("## ĐỀ TÀI: NỀN TẢNG WEB GAME ĐOÁN TÊN BÀI HÁT THỜI GIAN THỰC MELOO")
    lines.append("")
    lines.append("> **Mã tài liệu:** DOC-05 · **Mã ticket Jira:** MEL-62 · **Mốc bàn giao:** Mốc 2 (Sprint S3)")  
    lines.append("> **Sinh viên thực hiện:** Trần Văn Thái · **Người kiểm tra / Phản biện:** Codex (AI Reviewer)")  
    lines.append("> **Hạn nộp chính thức:** 16/11/2026, 09:07 · **Trạng thái:** Hoàn thành biên soạn hồ sơ thiết kế")  
    lines.append("> **Căn cứ tài liệu nền tảng:** BIZ-02 (MEL-18), BIZ-04 (MEL-20), BIZ-05 (MEL-53), DOC-01/SRS v1 (MEL-28), DOC-02/Use Cases (MEL-29), DES-01/Data Model (MEL-30), DOC-03/UML (MEL-51), DOC-04/Architecture (MEL-52), ADR-001..008 (MEL-26, MEL-118, MEL-136).")  
    lines.append("")
    lines.append("---")
    lines.append("")

    # Lời nói đầu
    lines.append("## LỜI NÓI ĐẦU VÀ TỔNG QUAN BÀN GIAO MỐC 2")
    lines.append("")
    lines.append("Tài liệu này là bản **Thuyết minh chi tiết thiết kế hệ thống** hoàn chỉnh của đề tài **Meloo - Web game đoán tên bài hát trực tuyến theo thời gian thực**, được xây dựng phục vụ đánh giá tiến độ học thuật Mốc 2 theo chuẩn đầu ra của học phần. Báo cáo tập hợp toàn bộ các kết quả phân tích nghiệp vụ, mô hình hóa yêu cầu, kiến trúc hệ thống C4, thiết kế phần mềm hướng đối tượng UML, mô hình cơ sở dữ liệu quan hệ ERD và các quyết định kỹ thuật then chốt (ADR) đã được kiểm chứng và phê duyệt trong các Sprint S0 đến S3 của dự án.")
    lines.append("")
    lines.append("Hồ sơ được cấu trúc thành 7 phần nội dung chính và phụ lục, trình bày bằng văn phong học thuật tiếng Việt mạch lạc, hệ thống ký hiệu chuẩn hóa (FR-xx, NFR-xx, BR-xx, UC-xx, ADR-0xx) và nhúng toàn bộ 25 sơ đồ kỹ thuật chất lượng cao được kết xuất từ mã nguồn PlantUML phiên bản mới nhất trong kho lưu trữ.")
    lines.append("")
    lines.append("---")
    lines.append("")

    # Mục lục
    lines.append("## MỤC LỤC TỔNG THỂ")
    lines.append("")
    lines.append("- [PHẦN I: GIỚI THIỆU TỔNG QUAN VÀ PHÁT BIỂU BÀI TOÁN](#phần-i-giới-thiệu-tổng-quan-và-phát-biểu-bài-toán)")
    lines.append("  - [1.1. Meloo trong một câu và tầm nhìn sản phẩm](#11-meloo-trong-một-câu-và-tầm-nhìn-sản-phẩm)")
    lines.append("  - [1.2. Bối cảnh thị trường và vấn đề thực tế](#12-bối-cảnh-thị-trường-và-vấn-đề-thực-tế)")
    lines.append("  - [1.3. Khảo sát nhu cầu người dùng và kiểm chứng giả thuyết (H1–H4)](#13-khảo-sát-nhu-cầu-người-dùng-và-kiểm-chứng-giả-thuyết-h1h4)")
    lines.append("  - [1.4. Chân dung người dùng mục tiêu (Personas P1–P4)](#14-chân-dung-người-dùng-mục-tiêu-personas-p1p4)")
    lines.append("  - [1.5. Phân tích đối thủ cạnh tranh và giá trị khác biệt](#15-phân-tích-đối-thủ-cạnh-tranh-và-giá-trị-khác-biệt)")
    lines.append("- [PHẦN II: ĐẶC TẢ YÊU CẦU PHẦN MỀM (SRS) VÀ QUY TẮC NGHIỆP VỤ](#phần-ii-đặc-tả-yêu-cầu-phần-mềm-srs-và-quy-tắc-nghiệp-vụ)")
    lines.append("  - [2.1. Yêu cầu chức năng cốt lõi (Functional Requirements: FR-01 → FR-19)](#21-yêu-cầu-chức-năng-cốt-lõi-functional-requirements-fr-01--fr-19)")
    lines.append("  - [2.2. Yêu cầu phi chức năng (Non-Functional Requirements: NFR-01 → NFR-15)](#22-yêu-cầu-phi-chức-năng-non-functional-requirements-nfr-01--nfr-15)")
    lines.append("  - [2.3. Quy tắc nghiệp vụ then chốt (Business Rules: BR-01 → BR-29)](#23-quy-tắc-nghiệp-vụ-then-chốt-business-rules-br-01--br-29)")
    lines.append("  - [2.4. Thuật ngữ và từ điển khái niệm hệ thống](#24-thuật-ngữ-và-từ-điển-khái-niệm-hệ-thống)")
    lines.append("- [PHẦN III: THIẾT KẾ USE CASE VÀ ĐẶC TẢ CHI TIẾT](#phần-iii-thiết-kế-use-case-và-đặc-tả-chi-tiết)")
    lines.append("  - [3.1. Tổng quan mô hình tác nhân và danh mục 34 Use Case](#31-tổng-quan-mô-hình-tác-nhân-và-danh-mục-34-use-case)")
    lines.append("  - [3.2. Sơ đồ Use Case tổng thể và các phân hệ](#32-sơ-đồ-use-case-tổng-thể-và-các-phân-hệ)")
    lines.append("  - [3.3. Ma trận truy vết yêu cầu (Traceability Matrix: FR ↔ UC)](#33-ma-trận-truy-vết-yêu-cầu-traceability-matrix-fr--uc)")
    lines.append("  - [3.4. Đặc tả chi tiết 10 Use Case trọng tâm](#34-đặc-tả-chi-tiết-10-use-case-trọng-tâm)")
    lines.append("- [PHẦN IV: THIẾT KẾ KIẾN TRÚC HỆ THỐNG C4 MODEL VÀ TRIỂN KHAI](#phần-iv-thiết-kế-kiến-trúc-hệ-thống-c4-model-và-triển-khai)")
    lines.append("  - [4.1. Triết lý thiết kế và ràng buộc hệ thống](#41-triết-lý-thiết-kế-và-ràng-buộc-hệ-thống)")
    lines.append("  - [4.2. C4 Level 1: Sơ đồ bối cảnh hệ thống (Context Diagram)](#42-c4-level-1-sơ-đồ-bối-cảnh-hệ-thống-context-diagram)")
    lines.append("  - [4.3. C4 Level 2: Sơ đồ khối thùng chứa (Container Diagram)](#43-c4-level-2-sơ-đồ-khối-thùng-chứa-container-diagram)")
    lines.append("  - [4.4. C4 Level 3: Sơ đồ thành phần Backend Modular Monolith (Component Diagram)](#44-c4-level-3-sơ-đồ-thành-phần-backend-modular-monolith-component-diagram)")
    lines.append("  - [4.5. Sơ đồ triển khai hạ tầng đám mây AWS (Deployment Diagram)](#45-sơ-đồ-triển-khai-hạ-tầng-đám-mây-aws-deployment-diagram)")
    lines.append("  - [4.6. Tổng hợp các quyết định kiến trúc then chốt (ADR-001 → ADR-008)](#46-tổng-hợp-các-quyết-định-kiến-trúc-then-chốt-adr-001--adr-008)")
    lines.append("  - [4.7. Chiến thuật kiến trúc đáp ứng yêu cầu phi chức năng (NFR Tactics)](#47-chiến-thuật-kiến-trúc-đáp-ứng-yêu-cầu-phi-chức-năng-nfr-tactics)")
    lines.append("- [PHẦN V: THIẾT KẾ CHI TIẾT PHẦN MỀM (UML CHI TIẾT)](#phần-v-thiết-kế-chi-tiết-phần-mềm-uml-chi-tiết)")
    lines.append("  - [5.1. Sơ đồ lớp miền tổng quát (Domain Model Class Diagram)](#51-sơ-đồ-lớp-miền-tổng-quát-domain-model-class-diagram)")
    lines.append("  - [5.2. Thiết kế chi tiết sơ đồ lớp các module nghiệp vụ cốt lõi](#52-thiết-kế-chi-tiết-sơ-đồ-lớp-các-module-nghiệp-vụ-cốt-lõi)")
    lines.append("  - [5.3. Thiết kế sơ đồ tuần tự (Sequence Diagrams) cho các kịch bản tương tác chính](#53-thiết-kế-sơ-đồ-tuần-tự-sequence-diagrams-cho-các-kịch-bản-tương-tác-chính)")
    lines.append("  - [5.4. Thiết kế sơ đồ máy trạng thái (State Machine Diagrams)](#54-thiết-kế-sơ-đồ-máy-trạng-thái-state-machine-diagrams)")
    lines.append("  - [5.5. Sơ đồ hoạt động luồng một vòng chơi (Activity Diagram)](#55-sơ-đồ-hoạt-động-luồng-một-vòng-chơi-activity-diagram)")
    lines.append("- [PHẦN VI: THIẾT KẾ CƠ SỞ DỮ LIỆU VÀ TỔ CHỨC LƯU TRỮ](#phần-vi-thiết-kế-cơ-sở-dữ-liệu-và-tổ-chức-lưu-trữ)")
    lines.append("  - [6.1. Nguyên tắc thiết kế CSDL và phân quyền module Spring Modulith](#61-nguyên-tắc-thiết-kế-csdl-và-phân-quyền-module-spring-modulith)")
    lines.append("  - [6.2. Mô hình quan hệ thực thể ERD tổng thể](#62-mô-hình-quan-hệ-thực-thể-erd-tổng-thể)")
    lines.append("  - [6.3. Từ điển dữ liệu chi tiết 21 bảng (Data Dictionary)](#63-từ-điển-dữ-liệu-chi-tiết-21-bảng-data-dictionary)")
    lines.append("  - [6.4. Kế hoạch di trú dữ liệu tự động (Flyway Migration Plan V1 → V9)](#64-kế-hoạch-di-trú-dữ-liệu-tự-động-flyway-migration-plan-v1--v9)")
    lines.append("- [PHẦN VII: BỘ CÂU HỎI BẢO VỆ ĐỒ ÁN VÀ HƯỚNG DẪN GIẢI THÍCH](#phần-vii-bộ-câu-hỏi-bảo-vệ-đồ-án-và-hướng-dẫn-giải-thích)")
    lines.append("  - [7.1. Nhóm câu hỏi về Kiến trúc và Lựa chọn công nghệ](#71-nhóm-câu-hỏi-về-kiến-trúc-và-lựa-chọn-công-nghệ)")
    lines.append("  - [7.2. Nhóm câu hỏi về Bảo mật, Phòng chống gian lận và Bản quyền âm thanh](#72-nhóm-câu-hỏi-về-bảo-mật-phòng-chống-gian-lận-và-bản-quyền-âm-thanh)")
    lines.append("  - [7.3. Nhóm câu hỏi về Xử lý thời gian thực và Chống tranh chấp dữ liệu (Race Condition)](#73-nhóm-câu-hỏi-về-xử-lý-thời-gian-thực-và-chống-tranh-chấp-dữ-liệu-race-condition)")
    lines.append("  - [7.4. Nhóm câu hỏi về Thuật toán tính điểm và Luật chơi](#74-nhóm-câu-hỏi-về-thuật-toán-tính-điểm-và-luật-chơi)")
    lines.append("- [PHỤ LỤC: DANH MỤC TÀI LIỆU THAM CHIẾU](#phụ-lục-danh-mục-tài-liệu-tham-chiếu)")
    lines.append("")
    lines.append("---")
    lines.append("")

    # PHẦN I
    lines.append("# PHẦN I: GIỚI THIỆU TỔNG QUAN VÀ PHÁT BIỂU BÀI TOÁN")
    lines.append("")
    lines.append("### 1.1. Meloo trong một câu và tầm nhìn sản phẩm")
    lines.append("**Meloo là trò chơi đoán tên bài hát trực tuyến 10–15 phút, tham gia tức thời bằng một đường link trình duyệt, để nhóm bạn người Việt cùng chơi, tương tác trò chuyện và so tài âm nhạc bằng chính những giai điệu quen thuộc hàng ngày.**")
    lines.append("")
    lines.append("Sản phẩm định vị là một mini-game mang tính xã hội cao (social party game), giải quyết bài toán thiếu hụt hoạt động gắn kết ngắn hạn cho sinh viên, đồng nghiệp trẻ trong giờ giải lao hoặc những buổi tối rảnh rỗi mà không đòi hỏi bất kỳ rào cản kỹ thuật hay cài đặt phức tạp nào.")
    lines.append("")
    lines.append("### 1.2. Bối cảnh thị trường và vấn đề thực tế")
    lines.append("Nghiên cứu thị trường và hành vi nghe nhạc tại Việt Nam cho thấy những thực trạng rõ nét:")
    lines.append("1. **Nghe nhạc là thói quen phổ biến nhưng thụ động:** Theo khảo sát của Q&Me, 75% người Việt nghe nhạc mỗi ngày và 93% nghe qua điện thoại di động [S1]. Tuy nhiên, các nền tảng streaming (YouTube 74%, Zing MP3 45%, TikTok 32%, Spotify 27% theo Decision Lab Q4/2025) thuần túy phục vụ việc nghe cá nhân, đơn lẻ, thiếu tính cạnh tranh và tương tác bạn bè.")
    lines.append("2. **Sức hút đã được kiểm chứng của thể loại đoán nhạc:** Mô hình Heardle (Wordle âm nhạc) từng đạt 69 triệu lượt truy cập toàn cầu trong tháng 3/2022 trước khi được Spotify mua lại và đóng cửa vào tháng 5/2023 [S2, S3]. Điều này chứng minh nhu cầu thử thách kiến thức âm nhạc ngắn hàng ngày là rất lớn.")
    lines.append("3. **Khoảng trống thị trường tại Việt Nam:** Các tựa game quốc tế hiện nay gần như bỏ quên thị trường Việt Nam. SongTrivia hỗ trợ phòng realtime nhưng không có tiếng Việt và hoàn toàn không có kho nhạc Việt. SongPop hỗ trợ 17 ngôn ngữ nhưng thiếu tiếng Việt và bắt buộc cài đặt app di động dung lượng lớn. Một số trang web Việt Nam chỉ cung cấp trắc nghiệm 1 trong 4 đáp án đơn điệu, dễ đoán mò và không hỗ trợ nhiều người chơi cùng lúc.")
    lines.append("")
    lines.append("### 1.3. Khảo sát nhu cầu người dùng và kiểm chứng giả thuyết (H1–H4)")
    lines.append("Dự án áp dụng phương pháp luận phát triển tinh gọn (Lean Startup), đặt ra 4 giả thuyết cốt lõi kèm tiêu chuẩn kiểm chứng thực nghiệm trước khi phát hành khảo sát (chốt tại BIZ-02 ngày 19/10/2026 và kế thừa tại BIZ-05/MEL-53):")
    lines.append("")
    lines.append("| Mã | Giả thuyết bài toán | Câu hỏi kiểm chứng (Google Form) | Tiêu chí & Ngưỡng xác nhận (BIZ-02) | Trạng thái ghi nhận |")
    lines.append("|---|---|---|---|---|")
    lines.append(r"| **H1** | Nhóm bạn thiếu một hoạt động online **ngắn, vào ngay**; rào cản lớn nhất là phải cài app hoặc đăng ký tài khoản. | K1 (Tần suất chơi cùng bạn bè), K2 (Công cụ thoại), K3 (Rào cản thử game mới). | **$\ge$ 50%** chơi cùng bạn bè $\ge$ 1 lần/tháng **VÀ** **$\ge$ 40%** chọn 'phải cài app/đăng ký' hoặc 'mất thời gian rủ/hướng dẫn' ở K3. | Chờ trích xuất số liệu đóng khảo sát (08/11/2026) |")
    lines.append(r"| **H2** | Người chơi muốn đoán **nhạc Việt** với **giao diện tiếng Việt**; các tựa game ngoại hiện tại không đáp ứng. | K4 (Game từng chơi), K5 (Thể loại nhạc ưa thích), K6 (Điểm chưa hài lòng ở game cũ). | **$\ge$ 60%** chọn V-pop trong top 2 ở K5 **VÀ** trong nhóm từng chơi, **$\ge$ 40%** chọn 'ít nhạc Việt' hoặc 'không có tiếng Việt' ở K6. | Chờ trích xuất số liệu đóng khảo sát (08/11/2026) |")
    lines.append(r"| **H3** | Người nghe nhạc yêu thích **thử thách ngắn hằng ngày** và có nhu cầu **so tài, khoe kết quả** (hiệu ứng Wordle/Heardle). | K7 (Thói quen chơi game hằng ngày), K8 (Hành vi chia sẻ kết quả lên mạng xã hội/nhóm chat). | **$\ge$ 30%** trả lời 'Có' ở K7 **VÀ** **$\ge$ 40%** trả lời 'Có' ở K8. | Chờ trích xuất số liệu đóng khảo sát (08/11/2026) |")
    lines.append(r"| **H4** | Người tổ chức hoạt động (lớp trưởng, quản trò, HR, streamer) cần **công cụ khởi động 10 phút** có bộ đề linh hoạt. | K9 (Kinh nghiệm tổ chức hoạt động), K10 (Mức độ quan tâm dùng game đoán nhạc làm ice-breaker). | **$\ge$ 20%** từng tổ chức hoạt động **VÀ** trong số đó **$\ge$ 50%** quan tâm dùng thử. | Đánh giá phục vụ cơ hội mở rộng Phase 2 |")
    lines.append("")
    lines.append("> *Ghi chú học thuật:* Khảo sát thu thập trực tuyến qua Google Form (K1–K12) hướng tới đối tượng sinh viên và người trẻ yêu âm nhạc. Tiêu chuẩn làm sạch dữ liệu nghiêm ngặt loại bỏ các phản hồi dưới 35 giây hoặc có đáp án mâu thuẫn nội tại nhằm đảm bảo tính khách quan tối đa theo quy ước `meloo-docs`.")
    lines.append("")
    lines.append("### 1.4. Chân dung người dùng mục tiêu (Personas P1–P4)")
    lines.append("1. **P1 · Minh (20 tuổi) - Sinh viên đại học (Người dùng trọng tâm):** Thường xuyên học nhóm và tán gẫu qua Discord cùng bạn bè cũ vào buổi tối. Cần một trò chơi vui nhộn, tham gia trong 10 giây qua link gửi trong chat nhóm, không đòi hỏi mọi người phải tải app hay tạo tài khoản.")
    lines.append("2. **P2 · Lan (24 tuổi) - Nhân viên văn phòng, tín đồ âm nhạc:** Nghe nhạc cả ngày trên Spotify/Zing MP3, thuộc lòng lời bài hát V-pop. Thích chơi thử thách ngày mỗi sáng trên xe buýt và tự hào khoe thành tích đoán đúng ngay 2 giây đầu tiên lên Story mạng xã hội.")
    lines.append("3. **P3 · Hoàng (21 tuổi) - Game thủ cạnh tranh:** Thích các trận đấu xếp hạng căng thẳng, đề cao sự công bằng, ghét gian lận và mong muốn leo lên Top 10 bảng xếp hạng mùa.")
    lines.append("4. **P4 · Thảo (26 tuổi) - Quản lý nhân sự / Quản trò:** Cần một minigame trực tuyến vui nhộn kéo dài 10 phút để khuấy động không khí đầu giờ các buổi sinh hoạt nội bộ hoặc họp trực tuyến.")
    lines.append("")
    lines.append("### 1.5. Phân tích đối thủ cạnh tranh và giá trị khác biệt")
    lines.append("Bảng đối chiếu tính năng giữa Meloo và các đối thủ trực tiếp trên thị trường (dựa trên kết quả khảo nghiệm BIZ-04):")
    lines.append("")
    lines.append("| Tiêu chí so sánh | Meloo (Đề xuất) | SongTrivia | SongPop | Heardle (Đã đóng) | Web trắc nghiệm VN |")
    lines.append("|---|---|---|---|---|---|")
    lines.append("| **Nền tảng hỗ trợ** | Web (mọi thiết bị) | Web | Mobile App (iOS/Android) | Web | Web |")
    lines.append("| **Rào cản đăng nhập** | Vào ngay (Khách vãng lai) | Cần tài khoản để lưu điểm | Bắt buộc đăng ký tài khoản | Chơi ngay không cần tài khoản | Chơi ngay |")
    lines.append("| **Kho nhạc Việt Nam** | Chuyên sâu V-pop & Quốc tế | Hoàn toàn không có | Rất hạn chế (< 2%) | Không có (chỉ US-UK) | Rải rác, không chuẩn hóa |")
    lines.append("| **Chơi nhóm thời gian thực** | Có (2–5 người, STOMP) | Có (Realtime WebSocket) | Bất đồng bộ (Turn-based) | Không (Chỉ chơi đơn) | Không (Chỉ chơi đơn) |")
    lines.append("| **Cơ chế gợi ý âm thanh** | Mở dần 2s → 30s (5 mức) | Nghe cố định 10s | Nghe cố định 10s | Mở dần 1s → 16s | Nghe cả đoạn 30s |")
    lines.append("| **Chống đoán mò** | Nhập tự do + gợi ý chuẩn hóa | Trắc nghiệm 1/4 | Trắc nghiệm 1/4 | Nhập tự do | Trắc nghiệm 1/4 |")
    lines.append("")
    lines.append("---")
    lines.append("")

    # PHẦN II
    lines.append("# PHẦN II: ĐẶC TẢ YÊU CẦU PHẦN MỀM (SRS) VÀ QUY TẮC NGHIỆP VỤ")
    lines.append("")
    lines.append("### 2.1. Yêu cầu chức năng cốt lõi (Functional Requirements: FR-01 → FR-19)")
    lines.append("Các yêu cầu chức năng được phân loại theo mô hình MoSCoW, phục vụ kiểm chứng trực tiếp qua bộ Acceptance Criteria của từng ticket Jira:")
    lines.append("")
    lines.append("| Mã FR | Phân hệ nghiệp vụ | Nội dung yêu cầu chức năng | Mức ưu tiên | Sprint | Quy tắc BR liên quan |")
    lines.append("|---|---|---|---|---|---|")
    lines.append("| **FR-01** | Tài khoản & Định danh | Vào chơi ngay bằng biệt danh (Guest session), cấp phát phiên làm việc an toàn không đòi hỏi mật khẩu. | **Must** | S1 | BR-20, BR-22 |")
    lines.append("| **FR-02** | Tài khoản & Định danh | Đăng nhập bằng Google OAuth 2.0 (Authorization Code + PKCE), cấp JWT token. | **Must** | S1 | BR-21 |")
    lines.append("| **FR-03** | Tài khoản & Định danh | Đăng nhập Facebook OAuth và chức năng xóa tài khoản hoàn toàn theo cam kết quyền riêng tư. | **Should** | S4 | BR-21 |")
    lines.append("| **FR-04** | Tài khoản & Hồ sơ | Xem thông tin hồ sơ cá nhân, cấp độ kinh nghiệm (EXP), tổng điểm xếp hạng và lịch sử đấu. | **Must** | S4 | BR-20, BR-21, BR-24 |")
    lines.append("| **FR-05** | Quản trị danh mục | Quản lý kho bài hát, ca sĩ, thể loại và tạo bộ đề (Playlists); nhập bài hát từ API Deezer. | **Must** | S1 | BR-27, BR-28 |")
    lines.append("| **FR-06** | Tìm kiếm bài hát | Ô nhập tìm kiếm gợi ý thông minh, gõ không dấu tự động chuẩn hóa tìm đúng tên bài hát. | **Must** | S2 | BR-05, BR-06 |")
    lines.append("| **FR-07** | Luật chơi & Chấm điểm | Chế độ chơi đơn 10 vòng, mở mức nghe 2s → 30s, chấm điểm giảm dần, thưởng combo và gợi ý ca sĩ. | **Must** | S2 | BR-01..08, BR-23 |")
    lines.append("| **FR-08** | Thử thách ngày | Chơi bộ đề thử thách ngày (Daily Challenge) duy nhất cho toàn cộng đồng, đổi bài lúc 00:00 VN, chia sẻ kết quả. | **Should** | S4 | BR-08, BR-25 |")
    lines.append("| **FR-09** | Phòng nhiều người | Tạo phòng chơi riêng bằng mã 6 ký tự hoặc link mời, sảnh chờ, nút sẵn sàng và bắt đầu trận đấu 2–5 người. | **Must** | S3 | BR-11, BR-12, BR-15 |")
    lines.append("| **FR-10** | Phòng công khai & Ghép trận | Danh sách phòng công khai và chế độ ghép trận nhanh tự động (Quick Matchmaking) tính điểm xếp hạng. | **Must** | S4 | BR-10, BR-13, BR-23 |")
    lines.append("| **FR-11** | Giao tiếp phòng | Trò chuyện văn bản (Text Chat) trong phòng chơi, tự động chặn tin nhắn làm lộ đáp án và từ ngữ thô tục. | **Must** | S3 | BR-16, BR-17 |")
    lines.append("| **FR-12** | Giao tiếp phòng | Đã bỏ ngày 14/11/2026 (trước đây: voice chat trong phòng riêng). Người chơi trao đổi bằng chat chữ (FR-11). | **Won't** | — | — |")
    lines.append("| **FR-13** | Lưu trữ lịch sử | Tự động lưu trữ chi tiết từng vòng đấu, điểm số từng thành viên vào cơ sở dữ liệu khi trận đấu kết thúc. | **Must** | S2 | BR-23, BR-24 |")
    lines.append("| **FR-14** | Bảng xếp hạng | Bảng vinh danh top người chơi theo mùa giải, tuần, và bảng xếp hạng kết quả thử thách ngày. | **Must** | S4 | BR-24, BR-25 |")
    lines.append("| **FR-15** | Hệ thống tiến trình | Hệ thống thành tựu, huy hiệu danh hiệu và thanh tiến trình thăng cấp người chơi. | **Should** | S5 | BR-26 |")
    lines.append("| **FR-16** | Bảng số liệu quản trị | Dashboard thống kê số lượng phòng hoạt động, người chơi đồng thời (CCU), bài hát được chơi nhiều nhất. | **Should** | S5 | BR-28 |")
    lines.append("| **FR-17** | Kiểm duyệt nội dung | Xem danh sách tin nhắn bị người chơi báo cáo, khóa tài khoản hoặc cấm id khách vi phạm tiêu chuẩn cộng đồng. | **Must** | S5 | BR-28 |")
    lines.append("| **FR-18** | Đa ngôn ngữ (i18n) | Hỗ trợ chuyển đổi giao diện mượt mà giữa tiếng Việt và tiếng Anh (vi/en). | **Must** | S2 | Tiêu chuẩn chất lượng |")
    lines.append("| **FR-19** | Ứng dụng Web cấp tiến | Khả năng cài đặt lên màn hình chính điện thoại dưới dạng Progressive Web App (PWA). | **Should** | S5 | Tiêu chuẩn trải nghiệm |")
    lines.append("")
    lines.append("### 2.2. Yêu cầu phi chức năng (Non-Functional Requirements: NFR-01 → NFR-15)")
    lines.append("Các chỉ số kỹ thuật và ràng buộc chất lượng được lượng hóa rõ ràng:")
    lines.append(r"- **NFR-01 (Thời gian phản hồi API):** Thời gian phản hồi phân vị 95 (p95) của các điểm cuối REST API $\le 300\text{ ms}$ khi kiểm thử tải k6.")
    lines.append(r"- **NFR-02 (Độ trễ sự kiện thời gian thực):** Độ trễ phát và nhận thông điệp STOMP qua WebSocket giữa các người chơi trong phòng $\le 200\text{ ms}$.")
    lines.append(r"- **NFR-03 (Khả năng chịu tải đồng thời):** Hệ thống phục vụ ổn định tối thiểu **20 phòng chơi $\times$ 5 người (100 kết nối đồng thời)** trên cấu hình máy chủ đơn AWS EC2 `t3.micro` (1 vCPU, 1 GB RAM).")
    lines.append(r"- **NFR-04 (Độ trễ phát âm thanh):** Nhờ cơ chế nạp trước luồng đệm (Audio Preloading) trong pha chuẩn bị, âm thanh phát ra trong vòng $\le 1,0\text{ giây}$ ngay khi vòng đấu chính thức bắt đầu.")
    lines.append("- **NFR-05 (Bảo mật phiên):** Token JWT Access lưu trong bộ nhớ JavaScript chống XSS; Cookie Refresh Token mang cờ `HttpOnly`, `Secure`, `SameSite=Lax` chống CSRF.")
    lines.append(r"- **NFR-06 (Thời gian phục hồi dịch vụ):** Thời gian khởi động lại và khôi phục ứng dụng backend khi có sự cố $\le 60\text{ giây}$.")
    lines.append("- **NFR-07 (Tuyệt đối không lộ đáp án - Anti-cheating):** Đáp án, metadata bài hát và URL gốc không bao giờ xuất hiện trong response HTTP, payload WebSocket hoặc log máy chủ trước khi vòng chơi kết thúc.")
    lines.append("- **NFR-08 (Loại trừ tranh chấp dữ liệu):** Mọi thao tác đoán bài và thay đổi trạng thái trong phòng được xử lý tuần tự qua hàng đợi đơn luồng (Actor queue), đảm bảo tính toàn vẹn 100%.")
    lines.append(r"- **NFR-09 (Chất lượng mã nguồn & Kiểm thử):** Tỷ lệ bao phủ kiểm thử (Code Coverage) tầng logic nghiệp vụ cốt lõi (Domain logic) đạt $\ge 80\%$; vượt qua kiểm thử Modulith định kỳ.")
    lines.append(r"- **NFR-10 (Tốc độ tiếp cận):** Người chơi mới vào được màn hình chơi game hoặc sảnh chờ trong vòng $\le 30\text{ giây}$ từ khi truy cập đường link.")
    lines.append(r"- **NFR-11 (Gia nhập phòng tức thì):** Tham gia phòng bằng mã hoặc link chỉ với 1 thao tác nhập biệt danh $\le 3\text{ bước}$.")
    lines.append(r"- **NFR-12 (Tối ưu tải trang ban đầu):** Chỉ số Largest Contentful Paint (LCP) trang chủ $\le 2,5\text{ giây}$ trên mạng di động 4G tiêu chuẩn.")
    lines.append("- **NFR-13 (Tiếp cận công bằng - Accessibility):** Giao diện đáp ứng tiêu chuẩn WCAG 2.1 AA, không dùng màu sắc làm phương tiện duy nhất để biểu thị kết quả.")
    lines.append("- **NFR-14 (Cấu trúc phòng linh hoạt):** Hỗ trợ tối đa 5 người chơi mỗi phòng; tự động thích ứng mượt mà khi người chơi vào/ra giữa chừng.")
    lines.append("- **NFR-15 (Khôi phục dịch vụ):** Khôi phục dịch vụ ≤ 1 giờ sau sự cố (RTO); khi cơ sở dữ liệu hỏng và phải khôi phục từ bản sao lưu tự động hằng ngày của RDS, mất tối đa 24 giờ dữ liệu (RPO).")
    lines.append("")
    lines.append("### 2.3. Quy tắc nghiệp vụ then chốt (Business Rules: BR-01 → BR-29)")
    lines.append("29 quy tắc nghiệp vụ là **Nguồn sự thật tối cao (Single Source of Truth)** điều phối toàn bộ thuật toán backend:")
    lines.append("")
    lines.append("| Mã BR | Tên quy tắc nghiệp vụ | Tóm tắt nội dung quy tắc |")
    lines.append("|---|---|---|")
    lines.append("| **BR-01** | Năm mức nghe mở dần | Mỗi vòng gồm 5 mức độ dài preview: **2 giây, 5 giây, 10 giây, 20 giây, 30 giây** (ghi nhãn ngắn: 2s, 5s, 10s, 20s, 30s). |")
    lines.append("| **BR-02** | Thang điểm cơ bản | Điểm cơ bản giảm đều 200 điểm theo mức nghe đoán đúng: **1.000 điểm (mức 2s), 800 điểm (5s), 600 điểm (10s), 400 điểm (20s), 200 điểm (30s)**. Đoán sai mức 30s được 0 điểm. |")
    lines.append("| **BR-03** | Nghe lại tự do | Người chơi được bấm nghe lại đoạn nhạc ở mức hiện tại không giới hạn số lần và không bị trừ điểm. |")
    lines.append("| **BR-04** | Cơ chế nghe thêm & Bỏ qua | Bấm 'Nghe thêm' hoặc đoán sai tại các mức 2s–20s sẽ tự động mở mức kế tiếp. Tại mức 30s, có nút 'Bỏ qua' để kết thúc lượt ngay (0 điểm). |")
    lines.append("| **BR-05** | Chuẩn hóa so khớp tên bài | Tên bài hát được chuẩn hóa bỏ dấu tiếng Việt, chuyển chữ thường Unicode NFC, xóa ký tự đặc biệt, dấu câu và nội dung phụ trong ngoặc (ví dụ: 'feat.', 'remix', 'OST'). Đoán trúng tên chuẩn hóa là đúng. |")
    lines.append("| **BR-06** | Gợi ý đúng ca sĩ | Nếu người chơi đoán sai tên bài nhưng trùng đúng tên ca sĩ chính (Primary Artist), hệ thống hiển thị thông báo gợi ý màu vàng và mở mức nghe tiếp theo. |")
    lines.append(r"| **BR-07** | Luật vòng chơi nhiều người | Mỗi thành viên tự mở mức nghe của mình; đồng hồ phòng đếm ngược 45 giây chung. Đoán đúng nhận thêm điểm thưởng tốc độ: $\lfloor 200 \times \text{ms còn lại} \div 45.000 \rfloor$. Khi có người đúng, phòng nhận thông báo nhưng **tuyệt đối ẩn tên bài**. |")
    lines.append(r"| **BR-08** | Hệ số chuỗi đúng (Combo) | Đoán đúng ở mức $\le 5\text{s}$ làm tăng chuỗi combo liên tiếp. Vòng 2 nhân hệ số $\times 1,1$; từ vòng 3 trở đi nhân hệ số $\times 1,2$ vào điểm cơ bản. Đoán ở mức $\ge 10\text{s}$ hoặc sai làm mất chuỗi. |")
    lines.append("| **BR-09** | Chọn bài ngẫu nhiên | Mỗi trận đấu chọn 10 bài hát ngẫu nhiên không trùng lặp từ bộ đề hoặc bộ lọc đã chọn. |")
    lines.append(r"| **BR-10** | Phân định hòa điểm | Hai người chơi bằng điểm sẽ xếp hạng theo: tổng thời gian đoán đúng ngắn hơn $\rightarrow$ số lần đoán đúng ở mức 2s nhiều hơn. |")
    lines.append("| **BR-11** | Cấu trúc mã phòng | Mã phòng gồm 6 ký tự chữ hoa dễ đọc, loại bỏ các ký tự dễ gây nhầm lẫn: `0, O, 1, I, L`. |")
    lines.append(r"| **BR-12** | Quyền hạn chủ phòng | Chủ phòng chọn bộ đề, số vòng (5/10/15), bấm bắt đầu khi $\ge 2$ người đều ở trạng thái sẵn sàng; có quyền mời (kick) người treo máy; kết thúc trận có quyền chọn chơi lại giữ nguyên mã phòng. |")
    lines.append("| **BR-13** | Cơ chế ghép trận nhanh | Phòng ghép trận do server tự tạo, không có chủ phòng, bắt đầu đếm ngược 30 giây khi có người thứ 2. Nếu số người tụt xuống < 2, đồng hồ dừng lại. |")
    lines.append("| **BR-14** | Xử lý rớt mạng trong phòng | Người chơi mất kết nối được giữ chỗ trong 30 giây (Grace period); trận đấu không dừng lại. Quay lại kịp chơi tiếp vòng hiện tại; các vòng bị lỡ nhận 0 điểm. Quá 30s xử lý xử thua (Forfeited). |")
    lines.append("| **BR-15** | Vòng đời phòng chơi | Phòng tự động giải tán sau 10 phút nếu không có hoạt động hoặc toàn bộ người chơi rời khỏi phòng. Người vào phòng khi trận đang diễn ra sẽ ở trạng thái chờ đến ván sau. |")
    lines.append("| **BR-16** | Chặn chat lộ đáp án | Tin nhắn trong phòng có nội dung chứa tên bài hát đáp án sau chuẩn hóa sẽ bị máy chủ chặn ngay lập tức và phát cảnh báo riêng cho người gửi. |")
    lines.append("| **BR-17** | Lọc từ ngữ thô tục | Toàn bộ tin nhắn thô tục, xúc phạm nằm trong danh mục từ cấm bị chặn hoàn toàn. |")
    lines.append("| **BR-18** | Đã bỏ (14/11/2026) | Meloo không có voice chat; giữ mã để không đánh số lại các BR sau. |")
    lines.append("| **BR-19** | Đã bỏ (14/11/2026) | Đi cùng BR-18; không còn mic nên không cần tự ngắt khi phát nhạc. |")
    lines.append("| **BR-20** | Quyền hạn khách vãng lai | Khách chơi được mọi chế độ; dữ liệu phiên khách (lịch sử, huy hiệu, thống kê) được lưu tạm trong 30 ngày. |")
    lines.append("| **BR-21** | Gộp dữ liệu khi đăng nhập | Khi khách đăng nhập Google/Facebook, toàn bộ tiến trình thi đấu, huy hiệu và lịch sử của phiên khách được tự động gộp vào tài khoản chính thức. |")
    lines.append("| **BR-22** | Tiêu chuẩn biệt danh | Biệt danh có độ dài 3–20 ký tự, không chứa ký tự đặc biệt độc hại và không trùng từ cấm. |")
    lines.append("| **BR-23** | Tính điểm xếp hạng công bằng | Chỉ các trận đấu bằng bộ đề chuẩn của hệ thống (Ghép trận nhanh hoặc Chơi đơn xếp hạng) mới được tính điểm xếp hạng mùa. Phòng riêng tự chọn bộ đề không tính điểm xếp hạng để chống cày điểm. |")
    lines.append("| **BR-24** | Bảng xếp hạng mùa giải | Xếp hạng dựa trên tổng điểm tích lũy của các trận đấu hợp lệ; làm mới theo chu kỳ mùa (Season) hoặc tuần. |")
    lines.append("| **BR-25** | Quy tắc thử thách hàng ngày | Toàn bộ cộng đồng thi đấu một bộ đề chung duy nhất mỗi ngày, làm mới lúc 00:00 giờ Việt Nam. Mỗi người có 1 lượt chơi; xếp hạng theo mức đoán đúng và tổng thời gian (trần tối đa 5 phút). |")
    lines.append("| **BR-26** | Điều kiện mở khóa thành tựu | Huy hiệu được cấp tự động khi người chơi đạt mốc (ví dụ: đoán đúng 5 lần liên tiếp ở mức 2 giây, hoàn thành 7 ngày thử thách liên tục). |")
    lines.append("| **BR-27** | Bản quyền và nguồn nhạc | Sử dụng đoạn trích âm thanh ngắn hợp lệ (30s preview) từ nhà cung cấp Deezer API; không lưu trữ file nhạc lậu trên máy chủ. |")
    lines.append("| **BR-28** | Kiểm duyệt và khóa tài khoản | Quản trị viên xử lý báo cáo vi phạm; có quyền tạm khóa hoặc cấm vĩnh viễn tài khoản / phiên khách vi phạm kèm lý do rõ ràng. |")
    lines.append("| **BR-29** | Vinh danh bản quyền sau vòng | Sau khi vòng đấu kết thúc, hệ thống công bố đầy đủ tên bài, nghệ sĩ biểu diễn, ảnh bìa album và liên kết trực tiếp để nghe bài hát trọn vẹn trên nền tảng nhạc gốc. |")
    lines.append("")
    lines.append("### 2.4. Thuật ngữ và từ điển khái niệm hệ thống")
    lines.append("- **Trận đấu (Match):** Một lượt chơi hoàn chỉnh gồm 10 vòng đấu (hoặc 5/15 vòng tùy cấu hình phòng).")
    lines.append("- **Vòng đấu (Round):** Đơn vị thi đấu đoán 1 bài hát cụ thể, trải qua 5 mức nghe từ 2 giây đến 30 giây.")
    lines.append("- **Mức nghe (Listening Level):** Phân đoạn độ dài đoạn nhạc được mở: Mức 1 (2s), Mức 2 (5s), Mức 3 (10s), Mức 4 (20s), Mức 5 (30s).")
    lines.append(r"- **Audio Token mờ:** Chuỗi mã hóa HMAC thời hạn $\le 2$ phút cấp cho client để stream đúng phân đoạn audio tại máy chủ mà không làm lộ URL gốc.")
    lines.append("- **Bộ đề (Playlist):** Danh sách tập hợp các bài hát được chọn lọc theo chủ đề (V-pop 2020s, Nhạc phim, US-UK Hits...).")
    lines.append("")
    lines.append("---")
    lines.append("")

    # PHẦN III
    lines.append("# PHẦN III: THIẾT KẾ USE CASE VÀ ĐẶC TẢ CHI TIẾT")
    lines.append("")
    lines.append("### 3.1. Tổng quan mô hình tác nhân và danh mục 34 Use Case")
    lines.append("Hệ thống định nghĩa 6 tác nhân (Actors) chính tham gia vào các luồng nghiệp vụ:")
    lines.append("1. **Khách (Guest):** Người chơi chưa đăng nhập, tham gia tức thì bằng biệt danh (Tác nhân nền tảng).")
    lines.append("2. **Người chơi đã đăng nhập (Member):** Kế thừa mọi quyền của Khách, bổ sung tính năng xếp hạng chính thức, gộp tài khoản và xóa tài khoản.")
    lines.append("3. **Chủ phòng (Host):** Người tạo phòng chơi tự chọn, sở hữu các quyền điều phối sảnh chờ và trận đấu.")
    lines.append("4. **Quản trị viên (Admin):** Thành viên đội ngũ vận hành quản lý danh mục bài hát, người dùng và báo cáo vi phạm.")
    lines.append("5. **Hệ thống API nhạc bên ngoài (Music Provider API):** Cung cấp dữ liệu catalog và luồng âm thanh preview 30s (Deezer API).")
    lines.append("6. **Nhà cung cấp OAuth bên ngoài (Identity Provider):** Dịch vụ xác thực tập trung Google OAuth 2.0 và Facebook Login.")
    lines.append("")
    lines.append("### 3.2. Sơ đồ Use Case tổng thể và các phân hệ")
    lines.append("")
    lines.append("#### Sơ đồ Use Case Tổng thể toàn hệ thống")
    lines.append("Biểu diễn toàn cảnh 6 tác nhân và 34 ca sử dụng được phân nhóm theo từng phân hệ chức năng:")
    lines.append("")
    lines.append("![Sơ đồ Use Case Tổng quát](../uml/usecase/uc-00-tong-quat.png)")
    lines.append("*Hình 3.1: Sơ đồ Use Case tổng quan toàn bộ hệ thống Meloo*")
    lines.append("")
    lines.append("#### Sơ đồ Use Case Phân hệ Tài khoản và Xác thực (UC-01 → UC-05)")
    lines.append("![Use Case Tài khoản](../uml/usecase/uc-01-tai-khoan.png)")
    lines.append("*Hình 3.2: Sơ đồ Use Case phân hệ Quản lý tài khoản và định danh*")
    lines.append("")
    lines.append("#### Sơ đồ Use Case Phân hệ Chơi đơn & Thử thách ngày (UC-06 → UC-11)")
    lines.append("![Use Case Chơi đơn](../uml/usecase/uc-02-choi-don.png)")
    lines.append("*Hình 3.3: Sơ đồ Use Case phân hệ Chơi đơn và Thử thách hàng ngày*")
    lines.append("")
    lines.append("#### Sơ đồ Use Case Phân hệ Phòng chơi nhiều người & Giao tiếp (UC-12 → UC-22)")
    lines.append("![Use Case Chơi nhiều người](../uml/usecase/uc-03-nhieu-nguoi.png)")
    lines.append("*Hình 3.4: Sơ đồ Use Case phân hệ Phòng chơi nhiều người và tương tác thời gian thực*")
    lines.append("")
    lines.append("#### Sơ đồ Use Case Phân hệ Bảng xếp hạng & Tiến trình (UC-24, UC-25)")
    lines.append("![Use Case Xếp hạng](../uml/usecase/uc-04-xep-hang.png)")
    lines.append("*Hình 3.5: Sơ đồ Use Case phân hệ Bảng xếp hạng và Thành tựu người chơi*")
    lines.append("")
    lines.append("#### Sơ đồ Use Case Phân hệ Quản trị hệ thống (UC-26 → UC-32)")
    lines.append("![Use Case Quản trị](../uml/usecase/uc-05-quan-tri.png)")
    lines.append("*Hình 3.6: Sơ đồ Use Case phân hệ Quản trị nội dung và giám sát hệ thống*")
    lines.append("")
    lines.append("### 3.3. Ma trận truy vết yêu cầu (Traceability Matrix: FR ↔ UC)")
    lines.append("Bảo đảm 100% các yêu cầu chức năng trong tài liệu SRS đều được hiện thực hóa bởi ít nhất một Use Case cụ thể:")
    lines.append("")
    lines.append("| Mã FR | Tên yêu cầu chức năng | Mã Use Case hiện thực | Ghi chú quan hệ |")
    lines.append("|---|---|---|---|")
    lines.append("| FR-01 | Vào chơi ngay bằng biệt danh | UC-01 | Luồng khách vãng lai chính |")
    lines.append("| FR-02 | Đăng nhập Google OAuth2 | UC-02, UC-03 | Bao gồm include Gộp dữ liệu khách (UC-03) |")
    lines.append("| FR-03 | Đăng nhập Facebook & Xóa tài khoản | UC-02, UC-05 | Phục vụ quyền riêng tư người dùng |")
    lines.append("| FR-04 | Hồ sơ cá nhân & Thống kê | UC-04 | Mở chế độ chỉ xem cho Khách |")
    lines.append("| FR-05 | Quản lý kho nhạc & Playlist | UC-27, UC-28 | Bao gồm extend Nhập bài từ Deezer API (UC-27) |")
    lines.append("| FR-06 | Tìm kiếm bài hát thông minh | UC-06, UC-08 | Tích hợp trực tiếp trong ô đoán bài |")
    lines.append("| FR-07 | Chế độ chơi đơn & Tính điểm | UC-07, UC-08, UC-09 | Bao gồm extend Mở mức nghe (UC-09) |")
    lines.append("| FR-08 | Thử thách đoán nhạc hàng ngày | UC-10, UC-11 | Bao gồm extend Chia sẻ kết quả (UC-11) |")
    lines.append("| FR-09 | Phòng chơi nhiều người (Tự tạo) | UC-12, UC-13, UC-16, UC-17, UC-18, UC-19, UC-20 | Bộ use case trọn vẹn cho sảnh và trận đấu |")
    lines.append("| FR-10 | Phòng công khai & Ghép trận nhanh | UC-14, UC-15, UC-20 | Matchmaking tự động do máy chủ điều phối |")
    lines.append("| FR-11 | Trò chuyện văn bản trong phòng | UC-21, UC-22 | Bao gồm extend Báo cáo tin nhắn (UC-22) |")
    lines.append("| FR-13 | Lưu trữ kết quả trận đấu | UC-07, UC-10, UC-18 | Cơ chế tự động kích hoạt bởi server |")
    lines.append("| FR-14 | Bảng xếp hạng mùa & ngày | UC-24 | Vinh danh thành tích người chơi |")
    lines.append("| FR-15 | Hệ thống huy hiệu thành tựu | UC-25 | Include trong xem hồ sơ |")
    lines.append("| FR-16 | Dashboard số liệu quản trị | UC-26 | Giám sát vận hành |")
    lines.append("| FR-17 | Kiểm duyệt báo cáo & Khóa tài khoản | UC-29, UC-30, UC-31, UC-32 | Quản lý an toàn nội dung và từ cấm |")
    lines.append("| FR-18 | Đa ngôn ngữ giao diện | UC-33 | Hỗ trợ vi/en |")
    lines.append("| FR-19 | Cài đặt ứng dụng PWA | UC-34 | Tối ưu trải nghiệm mobile |")
    lines.append("")
    lines.append("### 3.4. Đặc tả chi tiết 10 Use Case trọng tâm")
    lines.append("Dưới đây là đặc tả chi tiết của 10 Use Case cốt lõi nhất đại diện cho toàn bộ luồng nghiệp vụ của hệ thống:")
    lines.append("")
    
    usecases_detail = [
        {
            "id": "UC-01", "name": "Vào chơi bằng biệt danh", "actor": "Khách (Guest)", "trigger": "Người dùng bấm nút 'Chơi ngay' tại trang chủ hoặc truy cập link phòng.",
            "pre": "Người dùng chưa đăng nhập tài khoản hệ thống.",
            "post": "Hệ thống cấp phiên khách hợp lệ (JWT Access Token 15 phút, Refresh Cookie 30 ngày), người dùng vào thẳng màn hình chơi hoặc sảnh.",
            "main": [
                "Người dùng truy cập Meloo, nhập biệt danh mong muốn (hoặc giữ biệt danh ngẫu nhiên do hệ thống gợi ý).",
                "Người dùng nhấn 'Bắt đầu'.",
                "Frontend gửi yêu cầu `POST /api/v1/auth/guest` kèm biệt danh.",
                "Backend kiểm tra giới hạn tần suất (Rate Limiter) và xác thực tính hợp lệ của biệt danh (độ dài 3–20 ký tự, lọc từ cấm theo BR-22).",
                "Backend tạo bản ghi `app_user` loại `GUEST`, sinh Access Token và Refresh Token.",
                "Hệ thống điều hướng người chơi vào màn hình chọn chế độ hoặc sảnh chờ phòng thi đấu."
            ],
            "alt": "4a. Biệt danh chứa từ cấm hoặc không đúng định dạng: Hệ thống báo lỗi cụ thể, yêu cầu nhập lại mà không tạo phiên mới."
        },
        {
            "id": "UC-02", "name": "Đăng nhập bằng Google & Gộp tài khoản khách", "actor": "Khách / Người chơi", "trigger": "Người dùng bấm nút 'Đăng nhập với Google' trên thanh điều hướng.",
            "pre": "Thiết bị có kết nối Internet và có tài khoản Google hợp lệ.",
            "post": "Người dùng được xác thực quyền `REGISTERED`, dữ liệu phiên khách trước đó (nếu có) được gộp toàn vẹn vào tài khoản theo BR-21.",
            "main": [
                "Người dùng chọn 'Đăng nhập Google'. Trình duyệt chuyển hướng đến màn hình xác thực tài khoản của Google.",
                "Người dùng chấp thuận cấp quyền định danh cơ bản (Email, Profile).",
                "Google chuyển hướng về Meloo kèm Authorization Code.",
                "Frontend chuyển mã code lên Backend qua endpoint xác thực.",
                "Backend xác minh code với Google API, trích xuất email và Google User ID.",
                "Backend kiểm tra: nếu đã có tài khoản, thực hiện đăng nhập; nếu chưa có, tự động tạo mới tài khoản `REGISTERED`.",
                "Nếu trước đó người dùng đang có phiên khách hợp lệ, Backend tự động kích hoạt tiến trình gộp dữ liệu (UC-03): chuyển toàn bộ lịch sử đấu, tổng điểm xếp hạng, thống kê và huy hiệu sang tài khoản mới.",
                "Backend trả về JWT mới và thiết lập Refresh Cookie an toàn; giao diện cập nhật trạng thái đã đăng nhập."
            ],
            "alt": "5a. Mã xác thực Google không hợp lệ hoặc hết hạn: Backend trả lỗi 401, giao diện hiển thị thông báo đăng nhập thất bại và giữ nguyên phiên khách."
        },
        {
            "id": "UC-07", "name": "Chơi luyện tập đơn (Solo Practice)", "actor": "Khách / Người chơi", "trigger": "Người dùng chọn tab 'Chơi đơn' và chọn bộ đề/bộ lọc mong muốn.",
            "pre": "Đã có phiên làm việc hợp lệ (Khách hoặc Đã đăng nhập).",
            "post": "Trận đấu đơn hoàn thành, kết quả được lưu trữ bền vững vào cơ sở dữ liệu.",
            "main": [
                "Người dùng chọn bộ đề (ví dụ: 'V-pop 2020s') và chọn chế độ: 'Xếp hạng' (bộ chuẩn) hoặc 'Luyện tập' (tự chọn thể loại).",
                "Frontend gửi yêu cầu `POST /api/v1/solo/matches`.",
                "Backend tạo phiên đấu `SoloMatch` trong bộ nhớ, chọn ngẫu nhiên 10 bài hát không trùng lặp (BR-09), khởi tạo Vòng 1.",
                "Lặp lại 10 vòng đấu: Người chơi thực hiện nghe nhạc và đoán bài hát (UC-08).",
                "Sau khi hoàn thành vòng 10, Backend tính toán tổng điểm, chuỗi combo cao nhất, phát sự kiện `MatchFinished`.",
                "Module `progression` lưu lịch sử trận đấu, cộng điểm kinh nghiệm (EXP) và cập nhật thành tựu.",
                "Giao diện hiển thị bảng tổng kết trận đấu và danh sách bài hát đã chơi kèm liên kết nghe trên Deezer (BR-29)."
            ],
            "alt": "4a. Người dùng thoát giữa chừng: Hệ thống lưu trạng thái trận đấu trong bộ nhớ tối đa 30 phút (BR-23). Nếu quay lại trong 30 phút, tiếp tục ván đấu; nếu quá 30 phút, ván đấu tự động hủy."
        },
        {
            "id": "UC-08", "name": "Đoán bài hát trong một vòng đấu", "actor": "Khách / Người chơi", "trigger": "Vòng chơi bắt đầu hoặc người dùng bấm 'Nghe thêm' / nhập câu trả lời.",
            "pre": "Vòng đấu đang ở trạng thái `IN_PROGRESS`.",
            "post": "Lượt đoán được chấm điểm chính xác, vòng đấu chuyển trạng thái phù hợp.",
            "main": [
                "Client nhận thông tin vòng chơi từ Backend (gồm số thứ tự vòng, mức nghe hiện tại và chuỗi Audio Token mờ).",
                "Frontend gọi streaming proxy audio với token để phát đoạn âm thanh tương ứng mức nghe (mặc định bắt đầu từ Mức 1: 2 giây).",
                "Người chơi nhập tên bài hát vào ô tìm kiếm (gõ không dấu, UC-06 gợi ý danh sách bài tương ứng).",
                "Người chơi nhấn Enter hoặc chọn bài hát: Frontend gửi `POST /api/v1/solo/matches/{matchId}/rounds/current/guesses`.",
                "Backend chuẩn hóa tên bài hát đoán và so khớp với đáp án theo chuẩn BR-05 (TitleNormalizer):",
                "   - Nếu ĐÚNG: Tính điểm cơ bản theo mức nghe (BR-02), nhân hệ số combo (BR-08), đánh dấu vòng `CORRECT`, công bố đáp án và dừng vòng.",
                r"   - Nếu SAI nhưng ĐÚNG CA SĨ: Hiển thị gợi ý ca sĩ (BR-06), tự động mở mức nghe tiếp theo (2s $\rightarrow$ 5s $\rightarrow$ 10s $\rightarrow$ 20s $\rightarrow$ 30s).",
                "   - Nếu SAI HOÀN TOÀN: Đánh dấu đoán sai, tự động mở mức nghe tiếp theo.",
                "Nếu đã ở mức 30s mà đoán sai hoặc bấm 'Bỏ qua', vòng kết thúc với 0 điểm (`WRONG_OUT` / `GAVE_UP`).",
                "Giao diện hiển thị kết quả vòng, phát hiệu ứng âm thanh và chuyển sang vòng tiếp theo."
            ],
            "alt": "2a. Lỗi mạng khi phát audio: Người chơi bấm nút 'Nghe lại' (BR-03) để tải lại đoạn preview mà không bị phạt điểm."
        },
        {
            "id": "UC-10", "name": "Tham gia Thử thách hàng ngày (Daily Challenge)", "actor": "Khách / Người chơi", "trigger": "Người dùng chọn mục 'Thử thách ngày' tại trang chủ.",
            "pre": "Hôm nay người dùng chưa hoàn thành lượt thử thách ngày (mỗi người 1 lượt/ngày theo BR-25).",
            "post": "Kết quả được ghi nhận vào bảng vinh danh ngày, chuỗi ngày liên tiếp (Streak) được cập nhật.",
            "main": [
                "Người dùng truy cập màn hình Thử thách ngày.",
                "Frontend gửi yêu cầu `POST /api/v1/daily/attempt`.",
                "Backend kiểm tra: nếu người dùng đã chơi hôm nay, từ chối tạo lượt mới và trả về kết quả cũ; nếu chưa chơi, cấp bài hát của ngày hôm nay (cố định toàn server theo ngày giờ Việt Nam).",
                "Kích hoạt đồng hồ đếm giờ (giới hạn trần tối đa 5 phút theo quy định BR-25).",
                r"Người chơi tiến hành nghe các mức 2s $\rightarrow$ 30s và đưa ra dự đoán duy nhất cho bài hát.",
                "Khi đoán đúng hoặc hết 5 phút / bỏ cuộc, hệ thống ghi nhận kết quả `DailyAttempt`.",
                "Giao diện hiển thị bảng kết quả ngày, chuỗi ngày tham gia liên tục và nút 'Chia sẻ thành tích' (UC-11)."
            ],
            "alt": "3a. Người dùng đã hoàn thành thử thách trước đó: Hiển thị màn hình xem lại kết quả đã chơi và đếm ngược thời gian đến 00:00 VN để mở lượt mới."
        },
        {
            "id": "UC-12", "name": "Tạo phòng chơi nhiều người (Create Room)", "actor": "Chủ phòng (Host)", "trigger": "Người dùng chọn 'Tạo phòng' tại sảnh nhiều người chơi.",
            "pre": "Người dùng có phiên làm việc hợp lệ.",
            "post": "Phòng chơi mới được khởi tạo trên server, người dùng trở thành Chủ phòng trong sảnh chờ.",
            "main": [
                "Người dùng bấm 'Tạo phòng'.",
                "Frontend gửi yêu cầu `POST /api/v1/rooms` kèm cấu hình ban đầu (tên phòng, số vòng đấu, chế độ công khai/riêng tư).",
                "Backend sinh ngẫu nhiên mã phòng 6 ký tự dễ đọc (loại bỏ `0, O, 1, I, L` theo BR-11).",
                "Backend khởi tạo một thực thể `RoomActor` độc lập trong bộ nhớ, gán người tạo làm Chủ phòng.",
                "Frontend nhận mã phòng, tự động thiết lập kết nối WebSocket STOMP tới `/ws` và subscribe vào các topic `/topic/rooms/{roomId}/*`.",
                "Giao diện chuyển sang màn hình Sảnh chờ (Lobby), hiển thị mã phòng và nút sao chép liên kết mời bạn bè."
            ],
            "alt": "3a. Trùng mã phòng đang hoạt động: Bộ sinh mã tự động thử lại mã ngẫu nhiên khác cho đến khi duy nhất."
        },
        {
            "id": "UC-13", "name": "Tham gia phòng chơi bằng mã hoặc liên kết", "actor": "Khách / Người chơi", "trigger": "Người dùng nhập mã phòng 6 ký tự hoặc mở đường link chia sẻ.",
            "pre": "Phòng chơi tồn tại và chưa vượt quá sĩ số tối đa (tối đa 5 người theo BR-11).",
            "post": "Người chơi kết nối thành công vào sảnh chờ phòng thi đấu.",
            "main": [
                "Người dùng mở liên kết phòng hoặc nhập mã phòng tại ô 'Vào phòng'.",
                "Frontend gửi yêu cầu `POST /api/v1/rooms/join` kèm mã phòng và thông tin người chơi.",
                "Backend kiểm tra trạng thái phòng qua `RoomActor`:",
                "   - Nếu phòng đã đủ 5 người: từ chối, báo lỗi phòng đầy.",
                "   - Nếu người chơi từng bị chủ phòng đuổi (Kicked): từ chối theo BR-12.",
                "   - Nếu hợp lệ: thêm người chơi vào danh sách thành viên phòng.",
                "Client kết nối WebSocket STOMP và subscribe topic phòng.",
                "Backend phát sự kiện `PlayerJoinedEvent` tới toàn bộ người trong phòng.",
                "Giao diện sảnh chờ của mọi thành viên tự động cập nhật danh sách người chơi tức thời."
            ],
            "alt": "3a. Trận đấu trong phòng đang diễn ra: Người chơi được đưa vào trạng thái 'Khán giả chờ' (Waiting lobby), tự động tham gia ở ván đấu tiếp theo (BR-15)."
        },
        {
            "id": "UC-15", "name": "Ghép trận tự động nhanh (Quick Matchmaking)", "actor": "Khách / Người chơi", "trigger": "Người dùng nhấn nút 'Ghép trận nhanh'.",
            "pre": "Có phiên đăng nhập hợp lệ.",
            "post": "Người chơi được xếp vào phòng ghép trận chuẩn và tự động bắt đầu thi đấu khi đủ điều kiện.",
            "main": [
                "Người chơi nhấn 'Ghép trận nhanh'.",
                "Frontend gửi yêu cầu tìm phòng ghép tới hệ thống.",
                "Hệ thống Matchmaker tìm kiếm phòng ghép trận đang ở trạng thái chờ có sẵn vị trí trống:",
                "   - Nếu tìm thấy: gán người chơi vào phòng đó.",
                "   - Nếu không có: tự động tạo một phòng ghép trận mới không có chủ phòng (BR-13).",
                "Khi phòng có từ 2 người chơi trở lên: Server kích hoạt đồng hồ đếm ngược 30 giây.",
                "Hết 30 giây (hoặc khi đạt đủ 5 người): Trận đấu tự động kích hoạt bắt đầu mà không cần ai bấm sẵn sàng.",
                "Toàn bộ thành viên tự động chuyển vào màn hình thi đấu Vòng 1."
            ],
            "alt": "4a. Người chơi thứ 2 rời phòng khi đang đếm ngược: Đồng hồ đếm ngược tự động dừng lại; khi có người chơi mới vào, bộ đếm kích hoạt lại từ đầu đủ 30 giây (BR-13)."
        },
        {
            "id": "UC-18", "name": "Thi đấu vòng chơi nhiều người thời gian thực", "actor": "Khách / Người chơi", "trigger": "Trận đấu nhiều người chuyển sang vòng tiếp theo.",
            "pre": "Phòng chơi đang ở trạng thái `IN_MATCH`.",
            "post": "Vòng chơi hoàn tất, điểm số và bảng xếp hạng trực tiếp trong phòng được cập nhật đồng bộ.",
            "main": [
                "`RoomActor` chọn bài hát ngẫu nhiên cho vòng từ kho nhạc (BR-09).",
                "Pha chuẩn bị (Prepare Phase, 3 giây): Server cấp token audio Mức 1 (2s) qua hàng đợi riêng `/user/queue/private` cho từng người chơi để client nạp đệm trước (Audio Preload, NFR-04).",
                "Pha chơi chính thức (Playing Phase, 45 giây): Server phát `RoundStartEvent` ra topic chung `/topic/rooms/{roomId}/round`, đồng hồ 45s server bắt đầu đếm ngược.",
                "Mỗi người chơi tự điều khiển mức nghe của mình (BR-07); phát âm thanh cục bộ.",
                "Người chơi nhập câu trả lời: Lệnh gửi qua kênh WebSocket `/app/rooms/{roomId}/guess`.",
                "Hàng đợi `RoomActor` xử lý tuần tự từng lệnh đoán:",
                "   - Kiểm tra thời gian: nếu nhận trước mốc 45.000 ms, tiến hành so khớp chuẩn hóa (BR-05).",
                r"   - Nếu ĐÚNG: Tính điểm cơ bản + thưởng combo (BR-08) + thưởng tốc độ $\lfloor 200 \times \text{ms còn lại} \div 45.000 \rfloor$. Phát thông báo có người đúng nhưng **ẩn tên bài** (BR-07).",
                "   - Nếu SAI: Phát kết quả sai riêng cho người đó, tự động cấp token mức nghe tiếp theo.",
                "Vòng kết thúc khi: Hết 45 giây HOẶC toàn bộ người chơi trong phòng đều đã hoàn thành lượt (đoán đúng, hết lượt hoặc bỏ cuộc).",
                "Server phát `RoundEndEvent` công bố đáp án chính thức, link nghe Deezer (BR-29) và phát `ScoreboardUpdateEvent` cập nhật điểm số toàn phòng."
            ],
            "alt": "6a. Tin nhắn chat của người chơi cố tình gửi tên bài hát đáp án: Bộ lọc tại `RoomActor` nhận diện chuỗi trùng khớp với đáp án vòng hiện tại, tự động chặn tin nhắn và phát cảnh báo vi phạm riêng cho người gửi theo BR-16."
        },
        {
            "id": "UC-21", "name": "Trò chuyện văn bản trong phòng (Room Chat)", "actor": "Khách / Người chơi", "trigger": "Người chơi nhập tin nhắn trong hộp thoại chat của phòng.",
            "pre": "Người chơi đang có mặt trong phòng thi đấu.",
            "post": "Tin nhắn hợp lệ được phát tới tất cả các thành viên trong phòng.",
            "main": [
                "Người chơi nhập nội dung tin nhắn và nhấn gửi.",
                "Frontend gửi thông điệp STOMP tới `/app/rooms/{roomId}/chat`.",
                "Backend (`RoomActor`) tiếp nhận và thực hiện kiểm duyệt an toàn nội dung:",
                "   - Kiểm tra rò rỉ đáp án (BR-16): Chuẩn hóa nội dung tin nhắn, so khớp với tên bài hát đáp án của vòng đang thi đấu. Nếu trùng, hủy tin nhắn và gửi cảnh báo về người gửi.",
                "   - Kiểm tra từ ngữ thô tục (BR-17): So khớp với bảng danh mục từ cấm `banned_word`. Nếu vi phạm, chặn tin nhắn.",
                "Nếu tin nhắn hợp lệ, server chuyển tiếp tin nhắn ra topic `/topic/rooms/{roomId}/chat`.",
                "Toàn bộ người chơi trong phòng nhận và hiển thị tin nhắn trên khung chat giao diện."
            ],
            "alt": "3a. Tin nhắn bị chặn do vi phạm: Server gửi thông điệp lỗi riêng vào `/user/queue/errors`, khung chat hiển thị cảnh báo đỏ 'Tin nhắn bị chặn do vi phạm tiêu chuẩn cộng đồng hoặc lộ đáp án'."
        }
    ]

    for uc in usecases_detail:
        lines.append(f"#### Đặc tả chi tiết {uc['id']} · {uc['name']}")
        lines.append(f"- **Tác nhân chính:** {uc['actor']}")
        lines.append(f"- **Sự kiện kích hoạt:** {uc['trigger']}")
        lines.append(f"- **Tiền điều kiện:** {uc['pre']}")
        lines.append(f"- **Hậu điều kiện:** {uc['post']}")
        lines.append("- **Luồng sự kiện chính (Main Success Scenario):**")
        for step in uc['main']:
            lines.append(f"  {step if step.startswith('   -') else '1. ' + step}")
        lines.append(f"- **Luồng ngoại lệ / Rẽ nhánh:** {uc['alt']}")
        lines.append("")

    lines.append("---")
    lines.append("")

    # PHẦN IV
    lines.append("# PHẦN IV: THIẾT KẾ KIẾN TRÚC HỆ THỐNG C4 MODEL VÀ TRIỂN KHAI")
    lines.append("")
    lines.append("### 4.1. Triết lý thiết kế và ràng buộc hệ thống")
    lines.append("Kiến trúc Meloo được định hình dựa trên 4 trụ cột triết lý kỹ thuật:")
    lines.append("1. **Máy chủ là trọng tài tối cao (Server-as-Referee):** Đảm bảo tính công bằng tuyệt đối. Mọi phép toán chấm điểm, xử lý đồng hồ đếm ngược, phân định thắng thua và xác thực đáp án đều diễn ra độc quyền tại backend. Trình duyệt client chỉ đóng vai trò hiển thị và gửi tương tác (NFR-07).")
    lines.append("2. **Kiến trúc Modular Monolith (Spring Modulith):** Toàn bộ nghiệp vụ được gói gọn trong một đơn vị triển khai duy nhất nhưng phân tách ranh giới module vật lý nghiêm ngặt (ADR-001). Cấu trúc này giúp tối ưu hóa chi phí phần cứng (chạy trọn vẹn trên 1 GB RAM của AWS Free Tier) đồng thời ngăn chặn xung đột mã nguồn khi nhiều AI agent cùng lập trình.")
    lines.append("3. **Hợp đồng là trên hết (Contract-First / API-First):** OpenAPI 3.1 quy chuẩn hóa toàn bộ REST API và AsyncAPI 3 quy chuẩn hóa toàn bộ kênh WebSocket STOMP (ADR-002). Code DTO và client hai đầu được sinh tự động, đảm bảo khớp nối 100%.")
    lines.append(r"4. **Bảo mật luồng âm thanh qua Proxy Streaming:** Phân phối preview nhạc bằng cơ chế proxy cắt byte CBR tại máy chủ với token HMAC ngắn hạn ($\le 2$ phút), triệt tiêu nguy cơ tải trước hoặc dịch ngược đáp án (ADR-008).")
    lines.append("")
    lines.append("### 4.2. C4 Level 1: Sơ đồ bối cảnh hệ thống (Context Diagram)")
    lines.append("Sơ đồ thể hiện phạm vi hệ thống Meloo trong tương tác với các nhóm người dùng và các hệ thống dịch vụ bên thứ ba:")
    lines.append("")
    lines.append("![C4 Level 1 Context](../uml/c4/c4-context.png)")
    lines.append("*Hình 4.1: Sơ đồ bối cảnh hệ thống (C4 Level 1 - System Context Diagram)*")
    lines.append("")
    lines.append("**Các tương tác ngoại vi then chốt:**")
    lines.append("- **Khách & Thành viên:** Tương tác qua trình duyệt web trên máy tính hoặc điện thoại thông minh.")
    lines.append("- **Google OAuth 2.0 / Facebook Login:** Cung cấp dịch vụ định danh tập trung an toàn.")
    lines.append("- **Deezer Music API & CDN:** Cung cấp danh mục bài hát phong phú và luồng audio MP3 128 kbps hợp pháp.")
    lines.append("")
    lines.append("### 4.3. C4 Level 2: Sơ đồ khối thùng chứa (Container Diagram)")
    lines.append("Sơ đồ phân rã các khối phần mềm độc lập cấu thành giải pháp Meloo và công nghệ tương ứng:")
    lines.append("")
    lines.append("![C4 Level 2 Container](../uml/c4/c4-container.png)")
    lines.append("*Hình 4.2: Sơ đồ khối thùng chứa phần mềm (C4 Level 2 - Container Diagram)*")
    lines.append("")
    lines.append("**Chi tiết các Container:**")
    lines.append("1. **Frontend Single-Page Application (SPA):** Công nghệ React 19, TypeScript strict, Vite, Tailwind CSS, shadcn/ui, Web Audio API và STOMP client. Lưu trữ và phân phối biên qua mạng toàn cầu Vercel Edge Network.")
    lines.append("2. **Reverse Proxy & TLS Gateway (Caddy):** Máy chủ Caddy 2 chạy trong Docker trên EC2, tự động quản lý chứng chỉ SSL/TLS Let's Encrypt, định tuyến REST API `/api/**` và tự động nâng cấp WebSocket `/ws`.")
    lines.append("3. **Backend Application (Spring Boot Modular Monolith):** Đóng gói Java 21, Spring Boot 4.x, Spring Modulith, Spring Security 7. Vận hành toàn bộ máy trạng thái phòng chơi và luật tính điểm.")
    lines.append("4. **Hệ quản trị CSDL Quan hệ (PostgreSQL 17):** Dịch vụ CSDL được quản lý AWS RDS PostgreSQL 17, lưu trữ dữ liệu người dùng, bài hát và kết quả thi đấu.")
    lines.append("")
    lines.append("### 4.4. C4 Level 3: Sơ đồ thành phần Backend Modular Monolith (Component Diagram)")
    lines.append("Sơ đồ thể hiện cấu trúc phân rã các module nghiệp vụ nội bộ bên trong tiến trình Spring Boot Backend:")
    lines.append("")
    lines.append("![C4 Level 3 Component Backend](../uml/c4/c4-component-backend.png)")
    lines.append("*Hình 4.3: Sơ đồ thành phần các module Backend (C4 Level 3 - Component Diagram)*")
    lines.append("")
    lines.append("**Ranh giới và chức năng của các module Spring Modulith:**")
    lines.append("- **`identity`:** Quản lý phiên khách vãng lai, xác thực OAuth2, cấp phát và xoay vòng JWT/Cookie.")
    lines.append("- **`catalog`:** Quản lý kho dữ liệu bài hát, thể loại, playlist; kết nối Deezer API client.")
    lines.append("- **`audio`:** Dịch vụ proxy luồng âm thanh trực tiếp từ CDN, khống chế độ dài byte theo mức nghe qua token HMAC.")
    lines.append("- **`game`:** Lõi luật chơi, điều phối máy trạng thái vòng đấu đơn, bộ so khớp tên bài `TitleNormalizer` và bộ tính điểm `ScoreCalculator`.")
    lines.append("- **`room`:** Điều phối phòng thi đấu nhiều người bằng mô hình hàng đợi đơn luồng `RoomActor`, xử lý tin nhắn STOMP.")
    lines.append("- **`chat`:** Kênh trao đổi tin nhắn văn bản, tích hợp bộ kiểm duyệt từ cấm và chống lộ đáp án.")
    lines.append("- **`progression`:** Xử lý sự kiện kết thúc trận `MatchFinished` để ghi nhận kết quả bền, tính điểm xếp hạng mùa và thăng cấp.")
    lines.append("- **`daily`:** Quản lý chu kỳ thử thách đoán nhạc hàng ngày, đồng bộ làm mới lúc 00:00 VN.")
    lines.append("- **`admin`:** Điểm cuối REST API dành riêng cho quản trị viên vận hành hệ thống.")
    lines.append("- **`shared`:** Thư viện chung toàn cục chứa chuẩn hóa lỗi ProblemDetail RFC 7807 và tiện ích bảo mật.")
    lines.append("")
    lines.append("### 4.5. Sơ đồ triển khai hạ tầng đám mây AWS (Deployment Diagram)")
    lines.append("Sơ đồ mô tả hiện thực hóa kiến trúc vật lý trên hạ tầng đám mây công cộng (AWS Region `ap-southeast-1` Singapore kết hợp Vercel và Cloudflare):")
    lines.append("")
    lines.append("![Sơ đồ Triển khai](../uml/deployment/deployment.png)")
    lines.append("*Hình 4.4: Sơ đồ triển khai hạ tầng vật lý (Physical Deployment Architecture Diagram)*")
    lines.append("")
    lines.append("**Cấu hình hạ tầng chi tiết:**")
    lines.append("- **Phân tầng Edge & DNS:** Cloudflare DNS quản lý bản ghi tên miền và chống DDoS; Vercel CDN lưu trữ Frontend tĩnh.")
    lines.append("- **Phân tầng Máy chủ ứng dụng (AWS EC2 `t3.micro`):** Cấu hình 1 vCPU, 1 GB RAM vật lý, bổ sung 2 GB swapfile làm vùng đệm bộ nhớ an toàn. Chạy Docker Compose cô lập Caddy và Spring Boot Backend.")
    lines.append("- **Phân tầng Cơ sở dữ liệu (AWS RDS PostgreSQL 17):** Cấu hình `Publicly Accessible = No`, đặt trong subnet an toàn, chỉ cho phép kết nối TCP cổng 5432 từ duy nhất Security Group của EC2.")
    lines.append("")
    lines.append("### 4.6. Tổng hợp các quyết định kiến trúc then chốt (ADR-001 → ADR-008)")
    lines.append("Toàn bộ các quyết định kỹ thuật quan trọng của dự án được ghi nhận chính thức qua các bản Architecture Decision Record:")
    lines.append("")
    lines.append("| Mã ADR | Quyết định công nghệ | Phương án thay thế | Lý do cốt lõi & Đánh đổi |")
    lines.append("|---|---|---|---|")
    lines.append("| **ADR-001** | **Modular Monolith với Spring Modulith** | Microservices, Monolith truyền thống | Tối ưu hóa tuyệt đối cho giới hạn 1 GB RAM máy chủ EC2 Free Tier; Spring Modulith kiểm chứng ranh giới module tự động trong CI bằng `verify()`, loại bỏ chi phí vận hành mạng phức tạp. |")
    lines.append("| **ADR-002** | **Hợp đồng API-First (OpenAPI 3.1 & AsyncAPI 3)** | Code-First | Đồng bộ hóa hai đầu Backend-Frontend, hỗ trợ kiểm thử tự động và cho phép phát triển song song độc lập. |")
    lines.append("| **ADR-003** | **Nguồn nhạc Deezer API làm chủ đạo** | iTunes Search, Spotify API | Deezer cung cấp định dạng MP3 128 kbps (tiết kiệm 50% băng thông so với iTunes AAC 256 kbps), có API danh mục phong phú. PO chấp nhận ngoại lệ bản quyền cho giai đoạn beta học thuật. |")
    lines.append("| **ADR-004** | **Giao tiếp Realtime qua STOMP trên WebSocket** | Polling REST, SSE, Raw WebSocket | STOMP hỗ trợ sẵn mô hình Publish/Subscribe (Topic phòng, Queue riêng), dễ tích hợp xác thực tại frame CONNECT của Spring Security. |")
    lines.append("| **ADR-005** | **Xác thực JWT in-memory + Cookie Refresh HttpOnly** | LocalStorage token, Stateful Session | JWT Access Token lưu trong RAM chống rò rỉ XSS; Cookie Refresh Token mang cờ `HttpOnly`, `SameSite=Lax` chống CSRF. Lưu hash refresh token trong DB, hỗ trợ xoay vòng theo family. |")
    lines.append("| **ADR-006** | **Hạ tầng Vercel (FE) + AWS EC2/RDS (BE) + Cloudflare** | VPS đơn lẻ, PaaS all-in-one | Tận dụng trọn vẹn gói dịch vụ miễn phí (Free Tier), Vercel cung cấp CDN toàn cầu và preview tự động cho mỗi PR; EC2 duy trì kết nối WebSocket bền bỉ. |")
    lines.append("| **ADR-007** | **Trạng thái phòng trong bộ nhớ + Hàng đợi tuần tự** | DB pessimistic lock, Redis distributed | Loại bỏ hoàn toàn I/O đĩa trong lúc thi đấu; mỗi phòng vận hành một hàng đợi `RoomActor` xử lý tuần tự, triệt tiêu 100% race condition khi nhiều người cùng đoán bài. |")
    lines.append(r"| **ADR-008** | **Chống gian lận: Streaming Proxy + HMAC Token** | Trả trực tiếp URL nhạc của CDN | Bảo mật tuyệt đối đáp án: URL không chứa tên bài hay trackId; token HMAC hết hạn sau $\le 2$ phút; backend cắt byte CBR tương ứng mức nghe, ngăn chặn nghe trước. |")
    lines.append("")
    lines.append("### 4.7. Chiến thuật kiến trúc đáp ứng yêu cầu phi chức năng (NFR Tactics)")
    lines.append(r"1. **Chiến thuật Bảo mật (NFR-05, NFR-07):** Giấu kín hoàn toàn thông tin bài hát trong đối tượng `Round`; endpoint audio `/api/v1/audio/{token}` sử dụng token băm HMAC-SHA256, kiểm tra chữ ký bằng `MessageDigest.isEqual` chống tấn công định thời (Timing Attack); định dạng Access Log Tomcat loại bỏ vết token (`%m` thay vì `%r`).")
    lines.append(r"2. **Chiến thuật Hiệu năng (NFR-01, NFR-02, NFR-04):** Chuyển tiếp luồng byte âm thanh trực tiếp (Zero-Copy Streaming) từ Deezer CDN sang client mà không ghi đĩa và không giữ trong Heap RAM; duy trì trạng thái thi đấu hoàn toàn trong RAM (Zero-Disk I/O In-Game) giúp độ trễ STOMP đạt $< 200\text{ ms}$; nạp đệm audio trước 3s trong pha `ROUND_PREPARE` giúp âm thanh phát $< 1,0\text{ s}$.")
    lines.append(r"3. **Chiến thuật Khả năng Chịu tải (NFR-03):** Cấu hình JVM tối ưu `-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError` ngăn chặn OOM Killer; phục vụ ổn định 20 phòng $\times$ 5 người (100 CCU) trên máy chủ 1 GB RAM.")
    lines.append("")
    lines.append("---")
    lines.append("")

    # PHẦN V
    lines.append("# PHẦN V: THIẾT KẾ CHI TIẾT PHẦN MỀM (UML CHI TIẾT)")
    lines.append("")
    lines.append("### 5.1. Sơ đồ lớp miền tổng quát (Domain Model Class Diagram)")
    lines.append("Mô hình thực thể miền biểu diễn các khái niệm nghiệp vụ cốt lõi của hệ thống:")
    lines.append("")
    lines.append("![Sơ đồ Lớp Miền](../uml/class/domain.png)")
    lines.append("*Hình 5.1: Sơ đồ lớp miền tổng quát toàn hệ thống Meloo*")
    lines.append("")
    lines.append("### 5.2. Thiết kế chi tiết sơ đồ lớp các module nghiệp vụ cốt lõi")
    lines.append("")
    lines.append("#### Module Quản lý Danh tính & Xác thực (`identity`)")
    lines.append("Bao gồm các thành phần tiếp nhận yêu cầu REST, dịch vụ tạo phiên khách kèm bộ kiểm tra biệt danh `NicknameValidator`, dịch vụ cấp phát JWT và quản lý vòng đời xoay vòng Refresh Token:")
    lines.append("")
    lines.append("![Sơ đồ Lớp Identity](../uml/class/identity.png)")
    lines.append("*Hình 5.2: Thiết kế sơ đồ lớp chi tiết module Identity*")
    lines.append("")
    lines.append("#### Module Kho nhạc & Tìm kiếm gợi ý (`catalog`)")
    lines.append("Bao gồm thực thể bài hát, nghệ sĩ, playlist, dịch vụ tìm kiếm bài hát sau chuẩn hóa không dấu `TrackSearchService`, và mẫu thiết kế Adapter trừu tượng hóa nhà cung cấp nhạc `MusicProvider`:")
    lines.append("")
    lines.append("![Sơ đồ Lớp Catalog](../uml/class/catalog.png)")
    lines.append("*Hình 5.3: Thiết kế sơ đồ lớp chi tiết module Catalog*")
    lines.append("")
    lines.append("#### Module Luật chơi, Tính điểm & Chơi đơn (`game`)")
    lines.append("Đóng gói thực thể aggregate `Round` độc lập hoàn toàn với framework, bộ tính điểm `ScoreCalculator`, bộ theo dõi chuỗi combo `ComboTracker` và quản lý phiên đấu đơn `SoloMatchManager`:")
    lines.append("")
    lines.append("![Sơ đồ Lớp Game](../uml/class/game.png)")
    lines.append("*Hình 5.4: Thiết kế sơ đồ lớp chi tiết module Game*")
    lines.append("")
    lines.append("#### Module Phòng chơi & Realtime STOMP (`room`)")
    lines.append("Trung tâm điều phối nhiều người chơi áp dụng mẫu hình Actor Model với `RoomActor` vận hành trên luồng ảo (Virtual Thread), quản lý hàng đợi lệnh tuần tự và phát sự kiện STOMP:")
    lines.append("")
    lines.append("![Sơ đồ Lớp Room](../uml/class/room.png)")
    lines.append("*Hình 5.5: Thiết kế sơ đồ lớp chi tiết module Room*")
    lines.append("")
    lines.append("### 5.3. Thiết kế sơ đồ tuần tự (Sequence Diagrams) cho các kịch bản tương tác chính")
    lines.append("Dưới đây là 5 kịch bản tương tác trọng tâm thể hiện sự phối hợp nhịp nhàng giữa Frontend, Caddy Gateway, các Controller, Service và Message Broker:")
    lines.append("")
    lines.append("#### Kịch bản 1: Đăng nhập khách bằng biệt danh (UC-01, BR-20, BR-22)")
    lines.append("![Sơ đồ Tuần tự Đăng nhập Khách](../uml/sequence/seq-dang-nhap-khach.png)")
    lines.append("*Hình 5.6: Sơ đồ tuần tự quy trình tạo phiên khách vãng lai bằng biệt danh*")
    lines.append("")
    lines.append("#### Kịch bản 2: Đăng nhập Google & Gộp tài khoản khách (UC-02, BR-21)")
    lines.append("![Sơ đồ Tuần tự Đăng nhập Google](../uml/sequence/seq-dang-nhap-google.png)")
    lines.append("*Hình 5.7: Sơ đồ tuần tự xác thực Google OAuth2 và tự động gộp dữ liệu khách*")
    lines.append("")
    lines.append("#### Kịch bản 3: Một vòng chơi đơn và cấp phát Audio Token mờ (UC-07, UC-08, ADR-008)")
    lines.append("![Sơ đồ Tuần tự Chơi đơn](../uml/sequence/seq-vong-choi-don.png)")
    lines.append("*Hình 5.8: Sơ đồ tuần tự một vòng chơi đơn và quy trình phát audio bảo mật*")
    lines.append("")
    lines.append("#### Kịch bản 4: Tạo phòng và vào phòng chơi nhiều người (UC-12, UC-13, BR-11..15)")
    lines.append("![Sơ đồ Tuần tự Tạo và Vào phòng](../uml/sequence/seq-tao-va-vao-phong.png)")
    lines.append("*Hình 5.9: Sơ đồ tuần tự quy trình tạo phòng, kết nối STOMP và vào sảnh chờ*")
    lines.append("")
    lines.append("#### Kịch bản 5: Một vòng thi đấu nhiều người thời gian thực qua STOMP (UC-18, BR-07..10)")
    lines.append("![Sơ đồ Tuần tự Vòng nhiều người](../uml/sequence/seq-vong-nhieu-nguoi.png)")
    lines.append("*Hình 5.10: Sơ đồ tuần tự thi đấu vòng nhiều người realtime, đồng bộ điểm số và ẩn đáp án*")
    lines.append("")
    lines.append("### 5.4. Thiết kế sơ đồ máy trạng thái (State Machine Diagrams)")
    lines.append("")
    lines.append("#### Vòng đời Phòng chơi (Room State Machine - BR-11..15)")
    lines.append(r"Biểu diễn các trạng thái `WAITING_LOBBY` $\rightarrow$ `COUNTDOWN` $\rightarrow$ `IN_MATCH` $\rightarrow$ `MATCH_FINISHED` $\rightarrow$ `CLOSED`:")
    lines.append("")
    lines.append("![Sơ đồ Trạng thái Phòng](../uml/state/state-phong.png)")
    lines.append("*Hình 5.11: Sơ đồ máy trạng thái vòng đời phòng chơi*")
    lines.append("")
    lines.append("#### Vòng đời Vòng đấu (Round State Machine - BR-01..07)")
    lines.append(r"Biểu diễn các trạng thái `WAITING` $\rightarrow$ `IN_PROGRESS` (5 mức 2s..30s) $\rightarrow$ `CORRECT` / `WRONG_OUT` / `GAVE_UP` / `TIMEOUT` $\rightarrow$ `ENDED`:")
    lines.append("")
    lines.append("![Sơ đồ Trạng thái Vòng](../uml/state/state-vong.png)")
    lines.append("*Hình 5.12: Sơ đồ máy trạng thái vòng đấu*")
    lines.append("")
    lines.append("#### Vòng đời Người chơi trong phòng & Xử lý rớt mạng (BR-12, BR-14, BR-15)")
    lines.append(r"Biểu diễn chuyển dịch `NOT_READY` $\leftrightarrow$ `READY`, trạng thái trong trận và vùng đệm 30 giây rớt mạng (`DISCONNECTED_GRACE`):")
    lines.append("")
    lines.append("![Sơ đồ Trạng thái Người chơi](../uml/state/state-nguoi-choi-trong-phong.png)")
    lines.append("*Hình 5.13: Sơ đồ máy trạng thái người chơi và cơ chế phục hồi rớt mạng 30 giây*")
    lines.append("")
    lines.append("### 5.5. Sơ đồ hoạt động luồng một vòng chơi (Activity Diagram)")
    lines.append("Biểu diễn toàn cảnh luồng hoạt động rẽ nhánh từ lúc mở mức nghe, xử lý nghe lại, nhập đáp án, so khớp chuỗi cho đến kết thúc vòng:")
    lines.append("")
    lines.append("![Sơ đồ Hoạt động Vòng chơi](../uml/activity/act-luong-mot-vong-choi.png)")
    lines.append("*Hình 5.14: Sơ đồ hoạt động chi tiết luồng xử lý một vòng chơi*")
    lines.append("")
    lines.append("---")
    lines.append("")

    # PHẦN VI
    lines.append("# PHẦN VI: THIẾT KẾ CƠ SỞ DỮ LIỆU VÀ TỔ CHỨC LƯU TRỮ")
    lines.append("")
    lines.append("### 6.1. Nguyên tắc thiết kế CSDL và phân quyền module Spring Modulith")
    lines.append("1. **Server là trọng tài:** Mọi điểm số lưu trong CSDL đều do server tính toán và thẩm định; không có bất kỳ bảng nào nhận dữ liệu điểm do client tự gửi lên.")
    lines.append("2. **Quyền sở hữu độc quyền:** Mỗi bảng trong cơ sở dữ liệu thuộc quyền sở hữu của đúng một module duy nhất. Các module khác không bao giờ được phép `JOIN` hoặc truy vấn trực tiếp bảng của nhau mà phải giao tiếp qua interface Java hoặc sự kiện miền (Spring Modulith).")
    lines.append("3. **Tách biệt trạng thái sống ngắn và kết quả bền:** Trạng thái phòng chơi, vòng đấu đang chạy được duy trì hoàn toàn trong RAM (ADR-007). Cơ sở dữ liệu PostgreSQL chỉ lưu trữ kết quả bền vững khi trận đấu hoàn tất.")
    lines.append("4. **Quy chuẩn thời gian:** Toàn bộ các trường mốc thời gian sử dụng kiểu `timestamptz` (UTC); riêng ngày của thử thách hàng ngày sử dụng kiểu `date` theo múi giờ Việt Nam (BR-25).")
    lines.append("")
    lines.append("### 6.2. Mô hình quan hệ thực thể ERD tổng thể")
    lines.append("Lược đồ quan hệ thực thể gồm **21 bảng dữ liệu** được chuẩn hóa bậc 3 (3NF):")
    lines.append("")
    lines.append("![Mô hình ERD](../architecture/data/erd.png)")
    lines.append("*Hình 6.1: Mô hình thực thể liên kết (Entity-Relationship Diagram - ERD) cơ sở dữ liệu Meloo*")
    lines.append("")
    lines.append("### 6.3. Từ điển dữ liệu chi tiết 21 bảng (Data Dictionary)")
    lines.append("Cấu trúc 21 bảng dữ liệu được chuẩn hóa và đồng bộ 100% với tài liệu thiết kế CSDL DES-01 (`docs/architecture/data/02-tu-dien-du-lieu.md`) và quyết định kiến trúc ADR-003:")
    lines.append("")
    lines.append("#### Phân hệ Định danh & Xác thực (`identity` - Migration V2)")
    lines.append("1. **`app_user` (Người chơi & Quản trị viên):** `id` (uuid, PK), `kind` (varchar 12, not null, check `GUEST`/`REGISTERED`), `role` (varchar 8, not null default `USER`, check `USER`/`ADMIN`), `status` (varchar 8, not null default `ACTIVE`, check `ACTIVE`/`LOCKED`), `nickname` (varchar 20, not null, char_length 3–20, BR-22), `email` (varchar 254, unique, null với khách), `lock_reason` (text, null, bắt buộc khi `LOCKED`), `locked_at` (timestamptz, null), `merged_into_id` (uuid, FK `app_user(id)`, null, BR-21), `last_active_at` (timestamptz, not null default now(), BR-20).")
    lines.append("2. **`auth_identity` (Liên kết nhà cung cấp OAuth):** `id` (uuid, PK), `user_id` (uuid, not null, FK `app_user(id)` on delete cascade), `provider` (varchar 10, not null, check `GOOGLE`/`FACEBOOK`), `provider_subject` (varchar 128, not null). Unique `(provider, provider_subject)`.")
    lines.append("3. **`refresh_token` (Phiên làm việc & Token xoay vòng ADR-005):** `id` (uuid, PK), `user_id` (uuid, not null, FK `app_user(id)` on delete cascade), `family_id` (uuid, not null), `token_hash` (char 64, not null, unique - SHA-256), `expires_at` (timestamptz, not null - 30 ngày), `revoked_at` (timestamptz, null), `replaced_by_id` (uuid, FK `refresh_token(id)`, null).")
    lines.append("")
    lines.append("#### Phân hệ Kho nhạc & Danh mục (`catalog` - Migration V3)")
    lines.append("4. **`artist` (Nghệ sĩ biểu diễn):** `id` (uuid, PK), `name` (varchar 200, not null), `name_normalized` (varchar 200, not null, GIN `gin_trgm_ops`), `provider` (varchar 16, not null: `DEEZER`, `ITUNES`), `provider_artist_id` (varchar 64, not null). Unique `(provider, provider_artist_id)`.")
    lines.append("5. **`genre` (Thể loại âm nhạc):** `id` (uuid, PK), `code` (varchar 40, not null, unique), `name_vi` (varchar 80, not null), `name_en` (varchar 80, not null).")
    lines.append("6. **`track` (Bản thu dùng làm đáp án):** `id` (uuid, PK), `provider` (varchar 16, not null), `provider_track_id` (varchar 64, not null - tuyệt đối không lưu URL preview hay audio trên CSDL theo ADR-003:31), `title` (varchar 300, not null), `title_normalized` (varchar 300, not null, GIN `gin_trgm_ops`, BR-05), `primary_artist_id` (uuid, not null, FK `artist(id)`), `genre_id` (uuid, FK `genre(id)`, null), `country_code` (char 2, null), `release_year` (smallint, null), `popularity_rank` (int, null), `enabled` (boolean, not null default true), `disabled_reason` (varchar 200, null). Unique `(provider, provider_track_id)`.")
    lines.append("7. **`playlist` (Bộ đề thi đấu):** `id` (uuid, PK), `code` (varchar 60, not null, unique), `name_vi` (varchar 120, not null), `name_en` (varchar 120, not null), `kind` (varchar 10, not null, check `STANDARD`/`CURATED`), `enabled` (boolean, not null default true).")
    lines.append("8. **`playlist_track` (Liên kết bài hát vào bộ đề):** `playlist_id` (uuid, PK, FK `playlist(id)` on delete cascade), `track_id` (uuid, PK, FK `track(id)`), `position` (int, not null).")
    lines.append("")
    lines.append("#### Phân hệ Lưu trữ Trận đấu & Tiến trình (`progression` - Migration V4, V6)")
    lines.append("9. **`match` (Lịch sử ván đấu):** `id` (uuid, PK - khớp `matchId`), `type` (varchar 16, not null, check `SOLO`/`PRIVATE_ROOM`/`PUBLIC_ROOM`/`QUICK_MATCH`, BR-23), `ranked` (boolean, not null, check `status <> 'ABANDONED' or not ranked`), `status` (varchar 10, not null, check `FINISHED`/`ABANDONED`), `round_count` (smallint, not null, check 5/10/15), `playlist_id` (uuid, null, ref `playlist`), `filter` (jsonb, null), `started_at` (timestamptz, not null), `ended_at` (timestamptz, not null).")
    lines.append("10. **`match_player` (Thành viên tham gia trận):** `id` (uuid, PK), `match_id` (uuid, not null, FK `match(id)` on delete cascade), `user_id` (uuid, null, ref `app_user`), `nickname` (varchar 20, not null), `is_guest` (boolean, not null), `total_score` (int, not null), `rank` (smallint, not null, BR-10), `correct_count` (smallint, not null), `correct_time_ms` (bigint, not null), `counts_for_leaderboard` (boolean, not null, BR-20, BR-21), `ended_at` (timestamptz, not null). Unique `(match_id, user_id)`.")
    lines.append("11. **`round` (Thông tin từng vòng):** `id` (uuid, PK), `match_id` (uuid, not null, FK `match(id)` on delete cascade), `round_no` (smallint, not null), `track_id` (uuid, not null, ref `track`), `track_title` (varchar, not null), `artist_name` (varchar, not null - chụp lại lúc đấu). Unique `(match_id, round_no)`.")
    lines.append("12. **`round_result` (Kết quả vòng của từng người):** `id` (uuid, PK), `round_id` (uuid, not null, FK `round(id)` on delete cascade), `match_player_id` (uuid, not null, FK `match_player(id)` on delete cascade), `outcome` (varchar 10, not null, check `CORRECT`/`WRONG_OUT`/`GAVE_UP`/`TIMEOUT`/`MISSED`), `correct_level` (smallint, null, check 1–5), `base_score` (int, not null, BR-02), `combo_percent` (smallint, not null default 0, check 0/10/20, BR-08), `speed_bonus` (int, not null default 0, BR-07), `round_score` (int, not null, = `base_score` × (1 + `combo_percent`/100) + `speed_bonus`), `wrong_count` (smallint, not null default 0), `answer_time_ms` (int, null). Unique `(round_id, match_player_id)`.")
    lines.append("13. **`player_stats` (Chỉ số tích lũy cá nhân):** `user_id` (uuid, PK, ref `app_user`), `is_guest` (boolean, not null), `xp` (int, not null default 0), `matches_played` (int, not null default 0), `matches_won` (int, not null default 0), `rounds_correct` (int, not null default 0), `best_combo` (smallint, not null default 0), `ranked_score` (bigint, not null default 0 - bảng toàn thời gian BR-24), `ranked_reached_at` (timestamptz, null - thời điểm đạt ranked_score hiện tại, bằng điểm thì ai đạt trước xếp trên).")
    lines.append("14. **`achievement` (Danh mục huy hiệu):** `code` (varchar 60, PK - điều kiện mở khóa nằm trong code BE-PROG-02, không trong DB), `sort_order` (smallint, not null - tên và mô tả lấy từ file i18n theo code).")
    lines.append("15. **`user_achievement` (Huy hiệu người chơi đạt được):** `user_id` (uuid, PK, ref `app_user`), `achievement_code` (varchar 60, PK, FK `achievement(code)`), `unlocked_at` (timestamptz, not null - BR-26: không thu hồi).")
    lines.append("")
    lines.append("#### Phân hệ Thử thách hàng ngày (`daily` - Migration V7)")
    lines.append("16. **`daily_challenge` (Bộ đề theo ngày):** `challenge_date` (date, PK - ngày theo giờ Việt Nam, BR-25), `track_id` (uuid, not null, ref `track`), `source` (varchar 10, not null, check `SCHEDULED`/`AUTO` - admin đặt hoặc tự chọn), `set_by` (uuid, null, ref `app_user`).")
    lines.append("17. **`daily_attempt` (Lượt chơi thử thách của người dùng):** `id` (uuid, PK), `challenge_date` (date, not null, FK `daily_challenge` - ngày server cấp bài, kể cả khi xong sau 00:00), `user_id` (uuid, null, ref `app_user` - null sau khi ẩn danh), `is_guest` (boolean, not null - khách chơi được nhưng không lên bảng), `status` (varchar 11, not null, check `IN_PROGRESS`/`SOLVED`/`UNSOLVED` - UNSOLVED gồm hết lượt và quá trần 5 phút), `started_at` (timestamptz, not null - server cấp), `finished_at` (timestamptz, null), `correct_level` (smallint, null, check 1–5), `elapsed_ms` (int, null - từ started_at đến lúc đoán đúng). Unique `(challenge_date, user_id)`.")
    lines.append("")
    lines.append("#### Phân hệ Kiểm duyệt & Giao tiếp (`chat`, `admin` - Migration V5, V8, V9)")
    lines.append("18. **`banned_word` (Từ điển từ ngữ cấm):** `id` (uuid, PK), `word` (varchar 80, not null, unique, check `word = lower(word)` - chữ thường, Unicode NFC, giữ nguyên dấu tiếng Việt, BR-17, BR-22), `created_by` (uuid, null, ref `app_user`).")
    lines.append("19. **`chat_report` (Báo cáo tin nhắn vi phạm):** `id` (uuid, PK), `message_text` (varchar 200, not null, BR-16), `sender_id` (uuid, not null, ref `app_user` - khách bị khóa theo id này, BR-28), `sender_nickname` (varchar 20, not null - chụp lại lúc báo), `reporter_id` (uuid, not null, ref `app_user`), `context` (jsonb, not null default '[]' - 5 tin trước đó), `status` (varchar 10, not null default `OPEN`, check `OPEN`/`DISMISSED`/`ACTIONED`), `handled_by` (uuid, null, ref `app_user`), `handled_at` (timestamptz, null).")
    lines.append("20. **`audit_log` (Nhật ký thao tác admin V8, BR-28):** `id` (bigint generated always as identity, PK), `actor_id` (uuid, not null, ref `app_user`), `action` (varchar 40, not null - VD `USER_LOCK`, `USER_UNLOCK`, `BANNED_WORD_ADD`, `TRACK_DISABLE`), `target_type` (varchar 30, not null), `target_id` (varchar 64, null), `reason` (text, null - bắt buộc với USER_LOCK ở ứng dụng), `detail` (jsonb, null), `created_at` (timestamptz, not null default now()). Chỉ thêm, không sửa, không xóa.")
    lines.append("21. **`analytics_event` (Số liệu sử dụng cho dashboard V9, FR-16):** `id` (bigint generated always as identity, PK), `event_type` (varchar 40, not null - `GUEST_CREATED`, `USER_REGISTERED`, `MATCH_STARTED`, `MATCH_FINISHED`, `DAILY_ATTEMPT_FINISHED`), `user_id` (uuid, null, ref `app_user` - đặt null khi nhận AccountDeleted), `props` (jsonb, null - VD loại trận), `occurred_at` (timestamptz, not null). **Thời hạn lưu trữ 90 ngày:** Job của module admin chạy hằng ngày xóa dòng có `occurred_at` quá 90 ngày (delete where occurred_at < now() - interval 90 days).")
    lines.append("### 6.4. Kế hoạch di trú dữ liệu tự động (Flyway Migration Plan V1 → V9)")
    lines.append("Tiến trình di trú schema được phân phiên bản tuần tự rõ ràng qua Flyway:")
    lines.append("- **V1:** Thiết lập hạ tầng CSDL ban đầu, kích hoạt extension `uuid-ossp` và hàm tiện ích.")
    lines.append("- **V2:** Khởi tạo bảng danh tính `app_user`, `auth_identity`, `refresh_token` và chỉ mục tìm kiếm.")
    lines.append("- **V3:** Khởi tạo kho nhạc `artist`, `genre`, `track`, `playlist`, `playlist_track`.")
    lines.append("- **V4:** Khởi tạo cấu trúc lưu trữ ván đấu `match`, `match_player`, `round`, `round_result`.")
    lines.append("- **V5:** Khởi tạo phân hệ kiểm duyệt `chat_report`, `banned_word`.")
    lines.append("- **V6:** Khởi tạo hệ thống tiến trình người chơi `player_stats`, `achievement`, `user_achievement`.")
    lines.append("- **V7:** Khởi tạo phân hệ thử thách ngày `daily_challenge`, `daily_attempt`.")
    lines.append("- **V8:** Khởi tạo bảng nhật ký kiểm toán quản trị viên `audit_log`.")
    lines.append("- **V9:** Khởi tạo phân hệ đo lường số liệu hoạt động `analytics_event`.")
    lines.append("")
    lines.append("---")
    lines.append("")

    # PHẦN VII
    lines.append("# PHẦN VII: BỘ CÂU HỎI BẢO VỆ ĐỒ ÁN VÀ HƯỚNG DẪN GIẢI THÍCH")
    lines.append("")
    lines.append("Nhằm phục vụ sinh viên tự tin giải trình và bảo vệ các quyết định thiết kế trước Hội đồng phản biện, dưới đây là bộ câu hỏi trọng tâm kèm gợi ý trả lời chuẩn xác:")
    lines.append("")
    lines.append("### 7.1. Nhóm câu hỏi về Kiến trúc và Lựa chọn công nghệ")
    lines.append("**Câu 1: Tại sao lại chọn kiến trúc Modular Monolith thay vì Microservices ngay từ đầu?**")
    lines.append("> *Trả lời:* Đề tài có ràng buộc phát triển bởi một lập trình viên duy nhất và triển khai trên máy chủ đám mây miễn phí AWS EC2 `t3.micro` chỉ có 1 GB RAM. Nếu áp dụng Microservices, chi phí bộ nhớ cho nhiều máy ảo Java (JVM), API Gateway và mạng nội bộ sẽ vượt quá giới hạn tài nguyên và dẫn đến treo máy. Kiến trúc Modular Monolith với Spring Modulith cho phép đóng gói toàn bộ miền nghiệp vụ trong một tiến trình duy nhất (tiết kiệm RAM tối đa) nhưng vẫn đảm bảo ranh giới module rõ ràng, tự động kiểm tra vi phạm package bằng công cụ CI (`verify()`) và sẵn sàng tách thành Microservices trong Phase 2 khi quy mô người dùng tăng cao.")
    lines.append("")
    lines.append("**Câu 2: Làm thế nào để đảm bảo tính nhất quán giữa Backend (Java) và Frontend (TypeScript)?**")
    lines.append("> *Trả lời:* Dự án áp dụng triệt để phương pháp tiếp cận **Hợp đồng là trên hết (Contract-First)**. Mọi giao tiếp RESTful đều được định nghĩa trước qua OpenAPI 3.1 (`openapi.yaml`) và giao tiếp thời gian thực STOMP qua AsyncAPI 3 (`asyncapi.yaml`). Mã nguồn DTO của Java và client TypeScript được sinh tự động bằng generator tool, kết hợp bộ test tự động kiểm chứng khớp nối (Contract Verification Test). Nhờ đó, backend và frontend luôn hiểu chung một cấu trúc dữ liệu và không xảy ra lỗi lệch trường.")
    lines.append("")
    lines.append("### 7.2. Nhóm câu hỏi về Bảo mật, Phòng chống gian lận và Bản quyền âm thanh")
    lines.append("**Câu 3: Làm sao ngăn chặn người chơi gian lận mở Network DevTools để xem trước tên bài hát?**")
    lines.append(r"> *Trả lời:* Theo quyết định kiến trúc ADR-008, server áp dụng nguyên tắc **Zero-Knowledge Client**: metadata bài hát và đáp án không bao giờ gửi về client trước khi vòng đấu kết thúc. URL phát nhạc chỉ là một endpoint trung gian `/api/v1/audio/{token}`. Chuỗi token là mã băm HMAC-SHA256 có thời hạn $\le 2$ phút, không chứa ID bài hay tên bài. Backend đóng vai trò Streaming Proxy, chỉ đọc trực tiếp và cắt đúng dung lượng byte ứng với mức nghe (ví dụ mức 2s chỉ stream khoảng 32 KB dữ liệu MP3) rồi đóng luồng. Trình duyệt không thể tua trước hoặc trích xuất tiêu đề bài hát.")
    lines.append("")
    lines.append("**Câu 4: Vấn đề bản quyền âm thanh được giải quyết thế nào khi phát nhạc trên web?**")
    lines.append("> *Trả lời:* Hệ thống chỉ sử dụng các đoạn trích âm thanh ngắn 30 giây (preview) được cấp phát công khai qua API chính thức của nhà cung cấp nhạc hợp pháp (Deezer API). Máy chủ không lưu trữ bất kỳ file MP3 lậu nào trên ổ cứng. Sau mỗi vòng chơi, hệ thống vinh danh bản quyền (BR-29) bằng cách hiển thị tên nghệ sĩ, ảnh album và cung cấp đường dẫn nghe bài hát gốc trên Deezer/Spotify.")
    lines.append("")
    lines.append("### 7.3. Nhóm câu hỏi về Xử lý thời gian thực và Chống tranh chấp dữ liệu (Race Condition)")
    lines.append("**Câu 5: Nếu 5 người chơi trong phòng cùng nhấn nút đoán bài trong cùng 1 mili giây thì hệ thống xử lý thế nào?**")
    lines.append("> *Trả lời:* Theo quyết định ADR-007, mỗi phòng thi đấu được điều phối bởi một đối tượng `RoomActor` riêng biệt chạy trên luồng ảo (Virtual Thread) với một hàng đợi lệnh tuần tự (`SingleThreadExecutor`). Mọi thao tác đoán bài gửi lên WebSocket STOMP đều được xếp vào hàng đợi này và xử lý lần lượt theo thứ tự thời gian server nhận lệnh. Nhờ đó, hiện tượng tranh chấp điều kiện (Race Condition) bị loại trừ 100%, server phân định người đoán trước/sau một cách hoàn toàn công bằng.")
    lines.append("")
    lines.append("**Câu 6: Nếu người chơi đang trong trận đấu mà bị rớt mạng thì ván chơi có bị gián đoạn không?**")
    lines.append("> *Trả lời:* Theo quy tắc BR-14, hệ thống thiết lập cơ chế **Vùng đệm giữ chỗ 30 giây (Disconnection Grace Period)**. Khi một người chơi mất kết nối, trận đấu của các thành viên còn lại vẫn tiếp tục bình thường, không ai phải chờ đợi vô ích (Nguyên tắc 1). Nếu người chơi kết nối lại trong vòng 30 giây, họ sẽ tiếp tục thi đấu ở vòng hiện tại; các vòng bị lỡ được ghi nhận 0 điểm. Nếu quá 30 giây không quay lại, server mới xử lý xử thua (Forfeited) để dọn dẹp tài nguyên.")
    lines.append("")
    lines.append("### 7.4. Nhóm câu hỏi về Thuật toán tính điểm và Luật chơi")
    lines.append("**Câu 7: Thuật toán so khớp tên bài hát xử lý thế nào với việc gõ sai chính tả hoặc gõ không dấu?**")
    lines.append("> *Trả lời:* Theo quy tắc BR-05, hệ thống áp dụng thuật toán `TitleNormalizer` chuẩn hóa chuỗi hai đầu: chuyển toàn bộ về chữ thường Unicode NFC, loại bỏ toàn bộ dấu thanh tiếng Việt (A-Z không dấu), xóa các ký tự đặc biệt, dấu câu và tự động loại bỏ các phần nội dung phụ trong ngoặc đơn/ngoặc vuông (như 'feat.', 'remix', 'OST', 'official audio'). Người chơi chỉ cần gõ đúng từ ngữ cốt lõi là được công nhận kết quả. Ngoài ra, ô tìm kiếm gợi ý bài hát theo từ khóa giúp giảm thiểu tối đa việc gõ sai chính tả.")
    lines.append("")
    lines.append("**Câu 8: Tại sao phòng chơi riêng tự tạo lại không được tính điểm lên Bảng xếp hạng mùa giải?**")
    lines.append("> *Trả lời:* Theo quy tắc BR-23, phòng riêng cho phép chủ phòng tự do chọn bộ đề và mời bạn bè, do đó người chơi rất dễ tạo tài khoản phụ để gian lận hoặc cày điểm trên các bộ đề quen thuộc. Để bảo vệ tính công bằng học thuật và sự trong sạch của bảng xếp hạng, chỉ có các trận đấu ngẫu nhiên do hệ thống điều phối (Ghép trận nhanh) hoặc Chơi đơn bộ chuẩn mới được tính điểm xếp hạng mùa. Tuy nhiên, phòng riêng vẫn lưu lịch sử trận đấu, cộng điểm kinh nghiệm (EXP) và tính huy hiệu bình thường để khuyến khích tinh thần giao lưu nhóm bạn.")
    lines.append("")
    lines.append("---")
    lines.append("")

    # PHỤ LỤC
    lines.append("# PHỤ LỤC: DANH MỤC TÀI LIỆU THAM CHIẾU")
    lines.append("")
    lines.append("1. **[S1] Báo cáo thị trường nghe nhạc số Việt Nam:** Q&Me Vietnam Music Streaming Behavior (2025) & Decision Lab The Connected Consumer Q4/2025.")
    lines.append("2. **[S2] Heardle Phenomenon & Spotify Acquisition:** TechCrunch, *'Spotify acquires Heardle, the music trivia game inspired by Wordle'* (12/07/2022).")
    lines.append("3. **[S3] Heardle Shutdown Announcement:** Billboard, *'Spotify is Shutting Down Heardle'* (14/04/2023).")
    lines.append("4. **[S4] Khảo sát đối thủ cạnh tranh quốc tế:** Nghiên cứu đối thủ trực tiếp SongTrivia.io và SongPop Party (Tài liệu Meloo BIZ-04 / MEL-20).")
    lines.append("5. **[S5] Tài liệu Đặc tả Yêu cầu Phần mềm Meloo (SRS v1):** `docs/srs/01-srs.md` & `docs/srs/02-business-rules.md` (DOC-01 / MEL-28).")
    lines.append("6. **[S6] Mô hình Use Case & Đặc tả 10 Use Case:** `docs/srs/usecases/` (DOC-02 / MEL-29).")
    lines.append("7. **[S7] Tài liệu Thiết kế Kiến trúc Hệ thống Meloo:** `docs/architecture/01-architecture.md` (DOC-04 / MEL-52).")
    lines.append("8. **[S8] Thiết kế Cơ sở Dữ liệu & Kế hoạch Migration:** `docs/architecture/data/` (DES-01 / MEL-30).")
    lines.append("9. **[S9] Bộ sơ đồ UML Phân tích & Thiết kế Phần mềm:** `docs/uml/` (DOC-03 / MEL-51).")
    lines.append("10. **[S10] Tập hợp các Quyết định Kiến trúc (ADR-001..008):** `docs/architecture/adr/`.")
    lines.append("")

    content = "\n".join(lines)
    with open(MD_FILE, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Generated Markdown: {MD_FILE} ({len(lines)} lines, {len(content)} bytes)")

def set_cell_background(cell, color_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{color_hex}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=80, bottom=80, left=100, right=100):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(
        f'<w:tcMar {nsdecls("w")}>'
        f'<w:top w:w="{top}" w:type="dxa"/>'
        f'<w:bottom w:w="{bottom}" w:type="dxa"/>'
        f'<w:left w:w="{left}" w:type="dxa"/>'
        f'<w:right w:w="{right}" w:type="dxa"/>'
        f'</w:tcMar>'
    )
    tcPr.append(tcMar)

def add_formatted_runs(paragraph, text, italic=False, text_color=None):
    tokens = re.split(r'(\*\*.*?\*\*|\*.*?\*|`.*?`)', text)
    for token in tokens:
        if not token:
            continue
        if token.startswith("**") and token.endswith("**") and len(token) >= 4:
            run = paragraph.add_run(token[2:-2])
            run.font.bold = True
            if italic:
                run.font.italic = True
            if text_color:
                run.font.color.rgb = text_color
        elif token.startswith("*") and token.endswith("*") and len(token) >= 2:
            run = paragraph.add_run(token[1:-1])
            run.font.italic = True
            if text_color:
                run.font.color.rgb = text_color
        elif token.startswith("`") and token.endswith("`") and len(token) >= 2:
            run = paragraph.add_run(token[1:-1])
            run.font.name = "Consolas"
            run.font.size = Pt(9.5)
            run.font.color.rgb = RGBColor(160, 30, 60)
        else:
            run = paragraph.add_run(token)
            if italic:
                run.font.italic = True
            if text_color:
                run.font.color.rgb = text_color

def render_table(doc, table_lines):
    rows_data = []
    for line in table_lines:
        if re.match(r'^\|[\s\-:]+(\|[\s\-:]+)+\|$', line):
            continue
        cells = [c.strip() for c in line.split("|")[1:-1]]
        if cells:
            rows_data.append(cells)

    if not rows_data:
        return

    num_rows = len(rows_data)
    num_cols = max(len(r) for r in rows_data)

    table = doc.add_table(rows=num_rows, cols=num_cols)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = True

    tblPr = table._tbl.tblPr
    borders = parse_xml(
        f'<w:tblBorders {nsdecls("w")}>'
        f'<w:top w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>'
        f'<w:bottom w:val="single" w:sz="8" w:space="0" w:color="0F3460"/>'
        f'<w:insideH w:val="single" w:sz="4" w:space="0" w:color="E0E0E0"/>'
        f'<w:insideV w:val="none"/>'
        f'<w:left w:val="none"/>'
        f'<w:right w:val="none"/>'
        f'</w:tblBorders>'
    )
    tblPr.append(borders)

    for r_idx, row in enumerate(rows_data):
        is_header = (r_idx == 0)
        tbl_row = table.rows[r_idx]
        for c_idx in range(num_cols):
            cell_text = row[c_idx] if c_idx < len(row) else ""
            cell = tbl_row.cells[c_idx]
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            set_cell_margins(cell, top=80, bottom=80, left=100, right=100)

            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(2)

            if is_header:
                set_cell_background(cell, "0F3460")
                add_formatted_runs(p, cell_text, text_color=RGBColor(255, 255, 255))
                for run in p.runs:
                    run.font.bold = True
                    run.font.size = Pt(9.5)
            else:
                if r_idx % 2 == 1:
                    set_cell_background(cell, "F9FAFC")
                add_formatted_runs(p, cell_text)
                for run in p.runs:
                    run.font.size = Pt(9.0)

    p_after = doc.add_paragraph()
    p_after.paragraph_format.space_before = Pt(2)
    p_after.paragraph_format.space_after = Pt(6)

def generate_docx():
    doc = docx.Document()
    for section in doc.sections:
        section.page_width = Inches(8.27)
        section.page_height = Inches(11.69)
        section.top_margin = Inches(0.8)
        section.bottom_margin = Inches(0.8)
        section.left_margin = Inches(0.8)
        section.right_margin = Inches(0.8)
        
        footer = section.footer
        fp = footer.paragraphs[0]
        fp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        frun = fp.add_run("Meloo · Thuyết minh chi tiết thiết kế hệ thống (Mốc 2)")
        frun.font.name = "Arial"
        frun.font.size = Pt(8.5)
        frun.font.color.rgb = RGBColor(128, 128, 128)

    normal_style = doc.styles['Normal']
    normal_font = normal_style.font
    normal_font.name = 'Arial'
    normal_font.size = Pt(10.5)
    normal_font.color.rgb = RGBColor(30, 30, 30)
    normal_style.paragraph_format.line_spacing = 1.25
    normal_style.paragraph_format.space_after = Pt(4)

    with open(MD_FILE, "r", encoding="utf-8") as f:
        md_text = f.read()

    lines = md_text.split("\n")
    i = 0
    n = len(lines)
    table_lines = []

    while i < n:
        line = lines[i]
        stripped = line.strip()

        if stripped.startswith("|") and stripped.endswith("|"):
            table_lines.append(stripped)
            i += 1
            continue
        elif table_lines:
            render_table(doc, table_lines)
            table_lines = []

        if not stripped:
            i += 1
            continue

        if stripped == "---":
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(8)
            p.paragraph_format.space_after = Pt(8)
            p_border = parse_xml(f'<w:pBdr {nsdecls("w")}><w:bottom w:val="single" w:sz="6" w:space="1" w:color="CCCCCC"/></w:pBdr>')
            p._p.get_or_add_pPr().append(p_border)
            i += 1
            continue

        if stripped.startswith("# "):
            text = stripped[2:].strip()
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(18)
            p.paragraph_format.space_after = Pt(6)
            run = p.add_run(text)
            run.font.name = "Arial"
            run.font.size = Pt(18)
            run.font.bold = True
            run.font.color.rgb = RGBColor(15, 52, 96)
            i += 1
            continue
        elif stripped.startswith("## "):
            text = stripped[3:].strip()
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(14)
            p.paragraph_format.space_after = Pt(4)
            run = p.add_run(text)
            run.font.name = "Arial"
            run.font.size = Pt(14)
            run.font.bold = True
            run.font.color.rgb = RGBColor(22, 33, 62)
            i += 1
            continue
        elif stripped.startswith("### "):
            text = stripped[4:].strip()
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(10)
            p.paragraph_format.space_after = Pt(3)
            run = p.add_run(text)
            run.font.name = "Arial"
            run.font.size = Pt(12)
            run.font.bold = True
            run.font.color.rgb = RGBColor(26, 26, 46)
            i += 1
            continue
        elif stripped.startswith("#### "):
            text = stripped[5:].strip()
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(8)
            p.paragraph_format.space_after = Pt(2)
            run = p.add_run(text)
            run.font.name = "Arial"
            run.font.size = Pt(11)
            run.font.bold = True
            run.font.color.rgb = RGBColor(40, 40, 40)
            i += 1
            continue

        img_match = re.match(r'!\[(.*?)\]\((.*?)\)', stripped)
        if img_match:
            caption = img_match.group(1)
            rel_path = img_match.group(2)
            clean_path = os.path.normpath(os.path.join(REPO_ROOT, rel_path.replace("../", "docs/")))
            if os.path.exists(clean_path):
                p = doc.add_paragraph()
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                p.paragraph_format.space_before = Pt(8)
                p.paragraph_format.space_after = Pt(2)
                run = p.add_run()
                run.add_picture(clean_path, width=Inches(6.2))
            i += 1
            continue

        if stripped.startswith("*Hình ") and stripped.endswith("*"):
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(8)
            run = p.add_run(stripped[1:-1])
            run.font.italic = True
            run.font.size = Pt(9.5)
            run.font.color.rgb = RGBColor(90, 90, 90)
            i += 1
            continue

        if stripped.startswith("> "):
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(4)
            p.paragraph_format.space_after = Pt(4)
            p.paragraph_format.left_indent = Inches(0.3)
            p_border = parse_xml(f'<w:pBdr {nsdecls("w")}><w:left w:val="single" w:sz="18" w:space="8" w:color="0F3460"/></w:pBdr>')
            p._p.get_or_add_pPr().append(p_border)
            add_formatted_runs(p, stripped[2:].strip(), italic=True, text_color=RGBColor(60, 60, 60))
            i += 1
            continue

        if stripped.startswith("- ") or stripped.startswith("* "):
            p = doc.add_paragraph(style='List Bullet')
            p.paragraph_format.space_before = Pt(1)
            p.paragraph_format.space_after = Pt(2)
            add_formatted_runs(p, stripped[2:].strip())
            i += 1
            continue

        num_match = re.match(r'^(\d+\.|\d+\.\d+\.?|[a-z]\.)\s+(.*)', stripped)
        if num_match:
            prefix = num_match.group(1)
            rest = num_match.group(2)
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Inches(0.25)
            p.paragraph_format.space_before = Pt(1)
            p.paragraph_format.space_after = Pt(2)
            run_num = p.add_run(prefix + " ")
            run_num.font.bold = True
            add_formatted_runs(p, rest)
            i += 1
            continue

        p = doc.add_paragraph()
        add_formatted_runs(p, stripped)
        i += 1

    if table_lines:
        render_table(doc, table_lines)

    doc.save(DOCX_FILE)
    print(f"Saved DOCX: {DOCX_FILE} ({os.path.getsize(DOCX_FILE)} bytes)")

def export_pdf():
    print(f"Exporting DOCX to PDF with LibreOffice...")
    cmd = [
        "libreoffice", "--headless", "--convert-to", "pdf",
        DOCX_FILE, "--outdir", BASE_DIR
    ]
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if res.returncode == 0 and os.path.exists(PDF_FILE):
        print(f"Saved PDF: {PDF_FILE} ({os.path.getsize(PDF_FILE)} bytes)")
    else:
        raise RuntimeError(f"PDF export failed: {res.stderr}")

if __name__ == "__main__":
    generate_markdown()
    generate_docx()
    export_pdf()
