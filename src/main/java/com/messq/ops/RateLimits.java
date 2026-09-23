package com.messq.ops;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import jakarta.servlet.http.*;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class RateLimits implements WebMvcConfigurer,HandlerInterceptor {
 private record Bucket(long minute,int count){}
 private final ConcurrentHashMap<String,Bucket> counts=new ConcurrentHashMap<>();
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(this).addPathPatterns("/api/auth/**","/api/workspace/actions/ai.ask");}
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler)throws Exception{
  long minute=System.currentTimeMillis()/60000;String key=request.getRemoteAddr()+":"+request.getRequestURI();int limit=request.getRequestURI().contains("ai.ask")?15:60;
  if(counts.size()>10000)counts.entrySet().removeIf(e->e.getValue().minute()<minute);
  Bucket current=counts.compute(key,(k,b)->b==null||b.minute()!=minute?new Bucket(minute,1):new Bucket(minute,b.count()+1));
  if(current.count()>limit){response.setStatus(429);response.setHeader("Retry-After","60");response.setContentType("application/json");response.getWriter().write("{\"message\":\"Too many attempts. Try again in a minute.\"}");return false;}return true;
 }
}
