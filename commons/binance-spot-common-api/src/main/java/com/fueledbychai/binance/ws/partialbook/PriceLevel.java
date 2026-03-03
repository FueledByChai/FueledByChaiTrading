package com.fueledbychai.binance.ws.partialbook;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Represents a price level in the order book. Binance sends price levels as
 * arrays like ["104092.80", "6.599"] where: - First element is the price -
 * Second element is the quantity
 */
@JsonDeserialize(using = PriceLevel.PriceLevelDeserializer.class)
public class PriceLevel {

    private final String price;
    private final String quantity;

    public PriceLevel(String price, String quantity) {
        this.price = price;
        this.quantity = quantity;
    }

    public String getPrice() {
        return price;
    }

    public String getQuantity() {
        return quantity;
    }

    /**
     * Get price as BigDecimal for calculations.
     */
    public BigDecimal getPriceAsDecimal() {
        return new BigDecimal(price);
    }

    /**
     * Get quantity as BigDecimal for calculations.
     */
    public BigDecimal getQuantityAsDecimal() {
        return new BigDecimal(quantity);
    }

    /**
     * Check if this price level represents a removal (quantity = "0.00000000").
     */
    public boolean isRemoval() {
        return "0.00000000".equals(quantity) || new BigDecimal(quantity).compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public String toString() {
        return "PriceLevel{" + "price='" + price + '\'' + ", quantity='" + quantity + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        PriceLevel that = (PriceLevel) o;

        if (!price.equals(that.price))
            return false;
        return quantity.equals(that.quantity);
    }

    @Override
    public int hashCode() {
        int result = price.hashCode();
        result = 31 * result + quantity.hashCode();
        return result;
    }

    /**
     * Custom deserializer to handle the array format ["price", "quantity"].
     */
    public static class PriceLevelDeserializer extends JsonDeserializer<PriceLevel> {

        @Override
        public PriceLevel deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);

            if (!node.isArray() || node.size() != 2) {
                throw new IOException("Expected array with 2 elements for PriceLevel, got: " + node);
            }

            String price = node.get(0).asText();
            String quantity = node.get(1).asText();

            return new PriceLevel(price, quantity);
        }
    }
}