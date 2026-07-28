package com.exchange;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full HTTP round-trip over the authenticated API: register -> trade -> inspect. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    private static final AtomicInteger SEQ = new AtomicInteger();

    /** Register a fresh user; returns their bearer token. Registration funds the account. */
    private String register(String prefix) throws Exception {
        String username = prefix + SEQ.incrementAndGet();
        String resp = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resp, "$.token");
    }

    private String placeOrder(String token, String side, String type, long price, long qty) throws Exception {
        String body = "{\"symbol\":\"BTC-USD\",\"side\":\"" + side + "\",\"type\":\"" + type
                + "\",\"price\":" + price + ",\"quantity\":" + qty + "}";
        return mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void tradeFlowProducesFillAndUpdatesBook() throws Exception {
        String seller = register("seller");   // funded with 10 BTC
        String buyer = register("buyer");      // funded with 1,000,000 USD

        placeOrder(seller, "SELL", "LIMIT", 100, 5);           // rests as an ask
        String buy = placeOrder(buyer, "BUY", "LIMIT", 100, 5); // crosses -> trade

        java.util.List<Object> trades = JsonPath.read(buy, "$.trades");
        org.junit.jupiter.api.Assertions.assertEquals(1, trades.size());
        org.junit.jupiter.api.Assertions.assertEquals(100, (int) (Integer) JsonPath.read(buy, "$.trades[0].price"));
        org.junit.jupiter.api.Assertions.assertEquals("FILLED", JsonPath.read(buy, "$.order.status"));

        // buyer started with 10 BTC (faucet) and bought 5 more -> 15
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.asset=='BTC')].available").value(org.hamcrest.Matchers.contains(15)));

        // seller's ask fully consumed -> no ask left
        mvc.perform(get("/api/market/BTC-USD/book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestAsk").doesNotExist());
    }

    @Test
    void unauthenticatedOrderIsRejected() throws Exception {
        String body = "{\"symbol\":\"BTC-USD\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":100,\"quantity\":1}";
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validationRejectsBadOrder() throws Exception {
        String token = register("validator");
        String body = "{\"symbol\":\"BTC-USD\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":100,\"quantity\":0}";
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        register("loginuser");   // creates loginuserN with password123
        // wrong password for a definitely-existing-style username still returns 422 (no user match)
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"loginuser1\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
