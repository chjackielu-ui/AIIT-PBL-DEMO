package cn.axg.platform;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    /** 业务校验异常：统一返回 {message}，业务文案不依赖 Spring 默认错误页配置。 */
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> business(ResponseStatusException e) {
        String reason = e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", reason == null ? "请求失败" : reason));
    }
    @ExceptionHandler({ClassCastException.class, IllegalArgumentException.class, HttpMessageNotReadableException.class, NullPointerException.class})
    ResponseEntity<?> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "请求字段格式有误，请检查表单或重新选择文件。"));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> large(Exception e) {
        return ResponseEntity.status(413).body(Map.of("message", "单文件不能超过 20 MB。"));
    }
}
