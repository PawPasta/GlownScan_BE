package com.pawpasta.glowscan_be.shared.response;

//Thật Ra Dùng Phần Này Để Cố Định DTO trả về cho FE
public record ApiResponse<T>(
        boolean success,
        String message,
        T data){
    public static <T> ApiResponse<T> success(String message, T data){
        return new ApiResponse<T>(true, message, data);
    }

    public static ApiResponse<Void> success(String message){
        return new ApiResponse<>(true, message, null);
    }

    public static ApiResponse<Void> error(String message){
        return new ApiResponse<>(false, message, null);
    }

}
