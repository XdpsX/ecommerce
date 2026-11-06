package com.ecommerce.media.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.ecommerce.media.asset.application.exception.DirectUploadException;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProblemProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void frameworkNegotiationFailureStays406() throws Exception {
        mvc.perform(get("/probe/representation").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    void malformedPathAndMissingMultipartStay400() throws Exception {
        mvc.perform(get("/probe/id/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(multipart("/probe/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unsupportedAndOversizedUploadsKeepTheirStatuses() throws Exception {
        mvc.perform(post("/probe/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(multipart("/probe/large").file(new MockMultipartFile("file", "x", "image/png", new byte[] {1})))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void unexpectedFailureIsSafeAndWrongMethodPreservesAllowHeader() throws Exception {
        mvc.perform(get("/probe/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected server error occurred."));
        mvc.perform(post("/probe/method"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void directUploadProblemsMapSemanticKindsToHttpStatuses() throws Exception {
        mvc.perform(get("/probe/direct/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/probe/direct/quota"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("UPLOAD_QUOTA_EXCEEDED"));
        mvc.perform(get("/probe/direct/disabled"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DIRECT_UPLOAD_UNAVAILABLE"));
    }

    @Test
    void persistenceFailuresUseTheCentralUnavailableProblem() throws Exception {
        mvc.perform(get("/probe/database"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MEDIA_PROVIDER_UNAVAILABLE"));
    }

    @RestController
    @RequestMapping("/probe")
    static class ProblemProbeController {
        @GetMapping("/representation")
        Representation representation() {
            return new Representation("value");
        }

        @GetMapping("/id/{id}")
        UUID id(@PathVariable UUID id) {
            return id;
        }

        @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        void upload(@RequestPart("file") MultipartFile file) {}

        @PostMapping(path = "/large", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        void large(@RequestPart("file") MultipartFile file) {
            throw new MaxUploadSizeExceededException(1);
        }

        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException("provider secret detail");
        }

        @GetMapping("/direct/{kind}")
        void direct(@PathVariable String kind) {
            DirectUploadException.Kind errorKind = switch (kind) {
                case "bad-request" -> DirectUploadException.Kind.BAD_REQUEST;
                case "quota" -> DirectUploadException.Kind.QUOTA_EXCEEDED;
                default -> DirectUploadException.Kind.DISABLED;
            };
            throw new DirectUploadException(errorKind);
        }

        @GetMapping("/database")
        void database() {
            throw new DataAccessResourceFailureException("fixture");
        }

        @GetMapping("/method")
        Representation method() {
            return new Representation("value");
        }
    }

    record Representation(String value) {}
}
