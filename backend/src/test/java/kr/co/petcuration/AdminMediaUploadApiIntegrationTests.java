package kr.co.petcuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMediaUploadApiIntegrationTests {

    @TempDir
    static Path MEDIA_ROOT;

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void mediaRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", MEDIA_ROOT::toString);
    }

    @Test
    void adminUploadsValidatedImageAndReceivesPublicStorageMetadata() throws Exception {
        AuthState admin = login("admin@example.com");
        MockMultipartFile image = new MockMultipartFile("file", "product.png", "image/png", png(4, 3));

        MvcResult result = mockMvc.perform(withCsrf(multipart("/api/v1/admin/media/images")
                        .file(image).cookie(admin.session()), admin.csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.storageKey", matchesPattern(
                        "uploads/products/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png")))
                .andExpect(jsonPath("$.data.url", matchesPattern(
                        "/media/uploads/products/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png")))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.sizeBytes").isNumber())
                .andExpect(jsonPath("$.data.width").value(4))
                .andExpect(jsonPath("$.data.height").value(3))
                .andReturn();

        String storageKey = JsonPath.read(result.getResponse().getContentAsString(), "$.data.storageKey");
        assertThat(Files.isRegularFile(MEDIA_ROOT.resolve(storageKey))).isTrue();
        mockMvc.perform(get("/media/" + storageKey))
                .andExpect(status().isOk());
    }

    @Test
    void uploadRequiresAuthenticationAdminRoleAndCsrf() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "product.png", "image/png", png(2, 2));
        CsrfState anonymousCsrf = csrf();
        mockMvc.perform(withCsrf(multipart("/api/v1/admin/media/images").file(image), anonymousCsrf))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        AuthState customer = login("demo@example.com");
        mockMvc.perform(withCsrf(multipart("/api/v1/admin/media/images")
                        .file(image).cookie(customer.session()), customer.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        AuthState admin = login("admin@example.com");
        mockMvc.perform(multipart("/api/v1/admin/media/images").file(image).cookie(admin.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void uploadRejectsOversizeInvalidAndMismatchedFiles() throws Exception {
        AuthState admin = login("admin@example.com");
        byte[] oversized = new byte[8 * 1024 * 1024 + 1];
        mockMvc.perform(withCsrf(multipart("/api/v1/admin/media/images")
                        .file(new MockMultipartFile("file", "large.png", "image/png", oversized))
                        .cookie(admin.session()), admin.csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMAGE_FILE_TOO_LARGE"));

        mockMvc.perform(withCsrf(multipart("/api/v1/admin/media/images")
                        .file(new MockMultipartFile("file", "fake.png", "image/png", new byte[] {1, 2, 3}))
                        .cookie(admin.session()), admin.csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_FORMAT"));

        mockMvc.perform(withCsrf(multipart("/api/v1/admin/media/images")
                        .file(new MockMultipartFile("file", "mismatch.jpg", "image/jpeg", png(2, 2)))
                        .cookie(admin.session()), admin.csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("IMAGE_CONTENT_TYPE_MISMATCH"));
    }

    @Test
    void uploadRejectsWebpExtendedHeaderWithoutImagePayload() throws Exception {
        AuthState admin = login("admin@example.com");
        mockMvc.perform(withCsrf(multipart("/api/v1/admin/media/images")
                        .file(new MockMultipartFile("file", "header-only.webp", "image/webp", vp8xHeaderOnly()))
                        .cookie(admin.session()), admin.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
    }

    private AuthState login(String email) throws Exception {
        CsrfState anonymousCsrf = csrf();
        MvcResult login = mockMvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"DemoPassword123!\"}"), anonymousCsrf))
                .andExpect(status().isOk()).andReturn();
        Cookie session = requireResponseCookie(login, "SESSION");
        return new AuthState(session, csrf(session));
    }

    private CsrfState csrf(Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/auth/csrf");
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return new CsrfState(requireResponseCookie(result, "XSRF-TOKEN"),
                JsonPath.read(result.getResponse().getContentAsString(), "$.data.token"));
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, CsrfState csrf) {
        return request.cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token());
    }

    private MockMultipartHttpServletRequestBuilder withCsrf(
            MockMultipartHttpServletRequestBuilder request,
            CsrfState csrf
    ) {
        return request.cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token());
    }

    private Cookie requireResponseCookie(MvcResult result, String name) {
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            if (header.startsWith(name + "=")) {
                int end = header.indexOf(';');
                String pair = end < 0 ? header : header.substring(0, end);
                return new Cookie(name, pair.substring(name.length() + 1));
            }
        }
        throw new AssertionError("Missing response cookie: " + name);
    }

    private static byte[] png(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0x336699);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG writer unavailable");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static byte[] vp8xHeaderOnly() {
        return new byte[] {
                'R', 'I', 'F', 'F', 22, 0, 0, 0, 'W', 'E', 'B', 'P',
                'V', 'P', '8', 'X', 10, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
    }

    private record CsrfState(Cookie cookie, String token) {
    }

    private record AuthState(Cookie session, CsrfState csrf) {
    }
}
