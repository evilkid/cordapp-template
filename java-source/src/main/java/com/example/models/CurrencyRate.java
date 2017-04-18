package com.example.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Created by evilkid on 4/18/17.
 */
@JsonDeserialize
public class CurrencyRate {
    @JsonProperty
    private String from;
    @JsonProperty
    private String to;
    @JsonProperty
    private float rate;

    public CurrencyRate() {

    }

    public CurrencyRate(String from, String to, float rate) {
        this.from = from.toUpperCase();
        this.to = to.toUpperCase();
        this.rate = rate;
    }

    public String getFrom() {
        return from.toUpperCase();
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to.toUpperCase();
    }

    public void setTo(String to) {
        this.to = to;
    }

    public float getRate() {
        return rate;
    }

    public void setRate(float rate) {
        this.rate = rate;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CurrencyRate that = (CurrencyRate) o;

        return from.equals(that.from) && to.equals(that.to);
    }

    @Override
    public int hashCode() {
        int result = from.hashCode();
        result = 31 * result + to.hashCode();
        return result;
    }
}
