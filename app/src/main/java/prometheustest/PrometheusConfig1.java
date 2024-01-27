package prometheustest;


import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration
public class PrometheusConfig1 {

    @Value("${prometheus.port:8080}")
    private int prometheusPort;

    @Bean
    public CollectorRegistry prometheusMeterRegistry() {
        return new CollectorRegistry();
    }

    @Bean
    public HTTPServer httpServer() throws Exception {
        var inetAddress = new InetSocketAddress("localhost", 9191);
        var result = new HTTPServer(inetAddress, prometheusMeterRegistry());
        return result;
    }


}