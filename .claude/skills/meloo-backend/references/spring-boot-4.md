# Spring Boot 4: những chỗ dễ viết sai theo thói quen Boot 3

Nguồn: hướng dẫn nâng cấp chính thức của Spring Boot 4 và Spring Security 7. Còn phân vân thì đối chiếu tài liệu đúng phiên bản trong pom.

## Jackson 3 (mặc định trong Boot 4)
- Group id `tools.jackson`, package `tools.jackson.*` (VD `tools.jackson.databind.ObjectMapper`, `tools.jackson.databind.json.JsonMapper`).
- Ngoại lệ: annotation vẫn ở `com.fasterxml.jackson.annotation` (`@JsonProperty`, `@JsonIgnore`…).
- Đổi tên: `@JsonComponent` → `@JacksonComponent`; `@JsonMixin` → `@JacksonMixin`; `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`.
- Property `spring.jackson.read.*` chuyển thành `spring.jackson.json.read.*`.
- Inject `JsonMapper`/`ObjectMapper` do Spring cấu hình, không tự `new`.

## Starter đổi tên, tách module
- `spring-boot-starter-web` → `spring-boot-starter-webmvc`; `spring-boot-starter-aop` → `spring-boot-starter-aspectj`.
- OAuth2: `spring-boot-starter-security-oauth2-client`, `spring-boot-starter-security-oauth2-resource-server`.
- Boot 4 tách auto-configuration theo module, nên mỗi tính năng cần đúng starter của nó (VD Flyway dùng `spring-boot-starter-flyway`, kèm `flyway-database-postgresql`). Thêm thư viện là việc của ticket giữ `hot-pom`.

## Spring Security 7
- Chỉ dùng lambda DSL: `http.authorizeHttpRequests(a -> a.requestMatchers("/api/v1/me").authenticated())`. Không `.and()`, không `authorizeRequests()`.
- Không dùng `AntPathRequestMatcher` hay `MvcRequestMatcher` (đã bỏ); dùng `requestMatchers(String...)` hoặc `PathPatternRequestMatcher`.
- Phân quyền theo method: `@EnableMethodSecurity` + `@PreAuthorize`.

## Test
- `@MockBean`/`@SpyBean` đã bỏ, thay bằng `@MockitoBean`/`@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito`).
- `@SpringBootTest` không còn tự cấu hình MockMvc, WebTestClient hay TestRestTemplate: thêm `@AutoConfigureMockMvc`, hoặc dùng `RestTestClient` với `@AutoConfigureRestTestClient`.
- `TestRestTemplate` chuyển sang `org.springframework.boot.resttestclient` (cần dependency `spring-boot-resttestclient`).
- Testcontainers: dùng `@ServiceConnection` cho PostgreSQL thay vì tự set URL. Tên artifact và package phụ thuộc phiên bản Testcontainers trong pom (bản 2.x đổi tên module), nên kiểm tra trước khi import.

## Nền tảng
- Jakarta EE 11, Servlet 6.1. Null-safety theo JSpecify (`org.jspecify.annotations.Nullable`).
- `HttpMessageConverters` deprecated → dùng `ServerHttpMessageConvertersCustomizer` hoặc `ClientHttpMessageConvertersCustomizer`.
- Gọi HTTP ra ngoài bằng `RestClient` (đồng bộ) hoặc HTTP interface; luôn đặt timeout rõ ràng.
- Actuator: probe `/actuator/health/liveness` và `/actuator/health/readiness` bật sẵn.
