package com.byq.acceservice;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class ConfigGson {

    @JsonProperty("QA")
    public QADTO qa = new QADTO();

    @NoArgsConstructor
    @Data
    public static class QADTO {
        @JsonProperty("Enable")
        public boolean enable = true;
        @JsonProperty("answer")
        public String answer = "";
        @JsonProperty("isAcquireAnswerMode")
        public boolean isAcquireAnswerMode;
    }
}
