package dev.autowatcher.shared;

import dev.autowatcher.shared.web.LocalAccessFilter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalAccessFilterTest {
    @Test void mutationRequiresSessionTokenAndSameOrigin() throws Exception {
        var filter=new LocalAccessFilter();
        var request=new MockHttpServletRequest("POST","/api/v1/settings/telegram");
        request.setServerName("localhost");request.addHeader("Host","localhost:8080");
        var response=new MockHttpServletResponse();var passed=new AtomicBoolean();
        filter.doFilter(request,response,(req,res)->passed.set(true));
        assertEquals(403,response.getStatus());assertFalse(passed.get());
        request=new MockHttpServletRequest("POST","/api/v1/settings/telegram");
        request.setServerName("localhost");request.addHeader("Host","localhost:8080");
        request.addHeader("X-AutoWatcher-CSRF",filter.token());
        response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->passed.set(true));
        assertTrue(passed.get());
    }
    @Test void untrustedHostAndForeignOriginAreDenied() throws Exception {
        var filter=new LocalAccessFilter();
        var request=new MockHttpServletRequest("GET","/api/v1/session");
        request.setServerName("evil.test");
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->fail("Must reject host"));
        assertEquals(403,response.getStatus());
        request=new MockHttpServletRequest("GET","/api/v1/session");
        request.setServerName("localhost");request.addHeader("Host","localhost:8080");request.addHeader("Origin","https://evil.test");
        response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->fail("Must reject origin"));
        assertEquals(403,response.getStatus());
    }
}
