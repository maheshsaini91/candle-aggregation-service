package com.multibank.aggregationservice.entities;

import com.multibank.aggregationservice.enums.SymbolStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "symbols")
public class Symbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, unique = true, length = 32)
    private String symbol;

    @Column(name = "status", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private SymbolStatus status;

    @Column(name = "base_price", nullable = false)
    private Double basePrice = 100.0;

    public Symbol() {
    }

    public Symbol(Long id, String symbol, SymbolStatus status, Double basePrice) {
        this.id = id;
        this.symbol = symbol;
        this.status = status;
        this.basePrice = basePrice != null ? basePrice : 100.0;
    }

    public Symbol(String symbol, SymbolStatus status) {
        this(null, symbol, status, 100.0);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public SymbolStatus getStatus() {
        return status;
    }

    public void setStatus(SymbolStatus status) {
        this.status = status;
    }

    public Double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(Double basePrice) {
        this.basePrice = basePrice != null ? basePrice : 100.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Symbol symbol1 = (Symbol) o;
        return Objects.equals(id, symbol1.id) && Objects.equals(symbol, symbol1.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, symbol);
    }

    @Override
    public String toString() {
        return "Symbol{id=" + id + ", symbol='" + symbol + "', status=" + status + "}";
    }
}
