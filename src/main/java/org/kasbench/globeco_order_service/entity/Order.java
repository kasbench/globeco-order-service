package org.kasbench.globeco_order_service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "\"order\"")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "blotter_id")
    private Blotter blotter;

    @ManyToOne(optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private Status status;

    @Column(name = "portfolio_id", length = 24, nullable = false)
    private String portfolioId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_type_id", nullable = false)
    private OrderType orderType;

    @Column(name = "security_id", length = 24, nullable = false)
    private String securityId;

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal quantity;

    @Column(name = "limit_price", precision = 18, scale = 8)
    private BigDecimal limitPrice;

    @Column(name = "trade_order_id")
    private Integer tradeOrderId;

    @Column(name = "order_timestamp", nullable = false)
    private OffsetDateTime orderTimestamp;

    @Column(nullable = false)
    private Integer version;

    public Order() {}

    public Order(Integer id, Blotter blotter, Status status, String portfolioId, OrderType orderType, String securityId, BigDecimal quantity, BigDecimal limitPrice, Integer tradeOrderId, OffsetDateTime orderTimestamp, Integer version) {
        this.id = id;
        this.blotter = blotter;
        this.status = status;
        this.portfolioId = portfolioId;
        this.orderType = orderType;
        this.securityId = securityId;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.tradeOrderId = tradeOrderId;
        this.orderTimestamp = orderTimestamp;
        this.version = version;
    }

    // Getters and setters for all fields
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Blotter getBlotter() { return blotter; }
    public void setBlotter(Blotter blotter) { this.blotter = blotter; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getPortfolioId() { return portfolioId; }
    public void setPortfolioId(String portfolioId) { this.portfolioId = portfolioId; }
    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }
    public String getSecurityId() { return securityId; }
    public void setSecurityId(String securityId) { this.securityId = securityId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public void setLimitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; }
    public Integer getTradeOrderId() { return tradeOrderId; }
    public void setTradeOrderId(Integer tradeOrderId) { this.tradeOrderId = tradeOrderId; }
    public OffsetDateTime getOrderTimestamp() { return orderTimestamp; }
    public void setOrderTimestamp(OffsetDateTime orderTimestamp) { this.orderTimestamp = orderTimestamp; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    // equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(id, order.id) &&
                Objects.equals(blotter, order.blotter) &&
                Objects.equals(status, order.status) &&
                Objects.equals(portfolioId, order.portfolioId) &&
                Objects.equals(orderType, order.orderType) &&
                Objects.equals(securityId, order.securityId) &&
                Objects.equals(quantity, order.quantity) &&
                Objects.equals(limitPrice, order.limitPrice) &&
                Objects.equals(tradeOrderId, order.tradeOrderId) &&
                Objects.equals(orderTimestamp, order.orderTimestamp) &&
                Objects.equals(version, order.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, blotter, status, portfolioId, orderType, securityId, quantity, limitPrice, tradeOrderId, orderTimestamp, version);
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", blotter=" + blotter +
                ", status=" + status +
                ", portfolioId='" + portfolioId + '\'' +
                ", orderType=" + orderType +
                ", securityId='" + securityId + '\'' +
                ", quantity=" + quantity +
                ", limitPrice=" + limitPrice +
                ", tradeOrderId=" + tradeOrderId +
                ", orderTimestamp=" + orderTimestamp +
                ", version=" + version +
                '}';
    }

    // Builder
    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private Integer id;
        private Blotter blotter;
        private Status status;
        private String portfolioId;
        private OrderType orderType;
        private String securityId;
        private BigDecimal quantity;
        private BigDecimal limitPrice;
        private Integer tradeOrderId;
        private OffsetDateTime orderTimestamp;
        private Integer version;

        public Builder id(Integer id) { this.id = id; return this; }
        public Builder blotter(Blotter blotter) { this.blotter = blotter; return this; }
        public Builder status(Status status) { this.status = status; return this; }
        public Builder portfolioId(String portfolioId) { this.portfolioId = portfolioId; return this; }
        public Builder orderType(OrderType orderType) { this.orderType = orderType; return this; }
        public Builder securityId(String securityId) { this.securityId = securityId; return this; }
        public Builder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public Builder limitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; return this; }
        public Builder tradeOrderId(Integer tradeOrderId) { this.tradeOrderId = tradeOrderId; return this; }
        public Builder orderTimestamp(OffsetDateTime orderTimestamp) { this.orderTimestamp = orderTimestamp; return this; }
        public Builder version(Integer version) { this.version = version; return this; }
        public Order build() {
            return new Order(id, blotter, status, portfolioId, orderType, securityId, quantity, limitPrice, tradeOrderId, orderTimestamp, version);
        }
    }

    // Manual builder test
    public static void main(String[] args) {
        Order o = Order.builder().tradeOrderId(123).build();
        System.out.println("BUILDER TEST: tradeOrderId = " + o.getTradeOrderId());
    }
} 