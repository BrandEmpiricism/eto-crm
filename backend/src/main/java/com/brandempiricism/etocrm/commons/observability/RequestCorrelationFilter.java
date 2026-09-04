package com.brandempiricism.etocrm.commons.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER="X-Request-Id";
    public static final String BUSINESS_TRANSACTION_HEADER="X-Business-Transaction-Id";
    private static final Pattern SAFE_ID=Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Pattern TRACE_PARENT=Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");
    private static final SecureRandom RANDOM=new SecureRandom();

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String requestId=safe(request.getHeader(REQUEST_ID_HEADER),UUID.randomUUID().toString());
        String traceId=traceId(request.getHeader("traceparent"));
        String transactionId=safe(request.getHeader(BUSINESS_TRANSACTION_HEADER),requestId);
        MDC.put("requestId",requestId);MDC.put("traceId",traceId);MDC.put("businessTransactionId",transactionId);
        response.setHeader(REQUEST_ID_HEADER,requestId);response.setHeader("traceparent","00-"+traceId+"-"+randomHex(8)+"-01");
        try{chain.doFilter(request,response);}finally{MDC.remove("requestId");MDC.remove("traceId");MDC.remove("businessTransactionId");}
    }
    private static String safe(String candidate,String fallback){return candidate!=null&&SAFE_ID.matcher(candidate).matches()?candidate:fallback;}
    private static String traceId(String traceparent){if(traceparent!=null){var matcher=TRACE_PARENT.matcher(traceparent.toLowerCase());if(matcher.matches()&&!matcher.group(1).equals("0".repeat(32)))return matcher.group(1);}return randomHex(16);}
    private static String randomHex(int bytes){byte[] value=new byte[bytes];RANDOM.nextBytes(value);return HexFormat.of().formatHex(value);}
}
