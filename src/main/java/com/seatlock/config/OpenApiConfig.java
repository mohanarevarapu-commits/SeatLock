package com.seatlock.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API metadata shown in Swagger UI at {@code /swagger-ui.html}. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI seatLockOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SeatLock API")
                .version("0.1.0")
                .description("""
                        Ticket reservation API. Hold seats for a limited time, then confirm the hold \
                        into a booking. A seat is never held or sold to two customers at once. \
                        Errors are RFC 9457 problem responses.""")
                .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
