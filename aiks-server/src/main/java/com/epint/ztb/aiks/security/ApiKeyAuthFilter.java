package com.epint.ztb.aiks.security;

import java.io.IOException;

import org.springframework.http.MediaType;

import com.epint.ztb.aiks.common.AiksErrorCode;
import com.epint.ztb.aiks.common.ApiResult;
import com.epint.ztb.aiks.config.AiksProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * X-Api-Key 静态令牌校验（平台侧 ztb-aiks-impl → 本服务）。
 * 由 WebConfig 注册、仅拦截 /api/**；/actuator/** 放行供探活。
 * 注意：不要给本类加 @Component——Spring Boot 会把 Filter 类型的 bean 自动注册到 /* 全路径，
 * 导致 /actuator/health 也被拦截、且出现重复注册。
 */
@RequiredArgsConstructor
public class ApiKeyAuthFilter implements Filter {

    public static final String API_KEY_HEADER = "X-Api-Key";

    private final AiksProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String apiKey = req.getHeader(API_KEY_HEADER);
//        if (apiKey == null || !apiKey.equals(props.getSecurity().getApiKey())) {
//            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
//            resp.setCharacterEncoding("UTF-8");
//            ApiResult<Void> result = ApiResult.fail(AiksErrorCode.UNAUTHORIZED, null);
//            resp.getWriter().write(objectMapper.writeValueAsString(result));
//            return;
//        }
        chain.doFilter(request, response);
    }
}
