package com.ecommerce.media.asset.adapter.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class DirectUploadRequests {
    private DirectUploadRequests() {}

    public record CreateBatch(@NotEmpty @Size(max = 10) List<@NotNull @Valid FileDeclaration> files) {}

    public record FileDeclaration(
            @NotBlank @Size(max = 64) String clientFileId,
            @NotBlank @Size(max = 255) String filename,
            @NotBlank @Pattern(regexp = "image/(jpeg|png)") String contentType,
            @Min(1) @Max(5_242_880) long sizeBytes) {}

    public record IntentIds(@NotEmpty @Size(max = 10) List<@NotNull UUID> intentIds) {}
}
