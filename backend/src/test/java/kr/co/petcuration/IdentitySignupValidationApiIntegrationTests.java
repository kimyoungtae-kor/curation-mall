package kr.co.petcuration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentitySignupValidationApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidPhoneReturnsAFieldSpecificProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-phone@example.com",
                                  "password": "DemoPassword123!",
                                  "name": "테스트회원",
                                  "phone": "1231231234",
                                  "requiredTermsAccepted": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("phone"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("Pattern"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("휴대전화 번호가 올바르지 않습니다."));
    }
}
