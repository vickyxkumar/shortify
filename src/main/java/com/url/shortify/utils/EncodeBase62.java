package com.url.shortify.utils;

import org.springframework.stereotype.Component;

@Component
public class EncodeBase62 {
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public String encodeBase62(long num) {
        StringBuilder sb = new StringBuilder();
    
        while (num > 0) {
            int rem = (int) (num % 62);
            sb.append(BASE62.charAt(rem));
            num /= 62;
        }
    
        return sb.reverse().toString();
    }
}
