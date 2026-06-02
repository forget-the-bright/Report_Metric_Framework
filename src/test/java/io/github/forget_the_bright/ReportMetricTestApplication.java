package io.github.forget_the_bright;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 报表指标框架测试应用启动类
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/6/1
 */
@SpringBootApplication
public class ReportMetricTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportMetricTestApplication.class, args);
        System.out.println("========================================");
        System.out.println("报表指标框架测试应用启动成功！");
        System.out.println("访问地址: http://localhost:8080");
        System.out.println("Druid监控: http://localhost:8080/druid/index.html");
        System.out.println("========================================");
    }
}
