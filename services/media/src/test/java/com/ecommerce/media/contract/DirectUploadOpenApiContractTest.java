package com.ecommerce.media.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

class DirectUploadOpenApiContractTest {
    @Test
    void declaresBatchCreateCompleteStatusAndTheIsolatedProviderCallback() {
        Path contract =
                Path.of("../../contracts/openapi/media.yaml").toAbsolutePath().normalize();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        OpenAPI api = new OpenAPIV3Parser().read(contract.toUri().toString(), null, options);

        assertThat(api).isNotNull();
        var paths = api.getPaths();
        assertThat(paths.get("/media/upload-intents").getPost().getResponses().containsKey("201"))
                .isTrue();
        assertThat(paths.get("/media/upload-intents")
                        .getPost()
                        .getResponses()
                        .get("201")
                        .getHeaders())
                .containsKeys("Location", "Cache-Control");
        assertThat(paths.get("/media/upload-intents/complete")
                        .getPost()
                        .getResponses()
                        .containsKey("200"))
                .isTrue();
        assertThat(paths.get("/media/upload-intents/status")
                        .getPost()
                        .getResponses()
                        .containsKey("200"))
                .isTrue();
        assertThat(paths.get("/media/cloudinary/notifications")
                        .getServers()
                        .getFirst()
                        .getUrl())
                .isEqualTo("/api");
        assertThat(api.getComponents().getSchemas())
                .containsKeys("CreateUploadIntentBatch", "UploadIntentBatchResult", "UploadIntentResult");
    }
}
