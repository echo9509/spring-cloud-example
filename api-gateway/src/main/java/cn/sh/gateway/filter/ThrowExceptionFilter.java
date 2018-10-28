package cn.sh.gateway.filter;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;

/**
 * @author sh
 */
@Component
public class ThrowExceptionFilter extends ZuulFilter {

    private final Logger logger = LoggerFactory.getLogger(ThrowExceptionFilter.class);

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return -1;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() throws ZuulException {
        logger.info("这是一个前置过滤器，它会抛出一个运行时异常");
/*        RequestContext context = RequestContext.getCurrentContext();
        try {*/
            doSomething();
/*        } catch (Exception e) {
            context.set("error.status_code", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            context.set("error.exception", e);
            context.setThrowable(e);
        }*/
        return null;
    }

    private void doSomething() {
        throw new RuntimeException("发生运行时异常");
    }
}
