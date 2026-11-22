package com.meditriaje.dto.affiliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;

public record CommitBatchRequest(
        @JsonProperty("modoCommit")
        @Pattern(regexp = "VALID_ROWS|ATOMIC_ALL", message = "modoCommit debe ser VALID_ROWS o ATOMIC_ALL")
        String commitMode
) {
}
