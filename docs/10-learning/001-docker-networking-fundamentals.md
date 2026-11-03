# Networking căn bản cho Docker và Ecommerce

> Tài liệu học tập — từ các khái niệm mạng cơ bản đến cách cấu hình Docker Compose trong dự án Ecommerce.

## Vì sao có tài liệu này?

Trong lúc cấu hình Docker Compose cho dự án Ecommerce, mình thắc mắc về cách khai báo `ports` của PostgreSQL và Redis:

```yaml
ports:
  - "127.0.0.1:5432:5432"
```

**Tại sao lại ghi `127.0.0.1` thay vì `localhost`? Nếu chỉ ghi `"5432:5432"` thì có gì khác?** Và nếu không khai báo `ports` thì ứng dụng hoặc công cụ như DBeaver sẽ kết nối tới PostgreSQL bằng cách nào?

Từ những câu hỏi đó, mình nhận ra cần hiểu các khái niệm network cơ bản trước khi quyết định cách cấu hình Docker Compose. Tài liệu này ghi lại kiến thức và ví dụ liên quan, nhằm giải thích *vì sao* chọn một cách cấu hình chứ không chỉ hướng dẫn *viết YAML như thế nào*.

Phạm vi tập trung vào môi trường phát triển của Ecommerce: PostgreSQL và Redis chạy trong Docker; ứng dụng có thể chạy từ IDE hoặc trong container.

---

## 1. Máy tính giao tiếp qua mạng như thế nào?

Một ứng dụng muốn giao tiếp với ứng dụng khác cần biết **địa chỉ đích** và **cổng dịch vụ**.

- **IP address**: địa chỉ của một thiết bị hoặc một network interface trong mạng. Ví dụ: `192.168.1.10`.
- **Port**: số giúp hệ điều hành chuyển lưu lượng đến đúng dịch vụ. Ví dụ: PostgreSQL thường lắng nghe ở `5432`; Redis thường ở `6379`.
- **Protocol**: quy tắc truyền thông. TCP thường được dùng cho kết nối PostgreSQL và Redis.
- **Network interface**: điểm kết nối mạng của máy, như Wi-Fi, Ethernet hoặc loopback.

Ví dụ `192.168.1.10:5432` có thể hiểu là: kết nối đến dịch vụ đang lắng nghe trên cổng 5432 của máy có IP `192.168.1.10`.

**Lưu ý:** IP không đồng nhất với máy tính. Một máy có thể có nhiều IP trên nhiều network interface.

## 2. `localhost`, `127.0.0.1` và `0.0.0.0`

### `localhost`

`localhost` là **hostname** thường được phân giải về địa chỉ loopback `127.0.0.1` (IPv4) hoặc `::1` (IPv6). Nó có nghĩa là **chính môi trường mạng mà chương trình đang chạy**.

### `127.0.0.1`

`127.0.0.1` là địa chỉ **IPv4 loopback**. Kết nối tới địa chỉ này không đi ra mạng LAN. Một server chỉ bind vào `127.0.0.1` thường chỉ nhận kết nối từ chính host đó.

### `0.0.0.0`

Khi **server bind/listen**, `0.0.0.0` là địa chỉ wildcard IPv4: lắng nghe trên mọi IPv4 interface phù hợp. Nó **không phải** địa chỉ đích thông thường mà client nên sử dụng để kết nối.

| Server bind vào | Ý nghĩa thường gặp |
| --- | --- |
| `127.0.0.1:5432` | Chỉ nhận kết nối qua loopback IPv4 của host |
| `192.168.1.10:5432` | Nhận kết nối qua interface có IP này |
| `0.0.0.0:5432` | Nhận kết nối qua các IPv4 interface của host |

Việc một port có thể truy cập từ máy khác còn phụ thuộc firewall, router, Docker và cấu hình của dịch vụ.

## 3. Docker container có `localhost` riêng

Container thông thường có **network namespace riêng**. Vì vậy:

- `localhost` **trong container PostgreSQL** trỏ về chính container PostgreSQL.
- `localhost` **trong container Ecommerce** trỏ về chính container Ecommerce, **không phải PostgreSQL**.
- `localhost` **trên máy developer** trỏ về máy developer.

Đây là lỗi phổ biến khi chuyển một Spring Boot application từ IDE vào Docker.

```text
Máy developer (host)
  ├── localhost:5432  ──[published port]──> PostgreSQL container :5432
  └── Docker network
       ├── ecommerce container ──> postgres:5432
       └── postgres container
```

Khi chạy từ IDE, JDBC URL có thể là `jdbc:postgresql://localhost:5432/ecommerce`.

Khi chạy trong Docker Compose, JDBC URL thường là `jdbc:postgresql://postgres:5432/ecommerce`, với `postgres` là **tên service** mà Docker Compose phân giải trong network chung.

## 4. Docker Compose `ports` hoạt động như thế nào?

Cú pháp ngắn thường dùng:

