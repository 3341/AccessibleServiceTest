package com.byq.acceservice;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;

@Entity(nameInDb = "question_db")
public class QuestionDB {
    public String question;
    public int qNum;
    public String currentAnswer;
    public int currentIndex;
    @Generated(hash = 2020688553)
    public QuestionDB(String question, int qNum, String currentAnswer,
            int currentIndex) {
        this.question = question;
        this.qNum = qNum;
        this.currentAnswer = currentAnswer;
        this.currentIndex = currentIndex;
    }
    @Generated(hash = 528052968)
    public QuestionDB() {
    }
    public String getQuestion() {
        return this.question;
    }
    public void setQuestion(String question) {
        this.question = question;
    }
    public int getQNum() {
        return this.qNum;
    }
    public void setQNum(int qNum) {
        this.qNum = qNum;
    }
    public String getCurrentAnswer() {
        return this.currentAnswer;
    }
    public void setCurrentAnswer(String currentAnswer) {
        this.currentAnswer = currentAnswer;
    }
    public int getCurrentIndex() {
        return this.currentIndex;
    }
    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }
}
