package com.example.demo.service;

import com.example.demo.dto.CreateOrderRequestDTO;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.exception.InsufficientStockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public Order placeOrder(CreateOrderRequestDTO request) {
        User user = verifyUserExists(request.getUserId());
        Order order = buildOrder(user);
        Set<OrderItem> orderItems = buildOrderItems(request, order);
        order.setItems(orderItems);
        return orderRepository.save(order);
    }

    private Set<OrderItem> buildOrderItems(CreateOrderRequestDTO request, Order order) {
        Set<OrderItem> orderItems = new HashSet<>();
        for (CreateOrderRequestDTO.OrderItemRequest itemReq : request.getItems()) {
            OrderItem orderItem = buildOrderItem(itemReq, order);
            orderItems.add(orderItem);
        }
        return orderItems;
    }

    private User verifyUserExists(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Order buildOrder(User user) {
        Order order = new Order();
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);
        order.setUser(user);
        return orderRepository.save(order);
    }

    private OrderItem buildOrderItem(CreateOrderRequestDTO.OrderItemRequest itemReq, Order order) {
        Product product = verifyProductExists(itemReq.getProductId());
        verifyStock(product, itemReq.getQuantity());
        decreaseStock(product, itemReq.getQuantity());

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setQuantity(itemReq.getQuantity());
        orderItem.setPrice(product.getPrice()); // Set correct product price
        return orderItemRepository.save(orderItem);
    }

    private Product verifyProductExists(Long productId) {
        if (productId == null) throw new IllegalArgumentException("Product ID cannot be null");
        return productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    private void verifyStock(Product product, int quantity) {
        if (product.getStockQuantity() == null)
            throw new IllegalStateException("Product stock quantity is not set");
        if (product.getStockQuantity() < quantity) {
            throw new InsufficientStockException("Insufficient stock for product: " + product.getName());
        }
    }

    // @Transactional // BUG C: Commented out, so no transaction management
    private void decreaseStock(Product product, int quantity) {
        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);
    }
    // Scheduled task: cancel PENDING orders older than 24h
    @Scheduled(cron = "0 0 * * * *") // runs every hour
    public void cancelStalePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        orderRepository.findAll().stream()
            .filter(o -> o.getStatus() == OrderStatus.PENDING && o.getOrderDate().isBefore(cutoff))
            .forEach(o -> {
                o.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(o);
            });
    }
}
