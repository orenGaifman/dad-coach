package com.dadcoach.api.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {success, data, error_code, error_message}: business rejections are HTTP 200 success:false, so the agent can explain them. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResponse(boolean success, Object data, @JsonProperty("error_code") String errorCode,
                           @JsonProperty("error_message") String errorMessage) {

    public static ToolResponse ok(Object data) {
        return new ToolResponse(true, data, null, null);
    }

    public static ToolResponse failure(String code, String message) {
        return new ToolResponse(false, null, code, message);
    }
}
