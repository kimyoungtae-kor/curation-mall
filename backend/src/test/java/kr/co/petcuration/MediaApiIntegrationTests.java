package kr.co.petcuration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaApiIntegrationTests {

    private static final byte[] WEBP_BYTES = {1, 2, 3, 4};
    private static final Path MEDIA_ROOT = createMediaRoot();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void mediaRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", MEDIA_ROOT::toString);
    }

    @Test
    void servesPublicMediaWithTypeLengthAndCacheHeaders() throws Exception {
        mockMvc.perform(get("/media/demo/catalog/sample.webp"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/webp"))
                .andExpect(content().bytes(WEBP_BYTES))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(header().longValue("Content-Length", WEBP_BYTES.length));
    }

    @Test
    void returnsNotFoundForMissingMedia() throws Exception {
        mockMvc.perform(get("/media/demo/catalog/missing.webp"))
                .andExpect(status().isNotFound());
    }

    private static Path createMediaRoot() {
        try {
            Path root = Files.createTempDirectory("pet-curation-media-");
            Path image = root.resolve("demo/catalog/sample.webp");
            Files.createDirectories(image.getParent());
            Files.write(image, WEBP_BYTES);
            root.toFile().deleteOnExit();
            image.getParent().toFile().deleteOnExit();
            image.toFile().deleteOnExit();
            return root;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
