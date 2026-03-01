package com.multibank.aggregationservice.repositories;

import com.multibank.aggregationservice.entities.CandleEntity;
import com.multibank.aggregationservice.entities.CandleId;
import com.multibank.aggregationservice.models.Candle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CandleJpaRepository extends JpaRepository<CandleEntity, CandleId> {

    @Query("SELECT new com.multibank.aggregationservice.models.Candle(" +
            "c.id.bucketStart, c.open, c.high, c.low, c.close, c.volume) " +
            "FROM CandleEntity c " +
            "WHERE c.id.symbol = :symbol AND c.id.intervalSec = :intervalSec " +
            "AND c.id.bucketStart BETWEEN :fromBucket AND :to " +
            "ORDER BY c.id.bucketStart")
    List<Candle> findFinalizedRange(
            @Param("symbol") String symbol,
            @Param("intervalSec") Long intervalSec,
            @Param("fromBucket") Long fromBucket,
            @Param("to") Long to);
}
