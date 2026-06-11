package com.fueledbychai.datarecording.parquet;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Identifies one Hive-style partition directory:
 * {@code {dataType}/exchange={EX}/symbol={SYM}/date=YYYY-MM-DD}.
 *
 * <p>One open Parquet file exists per key at a time. {@code exchange} and {@code symbol}
 * are sanitized for the filesystem (the true, unsanitized values are still carried in the
 * {@code exchange}/{@code symbol} columns inside the file), and {@code date} is the UTC
 * calendar day of the row's {@code recvTimestampMicros} so files never straddle a day.
 */
public record PartitionKey(String dataType, String exchange, String symbol, String date) {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    /** Build a key, deriving the UTC date partition from a row's receive time. */
    public static PartitionKey of(String dataType, String exchange, String symbol, long recvTimestampMicros) {
        String date = DATE.format(Instant.ofEpochMilli(recvTimestampMicros / 1_000L));
        return new PartitionKey(dataType, sanitize(exchange), sanitize(symbol), date);
    }

    /** Relative directory path under the recorder root, forward-slash separated. */
    public String relativeDir() {
        return dataType + "/exchange=" + exchange + "/symbol=" + symbol + "/date=" + date;
    }

    /** Replace filesystem/Hive-hostile characters (e.g. '/' in {@code SOL/USDT}) with '_'. */
    static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "UNKNOWN";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            sb.append(ok ? c : '_');
        }
        return sb.toString();
    }
}
