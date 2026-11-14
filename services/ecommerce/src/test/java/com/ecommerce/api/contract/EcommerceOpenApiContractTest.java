package com.ecommerce.api.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

class EcommerceOpenApiContractTest {
    @Test
    void parsesCategoryContractAndDeclaresStructuralResponsesAndEffectiveVisibility() {
        Path contract = Path.of("../../contracts/openapi/ecommerce.yaml")
                .toAbsolutePath()
                .normalize();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        OpenAPI api = new OpenAPIV3Parser().read(contract.toUri().toString(), null, options);

        assertThat(api).isNotNull();
        assertThat(api.getPaths().keySet())
                .contains("/categories", "/categories/{id}", "/categories/{id}/move", "/categories/sibling-order");
        var move = api.getPaths()
                .get("/categories/{id}/move")
                .getPost()
                .getResponses()
                .get("200");
        var reorder = api.getPaths()
                .get("/categories/sibling-order")
                .getPut()
                .getResponses()
                .get("200");
        for (var response : java.util.List.of(move, reorder)) {
            assertThat(response.getHeaders()).containsKey("Cache-Control");
            Object properties =
                    response.getContent().get("application/json").getSchema().getProperties();
            assertThat(properties).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) properties).containsKey("structureRevision"))
                    .isTrue();
            assertThat(((Map<?, ?>) properties).containsKey("category")).isTrue();
        }
        Object categoryProperties =
                api.getComponents().getSchemas().get("Category").getProperties();
        assertThat(categoryProperties).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) categoryProperties).containsKey("effectivelyVisible"))
                .isTrue();
        assertThat(api.getPaths().get("/categories").getGet().getResponses().keySet())
                .contains("400", "401", "403");
    }
}
