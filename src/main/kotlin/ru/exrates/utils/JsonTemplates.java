package ru.exrates.utils;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;

public class JsonTemplates {

    @NoArgsConstructor
    @Getter @Setter
    public static class ExchangePayload{
        private String exchange;
        private String timeout;
        private String[] pairs;

        @Override
        public String toString() {
            return "ExchangePayload{" +
                    "exchange='" + exchange + '\'' +
                    ", timeout='" + timeout + '\'' +
                    ", pairs=" + Arrays.toString(pairs) +
                    '}';
        }
    }


}
