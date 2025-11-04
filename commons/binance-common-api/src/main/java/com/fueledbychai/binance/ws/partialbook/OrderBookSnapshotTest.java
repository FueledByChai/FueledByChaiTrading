package com.fueledbychai.binance.ws.partialbook;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Example/test class showing how to parse Binance WebSocket depth update JSON.
 */
public class OrderBookSnapshotTest {

    public static void main(String[] args) {
        String json = """
                {
                    "stream": "btcusdt@depth5@100ms",
                    "data": {
                        "e": "depthUpdate",
                        "E": 1762269277814,
                        "T": 1762269277813,
                        "s": "BTCUSDT",
                        "U": 9080746693627,
                        "u": 9080746702163,
                        "pu": 9080746691210,
                        "b": [
                            [
                                "104092.80",
                                "6.599"
                            ],
                            [
                                "104092.70",
                                "0.019"
                            ],
                            [
                                "104092.60",
                                "0.001"
                            ],
                            [
                                "104092.40",
                                "0.069"
                            ],
                            [
                                "104092.10",
                                "0.002"
                            ]
                        ],
                        "a": [
                            [
                                "104092.90",
                                "0.895"
                            ],
                            [
                                "104093.00",
                                "0.006"
                            ],
                            [
                                "104093.60",
                                "0.002"
                            ],
                            [
                                "104094.20",
                                "0.002"
                            ],
                            [
                                "104094.70",
                                "0.003"
                            ]
                        ]
                    }
                }
                """;

        try {
            ObjectMapper mapper = new ObjectMapper();
            OrderBookSnapshot snapshot = mapper.readValue(json, OrderBookSnapshot.class);

            System.out.println("Stream: " + snapshot.getStream());
            System.out.println("Symbol: " + snapshot.getData().getSymbol());
            System.out.println("Event Type: " + snapshot.getData().getEventType());
            System.out.println("Event Time: " + snapshot.getData().getEventTime());

            System.out.println("\\nBids:");
            for (PriceLevel bid : snapshot.getData().getBids()) {
                System.out.println("  " + bid.getPrice() + " @ " + bid.getQuantity());
            }

            System.out.println("\\nAsks:");
            for (PriceLevel ask : snapshot.getData().getAsks()) {
                System.out.println("  " + ask.getPrice() + " @ " + ask.getQuantity());
            }

            System.out.println("\\nFull object:");
            System.out.println(snapshot);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}