```yaml
ports:
  - "HOST_IP:HOST_PORT:CONTAINER_PORT"
```

Ví dụ:

```yaml
ports:
  - "127.0.0.1:5432:5432"
```

Nghĩa là publish cổng 5432 của container ra cổng 5432 trên **loopback IPv4 của host**. DBeaver trên máy developer có thể kết nối bằng `localhost:5432` (miễn là client sử dụng IPv4 hoặc fallback phù hợp).

Nếu chỉ viết:

```yaml
ports:
  - "5432:5432"
```

Docker thường publish trên **mọi host interface** theo cấu hình daemon/network, thay vì giới hạn vào loopback. Điều này có thể khiến dịch vụ truy cập được từ LAN; firewall và thiết lập Docker vẫn quyết định khả năng truy cập thực tế.

Không nên viết `localhost:5432:5432` như một thay thế mặc định cho host IP: phần đầu của cú pháp là địa chỉ IP bind, nên dùng `127.0.0.1` nếu muốn giới hạn loopback IPv4.

**Quan trọng:** Publish port không thay thế authentication. PostgreSQL vẫn cần credentials, Redis cần cấu hình bảo vệ phù hợp, và không nên expose các dịch vụ này ra Internet.

## 5. `ports` khác gì `expose`?

- **`ports`**: publish cổng container lên host; công cụ trên host như DBeaver có thể truy cập qua cổng đã publish.
- **`expose`**: mô tả cổng container sử dụng; **không** publish cổng lên host.
- Các service trên cùng Docker Compose network thường có thể kết nối tới cổng đang lắng nghe của nhau **mà không cần** `ports` hay `expose`.

Ví dụ, service `ecommerce` có thể kết nối `postgres:5432` ngay cả khi PostgreSQL không có mục `ports`.

## 6. Áp dụng vào hai chế độ chạy Ecommerce

### Dev — chạy application trên IDE

Chạy infrastructure bằng `compose.yml`:

```bash
docker compose -f compose.yml up -d
```

PostgreSQL và Redis publish port ra host để ứng dụng trên IDE và công cụ quản trị kết nối được.

### Local — chạy toàn bộ bằng Docker

Hiện tại `compose.local.yml` đã **include** `compose.yml`; các application container sẽ được bổ sung sau:

```yaml
include:
  - path: compose.yml

services: {}
```

Sau này có thể bổ sung `ecommerce`, `identity`, `media`, `notification` khi Docker image và cấu hình ứng dụng đã sẵn sàng.

Chạy bằng một lệnh:

```bash
docker compose -f compose.local.yml up -d
```

> **Hiện trạng:** `include` đã được cấu hình, nhưng chưa có application containers.

### Dùng chung dữ liệu

Nếu cả hai chế độ cùng sử dụng **Compose project name**, tên service và tên volume, chúng có thể tái sử dụng cùng PostgreSQL/Redis container và named volumes. Tránh khởi chạy hai PostgreSQL instance độc lập cùng ghi vào một data directory. Lệnh `docker compose down -v` sẽ xóa named volumes do project quản lý; cần đặc biệt thận trọng.

## 7. Bài tập thực hành

1. Chạy `docker compose -f compose.yml config` để xem cấu hình đã được Compose xử lý.
2. Chạy `docker compose -f compose.yml up -d` và `docker compose -f compose.yml ps` để kiểm tra container/port.
3. Kết nối PostgreSQL từ DBeaver bằng `localhost` và host port đã cấu hình.
4. Chạy `docker compose -f compose.yml exec postgres sh` rồi thử `hostname` và `hostname -i` để quan sát môi trường container.
5. So sánh kết quả khi khai báo `127.0.0.1:5432:5432` với `5432:5432` bằng `docker compose ... ps` hoặc `docker port <container>`; không thử expose trên mạng không tin cậy.

## 8. Ghi nhớ nhanh

- `localhost` là **máy/container hiện tại theo góc nhìn của tiến trình**.
- `127.0.0.1` là IPv4 loopback; `0.0.0.0` khi bind server là mọi IPv4 interface.
- `HOST_PORT:CONTAINER_PORT` publish port ra host.
- Container gọi nhau qua **service name** trên Docker network, không qua `localhost` của nhau.
- Trong dev, ưu tiên `127.0.0.1:HOST_PORT:CONTAINER_PORT` nếu chỉ cần truy cập từ chính máy developer.
- Named volumes lưu dữ liệu độc lập với vòng đời container; đừng xóa volume nếu muốn giữ database.

## 9. Tài liệu đọc thêm

- [Docker: Publishing and exposing ports](https://docs.docker.com/get-started/docker-concepts/running-containers/publishing-ports/)
- [Docker Compose networking](https://docs.docker.com/compose/how-tos/networking/)
- [Docker Compose include](https://docs.docker.com/compose/how-tos/multiple-compose-files/include/)
- [Docker Compose ports reference](https://docs.docker.com/reference/compose-file/services/#ports)
