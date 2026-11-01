package org.example.orderservice;

import org.example.orderservice.client.StockFeignClient;
import org.example.orderservice.client.AccountFeignClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableFeignClients(clients = {StockFeignClient.class, AccountFeignClient.class})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}
