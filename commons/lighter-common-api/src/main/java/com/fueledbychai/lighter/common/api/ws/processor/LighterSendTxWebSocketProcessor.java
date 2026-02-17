package com.fueledbychai.lighter.common.api.ws.processor;

import org.json.JSONObject;

import com.fueledbychai.lighter.common.api.ws.model.LighterSendTxResponse;
import com.fueledbychai.websocket.AbstractWebSocketProcessor;
import com.fueledbychai.websocket.IWebSocketClosedListener;

public class LighterSendTxWebSocketProcessor extends AbstractWebSocketProcessor<LighterSendTxResponse> {

    public LighterSendTxWebSocketProcessor(IWebSocketClosedListener listener) {
        super(listener);
    }

    @Override
    protected LighterSendTxResponse parseMessage(String message) {
        JSONObject root = new JSONObject(message);
        Object idValue = root.opt("id");
        String requestId = toIdString(idValue);

        Integer code = parseInteger(root.opt("code"));
        String responseMessage = normalizeString(root.optString("msg", null));
        if ((code == null || responseMessage == null) && root.opt("result") instanceof JSONObject result) {
            if (code == null) {
                code = parseInteger(result.opt("code"));
            }
            if (responseMessage == null) {
                responseMessage = normalizeString(result.optString("msg", null));
            }
        }

        if (responseMessage == null && root.has("error")) {
            Object errorValue = root.opt("error");
            responseMessage = errorValue == null || errorValue == JSONObject.NULL ? null : errorValue.toString();
        }

        if (code == null && responseMessage == null) {
            return null;
        }
        return new LighterSendTxResponse(requestId, code, responseMessage, root.toString());
    }

    protected Integer parseInteger(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (Exception ex) {
            logger.warn("Unable to parse integer value '{}'", value);
            return null;
        }
    }

    protected String toIdString(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        String id = value.toString();
        return id.isBlank() ? null : id;
    }

    protected String normalizeString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
