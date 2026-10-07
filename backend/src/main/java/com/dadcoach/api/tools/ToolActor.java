package com.dadcoach.api.tools;

import com.dadcoach.api.error.ApiException;
import com.dadcoach.domain.father.Father;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/** The caller of a tool: a WhatsApp number (E.164) and, once he exists, the father who owns it. */
public record ToolActor(String phone, Optional<Father> father) {

    public String ref() {
        return "whatsapp:" + phone;
    }

    public Father requireFather() {
        return father.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FATHER_NOT_FOUND",
                "No Dad Coach profile for this number yet - save the profile (save_user_profile) first"));
    }
}
