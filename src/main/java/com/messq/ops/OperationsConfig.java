package com.messq.ops;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.*;
@Configuration @EnableScheduling
public class OperationsConfig {
 @Bean Clock clock(@Value("${app.timezone:Asia/Kolkata}")String zone){return Clock.system(ZoneId.of(zone));}
}
