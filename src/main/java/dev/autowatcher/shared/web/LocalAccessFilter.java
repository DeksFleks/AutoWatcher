package dev.autowatcher.shared.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(name="app.security.enabled",havingValue="true",matchIfMissing=true)
public class LocalAccessFilter extends OncePerRequestFilter {
    private final String token;
    public LocalAccessFilter() {
        byte[] value=new byte[32];new SecureRandom().nextBytes(value);token=HexFormat.of().formatHex(value);
    }
    public String token() { return token; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        long start=System.nanoTime();
        try { handle(request,response,chain); }
        finally {
            logger.info("Панель "+request.getMethod()+" "+request.getRequestURI()+" HTTP="+response.getStatus()
                +" время="+(System.nanoTime()-start)/1_000_000+" мс");
        }
    }
    private void handle(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("X-Frame-Options","DENY");
        response.setHeader("Referrer-Policy","same-origin");
        response.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'");
        response.setHeader("Cache-Control","no-store");
        String host=request.getServerName();
        if(!Set.of("localhost","127.0.0.1","[::1]","::1").contains(host)) {
            denied(response);return;
        }
        String origin=request.getHeader("Origin");
        String expected=request.getScheme()+"://"+request.getHeader("Host");
        if(origin!=null && !origin.equals(expected)) { denied(response);return; }
        if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) {
            String supplied=Objects.toString(request.getHeader("X-AutoWatcher-CSRF"),"");
            if(!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
                denied(response);return;
            }
        }
        chain.doFilter(request,response);
    }
    private void denied(HttpServletResponse response) throws IOException {
        response.setStatus(403);response.setContentType("application/problem+json");
        response.getWriter().write("{\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Local session required\"}");
    }
}
