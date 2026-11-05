package com.ecommerce.media.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

class MediaOpenApiContractTest {
    @Test
    void parsesMediaContractAndResolvesSharedProblemDefinitions() {
        Path contract =
                Path.of("../../contracts/openapi/media.yaml").toAbsolutePath().normalize();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        OpenAPI api = new OpenAPIV3Parser().read(contract.toUri().toString(), null, options);

        assertThat(api).isNotNull();
        assertThat(api.getPaths().containsKey("/media")).isTrue();
        assertThat(api.getPaths().containsKey("/media/{id}")).isTrue();
        assertThat(api.getPaths().containsKey("/media/{id}/download-link")).isTrue();
        assertThat(api.getComponents().getSchemas().containsKey("MediaAsset")).isTrue();
        assertThat(api.getComponents().getSchemas().containsKey("DownloadLink")).isTrue();

        var collectionResponses = api.getPaths().get("/media").getPost().getResponses();
        assertThat(collectionResponses.containsKey("201")).isTrue();
        assertThat(collectionResponses.containsKey("405")).isTrue();
        assertThat(collectionResponses.containsKey("406")).isTrue();
        assertThat(collectionResponses.containsKey("413")).isTrue();
        assertThat(collectionResponses.containsKey("415")).isTrue();
        assertThat(collectionResponses.containsKey("422")).isTrue();
        assertThat(collectionResponses.containsKey("503")).isTrue();

        var methodNotAllowedHeaders = collectionResponses.get("405").getHeaders();
        assertThat(methodNotAllowedHeaders.containsKey("Allow")).isTrue();

        var unauthorizedProperties = collectionResponses.get("401")
                .getContent()
                .get("application/problem+json")
                .getSchema()
                .getProperties();
        assertThat(unauthorizedProperties.containsKey("code")).isTrue();
    }
}
