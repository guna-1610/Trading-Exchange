package com.exchange.web;

import com.exchange.dto.OrderView;
import com.exchange.dto.PlaceOrderRequest;
import com.exchange.dto.PlaceOrderResponse;
import com.exchange.service.ExchangeService;
import com.exchange.service.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final ExchangeService exchange;

    public OrderController(ExchangeService exchange) { this.exchange = exchange; }

    private static String accountId(Jwt jwt) { return jwt.getClaimAsString("accountId"); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceOrderResponse place(@Valid @RequestBody PlaceOrderRequest req,
                                    @AuthenticationPrincipal Jwt jwt) {
        // account comes from the token, never the request body
        return exchange.placeOrder(accountId(jwt), req.symbol(), req.side(),
                req.type(), req.price(), req.quantity());
    }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return requireOwned(exchange.getOrder(id), jwt);
    }

    @DeleteMapping("/{id}")
    public OrderView cancel(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        requireOwned(exchange.getOrder(id), jwt);   // only the owner may cancel
        return exchange.cancel(id);
    }

    /** Hide other users' orders behind a 404 rather than confirming they exist. */
    private OrderView requireOwned(OrderView order, Jwt jwt) {
        if (!order.accountId().equals(accountId(jwt)))
            throw new NotFoundException("order not found: " + order.id());
        return order;
    }
}
