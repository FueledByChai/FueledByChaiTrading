package com.fueledbychai.binance.ws.partialbook;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Example/test class showing how to parse Binance WebSocket depth update JSON.
 */
public class OrderBookSnapshotTest {

    public static void main(String[] args) {
        String json = """
                {
                    "lastUpdateId": 11762451863,
                    "bids": [
                        [
                            "101630.12000000",
                            "0.17052000"
                        ],
                        [
                            "101626.11000000",
                            "0.06023000"
                        ],
                        [
                            "101626.10000000",
                            "0.06053000"
                        ],
                        [
                            "101626.09000000",
                            "0.17893000"
                        ],
                        [
                            "101626.08000000",
                            "0.00012000"
                        ]
                    ],
                    "asks": [
                        [
                            "101630.13000000",
                            "0.53744000"
                        ],
                        [
                            "101630.98000000",
                            "0.00012000"
                        ],
                        [
                            "101630.99000000",
                            "0.02903000"
                        ],
                        [
                            "101631.00000000",
                            "0.04370000"
                        ],
                        [
                            "101631.32000000",
                            "0.00012000"
                        ]
                    ]
                }
                """;
        try {
            ObjectMapper mapper = new ObjectMapper();
            OrderBookSnapshot snapshot = mapper.readValue(json, OrderBookSnapshot.class);

            System.out.println("Last Update ID: " + snapshot.getLastUpdateId());
            System.out.println("Best Bid: " + snapshot.getBestBid());
            System.out.println("Best Ask: " + snapshot.getBestAsk());
            System.out.println("Spread: " + snapshot.getSpread());
            System.out.println("Mid Price: " + snapshot.getMidPrice());

            System.out.println("\\nBids:");
            for (PriceLevel bid : snapshot.getBids()) {
                System.out.println("  " + bid.getPrice() + " @ " + bid.getQuantity());
            }

            System.out.println("\\nAsks:");
            for (PriceLevel ask : snapshot.getAsks()) {
                System.out.println("  " + ask.getPrice() + " @ " + ask.getQuantity());
            }
            System.out.println("\\nFull object:");
            System.out.println(snapshot);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}