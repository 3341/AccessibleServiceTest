package com.byq.acceservice;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@Data
public class QuestionGson {

    @JsonProperty("question")
    public String question;
    @JsonProperty("qNum")
    public Integer qNum;
    @JsonProperty("currentAnswer")
    public String currentAnswer;
    @JsonProperty("currentIndex")
    public Integer currentIndex;
}
