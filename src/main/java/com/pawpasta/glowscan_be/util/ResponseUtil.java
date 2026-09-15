package com.pawpasta.glowscan_be.util;

//Thật Ra Dùng Phần Này Để Cố Định DTO trả về cho FE
public record ResponseUtil<T>(
        boolean success,
        String message,
        T data){
    public static <T> ResponseUtil<T> success(String message, T data){
        return new ResponseUtil<T>(true, message, data);
    }

    public static ResponseUtil<Void> success(String message){
        return new ResponseUtil<>(true, message, null);
    }

    public static ResponseUtil<Void> error(String message){
        return new ResponseUtil<>(false, message, null);
    }

}


