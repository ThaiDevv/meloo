# Runbook: Thiết lập hạ tầng AWS, Tên miền & Reverse Proxy (INF-01)

> **Mục tiêu:** Xây dựng môi trường production thực tế, an toàn và tối ưu chi phí cho dự án Meloo.  
> **Người thực hiện:** Thái (Product Owner / Tech Lead) thao tác trên AWS Console & Cloudflare Dashboard theo hướng dẫn, hoặc chạy các script hỗ trợ đi kèm.  
> **Phiên bản:** 1.0.0 (INF-01 · MEL-40)

---

## 1. Điều kiện trước (Prerequisites)

1. **Tài khoản AWS:**
   - Tài khoản có quyền Administrator hoặc các quyền IAM quản lý: EC2, VPC, RDS, Budgets.
   - Region thống nhất: **`ap-southeast-1` (Singapore)**.
2. **Tên miền & DNS:**
   - Đã sở hữu tên miền (ví dụ: `meloo.vn`) và đã trỏ Nameservers về Cloudflare.
3. **Frontend Vercel:**
   - Đã khởi tạo dự án Meloo trên Vercel (kết nối từ repository).
4. **Môi trường máy cục bộ:**
   - Đã cài đặt SSH client (`ssh`).
   - (Tùy chọn) Đã cài đặt và cấu hình `aws-cli` (`aws configure`) nếu muốn dùng script tự động hóa.
5. **Nguyên tắc bảo mật:**
   - **Tuyệt đối không commit** file `.env`, SSH private key (`.pem`), token hay mật khẩu vào Git repository.
   - File cấu hình chứa mật khẩu trên EC2 bắt buộc phải đặt phân quyền `chmod 600`.

---

## 2. Chi phí & Kiểm soát Free Tier

| Dịch vụ | Cấu hình sử dụng | Định mức AWS Free Tier (12 tháng đầu) | Chi phí ước tính |
|---|---|---|---|
| **EC2** | `t3.micro` (2 vCPU, 1 GB RAM) | 750 giờ/tháng t2.micro/t3.micro | **$0.00** |
| **Elastic IP** | 1 địa chỉ gắn với EC2 đang chạy | Miễn phí khi instance chạy; $0.005/h nếu idle | **$0.00** |
| **EBS Storage** | 25 GB General Purpose SSD (`gp3`) | 30 GB EBS miễn phí/tháng | **$0.00** |
| **RDS PostgreSQL 17** | `db.t4g.micro` hoặc `db.t3.micro`, 20 GB `gp2`/`gp3` | 750 giờ/tháng Single-AZ DB instance + 20 GB storage | **$0.00** |
| **Cloudflare DNS** | Free Plan (DNS + SSL Edge) | Miễn phí không giới hạn query | **$0.00** |
| **Caddy HTTPS** | Tự cấp qua Let's Encrypt / ZeroSSL | Miễn phí chứng chỉ SSL/TLS | **$0.00** |

> [!IMPORTANT]
> **Cảnh báo chi phí:** Nhằm tránh rủi ro phát sinh chi phí ngoài ý muốn, bước đầu tiên của runbook là tạo **AWS Budgets cảnh báo $1.00 USD**.

---

## 3. Các bước thực hiện chi tiết

### Bước 1: Thiết lập AWS Budgets (Cảnh báo chi phí > 1 USD)

