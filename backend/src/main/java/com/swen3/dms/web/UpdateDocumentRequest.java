package com.swen3.dms.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDocumentRequest(@NotBlank @Size(max = 255) String title) {
}
