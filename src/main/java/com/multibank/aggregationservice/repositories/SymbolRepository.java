package com.multibank.aggregationservice.repositories;

import com.multibank.aggregationservice.entities.Symbol;
import com.multibank.aggregationservice.enums.SymbolStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SymbolRepository extends JpaRepository<Symbol, Long> {
    @Query("SELECT s FROM Symbol s WHERE s.status = :status ORDER BY s.symbol")
    List<Symbol> findByStatus(@Param("status") SymbolStatus status);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Symbol s WHERE s.symbol = :symbol")
    boolean existsBySymbol(@Param("symbol") String symbol);
}