#### Cách A: Thao tác trên AWS Console
1. Truy cập [AWS Billing & Cost Management Console](https://console.aws.amazon.com/billing/home).
2. Ở menu trái, chọn **Budgets** (hoặc **Cost Budgets**) → bấm **Create budget**.
3. Chọn loại: **Cost budget (recommended)** → bấm **Next**.
4. Thiết lập thông số:
   - **Budget name:** `Meloo-Monthly-Cost-Budget-1USD`
   - **Period:** `Monthly`
   - **Budget effective date:** `Recurring budget`
   - **Budgeting method:** `Fixed`
   - **Enter your budgeted amount:** `1.00` USD
5. Bấm **Next** sang bước **Set alert thresholds**:
   - **Alert 1:** `80%` của budgeted amount (tức $0.80 USD), Trigger: `Actual`, Email recipients: Nhập email của bạn.
   - Bấm **Add alert threshold**:
   - **Alert 2:** `100%` của budgeted amount (tức $1.00 USD), Trigger: `Actual`, Email recipients: Nhập email của bạn.
   - Bấm **Add alert threshold**:
   - **Alert 3:** `100%` của budgeted amount, Trigger: `Forecasted` (dự báo vượt), Email recipients: Nhập email của bạn.
6. Bấm **Next** → xem lại thông tin → bấm **Create budget**.

#### Cách B: Chạy script AWS CLI
Nếu bạn đã cấu hình AWS CLI trên máy:
```bash
./infra/aws/setup-budget.sh your-email@example.com
```

---

### Bước 2: Thiết lập Security Groups

Cần 2 Security Groups thuộc cùng VPC (sử dụng Default VPC tại region `ap-southeast-1`):

#### 1. Security Group cho EC2 (`meloo-ec2-sg`):
- **Tên:** `meloo-ec2-sg`
- **Mô tả:** `Security Group cho Meloo EC2 backend va Caddy`
- **Inbound Rules:**
  - `HTTP` (TCP 80): Source `0.0.0.0/0` (public để Caddy nhận traffic HTTP và giải quyết ACME challenge).
  - `HTTPS` (TCP 443): Source `0.0.0.0/0` (public cho người dùng truy cập API an toàn).
  - `SSH` (TCP 22): Source **`My IP`** (chọn `My IP` trên console hoặc CIDR IP của bạn dạng `x.x.x.x/32`). **Không mở `0.0.0.0/0`!**
- **Outbound Rules:** All traffic (`0.0.0.0/0`).

#### 2. Security Group cho RDS (`meloo-rds-sg`):
- **Tên:** `meloo-rds-sg`
- **Mô tả:** `Security Group cho Meloo RDS PostgreSQL (private)`
- **Inbound Rules:**
  - `PostgreSQL` (TCP 5432): Source chọn **Custom** → nhập ID của `meloo-ec2-sg` (dạng `sg-xxxxxxxxx`).
  - **Tuyệt đối không** thêm rule mở port 5432 cho IP công cộng.
- **Outbound Rules:** All traffic (hoặc mặc định).

#### Sử dụng script tự động:
```bash
./infra/aws/setup-security-groups.sh
```

---

### Bước 3: Khởi tạo EC2 Instance & Gán Elastic IP

#### 1. Khởi tạo EC2 Instance
1. Truy cập **EC2 Console** tại region `ap-southeast-1` (Singapore) → **Launch instances**.
2. **Name and tags:** `meloo-ec2-prod`
3. **Application and OS Images (AMI):**
   - Chọn **Ubuntu**.
   - AMI: **Ubuntu Server 24.04 LTS (HVM), SSD Volume Type** (hoặc 22.04 LTS), kiến trúc `64-bit (x86)`.
4. **Instance type:**
   - Chọn **`t3.micro`** (Free tier eligible).
5. **Key pair (login):**
   - Chọn **Create new key pair**.
   - Key pair name: `meloo-ec2-key`.
   - Key pair type: `RSA` hoặc `ED25519`.
   - Private key file format: `.pem` (cho OpenSSH).
   - Tải file về máy và lưu vào `~/.ssh/meloo-ec2-key.pem`.
   - Phân quyền private key trên máy local:
     ```bash
     chmod 400 ~/.ssh/meloo-ec2-key.pem
     ```
6. **Network settings:**
   - VPC: Default VPC.
   - Auto-assign public IP: `Enable`.
   - Firewall (security groups): Chọn **Select existing security group** → tick chọn `meloo-ec2-sg`.
7. **Configure storage:**
   - `25 GiB` `gp3` (General Purpose SSD).
8. Bấm **Launch instance**.

#### 2. Cấp phát và gán Elastic IP (EIP)
1. Trong menu EC2 trái, chọn **Elastic IPs** (dưới mục *Network & Security*).
2. Bấm **Allocate Elastic IP address** → Region `ap-southeast-1` → bấm **Allocate**.
3. Chọn địa chỉ EIP vừa tạo → bấm **Actions** → **Associate Elastic IP address**:
   - Resource type: `Instance`
   - Instance: Chọn `meloo-ec2-prod` vừa khởi tạo.
   - Private IP address: Chọn địa chỉ private IP của instance.
   - Bấm **Associate**.
4. Ghi lại địa chỉ Elastic IP public này (ví dụ: `18.138.x.x`).

---

### Bước 4: Khởi tạo máy chủ EC2 (Swap 2 GB, Docker, /opt/meloo)

1. Kết nối SSH vào EC2 instance:
   ```bash
   ssh -i ~/.ssh/meloo-ec2-key.pem ubuntu@<ELASTIC_IP>
   ```

2. Tải và chạy script bootstrap `setup-ec2.sh`:
   *(Bạn có thể scp từ máy local hoặc tải script từ repo)*
   ```bash
   # Cách 1: Copy script từ máy dev lên EC2 bằng scp
   # (thực hiện ở máy local):
   scp -i ~/.ssh/meloo-ec2-key.pem infra/aws/setup-ec2.sh ubuntu@<ELASTIC_IP>:~/

   # Cách 2: Trên EC2, chạy script:
   chmod +x ~/setup-ec2.sh
   sudo ~/setup-ec2.sh
   ```

3. Xác nhận kết quả bootstrap trên EC2:
   ```bash
   # Kiểm tra Swap 2 GB đã kích hoạt
   free -h
   # Kết quả: dòng Swap hiển thị Total: 2.0Gi

   # Kiểm tra Docker & Compose
   docker --version
   docker compose version

   # Kiểm tra cấu hình log rotation của Docker daemon
   cat /etc/docker/daemon.json
   # Kết quả hiển thị: "max-size": "10m", "max-file": "3"

   # Kiểm tra thư mục /opt/meloo
   ls -ld /opt/meloo
   ```

4. Áp dụng nhóm `docker` cho phiên terminal hiện tại mà không cần relogin:
   ```bash
   newgrp docker
   docker ps
   ```

---

### Bước 5: Khởi tạo AWS RDS PostgreSQL 17

1. Truy cập **Amazon RDS Console** tại region `ap-southeast-1` → bấm **Create database**.
2. **Choose a database creation method:** Chọn **Standard create**.
3. **Engine options:**
   - Engine type: **PostgreSQL**
   - Engine Version: **PostgreSQL 17.x** (chọn phiên bản 17 mới nhất hiển thị).
4. **Templates:** Chọn **Free tier**.
5. **Settings:**
   - DB instance identifier: `meloo-prod-db`
   - Master username: `meloo_admin`
   - Credentials management: Chọn **Self managed** → Nhập master password mạnh và lưu vào password manager an toàn.
6. **Instance configuration:**
   - DB instance class: **`db.t4g.micro`** hoặc **`db.t3.micro`** (2 vCPU, 1 GB RAM).
7. **Storage:**
   - Storage type: `General Purpose SSD (gp3)` hoặc `gp2`.
   - Allocated storage: `20 GiB`.
   - Storage autoscaling: **Bỏ tick** `Enable storage autoscaling` (để tránh phát sinh phí tự tăng dung lượng).
8. **Connectivity:**
   - Virtual private cloud (VPC): Chọn Default VPC (trùng VPC với EC2).
   - DB Subnet group: `default`.
   - **Public access:** Chọn **No** (Bắt buộc không public ra internet!).
   - VPC security group: Chọn **Choose existing** → Chọn **`meloo-rds-sg`** (bỏ chọn default nếu có).
   - Availability Zone: No preference.
   - Database port: `5432`.
9. **Additional configuration:**
   - Initial database name: `meloo`
   - Backup: Tick chọn **Enable automated backups** (Backup retention period: `7 days`, Backup window: `No preference` hoặc chọn khung giờ đêm ít truy cập).
   - Encryption: Enable encryption.
10. Bấm **Create database**. Quá trình tạo database diễn ra trong khoảng 5–10 phút.
11. Khi trạng thái chuyển sang **Available**, bấm vào instance và sao chép mục **Endpoint** (dạng `meloo-prod-db.cxxxxxx.ap-southeast-1.rds.amazonaws.com`).

#### Kiểm tra kết nối từ EC2:
Đứng trên máy chủ EC2 (đã SSH ở Bước 4), kiểm tra thông mạng tới RDS:
```bash
nc -zv <RDS_ENDPOINT> 5432
# Kết quả mong đợi: Connection to <RDS_ENDPOINT> 5432 port [tcp/postgresql] succeeded!
```

---

### Bước 6: Cấu hình Tên miền trên Cloudflare DNS

1. Đăng nhập [Cloudflare Dashboard](https://dash.cloudflare.com/) và chọn tên miền dự án của bạn (ví dụ: `meloo.vn`).
2. Vào mục **DNS** → **Records** → Bấm **Add record**:

| Type | Name | Content / Target | Proxy status | Ghi chú |
|---|---|---|---|---|
| **A** (hoặc CNAME) | `@` (hoặc domain chính) | IP của Vercel (hoặc `cname.vercel-dns.com`) | **DNS only** (Đám mây xám)* | Trỏ frontend về Vercel |
| **A** | `api` | `<ELASTIC_IP>` của EC2 | **DNS only** (Đám mây xám)* | Trỏ API backend về EC2 |

> [!NOTE]
> **Lưu ý về DNS only và SSL:**
> - **Frontend (`@`):** Vercel khuyến nghị để **DNS only (Đám mây xám)** khi dùng Cloudflare DNS để Vercel tự động cấp và gia hạn chứng chỉ SSL sạch sẽ, tránh xung đột proxy.
> - **Backend (`api`):** Ban đầu bắt buộc để **DNS only (Đám mây xám)** để Caddy trực tiếp nhận kết nối ACME challenge (HTTP-01) từ Let's Encrypt / ZeroSSL qua port 80.
> - **Khi muốn bật Cloudflare Proxy (Đám mây cam) cho `api` sau này:** Cần cấu hình SSL/TLS mode trên Cloudflare là **Full (strict)**. File `Caddyfile` của Meloo đã được cấu hình sẵn để tự động phát hiện header `CF-Connecting-IP`, bảo toàn IP thực của client cho các bộ lọc rate limit (`guest-create-per-hour`).

3. Kiểm tra phân giải tên miền từ máy local:
   ```bash
   dig +short api.<tên-miền>
   # Kết quả trả về đúng địa chỉ Elastic IP của EC2
   ```

---

### Bước 7: Cấu hình Caddy & Biến môi trường trên EC2

1. Đứng trên máy chủ EC2, chuẩn bị thư mục `/opt/meloo`:
   ```bash
   cd /opt/meloo
   ```

2. Đồng bộ các file cấu hình từ repository lên EC2:
   *(Thực hiện từ máy local của bạn bằng `scp`)*
   ```bash
   scp -i ~/.ssh/meloo-ec2-key.pem \
     infra/caddy/Caddyfile \
     infra/aws/docker-compose.prod.yml \
     infra/aws/env.prod.example \
     ubuntu@<ELASTIC_IP>:/opt/meloo/
   ```
   *(Hoặc trên EC2, đổi tên file compose cho tiện dùng: `mv /opt/meloo/docker-compose.prod.yml /opt/meloo/docker-compose.yml`)*

3. Tạo file `.env` trên EC2 từ mẫu `env.prod.example`:
   ```bash
   cd /opt/meloo
   cp env.prod.example .env
   nano .env
   ```
   Điền các giá trị thực tế:
   ```text
   BACKEND_IMAGE=ghcr.io/thaidevv/meloo-backend:latest
   API_DOMAIN=api.<tên-miền>
   ACME_EMAIL=admin@<tên-miền>

   DB_URL=jdbc:postgresql://<RDS_ENDPOINT>:5432/meloo?sslmode=require
   DB_USERNAME=meloo_admin
   DB_PASSWORD=<MẬT_KHẨU_RDS_ĐÃ_TẠO>

   MELOO_CORS_ALLOWED_ORIGINS=https://<tên-miền>,https://<tên-dự-án>.vercel.app
   MELOO_JWT_SECRET=<CHUỖI_NGẪU_NHIÊN_ÍT_NHẤT_32_BYTES>
   ```

4. **Phân quyền bảo mật nghiêm ngặt cho `.env`:**
   ```bash
   chmod 600 /opt/meloo/.env
   ls -la /opt/meloo/.env
   # Kết quả: -rw------- 1 ubuntu ubuntu ...
   ```

5. Chạy thử nghiệm Caddy để xác nhận cấp phát chứng chỉ HTTPS tự động:
   > [!NOTE]
   > Do image backend chưa được build ở ticket này (sẽ thực hiện ở INF-02 MEL-41), ta sử dụng cờ `--no-deps` để khởi động riêng Caddy mà không kéo container backend. Nếu package GHCR ở chế độ private, hãy chạy `echo $GITHUB_TOKEN | docker login ghcr.io -u <github-user> --password-stdin` trước khi pull image backend sau này.

   ```bash
   cd /opt/meloo
   docker compose up -d --no-deps caddy
   docker compose logs -f caddy
   ```
   *Quan sát log Caddy: Caddy đọc biến môi trường `$API_DOMAIN`, liên hệ Let's Encrypt / ZeroSSL, xác thực domain qua HTTP-01 challenge cổng 80 và hoàn tất lưu chứng chỉ HTTPS vào volume `caddy_data`.*

---

### Bước 8: Thiết lập GitHub Secrets (Chuẩn bị cho CD ở INF-02)

Tại repository GitHub, vào **Settings** → **Secrets and variables** → **Actions** → Bấm **New repository secret** để thêm các secret phục vụ CI/CD tự động deploy:

| Secret Name | Giá trị |
|---|---|
| `EC2_HOST` | Địa chỉ Elastic IP public của EC2 |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | Toàn bộ nội dung private key (`~/.ssh/meloo-ec2-key.pem`) bao gồm cả dòng `-----BEGIN ...-----` và `-----END ...-----` |

---

## 4. Bảng kiểm tra nghiệm thu (Verification Checklist)

| Hạng mục | Tiêu chí kiểm tra | Lệnh / Thao tác kiểm tra | Kết quả đạt |
|---|---|---|---|
| **AWS Budget** | Cảnh báo ngưỡng 1 USD | Vào Billing Console → Budgets | Budget hiển thị $1.00 USD, trạng thái OK/Active |
| **EC2 Free Tier** | Đúng region & loại máy | AWS Console | Region `ap-southeast-1`, Type `t3.micro` |
| **Elastic IP** | Gán cố định vào EC2 | EC2 Console → Elastic IPs | EIP gắn với `meloo-ec2-prod`, trạng thái Associated |
| **Security Group EC2** | Mở 80/443 public, SSH chỉ từ IP Thái | EC2 Console → Security Groups | 80/443 là `0.0.0.0/0`, 22 là `<IP_Thái>/32` |
| **Swap Memory** | Đủ 2 GB Swap cho EC2 | Trên EC2: `free -h` | Dòng `Swap` hiển thị `Total: 2.0Gi` |
| **Docker & Compose** | Đã cài đặt và giới hạn log | Trên EC2: `docker --version` && `cat /etc/docker/daemon.json` | Docker hoạt động, log daemon có max-size 10m |
| **RDS PostgreSQL 17** | Phiên bản 17, Private, SG hạn chế | RDS Console | Engine 17.x, Publicly Accessible = No, SG = `meloo-rds-sg` |
| **RDS Backup** | Tự động sao lưu | RDS Console → Maintenance & backups | Automated backups: Enabled (7 days) |
| **Thông mạng EC2 ↔ RDS** | EC2 kết nối được RDS qua cổng 5432 | Trên EC2: `nc -zv <RDS_ENDPOINT> 5432` | `succeeded!` |
| **RDS cách ly ngoài** | Không thể kết nối từ máy local | Máy local: `nc -zv <RDS_ENDPOINT> 5432` | Bị timeout hoặc connection refused |
| **Cloudflare DNS** | Trỏ đúng Vercel và EC2 | `dig +short api.<domain>` | Trả về đúng Elastic IP |
| **Bảo mật `.env`** | Quyền chmod 600, không lộ | Trên EC2: `stat -c "%a" /opt/meloo/.env` | Trả về đúng `600` |
| **Caddy HTTPS** | Tự cấp SSL cho `api.<domain>` | `curl -I https://api.<domain>` | Trả về header SSL hợp lệ (HTTP/2 502 Bad Gateway vì backend chưa bật, nhưng SSL handshake thành công) |

---

## 5. Xử lý sự cố & Kịch bản khôi phục (Troubleshooting & Rollback)

### Sự cố 1: SSH vào EC2 bị timeout hoặc "Permission denied"
- **Nguyên nhân 1:** IP công cộng của mạng nhà/văn phòng thay đổi khiến rule port 22 trong `meloo-ec2-sg` không còn khớp.
  - *Xử lý:* Vào AWS Console → Security Groups → `meloo-ec2-sg` → Edit inbound rules → Tại port 22, chọn lại `My IP` và bấm Save rules.
- **Nguyên nhân 2:** Quyền file `.pem` quá mở.
  - *Xử lý:* `chmod 400 ~/.ssh/meloo-ec2-key.pem`.

### Sự cố 2: Caddy không cấp được chứng chỉ SSL ("ACME challenge failed")
- **Nguyên nhân 1:** Cổng 80 chưa được mở trên Security Group của EC2.
  - *Xử lý:* Caddy cần cổng 80 để xác thực Let's Encrypt HTTP-01 challenge. Đảm bảo `meloo-ec2-sg` có inbound rule TCP port 80 cho `0.0.0.0/0`.
- **Nguyên nhân 2:** Record Cloudflare đang bật Proxy (đám mây cam) và mode SSL chưa tương thích.
  - *Xử lý:* Tạm thời chuyển DNS record `api` sang **DNS only** (đám mây xám) trên Cloudflare để Let's Encrypt kết nối thẳng tới Caddy.

### Sự cố 3: EC2 không thể kết nối tới RDS
- **Nguyên nhân:** `meloo-rds-sg` chưa nhận diện đúng `meloo-ec2-sg`, hoặc EC2 và RDS nằm khác VPC.
  - *Xử lý:* Kiểm tra VPC ID của cả hai dịch vụ trên AWS Console phải giống nhau (cùng default VPC). Trong Inbound Rules của `meloo-rds-sg`, kiểm tra Source của port 5432 phải là Security Group ID của EC2 (`sg-xxxxxxx`).

### Sự cố 4: Chi phí cảnh báo vượt 1 USD
- **Kiểm tra ngay:**
  1. Vào **AWS Cost Explorer** để xem dịch vụ nào đang tiêu tốn tiền.
  2. Kiểm tra có Elastic IP nào đang cấp phát mà không gắn vào instance đang chạy không (Unattached Elastic IP bị tính phí $0.005/h).
  3. Kiểm tra RDS có bị chọn nhầm instance class ngoài Free Tier (ví dụ: `db.m5.large`) hoặc dung lượng storage vượt quá 20 GB không.
  4. Kiểm tra snapshot/backup lưu trữ quá nhiều.

---

## 6. Dọn dẹp tài nguyên khi dừng dự án (Teardown)

Khi hoàn thành đồ án môn học hoặc cần hủy bỏ toàn bộ hạ tầng để dừng tính phí:
1. **Xóa RDS Instance:** RDS Console → chọn `meloo-prod-db` → Actions → Delete (bỏ tick create final snapshot nếu không cần lưu trữ).
2. **Hủy EC2 Instance:** EC2 Console → chọn `meloo-ec2-prod` → Instance State → Terminate instance.
3. **Giải phóng Elastic IP:** EC2 Console → Elastic IPs → chọn IP → Actions → Release Elastic IP addresses (rất quan trọng, tránh bị tính phí EIP rảnh).
4. **Xóa Security Groups:** Xóa `meloo-rds-sg` rồi xóa `meloo-ec2-sg`.
5. **Xóa AWS Budget:** Billing Console → Budgets → Delete budget.
