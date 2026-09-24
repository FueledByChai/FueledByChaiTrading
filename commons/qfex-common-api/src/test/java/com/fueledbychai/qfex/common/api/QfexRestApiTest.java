package com.fueledbychai.qfex.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fueledbychai.data.InstrumentDescriptor;

class QfexRestApiTest {

    @Test
    void parsesRefdataWithStringNumbers() throws Exception {
        JsonNode root = QfexRestApi.MAPPER.readTree("""
                [{"symbol":"GOLD-USD","base_asset":"GOLD","quote_asset":"USD","status":"ACTIVE",
                  "product_category":"COMMODITY","tick_size":"0.1","lot_size":"0.0001","min_quantity":"0.001",
                  "max_quantity":"100","min_price":"3000.0","max_price":"5000.0","default_max_leverage":20},
                 {"symbol":"EUR-USD","status":"DELISTED","tick_size":"0.00001","lot_size":"1"}]""");

        List<QfexContract> contracts = QfexRestApi.parseRefdata(root);

        assertEquals(2, contracts.size());
        QfexContract gold = contracts.get(0);
        assertTrue(gold.isActive());
        assertEquals(0, new BigDecimal("0.1").compareTo(gold.tickSize()));
        assertEquals(0, new BigDecimal("0.001").compareTo(gold.minQuantity()));
        InstrumentDescriptor d = QfexRestApi.toDescriptor(gold);
        assertEquals("GOLD-USD", d.getExchangeSymbol());
        assertEquals(0, new BigDecimal("0.0001").compareTo(d.getOrderSizeIncrement()));
    }

    @Test
    void candlesAreReturnedOldestFirst() throws Exception {
        JsonNode root = QfexRestApi.MAPPER.readTree("""
                {"candles":[{"startedAt":"2026-09-24T10:02:00.000Z","close":"3"},
                            {"startedAt":"2026-09-24T10:00:00.000Z","close":"1"},
                            {"startedAt":"2026-09-24T10:01:00.000Z","close":"2"}]}""");

        JsonNode sorted = QfexRestApi.sortCandlesOldestFirst(root);

        assertEquals("1", sorted.path("candles").get(0).path("close").asText());
        assertEquals("3", sorted.path("candles").get(2).path("close").asText());
    }
}
